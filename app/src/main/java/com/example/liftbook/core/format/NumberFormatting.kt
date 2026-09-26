package com.example.liftbook.core.format

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats a measurement for display: at most [maxFractionDigits] decimals, trailing zeros
 * dropped, digits grouped, in the locale's own separators and numerals.
 * 80.0 → "80", 82.5 → "82.5", 1234.567 → "1,234.57".
 */
fun formatDecimal(
    value: Double,
    maxFractionDigits: Int = 2,
    locale: Locale = Locale.getDefault(),
): String = NumberFormat.getNumberInstance(locale).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = maxFractionDigits
    roundingMode = RoundingMode.HALF_UP
}.format(value)

/** A duration as a clock reading: 45 → "0:45", 1500 → "25:00", 3930 → "1:05:30". */
fun formatDuration(totalSeconds: Int, locale: Locale = Locale.getDefault()): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(locale, "%d:%02d", minutes, remainder)
    }
}
