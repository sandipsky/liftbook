package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle
import com.example.liftbook.ui.theme.tabularNumbers

/*
 * Settings are tonal rows on the background, like the rest of the app's lists: a shift of
 * surface tone groups each one, with no outline or divider. A row's title is what it is, the
 * line under it what it does, and its current value sits on the trailing edge, where the eye
 * lands to check it.
 */

/**
 * A setting that opens something: a dialog, when it shows its [value], or another screen, when
 * it shows a chevron. [destructive] rows name what they destroy in the error colour, always
 * with their [icon], so the warning never rests on colour alone.
 */
@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    clickLabel: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    value: String? = null,
    spokenValue: String? = value,
    icon: ImageVector? = null,
    showsChevron: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val titleColor = if (destructive) colors.error else colors.onSurface
    val spoken = listOfNotNull(title, supporting, spokenValue).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .clickable(enabled = enabled, onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.size(IconSize.action),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.rowTitle, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleMedium.tabularNumbers(), color = colors.onSurface, maxLines = 1)
        }
        if (showsChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(IconSize.inline),
            )
        }
    }
}

/**
 * A setting with a few short choices, all in view: the title, what it affects, and a segmented
 * selector under them. One tap changes it; nothing to open and nothing to save.
 */
@Composable
fun <T> SettingChoice(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    spokenLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.rowTitle, color = colors.onSurface)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        SegmentedSelector(options = options, selected = selected, onSelect = onSelect, label = label, spokenLabel = spokenLabel)
    }
}

/**
 * A setting that's on or off. The whole row is the switch, a 64dp target, so it's hard to miss;
 * TalkBack hears it as one switch, named by [title] and [supporting].
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.rowTitle, color = colors.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        // The row takes the tap; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

private val RowMinHeight = 64.dp
private const val DISABLED_ALPHA = 0.38f

@ThemePreviews
@Composable
private fun SettingsRowPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SettingsRow(
                title = "Default rest",
                supporting = "For exercises without their own rest time",
                value = "1:30",
                onClick = {},
                clickLabel = "change",
            )
            SettingChoice(
                title = "Week starts on",
                supporting = "For the calendar and the weekly summary",
                options = listOf("Monday", "Sunday"),
                selected = "Monday",
                onSelect = {},
                label = { it },
                spokenLabel = { it },
            )
            SettingsSwitchRow(title = "Remind me before workouts", supporting = "Next · Wed 18:00, Push", checked = true, onCheckedChange = {})
            SettingsRow(title = "Backup & data", supporting = "Last backup · 3 days ago", showsChevron = true, onClick = {}, clickLabel = "open")
            SettingsRow(
                title = "Clear all data",
                supporting = "Delete everything and reset settings",
                icon = Icons.Outlined.DeleteOutline,
                destructive = true,
                onClick = {},
                clickLabel = "clear",
            )
        }
    }
}
