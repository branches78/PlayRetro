package com.swordfish.lemuroid.app.utils.android

import android.view.SoundEffectConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView

/**
 * Plays the navigation sound of the system when the D-pad moves the focus, like the screens made with views: Compose
 * moves the focus on its own and plays nothing. The arrows that a screen handles itself (e.g. up from the first row
 * to the tabs) never get here; the others move the focus as Compose would, with the sound.
 */
@Composable
fun TVNavigationSounds(content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    Box(
        modifier =
            Modifier.onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val (direction, sound) =
                    when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up to SoundEffectConstants.NAVIGATION_UP
                        Key.DirectionDown -> FocusDirection.Down to SoundEffectConstants.NAVIGATION_DOWN
                        Key.DirectionLeft -> FocusDirection.Left to SoundEffectConstants.NAVIGATION_LEFT
                        Key.DirectionRight -> FocusDirection.Right to SoundEffectConstants.NAVIGATION_RIGHT
                        else -> return@onKeyEvent false
                    }
                // When nothing is found here the focus may leave the screen (e.g. to the tabs): the views play it.
                focusManager.moveFocus(direction).also { moved -> if (moved) view.playSoundEffect(sound) }
            },
        propagateMinConstraints = true,
    ) {
        content()
    }
}
