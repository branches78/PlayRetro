package com.swordfish.lemuroid.app.shared.covers

import android.content.Context
import androidx.core.content.edit
import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The libretro thumbnails server does not always use the latest No-Intro names
 * (e.g. it has "Circus Charlie (Japan).png" but not "Circus Charlie (Japan) (En).png").
 * When a box art is missing, a few name variants are tried before giving up.
 *
 * The server can also be down (every cover answers 404). Each name is then also requested from the GitHub
 * repositories the server is built from, which hold the same files.
 *
 * Results are remembered on the device: the variant that worked is used directly next time,
 * and covers that don't exist at all are not requested again for a few days.
 */
class ThumbnailFallbackInterceptor(context: Context) : Interceptor {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).also {
            // Entries are never removed one by one, so the whole list is reset if it grows too much.
            if (it.all.size > MAX_ENTRIES) it.edit { clear() }
        }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String
        if (url == null || !url.contains(THUMBNAILS_HOST)) {
            return chain.proceed(chain.request)
        }

        val remembered = preferences.getString(url, null)
        when {
            remembered?.startsWith(PREFIX_REDIRECT) == true -> {
                val redirectedUrl = remembered.removePrefix(PREFIX_REDIRECT)
                val result = chain.proceed(chain.request.newBuilder().data(redirectedUrl).build())
                if (result is SuccessResult) return result
            }
            remembered?.startsWith(PREFIX_MISSING) == true -> {
                val missingSince = remembered.removePrefix(PREFIX_MISSING).toLongOrNull() ?: 0
                if (System.currentTimeMillis() - missingSince < MISSING_RETRY_MS) {
                    return ErrorResult(chain.request.error, chain.request, IOException("Cover known to be missing"))
                }
            }
        }

        val result = chain.proceed(chain.request)
        if (result is SuccessResult) {
            preferences.edit { remove(url) }
            return result
        }

        var allMissing = isMissing(result)
        for (alternative in candidateUrls(url)) {
            if (libretroUnavailable && alternative.contains(THUMBNAILS_HOST)) continue
            val alternativeResult = chain.proceed(chain.request.newBuilder().data(alternative).build())
            if (alternativeResult is SuccessResult) {
                // The same name worked on GitHub: the server is down, skip it for the next covers of this session.
                if (alternative == githubUrl(url)) libretroUnavailable = true
                preferences.edit { putString(url, PREFIX_REDIRECT + alternative) }
                return alternativeResult
            }
            allMissing = allMissing && isMissing(alternativeResult)
        }

        // Only remember covers that really don't exist, not network errors (e.g. no connection).
        if (allMissing) {
            preferences.edit { putString(url, PREFIX_MISSING + System.currentTimeMillis()) }
        }
        return result
    }

    private fun isMissing(result: ImageResult): Boolean {
        val throwable = (result as? ErrorResult)?.throwable
        return throwable is HttpException && throwable.response.code == HTTP_NOT_FOUND
    }

    /** The same name on GitHub first, then each name variant on the server and on GitHub. */
    private fun candidateUrls(url: String): List<String> {
        return buildList {
            githubUrl(url)?.let { add(it) }
            alternativeUrls(url).forEach { alternative ->
                add(alternative)
                githubUrl(alternative)?.let { add(it) }
            }
        }
    }

    /**
     * "https://thumbnails.libretro.com/Nintendo - Game Boy/Named_Boxarts/Game.png" ->
     * "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Game_Boy/master/Named_Boxarts/Game.png"
     */
    private fun githubUrl(url: String): String? {
        val path = url.substringAfter("$THUMBNAILS_HOST/", "")
        val system = path.substringBefore("/")
        val file = path.substringAfter("/", "")
        if (system.isEmpty() || file.isEmpty()) return null
        return "$GITHUB_BASE_URL/${system.replace(' ', '_')}/master/$file"
    }

    private fun alternativeUrls(url: String): List<String> {
        val baseUrl = url.substringBeforeLast("/") + "/"
        val name = url.substringAfterLast("/").removeSuffix(EXTENSION)
        val title = name.substringBefore(" (").trim()
        if (title == name) return emptyList()

        val firstGroup = Regex("\\([^)]*\\)").find(name)?.value

        val names =
            buildList {
                // Without language tags: "Circus Charlie (Japan) (En)" -> "Circus Charlie (Japan)".
                add(name.replace(LANGUAGE_GROUP, "").trim())
                // Only title and region: "Game (USA) (Rev 1)" -> "Game (USA)".
                firstGroup?.let { add("$title $it") }
                // Same game from another region.
                FALLBACK_REGIONS.forEach { add("$title ($it)") }
            }

        return names
            .filter { it != name }
            .distinct()
            .take(MAX_ALTERNATIVES)
            .map { "$baseUrl$it$EXTENSION" }
    }

    companion object {
        // Renamed when the GitHub mirror was added, so covers remembered as missing while the server was down are retried.
        private const val PREFERENCES_NAME = "thumbnail_fallbacks_v2"
        private const val PREFIX_REDIRECT = "redirect:"
        private const val PREFIX_MISSING = "missing:"
        private val MISSING_RETRY_MS = TimeUnit.DAYS.toMillis(7)
        private const val HTTP_NOT_FOUND = 404

        private const val THUMBNAILS_HOST = "thumbnails.libretro.com"
        private const val GITHUB_BASE_URL = "https://raw.githubusercontent.com/libretro-thumbnails"
        private const val EXTENSION = ".png"
        private const val MAX_ALTERNATIVES = 6
        private const val MAX_ENTRIES = 5000

        private val LANGUAGE_GROUP = Regex("\\s*\\((?:[A-Z][a-z](?:[,+][A-Z][a-z])*)\\)")
        private val FALLBACK_REGIONS = listOf("USA", "World", "Europe", "Japan")

        @Volatile
        private var libretroUnavailable = false
    }
}
