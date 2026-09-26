package com.example.liftbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews

/**
 * A screen's [PrimaryActionButton], held at the bottom where a thumb reaches it. It rides above
 * the keyboard, so Save stays reachable while typing. Use as a Scaffold's bottomBar, or at the
 * foot of a sheet with the sheet's colour as [containerColor].
 */
@Composable
fun BottomActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.background,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(containerColor)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
    ) {
        PrimaryActionButton(text = text, onClick = onClick, enabled = enabled)
    }
}

@ThemePreviews
@Composable
private fun BottomActionBarPreview() {
    LiftBookPreview {
        BottomActionBar(text = "Start workout", onClick = {})
    }
}
