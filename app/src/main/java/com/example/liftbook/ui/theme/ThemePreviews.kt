package com.example.liftbook.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Renders a preview in both light and dark theme. Every screen uses this. */
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ThemePreviews

/** Theme plus a background-coloured surface, for previewing components in isolation. */
@Composable
fun LiftBookPreview(content: @Composable () -> Unit) {
    LiftBookTheme {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
