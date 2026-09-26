package com.example.liftbook.domain.model

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** User settings (§2.10 of the architecture), changed on the settings screen (FR-6.2). */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Rest after a set, for exercises without their own rest time; 0 turns the timer off (FR-3.5). */
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
    /** Where the history calendar's weeks and the weekly summary start (FR-6.2). */
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 90
    }
}

/**
 * The display unit system. Values are always stored metric — kilograms and metres. LiftBook
 * shows kilograms and kilometres only, so this is always [KG]; the type stays so display code
 * keeps one place that names the unit.
 */
enum class WeightUnit {
    KG,
    LB,
}

// The enums below are persisted by name, in DataStore and in JSON exports (FR-6.3).
// Never rename or remove a constant; only add new ones.

/** The two week starts the setting offers (FR-6.2). */
enum class FirstDayOfWeek(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY);

    companion object {
        /**
         * Before the user picks, the locale's convention — or Monday where the locale starts
         * weeks on neither, since the setting offers only these two.
         */
        fun defaultFor(locale: Locale): FirstDayOfWeek =
            if (WeekFields.of(locale).firstDayOfWeek == DayOfWeek.SUNDAY) SUNDAY else MONDAY
    }
}

/** Light, dark, or whichever the system is using (FR-6.2). */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
