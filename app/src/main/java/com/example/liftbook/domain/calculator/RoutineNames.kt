package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.Routine
import java.util.Locale

/** Naming rules for routines (FR-2.1, FR-2.2). */
object RoutineNames {

    const val MAX_LENGTH = 40

    /** Trims and collapses runs of whitespace, as for exercise names. */
    fun normalize(raw: String): String = ExerciseNames.normalize(raw)

    /**
     * Checks a name typed into the editor. Names are unique among routines, ignoring case, so
     * the list and the history never show two routines that look the same. [editingId] is the
     * routine being edited, which may keep its own name.
     */
    fun validate(name: String, routines: List<Routine>, editingId: String? = null): RoutineNameError? {
        val normalized = normalize(name)
        return when {
            normalized.isEmpty() -> RoutineNameError.BLANK
            normalized.length > MAX_LENGTH -> RoutineNameError.TOO_LONG
            routines.any { it.id != editingId && normalize(it.name).equals(normalized, ignoreCase = true) } ->
                RoutineNameError.DUPLICATE
            else -> null
        }
    }

    /**
     * The name for a copy of [name]: the next number that isn't [taken]. "Push" → "Push 2", or
     * "Push 3" if that exists. A name that already ends in a number counts on from it, so a
     * copy of "Day 1" is "Day 2". Numbers read the same in every language, which a "(copy)"
     * suffix wouldn't, and the result is usually the name the user wanted anyway.
     */
    fun copyName(name: String, taken: Collection<String>): String {
        val normalized = normalize(name)
        val numbered = TRAILING_NUMBER.matchEntire(normalized)
        val base = numbered?.groupValues?.get(1) ?: normalized
        var number = numbered?.groupValues?.get(2)?.toInt()?.plus(1) ?: FIRST_COPY_NUMBER
        val takenKeys = taken.mapTo(HashSet()) { normalize(it).lowercase(Locale.ROOT) }
        while (true) {
            val suffix = " $number"
            val candidate = base.take(MAX_LENGTH - suffix.length).trimEnd() + suffix
            if (candidate.lowercase(Locale.ROOT) !in takenKeys) return candidate
            number++
        }
    }

    private const val FIRST_COPY_NUMBER = 2
    private val TRAILING_NUMBER = Regex("(.*\\S)\\s+(\\d{1,4})")
}

enum class RoutineNameError {
    BLANK,
    TOO_LONG,
    DUPLICATE,
}
