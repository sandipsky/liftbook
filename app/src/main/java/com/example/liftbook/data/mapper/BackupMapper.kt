package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.projection.DataCountsRow
import com.example.liftbook.domain.model.DataCounts

fun DataCountsRow.toDomain(): DataCounts =
    DataCounts(workouts = workouts, routines = routines, customExercises = customExercises, weighIns = weighIns)
