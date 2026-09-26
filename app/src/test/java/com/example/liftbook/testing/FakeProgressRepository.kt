package com.example.liftbook.testing

import com.example.liftbook.domain.calculator.completedSets
import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.TrainedExercise
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * A [ProgressRepository] read from [workouts]' finished workouts, with the real rules: completed
 * working sets only, finished workouts only.
 */
class FakeProgressRepository(private val workouts: FakeWorkoutRepository) : ProgressRepository {

    override fun observeExerciseWorkouts(exerciseId: String): Flow<List<ExerciseWorkout>> = workouts.finished.map { done ->
        done.values.sortedBy { it.startedAt }.mapNotNull { workout ->
            workout.workingSets().filter { it.first == exerciseId }.map { it.second.metrics }
                .takeIf { it.isNotEmpty() }
                ?.let { ExerciseWorkout(workout.id, workout.name, workout.startedAt, it) }
        }
    }

    override fun observeMuscleSets(from: Instant, until: Instant): Flow<List<MuscleSets>> = workouts.finished.map { done ->
        done.values
            .filter { !it.startedAt.isBefore(from) && it.startedAt.isBefore(until) }
            .flatMap { workout ->
                workout.exercises
                    .flatMap { exercise -> exercise.completedSets().filter { it.setType != SetType.WARMUP }.map { exercise.exercise.primaryMuscle } }
                    .groupingBy { it }
                    .eachCount()
                    .map { (muscle, sets) -> MuscleSets(workout.startedAt, muscle, sets) }
            }
    }

    override fun observeTrainedExercises(): Flow<List<TrainedExercise>> = workouts.finished.map { done ->
        done.values
            .flatMap { workout -> workout.exercises.filter { exercise -> exercise.completedSets().any { it.setType != SetType.WARMUP } }.map { it.exercise to workout } }
            .groupBy({ it.first.id }) { it }
            .map { (_, uses) ->
                TrainedExercise(
                    exercise = uses.first().first,
                    lastPerformedAt = uses.maxOf { it.second.startedAt },
                    workouts = uses.map { it.second.id }.distinct().size,
                )
            }
            .sortedByDescending { it.lastPerformedAt }
    }

    private fun Workout.workingSets() = exercises.flatMap { exercise ->
        exercise.completedSets().filter { it.setType != SetType.WARMUP }.map { exercise.exercise.id to it }
    }
}
