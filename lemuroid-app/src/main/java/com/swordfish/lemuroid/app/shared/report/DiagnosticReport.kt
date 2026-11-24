package com.swordfish.lemuroid.app.shared.report

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic report the user shares with the developer (Settings > Miscellaneous): device, app version, installed cores
 * and the logcat of the app. Nothing is sent by the app itself: the user picks where it goes.
 */
object DiagnosticReport {
    const val RECIPIENT = "playretro-app@outlook.com"

    private const val LOGCAT_LINES = 3000
    private const val REPORTS_DIR = "reports"
    private const val MIME_TYPE = "text/plain"
    private const val GAME_LOG_FILE = "last-game-error.txt"

    // At most MAX_REPORTS reports sent or exported every REPORTS_WINDOW_MS, against abuse. Only kept by the app:
    // clearing its data resets it.
    private const val MAX_REPORTS = 2
    private const val REPORTS_WINDOW_MS = 48L * 60 * 60 * 1000
    private const val PREFERENCES = "diagnostic_report"
    private const val KEY_SENT_TIMES = "sent_times"

    /** Builds the text of the report. Reads the logcat and the files of the cores: call it off the main thread. */
    suspend fun build(context: Context): String =
        withContext(Dispatchers.IO) {
            buildString {
                appendLine("PlayRetro diagnostic report")
                appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
                appendLine()
                appendSection("App")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Package: ${BuildConfig.APPLICATION_ID}")
                appendLine("Variant: ${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE}")
                appendLine()
                appendSection("Device")
                appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("TV: ${TVHelper.isTV(context)}")
                appendLine("OpenGL ES: ${glEsVersion(context)}")
                appendLine()
                appendSection("Cores")
                appendCores(context)
                appendLine()
                appendSection("Last game error")
                appendLine(readGameLog(context) ?: "None")
                appendLine()
                appendSection("Log")
                append(readLogcat())
            }
        }

    /** When a report can be sent again, or null if it can be sent now. */
    fun nextAvailableTime(context: Context): Long? {
        val sentTimes = recentSentTimes(context)
        return if (sentTimes.size < MAX_REPORTS) null else sentTimes.min() + REPORTS_WINDOW_MS
    }

    private fun recordSent(context: Context) {
        val sentTimes = recentSentTimes(context) + System.currentTimeMillis()
        preferences(context).edit().putString(KEY_SENT_TIMES, sentTimes.joinToString(",")).apply()
    }

    /** Times of the reports sent within the window, the oldest first. */
    private fun recentSentTimes(context: Context): List<Long> {
        val now = System.currentTimeMillis()
        return preferences(context).getString(KEY_SENT_TIMES, null).orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            // A time in the future (the clock of the device was changed) does not block forever.
            .filter { it in (now - REPORTS_WINDOW_MS)..now }
            .sorted()
            .takeLast(MAX_REPORTS)
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Saves the log of the game process when a game fails or crashes. Android only gives a process its own log
     * lines, and the game runs in its own process (":game"), so the main process can not read them later.
     */
    fun saveGameLog(context: Context) {
        runCatching {
            val header = "Saved: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}\n"
            gameLogFile(context).writeText(header + readLogcat())
        }
    }

    private fun gameLogFile(context: Context): File = File(context.filesDir, GAME_LOG_FILE)

    private fun readGameLog(context: Context): String? =
        runCatching { gameLogFile(context).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Subject of the mail, with the version of the app. */
    fun subject(): String = "PlayRetro report ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})"

    /**
     * Opens the Android app chooser to share [report] as a file. Mail apps get [RECIPIENT] and the subject already
     * filled in. Returns false when no app can receive it.
     */
    fun share(
        context: Context,
        report: String,
    ): Boolean {
        val file = writeCacheFile(context, report)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
                putExtra(Intent.EXTRA_SUBJECT, subject())
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser =
            Intent.createChooser(sendIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            context.startActivity(chooser)
            recordSent(context)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Writes [report] in the folder the user picked with the file manager. Returns the name of the folder, or null
     * when it could not be written.
     */
    suspend fun exportToFolder(
        context: Context,
        treeUri: Uri,
        report: String,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching null
                val file = folder.createFile(MIME_TYPE, fileName()) ?: return@runCatching null
                context.contentResolver.openOutputStream(file.uri)?.use { it.write(report.toByteArray()) }
                    ?: return@runCatching null
                recordSent(context)
                folder.name ?: treeUri.lastPathSegment
            }.getOrNull()
        }

    /**
     * Saves [report] in the Download folder of the device, for devices without a file manager to pick a folder.
     * Returns the folder, or null when it could not be written.
     */
    suspend fun exportToDownloads(
        context: Context,
        report: String,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val values =
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName())
                        put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
                    }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
                resolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } ?: return@runCatching null
                recordSent(context)
                Environment.DIRECTORY_DOWNLOADS
            }.getOrNull()
        }

    private fun fileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "playretro-report-$timestamp.txt"
    }

    private fun writeCacheFile(
        context: Context,
        report: String,
    ): File {
        val directory = File(context.cacheDir, REPORTS_DIR).apply { mkdirs() }
        // Only the last report is kept.
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, fileName()).apply { writeText(report) }
    }

    private fun StringBuilder.appendSection(title: String) {
        appendLine("== $title ==")
    }

    private fun glEsVersion(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.deviceConfigurationInfo?.glEsVersion ?: "unknown"
    }

    /** Downloaded cores (GitHub version), cores of the APK and Play modules installed. */
    private fun StringBuilder.appendCores(context: Context) {
        val coresDirectory = DirectoriesManager(context).getCoresDirectory()
        val downloaded = coresDirectory.walkTopDown().filter { it.isFile }.toList()
        appendLine("Downloaded (${downloaded.size}):")
        downloaded.sortedBy { it.path }.forEach {
            appendLine("  ${it.relativeTo(coresDirectory).path} ${it.length()} bytes")
        }

        val nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir)
        val bundled = nativeLibraryDir.listFiles()?.filter { it.name.contains("libretro") }.orEmpty()
        appendLine("Bundled (${bundled.size}):")
        bundled.sortedBy { it.name }.forEach { appendLine("  ${it.name} ${it.length()} bytes") }

        val splits = context.applicationInfo.splitNames?.toList().orEmpty()
        appendLine("Play modules (${splits.size}): ${splits.sorted().joinToString()}")
    }

    /** Logcat of the app: Android only gives an app its own lines, so no permission is needed. */
    private fun readLogcat(): String =
        runCatching {
            val process =
                // StrictMode (debug builds only) would fill the log with its stack traces.
                ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(), "StrictMode:S")
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        }.getOrElse { "Unable to read the log: ${it.message}" }
}
