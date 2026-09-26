package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.ExerciseRecords
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.domain.model.WorkoutSummary
import java.time.Duration
import java.time.LocalTime

/** How far through a workout the user is: what the header of the active workout shows. */
data class WorkoutProgress(
    val completedSets: Int,
    val totalSets: Int,
    /** Kilograms, completed working sets only (FR-3.10). */
    val volumeKg: Double,
) {
    val incompleteSets: Int get() = totalSets - completedSets

    companion object {
        val None = WorkoutProgress(completedSets = 0, totalSets = 0, volumeKg = 0.0)
    }
}

fun Workout.progress(): WorkoutProgress = WorkoutProgress(
    completedSets = exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
    totalSets = exercises.sumOf { it.sets.size },
    volumeKg = volume(exercises.flatMap { it.completedSets() }),
)

/** A set with the exercise it belongs to. */
data class SetInWorkout(val exercise: WorkoutExercise, val set: WorkoutSet)

fun Workout.findSet(setId: String): SetInWorkout? =
    exercises.firstNotNullOfOrNull { exercise -> exercise.sets.firstOrNull { it.id == setId }?.let { SetInWorkout(exercise, it) } }

/**
 * The set to do after [setId]: the next one not yet completed, in workout order, or — when
 * everything after it is done — the first one skipped earlier. Null when every set is done.
 */
fun Workout.nextSetAfter(setId: String): SetInWorkout? {
    val all = exercises.flatMap { exercise -> exercise.sets.map { SetInWorkout(exercise, it) } }
    val index = all.indexOfFirst { it.set.id == setId }
    return all.drop(index + 1).firstOrNull { !it.set.isCompleted }
        ?: all.take(index.coerceAtLeast(0)).firstOrNull { !it.set.isCompleted }
}

/**
 * What a finished workout adds up to (FR-3.8), with the personal records it set against
 * [previousSets] — each exercise's working sets from earlier workouts, by exercise id. An
 * exercise done twice in one workout is judged once, on all its sets together.
 */
fun Workout.summarize(previousSets: Map<String, List<LoggedSet>>): WorkoutSummary {
    val finished = finishedAt ?: startedAt
    val records = exercises
        .groupBy { it.exercise.id }
        .mapNotNull { (exerciseId, appearances) ->
            val records = detectPersonalRecords(previousSets[exerciseId].orEmpty(), appearances.flatMap { it.completedSets() })
            records.takeIf { it.isNotEmpty() }?.let { ExerciseRecords(appearances.first().exercise, it) }
        }
    return WorkoutSummary(
        durationSeconds = Duration.between(startedAt, finished).seconds.coerceAtLeast(0),
        volumeKg = volume(exercises.flatMap { it.completedSets() }),
        completedSets = exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
        records = records,
    )
}

/** The part of the day a workout starts in, which names an empty workout: "Evening workout". */
enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    companion object {
        fun of(time: LocalTime): TimeOfDay = when (time.hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> NIGHT
        }
    }
}
