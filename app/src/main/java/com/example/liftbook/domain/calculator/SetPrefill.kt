package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.toValues

/** A set about to be created: its type and the values it starts with. */
data class PlannedSet(
    val setType: SetType = SetType.NORMAL,
    val values: SetValues = SetValues(),
)

/**
 * Where a new set's starting values come from (FR-3.4). They are written into the set as real
 * values the user can change, never shown as a placeholder they can't (architecture §5.3).
 */
object SetPrefill {

    /**
     * An exercise added to a workout: the sets done last time, same types and values, all not
     * yet completed. Never done before, it gets the default number of empty sets.
     */
    fun forAddedExercise(type: ExerciseType, lastSession: List<LoggedSet>): List<PlannedSet> =
        if (lastSession.isEmpty()) {
            List(SetTarget.defaultFor(type).sets) { PlannedSet() }
        } else {
            lastSession.map { PlannedSet(it.setType, it.metrics.toValues().applicableTo(type)) }
        }

    /**
     * An exercise from a routine: the routine decides how many sets and what each aims for, and
     * last time fills only what the routine leaves blank — a planned weight wins over what was
     * lifted last time (architecture §8, Q9). Set i takes working set i from last time, or the
     * last one when the routine has more sets.
     */
    fun forRoutineExercise(target: SetTarget, type: ExerciseType, lastSession: List<LoggedSet>): List<PlannedSet> {
        val planned = target.applicableTo(type)
        val working = lastSession.filter { it.setType != SetType.WARMUP }.ifEmpty { lastSession }
        return List(planned.sets) { index ->
            val last = (working.getOrNull(index) ?: working.lastOrNull())?.metrics?.toValues() ?: SetValues()
            PlannedSet(
                values = SetValues(
                    weightKg = planned.weightKg ?: last.weightKg,
                    reps = planned.reps ?: last.reps,
                    durationSeconds = planned.durationSeconds ?: last.durationSeconds,
                    distanceMeters = planned.distanceMeters ?: last.distanceMeters,
                ).applicableTo(type),
            )
        }
    }

    /**
     * A set added to an exercise already in the workout: the values of its last working set,
     * or of its last set if all are warm-ups. It starts as a normal set.
     */
    fun forAddedSet(type: ExerciseType, existing: List<SetCandidate>): SetValues {
        val source = existing.lastOrNull { it.setType != SetType.WARMUP } ?: existing.lastOrNull()
        return source?.values?.applicableTo(type) ?: SetValues()
    }

    /**
     * When the set at [completedIndex] is completed with [values], which later sets take the
     * same values: those not yet completed, of the same type, with nothing entered. Log 80 × 8
     * for the first set of a new exercise and the next ones read 80 × 8, ready to confirm.
     * Returns their indices.
     */
    fun carryForward(
        type: ExerciseType,
        sets: List<SetCandidate>,
        completedIndex: Int,
    ): List<Int> {
        val completed = sets.getOrNull(completedIndex) ?: return emptyList()
        return sets.indices.filter { index ->
            val set = sets[index]
            index > completedIndex && !set.isCompleted && set.setType == completed.setType && set.values.isBlankFor(type)
        }
    }

    /** What pre-filling needs to know about a set already in the workout. */
    data class SetCandidate(
        val setType: SetType,
        val isCompleted: Boolean,
        val values: SetValues,
    )
}
