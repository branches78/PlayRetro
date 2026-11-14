package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSlider
import com.alorma.compose.settings.ui.SettingsSwitch
import com.swordfish.lemuroid.R
import kotlin.math.roundToInt

/**
 * Only provided on TV, on the right of the settings: each option is drawn as a card of its own (with a white border
 * while focused) instead of a row of the card of its group.
 */
val LocalSettingsItemCards = staticCompositionLocalOf { false }

internal val SETTINGS_ITEM_CARDS_SPACING = 8.dp

@Composable
fun LemuroidSettingsPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
fun LemuroidSettingsSwitch(
    enabled: Boolean = true,
    state: SettingValueState<Boolean>,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    SettingsFocusItem(enabled) { onFocusItemClicked ->
        BrandGreenFills {
            SettingsSwitch(
                enabled = enabled,
                state = state.value,
                icon = icon,
                title = title,
                subtitle = subtitle,
                onCheckedChange = {
                    onFocusItemClicked()
                    state.value = it
                    onCheckedChange(it)
                },
                colors = lemuroidSettingsColor(enabled),
            )
        }
    }
}

@Composable
fun LemuroidSettingsMenuLink(
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    // With a remote, → opens what an option with an arrow opens, like OK.
    SettingsFocusItem(enabled, onRightKey = if (action != null) onClick else null) { onFocusItemClicked ->
        val click = {
            onFocusItemClicked()
            onClick()
        }
        if (action == null) {
            SettingsMenuLink(
                enabled = enabled,
                icon = icon,
                title = title,
                subtitle = subtitle,
                onClick = click,
                colors = lemuroidSettingsColor(enabled),
            )
            return@SettingsFocusItem
        }
        // The action goes outside the tile, centered: with three lines of text, the tile puts it at the top.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsMenuLink(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                icon = icon,
                title = title,
                subtitle = subtitle,
                onClick = click,
                colors = lemuroidSettingsColor(enabled),
            )
            val colors = lemuroidSettingsColor(enabled)
            Box(
                modifier =
                    Modifier
                        // Only the tile takes the focus; tapping the action does the same.
                        .focusProperties { canFocus = false }
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = click,
                        )
                        .padding(end = ACTION_END_PADDING),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor,
                ) { action() }
            }
        }
    }
}

private val ACTION_END_PADDING = 24.dp

@Composable
fun LemuroidSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
    }
}

@Composable
fun LemuroidCardSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Each option is a card of its own (see SettingsFocusItem), with the title of the group above them.
    if (LocalSettingsItemCards.current) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SETTINGS_ITEM_CARDS_SPACING),
        ) {
            if (title != null) {
                SettingsGroupTitleSmall(title)
            }
            content()
        }
        return
    }

    Surface {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            // The background of the "About" cards, so both tabs look the same.
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                if (title != null) {
                    SettingsGroupTitleSmall(title)
                }
                content()
            }
        }
    }
}

/** A section of the main settings screen on mobile: a card with a single option, which opens the section. */
@Composable
fun LemuroidSettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

/** Arrow at the end of an option that opens another screen. */
@Composable
fun SettingsOpenArrow() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
    )
}

@Composable
fun LemuroidSettingsSlider(
    modifier: Modifier = Modifier,
    state: SettingValueState<Int>,
    steps: Int,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit = { },
) {
    val defaultColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val disabledColors =
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = defaultColors.disabledHeadlineColor,
            leadingIconColor = defaultColors.disabledLeadingIconColor,
            trailingIconColor = defaultColors.disabledTrailingIconColor,
            supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
        )

    SettingsFocusItem(enabled) {
        SettingsSlider(
            modifier = modifier,
            steps = steps,
            value = state.value.toFloat(),
            onValueChange = { state.value = it.roundToInt() },
            valueRange = valueRange,
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            colors = if (enabled) defaultColors else disabledColors,
        )
    }
}

// Switch tracks use the almost grey tone of the logo green (with a white thumb) instead of the primary color, which
// is white.
@Composable
private fun BrandGreenFills(content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme =
            colorScheme.copy(
                primary = colorResource(R.color.playretro_soft_green),
                onPrimary = colorResource(R.color.playretro_on_soft_green),
            ),
        content = content,
    )
}

@Composable
private fun SettingsGroupTitleSmall(title: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // As big as the options inside the card (16sp) and bolder, so it never looks smaller than them.
        val primary = MaterialTheme.colorScheme.primary
        val titleStyle = MaterialTheme.typography.titleMedium.copy(color = primary, fontWeight = FontWeight.Medium)
        ProvideTextStyle(value = titleStyle) { title() }
    }
}

@Composable
// Transparent rows: the background is the one of the card.
private fun lemuroidSettingsColor(enabled: Boolean): ListItemColors {
    val defaultColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    if (enabled) {
        return defaultColors
    }

    return ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = defaultColors.disabledHeadlineColor,
        leadingIconColor = defaultColors.disabledLeadingIconColor,
        trailingIconColor = defaultColors.disabledTrailingIconColor,
        supportingColor = defaultColors.supportingTextColor.copy(alpha = 0.3f),
    )
}
