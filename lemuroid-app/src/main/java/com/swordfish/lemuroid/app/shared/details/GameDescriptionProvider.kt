package com.swordfish.lemuroid.app.shared.details

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Short description of a game, taken from Wikipedia in the language of the device (English when there is no page
 * in that language). The game is looked up in Wikidata first: there each page is marked as a video game and with its
 * platforms, so a game is not mistaken for a film or a series with the same name.
 *
 * Results are remembered on the device, also the games without a description (retried after a while).
 * Network errors are not remembered.
 */
class GameDescriptionProvider(context: Context, private val okHttpClient: OkHttpClient) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).also {
            // Entries are never removed one by one, so the whole list is reset if it grows too much.
            if (it.all.size > MAX_ENTRIES) it.edit { clear() }
            forgetMissingOnce(it)
        }

    /** Description already known on the device, without going to the network. */
    fun getCachedDescription(game: Game): String? {
        return preferences.getString(cacheKey(game), null)?.takeUnless { it.startsWith(PREFIX_MISSING) }
    }

    suspend fun getDescription(game: Game): String? =
        withContext(Dispatchers.IO) {
            val key = cacheKey(game)
            val remembered = preferences.getString(key, null)
            if (remembered != null) {
                if (!remembered.startsWith(PREFIX_MISSING)) return@withContext remembered
                val missingSince = remembered.removePrefix(PREFIX_MISSING).toLongOrNull() ?: 0
                if (System.currentTimeMillis() - missingSince < MISSING_RETRY_MS) return@withContext null
            }

            val description =
                try {
                    // One game at a time: browsing quickly on TV sent many searches at once and Wikidata answered
                    // 429 (too many requests), so those games showed no description.
                    requestMutex.withLock {
                        preferences.getString(key, null)?.takeUnless { it.startsWith(PREFIX_MISSING) }
                            ?: fetchDescription(game)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Unable to get the description of ${game.title}")
                    return@withContext null
                }

            preferences.edit { putString(key, description ?: (PREFIX_MISSING + System.currentTimeMillis())) }
            description
        }

    private fun fetchDescription(game: Game): String? {
        val language = deviceLanguage()
        val entity = findGameEntity(searchTitle(game.title), game.systemId, language) ?: return null
        val sitelinks = entity.optJSONObject("sitelinks") ?: return null

        val (pageLanguage, pageTitle) =
            listOf(language, FALLBACK_LANGUAGE)
                .distinct()
                .firstNotNullOfOrNull { lang ->
                    sitelinks.optJSONObject("${lang}wiki")?.optString("title")?.takeIf { it.isNotBlank() }?.let { lang to it }
                } ?: return null

        val summaryUrl =
            HttpUrl.Builder()
                .scheme("https")
                .host("$pageLanguage.wikipedia.org")
                .addPathSegments("api/rest_v1/page/summary")
                .addPathSegment(pageTitle.replace(' ', '_'))
                .build()

        return getJson(summaryUrl)?.optString("extract")?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * The video game named [title], with a Wikipedia page in [language] or English, preferring the ones released on
     * the platform of [systemId]. The name is searched as it is, without colons (Wikidata finds nothing for
     * "Pokemon: Red Version"), among the games of the console by text (other punctuation, "Disney's Aladdin" for
     * "Aladdin", or a short name that other things also have, like "Contra") and lastly only the part before the
     * colon ("Galaga: Demons of Death" -> "Galaga"), which must be a game of the console.
     */
    private fun findGameEntity(
        title: String,
        systemId: String,
        language: String,
    ): JSONObject? {
        val platform = PLATFORMS[systemId]
        val pageLanguages = listOf(language, FALLBACK_LANGUAGE).distinct()

        listOf(title, title.replace(":", "")).distinct().forEach { name ->
            pickGame(searchByLabel(name), platform, pageLanguages)?.let { return it }
        }
        if (platform != null) {
            pickGame(searchByText(title, platform), platform, pageLanguages)?.let { return it }
        }
        if (platform != null && ":" in title) {
            val mainTitle = title.substringBefore(":").trim()
            pickGame(searchByLabel(mainTitle), platform, pageLanguages, onPlatformOnly = true)?.let { return it }
        }
        return null
    }

    /**
     * The first of [games] with a page, preferring the ones of [platform]: Wikidata often has an item without page for
     * the version of a console (e.g. "Street Fighter EX2 Plus") and the page in the item of the game.
     */
    private fun pickGame(
        games: List<JSONObject>,
        platform: String?,
        pageLanguages: List<String>,
        onPlatformOnly: Boolean = false,
    ): JSONObject? {
        val withPage =
            games.filter { game ->
                val sitelinks = game.optJSONObject("sitelinks")
                pageLanguages.any { sitelinks?.has("${it}wiki") == true }
            }
        val onPlatform = withPage.firstOrNull { platform != null && platform in claimIds(it, PROPERTY_PLATFORM) }
        return if (onPlatformOnly) onPlatform else onPlatform ?: withPage.firstOrNull()
    }

    /** Video games whose name or alias matches [name], in the order of the search. */
    private fun searchByLabel(name: String): List<JSONObject> {
        val searchUrl =
            WIKIDATA_API.newBuilder()
                .addQueryParameter("action", "wbsearchentities")
                .addQueryParameter("search", name)
                .addQueryParameter("language", FALLBACK_LANGUAGE)
                .addQueryParameter("type", "item")
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("format", "json")
                .build()

        val results = getJson(searchUrl)?.optJSONArray("search") ?: return emptyList()
        val ids = (0 until results.length()).mapNotNull { results.optJSONObject(it)?.optString("id") }
        return videoGames(ids)
    }

    /**
     * Video games of [platform] found by text, named like [title]: the same name without punctuation first, then a
     * name with all its words and up to [MAX_EXTRA_WORDS] more ("Disney's Aladdin" for "Aladdin").
     */
    private fun searchByText(
        title: String,
        platform: String,
    ): List<JSONObject> {
        val query = title.replace(":", " ")
        val searchUrl =
            WIKIDATA_API.newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("list", "search")
                .addQueryParameter("srsearch", "$query haswbstatement:$PROPERTY_PLATFORM=$platform")
                .addQueryParameter("srlimit", SEARCH_LIMIT.toString())
                .addQueryParameter("format", "json")
                .build()

        val results = getJson(searchUrl)?.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val ids = (0 until results.length()).mapNotNull { results.optJSONObject(it)?.optString("title") }
        val games = videoGames(ids, withLabels = true)

        val wanted = words(title)
        val sameName = games.filter { words(label(it)) == wanted }
        val similarName =
            games.filter {
                val words = words(label(it))
                words.containsAll(wanted) && words.size - wanted.size in 1..MAX_EXTRA_WORDS
            }
        return sameName + similarName
    }

    /** The items of [ids] that are video games or compilations, in the same order. */
    private fun videoGames(
        ids: List<String>,
        withLabels: Boolean = false,
    ): List<JSONObject> {
        if (ids.isEmpty()) return emptyList()
        val entitiesUrl =
            WIKIDATA_API.newBuilder()
                .addQueryParameter("action", "wbgetentities")
                .addQueryParameter("ids", ids.joinToString("|"))
                .addQueryParameter("props", if (withLabels) "claims|sitelinks|labels" else "claims|sitelinks")
                .apply { if (withLabels) addQueryParameter("languages", LABEL_LANGUAGES.joinToString("|")) }
                .addQueryParameter("format", "json")
                .build()

        val entities = getJson(entitiesUrl)?.optJSONObject("entities") ?: return emptyList()
        return ids.mapNotNull { entities.optJSONObject(it) }
            .filter { entity -> claimIds(entity, PROPERTY_INSTANCE_OF).any { it in VIDEO_GAME_TYPES } }
    }

    // Many items have their name only as a multilingual label ("mul"), e.g. "Contra".
    private fun label(entity: JSONObject): String {
        val labels = entity.optJSONObject("labels") ?: return ""
        return LABEL_LANGUAGES.firstNotNullOfOrNull { labels.optJSONObject(it)?.optString("value") } ?: ""
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }.toSet()

    private fun claimIds(
        entity: JSONObject,
        property: String,
    ): List<String> {
        val claims = entity.optJSONObject("claims")?.optJSONArray(property) ?: return emptyList()
        return (0 until claims.length()).mapNotNull {
            claims.optJSONObject(it)
                ?.optJSONObject("mainsnak")
                ?.optJSONObject("datavalue")
                ?.optJSONObject("value")
                ?.optString("id")
        }
    }

    private fun getJson(url: HttpUrl): JSONObject? {
        val request =
            Request.Builder()
                .url(url)
                // Wikimedia asks for a user agent that identifies the app.
                .header("User-Agent", USER_AGENT)
                .build()

        repeat(MAX_ATTEMPTS) { attempt ->
            okHttpClient.newCall(request).execute().use { response ->
                // Too many requests: waits what the server asks (a few seconds at most) and tries again.
                if (response.code == HTTP_TOO_MANY_REQUESTS && attempt < MAX_ATTEMPTS - 1) {
                    val waitSeconds = response.header("Retry-After")?.toLongOrNull() ?: DEFAULT_RETRY_SECONDS
                    Thread.sleep(TimeUnit.SECONDS.toMillis(waitSeconds.coerceIn(1, MAX_RETRY_SECONDS)))
                    return@use
                }
                if (response.code == HTTP_NOT_FOUND) return null
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
                return JSONObject(response.body?.string() ?: return null)
            }
        }
        return null
    }

    /**
     * Games remembered without a description before the search without colons existed are retried once,
     * instead of waiting for [MISSING_RETRY_MS].
     */
    private fun forgetMissingOnce(preferences: SharedPreferences) {
        if (preferences.getBoolean(KEY_MISSING_RESET, false)) return
        preferences.edit {
            preferences.all
                .filter { (_, value) -> (value as? String)?.startsWith(PREFIX_MISSING) == true }
                .keys
                .forEach { remove(it) }
            putBoolean(KEY_MISSING_RESET, true)
        }
    }

    private val requestMutex = Mutex()

    private fun cacheKey(game: Game) = "${deviceLanguage()}|${game.systemId}|${game.title}"

    private fun deviceLanguage(): String {
        val language = Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { FALLBACK_LANGUAGE }
        // Wikipedia names some languages with another code: Norwegian Bokmål is "no" and Filipino is "tl".
        return WIKIPEDIA_LANGUAGES[language] ?: language
    }

    companion object {
        private val WIKIPEDIA_LANGUAGES = mapOf("nb" to "no", "fil" to "tl")

        private const val PREFERENCES_NAME = "game_descriptions"
        private const val PREFIX_MISSING = "missing:"
        // Changed when the search finds more games, so games remembered without a description are retried once.
        private const val KEY_MISSING_RESET = "missing_reset_text_search"
        private val MISSING_RETRY_MS = TimeUnit.DAYS.toMillis(30)
        private const val MAX_ENTRIES = 5000
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_ATTEMPTS = 3
        private const val DEFAULT_RETRY_SECONDS = 2L
        private const val MAX_RETRY_SECONDS = 10L
        private const val MAX_EXTRA_WORDS = 2
        private val LABEL_LANGUAGES = listOf("en", "mul")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        private const val SEARCH_LIMIT = 10
        private const val FALLBACK_LANGUAGE = "en"
        private const val USER_AGENT = "PlayRetro/1.0 (https://github.com/branches78/PlayRetro)"

        private val WIKIDATA_API = "https://www.wikidata.org/w/api.php".toHttpUrl()
        private const val PROPERTY_INSTANCE_OF = "P31"
        private const val PROPERTY_PLATFORM = "P400"
        // Video game, and video game compilation (e.g. "Street Fighter Collection").
        private val VIDEO_GAME_TYPES = setOf("Q7889", "Q16070115")

        /** Wikidata items of the consoles, to tell apart games with the same name on different systems. */
        private val PLATFORMS =
            mapOf(
                SystemID.NES.dbname to "Q172742",
                SystemID.SNES.dbname to "Q183259",
                SystemID.N64.dbname to "Q184839",
                SystemID.GB.dbname to "Q186437",
                SystemID.GBC.dbname to "Q203992",
                SystemID.GBA.dbname to "Q188642",
                SystemID.NDS.dbname to "Q170323",
                SystemID.NINTENDO_3DS.dbname to "Q203597",
                SystemID.GENESIS.dbname to "Q10676",
                SystemID.SEGACD.dbname to "Q1047516",
                SystemID.SMS.dbname to "Q209868",
                SystemID.GG.dbname to "Q751719",
                SystemID.PSX.dbname to "Q10677",
                SystemID.PSP.dbname to "Q170325",
                SystemID.ATARI2600.dbname to "Q206261",
                SystemID.ATARI7800.dbname to "Q753600",
                SystemID.LYNX.dbname to "Q753657",
                SystemID.PC_ENGINE.dbname to "Q1057377",
                SystemID.NGP.dbname to "Q1977455",
                SystemID.NGC.dbname to "Q1977455",
                SystemID.WS.dbname to "Q1065792",
                SystemID.WSC.dbname to "Q1048035",
            )

        // Revision outside brackets in some names: "Donkey Kong Rev PRG1 (JP-US)".
        private val REVISION_SUFFIX = Regex("\\s+Rev\\s+[A-Z0-9]+$")
        private val BRACKET_GROUPS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
        private val MOVED_ARTICLE = Regex("^(.+?), (The|A|An)(\\s+-\\s+.*)?$")

        /**
         * Title as people write it: "Legend of Zelda, The - A Link to the Past (USA)" ->
         * "The Legend of Zelda: A Link to the Past".
         */
        fun searchTitle(title: String): String {
            val withoutTags = title.replace(BRACKET_GROUPS, "").trim().replace(REVISION_SUFFIX, "")
            val withArticle =
                MOVED_ARTICLE.matchEntire(withoutTags)?.let { match ->
                    val (name, article, subtitle) = match.destructured
                    "$article $name$subtitle"
                } ?: withoutTags
            return withArticle.replace(" - ", ": ")
        }
    }
}
