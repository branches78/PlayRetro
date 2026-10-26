package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Screen of a settings navigation used with a remote or a gamepad (TV settings, in-game menu). When it opens it moves
 * the focus to its first item ([focusFirstItem]), and when it is shown again with Back, to the item that opened the
 * other screen. Otherwise the focus would stay nowhere or jump to the first focusable view of the window.
 */
@OptIn(FlowPreview::class)
@Composable
fun SettingsFocusDestination(
    focusFirstItem: Boolean = true,
    content: @Composable () -> Unit,
) {
    var clickedItemKey by rememberSaveable { mutableStateOf<Int?>(null) }
    val focusMemory = remember { SettingsFocusMemory() }
    focusMemory.onItemClicked = { clickedItemKey = it }

    CompositionLocalProvider(LocalSettingsFocusMemory provides focusMemory) {
        content()
    }

    LaunchedEffect(Unit) {
        val restoreKey = clickedItemKey
        if (restoreKey == null && !focusFirstItem) return@LaunchedEffect

        if (restoreKey != null) {
            val target =
                withTimeoutOrNull(ITEMS_TIMEOUT_MS) {
                    snapshotFlow { focusMemory.item(restoreKey) }.filterNotNull().first()
                } ?: focusMemory.firstItem()
            clickedItemKey = null
            target?.let { runCatching { it.requestFocus() } }
            return@LaunchedEffect
        }

        // Some screens load their items in the background, and those at the top may arrive after the ones at
        // the bottom (e.g. the gamepads of "External devices", after "Reset bindings"). For a while the focus
        // follows the topmost item, as long as it stays where it was put (the user didn't move it).
        var focused: FocusRequester? = null
        withTimeoutOrNull(FIRST_ITEM_WATCH_MS) {
            snapshotFlow { focusMemory.firstItem() }
                .debounce(ITEMS_SETTLE_MS)
                .filterNotNull()
                // Stops as soon as the user moved the focus to another item.
                .takeWhile { focused.let { it == null || focusMemory.hasFocus(it) } }
                .collect { first ->
                    if (first != focused && runCatching { first.requestFocus() }.isSuccess) {
                        focused = first
                    }
                }
        }
    }
}

private const val ITEMS_TIMEOUT_MS = 1000L
private const val ITEMS_SETTLE_MS = 250L
private const val FIRST_ITEM_WATCH_MS = 3000L
