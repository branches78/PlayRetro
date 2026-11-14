package com.swordfish.lemuroid.app.utils.android.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Settings items of one screen. On TV it lets a screen move the focus to its first item
 * or back to the item that opened another screen: without it the focus jumps out of the settings (to the search
 * button) when the focused item leaves the screen. Items are identified by their position in the composition,
 * the same key rememberSaveable uses, so it is still valid when the screen is shown again.
 */
class SettingsFocusMemory {
    internal class Item(val key: Int, val requester: FocusRequester, val isEnabled: () -> Boolean) {
        /** Vertical position on screen, null until the item is laid out. */
        var top by mutableStateOf<Float?>(null)

        /** Whether the item (or a view inside it) has the focus. */
        var hasFocus by mutableStateOf(false)

        /** The same, but read while composing without recomposing when it changes. */
        var hadFocusLastEvent = false
    }

    internal val items = mutableStateListOf<Item>()

    var onItemClicked: (Int) -> Unit = { }

    /**
     * Topmost item that can take the focus (disabled items can't). Items are registered in the order they are
     * composed, which is not the order on screen when some of them are loaded in the background.
     */
    fun firstItem(): FocusRequester? =
        items.filter { it.isEnabled() && it.top != null }.minByOrNull { it.top!! }?.requester

    fun item(key: Int): FocusRequester? = items.firstOrNull { it.key == key && it.isEnabled() }?.requester

    /** Whether the item of [requester] has the focus. */
    fun hasFocus(requester: FocusRequester): Boolean = items.any { it.requester == requester && it.hasFocus }
}

/** Only provided on TV: on mobile the settings items don't track the focus. */
val LocalSettingsFocusMemory = staticCompositionLocalOf<SettingsFocusMemory?> { null }

/**
 * Registers [content] (one settings item) in the [SettingsFocusMemory] of the screen, if any, and gives
 * [content] the callback to call when the item is clicked. The focus requester goes on a box around the item:
 * the settings library puts the modifier of its tiles after their clickable, where it would find nothing to focus.
 */
@Composable
internal fun SettingsFocusItem(
    enabled: Boolean,
    // Called (if enabled) when → is pressed on the item: the options that open another screen open it.
    onRightKey: (() -> Unit)? = null,
    // Off for items that show the focus themselves (e.g. the outline of a text field).
    focusBorder: Boolean = true,
    content: @Composable (onClicked: () -> Unit) -> Unit,
) {
    val memory = LocalSettingsFocusMemory.current
    if (memory == null) {
        content { }
        return
    }

    val key = currentCompositeKeyHash
    val requester = remember { FocusRequester() }
    val currentEnabled = rememberUpdatedState(enabled)
    val item = remember(memory, key) { SettingsFocusMemory.Item(key, requester) { currentEnabled.value } }
    DisposableEffect(item) {
        memory.items.add(item)
        onDispose { memory.items.remove(item) }
    }
    // Read now: once the item is disabled it loses the focus, which would jump out of the settings (to the search button).
    val focusedWhileComposing = item.hadFocusLastEvent
    LaunchedEffect(enabled) {
        if (!enabled && focusedWhileComposing) memory.firstItem()?.let { runCatching { it.requestFocus() } }
    }
    val currentOnRightKey = rememberUpdatedState(onRightKey)
    // With a remote the focused item only gets a white border (no lighter fill: see TVSettingsTabFragment).
    val shape = MaterialTheme.shapes.medium
    val cardModifier =
        if (LocalSettingsItemCards.current) {
            val colors = MaterialTheme.colorScheme
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainer)
                .border(
                    width = if (item.hasFocus) ITEM_CARD_FOCUS_BORDER else ITEM_CARD_BORDER,
                    color = if (item.hasFocus) Color.White else colors.outlineVariant,
                    shape = shape,
                )
        } else {
            val borderColor = if (item.hasFocus && focusBorder) Color.White else Color.Transparent
            Modifier.border(ITEM_CARD_FOCUS_BORDER, borderColor, shape)
        }
    Box(
        modifier =
            Modifier
                .focusRequester(requester)
                .onFocusChanged {
                    item.hasFocus = it.hasFocus
                    item.hadFocusLastEvent = it.hasFocus
                }
                .onGloballyPositioned { item.top = it.positionInRoot().y }
                .onKeyEvent { event ->
                    val onRight = currentOnRightKey.value
                    val isRight = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    if (onRight == null || !isRight || !currentEnabled.value) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        memory.onItemClicked(key)
                        onRight()
                    }
                    true
                }
                .then(cardModifier),
    ) {
        content { memory.onItemClicked(key) }
    }
}

private val ITEM_CARD_BORDER = 1.dp
private val ITEM_CARD_FOCUS_BORDER = 2.dp
