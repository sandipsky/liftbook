package com.example.liftbook.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The only spacing values used in the app: a 4dp-based scale of 4 / 8 / 12 / 16 / 24 / 32 / 48.
 * If a layout seems to need something in between, the layout is wrong, not the scale.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp

    /** Horizontal inset of screen content from the display edge. */
    val gutter = md
}

object IconSize {
    /** Icons inside text fields, rows and chips. */
    val inline = 20.dp

    /** Icons that are themselves the action — app bar buttons, FABs, empty states. */
    val action = 24.dp
}
