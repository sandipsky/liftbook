package com.example.liftbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/*
 * Chips are tonal pills, matching the search field, with no outline: a page of bordered boxes
 * reads as a form, not a choice. Selection is shown in ink — an inverse-coloured fill — rather
 * than in the accent, which is kept for primary actions. Ink against the tonal fill is a large
 * luminance difference, so the state reads without relying on hue; FilterChip also exposes it
 * to TalkBack as "selected".
 */

/** One option in a single-choice group. Put the group in a [Modifier.selectableGroup]. */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = liftBookChipColors(),
        border = liftBookChipBorder(selected = selected, enabled = enabled),
    )
}

/** A chip that opens a list of options. [active] once an option other than "any" is chosen. */
@Composable
fun DropdownFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = modifier.semantics { role = Role.DropdownList },
        shape = CircleShape,
        colors = liftBookChipColors(),
        border = liftBookChipBorder(selected = active, enabled = true),
    )
}

@Composable
private fun liftBookChipColors(): SelectableChipColors {
    val colors = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        containerColor = colors.surfaceContainerHigh,
        labelColor = colors.onSurface,
        iconColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.inverseSurface,
        selectedLabelColor = colors.inverseOnSurface,
        selectedLeadingIconColor = colors.inverseOnSurface,
        selectedTrailingIconColor = colors.inverseOnSurface,
    )
}

@Composable
private fun liftBookChipBorder(selected: Boolean, enabled: Boolean): BorderStroke =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = Color.Transparent,
        selectedBorderColor = Color.Transparent,
        borderWidth = 0.dp,
        selectedBorderWidth = 0.dp,
    )

@ThemePreviews
@Composable
private fun ChoiceChipPreview() {
    LiftBookPreview {
        FlowRow(
            modifier = Modifier.padding(Spacing.gutter).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            DropdownFilterChip(label = "Muscle", active = false, onClick = {})
            DropdownFilterChip(label = "Barbell", active = true, onClick = {})
            ChoiceChip(selected = true, onClick = {}, label = "Chest")
            ChoiceChip(selected = false, onClick = {}, label = "Back")
            ChoiceChip(selected = false, onClick = {}, label = "Shoulders")
        }
    }
}
