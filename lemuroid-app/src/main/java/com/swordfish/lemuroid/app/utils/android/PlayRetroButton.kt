package com.swordfish.lemuroid.app.utils.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

/**
 * Button of dialogs and forms with the look of the buttons of the details of a game: a dark green pill with white
 * text, and white with dark text (a bit bigger) while focused with a remote or a gamepad (mobile and TV).
 */
@Composable
fun PlayRetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, label = "buttonScale")
    val background = if (focused) Color(FOCUSED_BACKGROUND) else colorResource(R.color.md_theme_dark_secondaryContainer)
    val content = if (focused) Color(FOCUSED_CONTENT) else Color.White
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier =
            modifier
                .scale(scale)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(background)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = MIN_HEIGHT.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

private const val FOCUSED_SCALE = 1.06f
private const val DISABLED_ALPHA = 0.45f
private const val MIN_HEIGHT = 40
private const val FOCUSED_BACKGROUND = 0xFFF1F3EC
private const val FOCUSED_CONTENT = 0xFF111311
