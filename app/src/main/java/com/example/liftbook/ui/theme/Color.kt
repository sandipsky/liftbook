package com.example.liftbook.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The LiftBook palette: a warm graphite neutral base and a single accent, Ember.
 *
 * Nothing outside ui/theme references these tones. Composables read
 * MaterialTheme.colorScheme, which Theme.kt assembles from them. Every foreground/background
 * pairing the schemes produce was checked for WCAG AA: 4.5:1 for text, 3:1 for component
 * boundaries. Re-check if a tone changes.
 */

// Graphite: the neutral base. Warm-shifted so white space reads as paper rather than screen.
internal val Graphite0 = Color(0xFFFFFFFF)
internal val Graphite25 = Color(0xFFF7F6F3)
internal val Graphite50 = Color(0xFFF4F2EE)
internal val Graphite75 = Color(0xFFF1F0EC)
internal val Graphite100 = Color(0xFFEDEBE7)
internal val Graphite150 = Color(0xFFE9E7E2)
internal val Graphite200 = Color(0xFFE3E1DB)
internal val Graphite250 = Color(0xFFDDDBD5)
internal val Graphite300 = Color(0xFFDAD7D0)
internal val Graphite400 = Color(0xFFB1ACA4)
internal val Graphite500 = Color(0xFF8B867E)
internal val Graphite600 = Color(0xFF5C5852)
internal val Graphite700 = Color(0xFF3A3835)
internal val Graphite750 = Color(0xFF33312E)
internal val Graphite800 = Color(0xFF282624)
internal val Graphite850 = Color(0xFF1E1D1B)
internal val Graphite900 = Color(0xFF1B1A18)
internal val Graphite950 = Color(0xFF121110)
internal val Graphite1000 = Color(0xFF0C0C0B)

// Ember: the one accent. Reserved for the primary action and for progress and PR moments.
internal val Ember100 = Color(0xFFFFDFD0)
internal val Ember300 = Color(0xFFFF9D70)
internal val Ember400 = Color(0xFFFF8A57)
internal val Ember700 = Color(0xFFB03A0A)
internal val Ember800 = Color(0xFF6E2A0E)
internal val Ember950 = Color(0xFF3C1200)

// Error red, kept cool so it never reads as a shade of the accent.
internal val Red100 = Color(0xFFF9DEDC)
internal val Red200 = Color(0xFFFFDAD6)
internal val Red300 = Color(0xFFFFB4AB)
internal val Red600 = Color(0xFFB3261E)
internal val Red700 = Color(0xFF93000A)
internal val Red800 = Color(0xFF690005)
internal val Red900 = Color(0xFF410E0B)
