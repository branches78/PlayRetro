package com.swordfish.lemuroid.app.utils.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    SettingsFocusItem(enabled) { onFocusItemClicked ->
        SettingsMenuLink(
            enabled = enabled,
            icon = icon,
            title = title,
            subtitle = subtitle,
            action = action,
            onClick = {
                onFocusItemClicked()
                onClick()
            },
            colors = lemuroidSettingsColor(enabled),
        )
    }
}

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
