package com.swordfish.lemuroid.app.tv.settings

import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import com.swordfish.lemuroid.app.utils.android.settings.LocalSettingsBack
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.Icons
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsSection
import com.swordfish.lemuroid.R

/**
 * Main screen of the TV settings, in two panes: the sections on the left and, on the right, a preview of the options of
 * the focused one ([preview]), which the focus doesn't get into. OK or → on a section opens its options on a screen of
 * their own ([onOpenSection]), which slides in; back from it, the focus returns to the section.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TVSettingsHome(
    onOpenSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable (SettingsSection) -> Unit,
) {
    val sections = SettingsSection.entries
    var selected by rememberSaveable { mutableStateOf(0) }
    // Whether a section was opened: shown again, the focus goes back to it.
    var sectionOpened by rememberSaveable { mutableStateOf(false) }
    val sectionRequesters = remember { sections.map { FocusRequester() } }

    LaunchedEffect(Unit) {
        if (sectionOpened) {
            sectionOpened = false
            runCatching { sectionRequesters[selected].requestFocus() }
        }
    }

    Row(
        modifier = modifier.fillMaxSize().padding(top = PANES_TOP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(PANES_SPACING),
    ) {
        Column(
            modifier =
                Modifier
                    .width(SECTIONS_WIDTH)
                    // Coming from the tabs, the focus lands on the selected section. Down from the last one, it stays.
                    .focusProperties {
                        enter = { sectionRequesters[selected] }
                        exit = { if (it == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default }
                    }
                    .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(SECTIONS_SPACING),
        ) {
            sections.forEachIndexed { index, section ->
                TVSettingsSectionItem(
                    title = stringResource(section.titleId),
                    selected = index == selected,
                    modifier =
                        Modifier
                            .focusRequester(sectionRequesters[index])
                            .onFocusChanged { if (it.isFocused) selected = index }
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.keyCode !in ENTER_KEYS) return@onKeyEvent false
                                // On key up: the key up of OK would otherwise click the option that gets the focus.
                                if (event.type == KeyEventType.KeyUp) {
                                    sectionOpened = true
                                    onOpenSection(section)
                                }
                                true
                            },
                )
            }
        }

        // Only a preview: the focus never gets in.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusProperties { enter = { FocusRequester.Cancel } }
                    .focusGroup(),
        ) {
            preview(sections[selected])
        }
    }
}

/**
 * A screen of the settings opened from a section or an option (BIOS, cores...): centered below the tabs bar, without
 * the sections. ← goes back to what opened it when there is nothing to its left, like Back; ↑ never leaves it.
 */
@Composable
fun TVSettingsSubScreen(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onFocusChanged { hasFocus = it.hasFocus }
                .onKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Back when the focus can't move left inside the screen (or would leave it).
                            if (event.type == KeyEventType.KeyDown && (!focusManager.moveFocus(FocusDirection.Left) || !hasFocus)) {
                                onBack()
                            }
                            true
                        }
                        // Up never leaves the screen (to the tabs bar): only the sections on the left go up to it.
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        else -> false
                    }
                }
                .padding(top = PANES_TOP_PADDING),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Hint on the left: ← and Back go back.
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterStart).size(SUB_SCREEN_ARROW_SIZE),
        )
        Box(modifier = Modifier.widthIn(max = SUB_SCREEN_WIDTH)) {
            CompositionLocalProvider(LocalSettingsBack provides onBack) { content() }
        }
    }
}

private val SUB_SCREEN_ARROW_SIZE = 32.dp

/**
 * A section in the list on the left: a white pill with dark text while focused (like the tabs), green when it is the
 * one shown on the right and the focus is there.
 */
@Composable
private fun TVSettingsSectionItem(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background =
        when {
            focused -> colorResource(R.color.tv_tab_focused_background)
            selected -> colorResource(R.color.tv_tab_selected_background)
            else -> Color.Transparent
        }
    val textColor =
        when {
            focused -> colorResource(R.color.tv_tab_focused_text)
            selected -> colorResource(R.color.tv_tab_selected_text)
            else -> MaterialTheme.colorScheme.onSurface
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SECTION_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                .background(background)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (focused || selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private val ENTER_KEYS =
    setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

private val PANES_TOP_PADDING = 16.dp
private val PANES_SPACING = 32.dp
private val SECTIONS_WIDTH = 240.dp
private val SECTIONS_SPACING = 4.dp
private val SECTION_HEIGHT = 48.dp
private val SUB_SCREEN_WIDTH = 560.dp

/**
 * Scrolls just enough to show the focused item with [margin] around it, like on mobile, instead of keeping it at a fixed
 * place of the screen as Compose does by default on TV. The margin also shows what a text field draws around its text
 * (its label above it), as it asks to show only its text.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class EdgeBringIntoViewSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val leadingEdge = offset - margin
        val trailingEdge = offset + size + margin
        return when {
            // Already visible, or bigger than the screen and covering it.
            leadingEdge >= 0 && trailingEdge <= containerSize -> 0f
            leadingEdge < 0 && trailingEdge > containerSize -> 0f
            abs(leadingEdge) < abs(trailingEdge - containerSize) -> leadingEdge
            else -> trailingEdge - containerSize
        }
    }
}

@Composable
internal fun rememberEdgeBringIntoViewSpec(): BringIntoViewSpec {
    val margin = with(LocalDensity.current) { EDGE_SCROLL_MARGIN.toPx() }
    return remember(margin) { EdgeBringIntoViewSpec(margin) }
}

private val EDGE_SCROLL_MARGIN = 32.dp
