package com.swordfish.lemuroid.app.mobile.feature.game

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

class GameActivity : BaseGameActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferGameRefreshRate()
        // The screen turns to the orientation chosen in the settings ("Auto" follows the device, as before).
        lifecycleScope.launch {
            requestedOrientation = screenOrientation(settingsManager.gameOrientation())
        }
    }

    /**
     * The emulator moves the game one frame per refresh of the screen. Phones with a 90 or 120 Hz screen that only
     * use it while touched made games run faster on every touch: while playing the screen stays at the 60 Hz of the
     * consoles (the closest mode with the same resolution). The rest of the app keeps the rate of the phone.
     */
    private fun preferGameRefreshRate() {
        val attributes = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val current = display?.mode
            val mode =
                display?.supportedModes
                    ?.filter { it.physicalWidth == current?.physicalWidth && it.physicalHeight == current.physicalHeight }
                    ?.minByOrNull { abs(it.refreshRate - GAME_REFRESH_RATE) }
            if (mode != null) attributes.preferredDisplayModeId = mode.modeId
        }
        // Also for screens that change their rate without separate modes.
        attributes.preferredRefreshRate = GAME_REFRESH_RATE
        window.attributes = attributes
    }

    private fun screenOrientation(value: String): Int =
        when (value) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "reverse_landscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "reverse_portrait" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}

private const val GAME_REFRESH_RATE = 60f
