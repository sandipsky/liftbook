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
import androidx.compose.ui.semantics.semantics
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import com.example.liftbook.ui.theme.rowTitle

/** A small labelled value for a screen's header: the label receding above, the value below. Read as one item by TalkBack. */
@Composable
fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xxs))
        Text(value, style = MaterialTheme.typography.rowTitle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@ThemePreviews
@Composable
private fun FactPreview() {
    LiftBookPreview {
        Row(Modifier.padding(Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Fact(label = "Muscle", value = "Chest", modifier = Modifier.weight(1f))
            Fact(label = "Equipment", value = "Barbell", modifier = Modifier.weight(1f))
            Fact(label = "Logged as", value = "Weight & reps", modifier = Modifier.weight(1f))
        }
    }
}
