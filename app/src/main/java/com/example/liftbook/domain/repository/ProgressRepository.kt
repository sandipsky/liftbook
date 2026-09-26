package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.TrainedExercise
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads for progress and stats (FR-5). Read-only: progress is worked out from logged sets and
 * never stored, so every flow here follows edits to past workouts (FR-4.2). Only finished
 * workouts count, and warm-ups never do (FR-3.10).
 */
interface ProgressRepository {

    /**
     * Every finished workout the exercise has working sets in, oldest first, with those sets
     * (FR-5.1). An exercise's history is bounded by how often it's been done, so this is a
     * list rather than pages: a chart needs all of it at once.
     */
    fun observeExerciseWorkouts(exerciseId: String): Flow<List<ExerciseWorkout>>

    /**
     * Working sets per muscle group for each finished workout that started at or after [from] and
     * before [until] (FR-5.3).
     */
    fun observeMuscleSets(from: Instant, until: Instant): Flow<List<MuscleSets>>

    /** Every exercise with finished working sets, archived ones included, most recently done first. */
    fun observeTrainedExercises(): Flow<List<TrainedExercise>>
}
