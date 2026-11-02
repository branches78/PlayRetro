package com.swordfish.lemuroid.app.shared.details

import android.content.Context
import androidx.core.content.edit
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
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
                    fetchDescription(game)
                } catch (e: Exception) {
                    Timber.w(e, "Unable to get the description of ${game.title}")
                    return@withContext null
                }

            preferences.edit { putString(key, description ?: (PREFIX_MISSING + System.currentTimeMillis())) }
            description
        }

    private fun fetchDescription(game: Game): String? {
        val language = deviceLanguage()
        val entity = findGameEntity(searchTitle(game.title), game.systemId) ?: return null
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
     * First search result that is a video game, preferring the ones released on the platform of [systemId].
     */
    private fun findGameEntity(
        title: String,
        systemId: String,
    ): JSONObject? {
        val searchUrl =
            WIKIDATA_API.newBuilder()
                .addQueryParameter("action", "wbsearchentities")
                .addQueryParameter("search", title)
                .addQueryParameter("language", FALLBACK_LANGUAGE)
                .addQueryParameter("type", "item")
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .addQueryParameter("format", "json")
                .build()

        val results = getJson(searchUrl)?.optJSONArray("search") ?: return null
        val ids = (0 until results.length()).mapNotNull { results.optJSONObject(it)?.optString("id") }
        if (ids.isEmpty()) return null

        val entitiesUrl =
            WIKIDATA_API.newBuilder()
                .addQueryParameter("action", "wbgetentities")
                .addQueryParameter("ids", ids.joinToString("|"))
                .addQueryParameter("props", "claims|sitelinks")
                .addQueryParameter("format", "json")
                .build()

        val entities = getJson(entitiesUrl)?.optJSONObject("entities") ?: return null
        val games =
            ids.mapNotNull { entities.optJSONObject(it) }
                .filter { VIDEO_GAME in claimIds(it, PROPERTY_INSTANCE_OF) }

        val platform = PLATFORMS[systemId]
        return games.firstOrNull { platform != null && platform in claimIds(it, PROPERTY_PLATFORM) }
            ?: games.firstOrNull()
    }

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

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == HTTP_NOT_FOUND) return null
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            return JSONObject(response.body?.string() ?: return null)
        }
    }

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
        private val MISSING_RETRY_MS = TimeUnit.DAYS.toMillis(30)
        private const val MAX_ENTRIES = 5000
        private const val HTTP_NOT_FOUND = 404
        private const val SEARCH_LIMIT = 10
        private const val FALLBACK_LANGUAGE = "en"
        private const val USER_AGENT = "PlayRetro/1.0 (https://github.com/branches78/PlayRetro)"

        private val WIKIDATA_API = "https://www.wikidata.org/w/api.php".toHttpUrl()
        private const val PROPERTY_INSTANCE_OF = "P31"
        private const val PROPERTY_PLATFORM = "P400"
        private const val VIDEO_GAME = "Q7889"

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

        private val BRACKET_GROUPS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
        private val MOVED_ARTICLE = Regex("^(.+?), (The|A|An)(\\s+-\\s+.*)?$")

        /**
         * Title as people write it: "Legend of Zelda, The - A Link to the Past (USA)" ->
         * "The Legend of Zelda: A Link to the Past".
         */
        fun searchTitle(title: String): String {
            val withoutTags = title.replace(BRACKET_GROUPS, "").trim()
            val withArticle =
                MOVED_ARTICLE.matchEntire(withoutTags)?.let { match ->
                    val (name, article, subtitle) = match.destructured
                    "$article $name$subtitle"
                } ?: withoutTags
            return withArticle.replace(" - ", ": ")
        }
    }
}
