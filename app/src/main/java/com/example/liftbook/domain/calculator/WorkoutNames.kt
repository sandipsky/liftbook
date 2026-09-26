package com.example.liftbook.domain.calculator

/**
 * Naming rules for a workout (FR-4.2). A workout started from a routine takes the routine's
 * name, so the length limit is the routine's.
 */
object WorkoutNames {

    const val MAX_LENGTH = RoutineNames.MAX_LENGTH

    /** Trims and collapses runs of whitespace, as for exercise and routine names. */
    fun normalize(raw: String): String = ExerciseNames.normalize(raw)
}
