package com.example.liftbook.domain.model

import java.time.Instant

/**
 * An exercise in the library (FR-1.1, FR-1.2).
 *
 * Built-in exercises ship with the app and cannot be edited; custom exercises can (FR-1.3).
 * Either kind can be archived: it leaves the library, but every set logged against it stays.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val type: ExerciseType,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val createdAt: Instant,
    /** Per-exercise rest override for the rest timer (FR-3.5); null means the global default. */
    val defaultRestSeconds: Int? = null,
    val notes: String? = null,
) {
    val isEditable: Boolean get() = isCustom
}

/** The four fields a user sets when creating or editing a custom exercise (FR-1.2). */
data class ExerciseDraft(
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val type: ExerciseType,
)

// The enums below are persisted by name, in Room and in JSON exports (FR-6.3).
// Never rename or remove a constant; only add new ones.

enum class MuscleGroup {
    CHEST,
    BACK,
    SHOULDERS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    CORE,
    QUADS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    FULL_BODY,
    CARDIO,
    OTHER,
}

enum class Equipment {
    BARBELL,
    DUMBBELL,
    KETTLEBELL,
    CABLE,
    MACHINE,
    BAND,
    NONE,
    CARDIO_MACHINE,
    OTHER,
}

/** Decides what a set records (FR-3.3). */
enum class ExerciseType {
    /** Weight × reps. */
    STRENGTH,

    /** Duration, with optional distance. */
    CARDIO,

    /** Reps only. */
    BODYWEIGHT;

    companion object {
        /**
         * The type a new exercise most likely has, given what's been picked so far. The editor
         * applies this until the user chooses a type themselves.
         */
        fun suggestedFor(primaryMuscle: MuscleGroup?, equipment: Equipment?): ExerciseType = when {
            primaryMuscle == MuscleGroup.CARDIO || equipment == Equipment.CARDIO_MACHINE -> CARDIO
            equipment == Equipment.NONE || equipment == Equipment.BAND -> BODYWEIGHT
            else -> STRENGTH
        }
    }
}
