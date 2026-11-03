package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.alorma.compose.settings.storage.base.SettingValueState
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import java.util.Locale

/**
 * Debug builds only: the language of the app, to take the Play Store screenshots in every language without changing
 * the one of the device. It is the per-app language of Android 13: the system recreates the screens with it.
 */
@Composable
fun DebugLanguageSetting() {
    if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    // The first entry follows the device.
    val tags = listOf("") + APP_LANGUAGES
    val names =
        listOf(stringResource(R.string.settings_language_device)) +
            APP_LANGUAGES.map { tag ->
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            }
    val state =
        remember {
            object : SettingValueState<Int> {
                // Android may give back another spelling of the same language ("he" for "iw").
                override var value: Int =
                    tags.indexOfFirst {
                        it.isNotEmpty() && Locale.forLanguageTag(it).toLanguageTag() == localeManager.applicationLocales.toLanguageTags()
                    }.coerceAtLeast(0)
                    set(index) {
                        field = index
                        localeManager.applicationLocales = LocaleList.forLanguageTags(tags[index])
                    }

                override fun reset() {
                    value = 0
                }
            }
        }

    LemuroidSettingsList(
        title = { Text(text = stringResource(R.string.settings_title_language)) },
        items = names,
        state = state,
    )
}

// One per res/values-* folder of the app (English is the default one).
private val APP_LANGUAGES =
    listOf(
        "en", "af-ZA", "ar-SA", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "es-ES", "fi-FI", "fr-FR", "hu-HU",
        "it-IT", "iw-IL", "ja-JP", "ko-KR", "nl-NL", "no-NO", "pl-PL", "pt-BR", "pt-PT", "ro-RO", "ru-RU", "sr-SP",
        "sv-SE", "tr-TR", "uk-UA", "vi-VN", "zh-CN", "zh-TW",
    )
