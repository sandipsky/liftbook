package com.example.liftbook.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * The one primary action of a screen: full width, 56dp tall and filled with the accent. Place it
 * in the lower part of the screen, where a thumb reaches it one-handed.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = PrimaryActionHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = Spacing.lg),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private val PrimaryActionHeight = 56.dp

@ThemePreviews
@Composable
private fun PrimaryActionButtonPreview() {
    LiftBookPreview {
        PrimaryActionButton(text = "Create exercise", onClick = {}, modifier = Modifier.padding(Spacing.gutter))
    }
}
