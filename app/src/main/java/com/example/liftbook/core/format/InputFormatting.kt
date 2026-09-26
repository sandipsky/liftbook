package com.example.liftbook.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/*
 * Text for number fields the user edits. Display formatting (formatDecimal) groups digits and
 * uses the locale's numerals; text to be edited must instead parse back exactly, so it is
 * ungrouped and uses ASCII digits, with the locale's decimal mark.
 */

/** A measurement as editable text: 82.5 → "82.5" ("82,5" where the decimal mark is a comma), 80.0 → "80". */
fun formatDecimalForInput(
    value: Double,
    maxFractionDigits: Int = 2,
    locale: Locale = Locale.getDefault(),
): String {
    if (value == 0.0) return "0"
    val plain = BigDecimal.valueOf(value)
        .setScale(maxFractionDigits, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    val separator = decimalSeparator(locale)
    return if (separator == '.') plain else plain.replace('.', separator)
}

/**
 * Reads a typed measurement. Either "." or "," is accepted as the decimal mark, whatever the
 * locale, because keyboards differ in which one they offer. Null for anything but a plain
 * non-negative number.
 */
fun parseDecimal(text: String, locale: Locale = Locale.getDefault()): Double? {
    val separator = decimalSeparator(locale)
    val normalized = buildString {
        for (char in text.trim()) {
            when {
                char == '.' || char == ',' || char == separator -> append('.')
                char.isDigit() -> append(Character.digit(char, 10))
                else -> return null
            }
        }
    }
    if (normalized.isEmpty() || normalized == "." || normalized.count { it == '.' } > 1) return null
    return normalized.toDoubleOrNull()
}

/** The characters a decimal field may contain as its decimal mark in [locale]. */
fun decimalSeparators(locale: Locale = Locale.getDefault()): Set<Char> = setOf('.', ',', decimalSeparator(locale))

/*
 * Duration fields are typed as digits that fill from the right, like a microwave: "130" reads
 * as 1:30. The last two digits are seconds and the rest minutes, so "90" is 0:90 — 90 seconds.
 */

/** The longest duration a duration field holds: 99:59. */
const val MAX_DURATION_INPUT_SECONDS = 99 * 60 + 59

/** Seconds as duration-field digits: 90 → "130", 45 → "45", 900 → "1500". Capped at 99:59. */
fun durationToDigits(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceIn(0, MAX_DURATION_INPUT_SECONDS)
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes == 0) remainder.toString() else minutes.toString() + remainder.toString().padStart(2, '0')
}

/** Duration-field digits as seconds: "130" → 90, "90" → 90. Null when there are no digits. */
fun digitsToDuration(digits: String): Int? {
    if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
    val ascii = digits.map { Character.digit(it, 10) }.joinToString("")
    val seconds = ascii.takeLast(2).toInt()
    val minutes = ascii.dropLast(2).toIntOrNull() ?: 0
    return minutes * 60 + seconds
}

private fun decimalSeparator(locale: Locale): Char = DecimalFormatSymbols.getInstance(locale).decimalSeparator
