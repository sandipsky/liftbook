package com.example.liftbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.tabularNumbers

/**
 * A number the user came to see — time, volume, sets — with its label receding above and its
 * unit receding after. The value takes the strongest treatment on the screen; [valueStyle]
 * sets how strong. Tabular figures, so a ticking clock doesn't jitter. TalkBack reads it as one
 * item, as "label, [spokenValue]".
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    spokenValue: String = listOfNotNull(value, unit).joinToString(" "),
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$label, $spokenValue" }) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = value,
                style = valueStyle.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@ThemePreviews
@Composable
private fun StatTilePreview() {
    LiftBookPreview {
        Row(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(label = "Time", value = "32:14", modifier = Modifier.weight(1f))
            StatTile(label = "Volume", value = "4,320", unit = "kg", modifier = Modifier.weight(1f))
            StatTile(label = "Sets", value = "8 / 18", modifier = Modifier.weight(1f))
        }
    }
}
