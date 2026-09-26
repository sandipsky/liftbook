package com.example.liftbook.data.local.projection

import androidx.room.Embedded
import androidx.room.Relation
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import java.time.Instant

/** A routine with its exercises (in no particular order — sort by position) and derived last-performed time. */
data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    /** Start of the latest finished workout from this routine (FR-2.4); computed by the query. */
    val lastPerformedAt: Instant?,
    @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseWithExercise>,
)

data class RoutineExerciseWithExercise(
    @Embedded val routineExercise: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)
