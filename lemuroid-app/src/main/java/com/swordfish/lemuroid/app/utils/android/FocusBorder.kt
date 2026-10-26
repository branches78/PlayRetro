package com.swordfish.lemuroid.app.utils.android

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * White border while focused, like the cards and buttons of the TV: buttons of dialogs and forms show where the
 * focus of a remote or a gamepad is. With touch nothing is focused, so nothing changes. The default shape is the pill
 * of Material buttons.
 */
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(percent = 50)): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.hasFocus }
            .border(FOCUS_BORDER_WIDTH.dp, if (focused) Color.White else Color.Transparent, shape)
    }

private const val FOCUS_BORDER_WIDTH = 2
