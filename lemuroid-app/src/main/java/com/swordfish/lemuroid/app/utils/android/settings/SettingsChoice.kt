package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import com.swordfish.lemuroid.app.utils.android.focusBorder
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The values an option can take, shown on a screen of their own on mobile (instead of a dialog): [title] goes in the
 * top bar. With a single choice, tapping a value closes the screen and then calls [onClick]; with [multiSelect],
 * [onClick] toggles the value right away and [onClose] is called when the screen is closed.
 */
class SettingsChoiceRequest(
    val title: @Composable () -> Unit,
    val subtitle: (@Composable () -> Unit)? = null,
    val items: List<String>,
    val itemSubtitles: List<String?> = emptyList(),
    val multiSelect: Boolean = false,
    val isSelected: (Int) -> Boolean,
    val onClick: (Int) -> Unit,
    val onClose: () -> Unit = { },
)

/** Opens the screens of the values of the options (see [SettingsChoiceRequest]). */
class SettingsChoiceHost(private val navigate: () -> Unit) {
    var request by mutableStateOf<SettingsChoiceRequest?>(null)
        private set

    fun open(request: SettingsChoiceRequest) {
        this.request = request
        navigate()
    }
}

/** Provided by the settings of the app (mobile and TV): in the game menu the values are chosen in a dialog. */
val LocalSettingsChoiceHost = staticCompositionLocalOf<SettingsChoiceHost?> { null }

/**
 * Screen with the values of the option opened last. Without one (e.g. after the app was restored), it closes. With a
 * remote ([focusSelected]) the focus starts on the selected value.
 */
@Composable
fun SettingsChoiceScreen(
    host: SettingsChoiceHost,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusSelected: Boolean = false,
) {
    val request = host.request
    if (request == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val scope = rememberCoroutineScope()
    // The value just chosen is marked while the screen closes.
    var chosen by remember(request) { mutableStateOf<Int?>(null) }
    if (request.multiSelect) {
        DisposableEffect(request) { onDispose { request.onClose() } }
    }
    val initialFocus = remember(request) { FocusRequester() }
    val initialIndex = remember(request) { request.items.indices.firstOrNull { request.isSelected(it) } ?: 0 }
    if (focusSelected) {
        LaunchedEffect(request) { runCatching { initialFocus.requestFocus() } }
    }

    LemuroidSettingsPage(modifier = modifier) {
        request.subtitle?.let {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { it() }
            }
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                request.items.forEachIndexed { index, item ->
                    val selected = chosen?.let { it == index } ?: request.isSelected(index)
                    val rowModifier =
                        if (request.multiSelect) {
                            Modifier.toggleable(value = selected, role = Role.Checkbox) { request.onClick(index) }
                        } else {
                            Modifier.selectable(selected = selected, role = Role.RadioButton) {
                                if (chosen != null) return@selectable
                                chosen = index
                                scope.launch {
                                    delay(CLOSE_DELAY_MS)
                                    onClose()
                                    request.onClick(index)
                                }
                            }
                        }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .then(if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier)
                                // Where the focus of a remote is, like the rows of the dialogs.
                                .focusBorder(RoundedCornerShape(12.dp))
                                .then(rowModifier)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!request.multiSelect) RadioButton(selected = selected, onClick = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item, style = MaterialTheme.typography.bodyLarge)
                            request.itemSubtitles.getOrNull(index)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (request.multiSelect) Checkbox(checked = selected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

// Like the dialogs, which closed a moment after a value was chosen.
private const val CLOSE_DELAY_MS = 200L
