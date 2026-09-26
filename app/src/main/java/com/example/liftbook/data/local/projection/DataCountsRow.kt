package com.example.liftbook.data.local.projection

/** How much is on this phone (FR-6.3–6.5), as one row of counts. */
data class DataCountsRow(
    val workouts: Int,
    val routines: Int,
    val customExercises: Int,
    val weighIns: Int,
)
