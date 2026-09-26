package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.WorkoutExercise

/**
 * Total volume in kilograms: Σ weight × reps over completed sets, leaving out warm-ups
 * (FR-3.10). A bodyweight set counts only what was added to it, and cardio counts nothing —
 * volume is a measure of load lifted (architecture §8, Q1 and Q2).
 */
fun volume(sets: List<LoggedSet>): Double = sets
    .filter { it.setType != SetType.WARMUP }
    .sumOf { set ->
        when (val metrics = set.metrics) {
            is SetMetrics.Strength -> metrics.weightKg * metrics.reps
            is SetMetrics.Bodyweight -> (metrics.addedWeightKg ?: 0.0) * metrics.reps
            is SetMetrics.Cardio -> 0.0
        }
    }

/**
 * The exercise's completed sets as what they recorded, in order. A completed set whose values
 * don't fit the exercise type is skipped rather than guessed at.
 */
fun WorkoutExercise.completedSets(): List<LoggedSet> = sets
    .filter { it.isCompleted }
    .mapNotNull { set -> set.values.metricsFor(exercise.type)?.let { LoggedSet(set.setType, it) } }
