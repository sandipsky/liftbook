package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.Exercise

/** Naming rules for custom exercises (FR-1.2, FR-1.3). */
object ExerciseNames {

    const val MAX_LENGTH = 60

    /** Trims and collapses runs of whitespace: "  Cable   Y-Raise " → "Cable Y-Raise". */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /**
     * Checks a name typed into the editor against the rest of the library. Names must be unique
     * among exercises in the library, ignoring case; an archived exercise doesn't reserve its
     * name. [editingId] is the exercise being edited, which may keep its own name.
     */
    fun validate(name: String, library: List<Exercise>, editingId: String? = null): ExerciseNameError? {
        val normalized = normalize(name)
        return when {
            normalized.isEmpty() -> ExerciseNameError.BLANK
            normalized.length > MAX_LENGTH -> ExerciseNameError.TOO_LONG
            library.any { it.isTaking(normalized, editingId) } -> ExerciseNameError.DUPLICATE
            else -> null
        }
    }

    /** [name] numbered to be unlike every name in [taken]: "Zercher Squat" → "Zercher Squat 2". */
    fun copyName(name: String, taken: Collection<String>): String = numberedName(name, taken, MAX_LENGTH)

    private fun Exercise.isTaking(name: String, editingId: String?) =
        id != editingId && !isArchived && normalize(this.name).equals(name, ignoreCase = true)

    private val WHITESPACE = Regex("\\s+")
}

enum class ExerciseNameError {
    BLANK,
    TOO_LONG,
    DUPLICATE,
}
