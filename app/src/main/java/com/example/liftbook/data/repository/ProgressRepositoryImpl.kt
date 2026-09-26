package com.example.liftbook.data.repository

import com.example.liftbook.data.local.dao.ProgressDao
import com.example.liftbook.data.local.projection.MuscleSetsRow
import com.example.liftbook.data.local.projection.TrainedExerciseRow
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.data.mapper.toExerciseWorkouts
import com.example.liftbook.domain.model.ExerciseWorkout
import com.example.liftbook.domain.model.MuscleSets
import com.example.liftbook.domain.model.TrainedExercise
import com.example.liftbook.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val progressDao: ProgressDao,
) : ProgressRepository {

    override fun observeExerciseWorkouts(exerciseId: String): Flow<List<ExerciseWorkout>> =
        progressDao.observeExerciseSets(exerciseId).map { rows -> rows.toExerciseWorkouts() }

    override fun observeMuscleSets(from: Instant, until: Instant): Flow<List<MuscleSets>> =
        progressDao.observeMuscleSets(from, until).map { rows -> rows.map(MuscleSetsRow::toDomain) }

    override fun observeTrainedExercises(): Flow<List<TrainedExercise>> =
        progressDao.observeTrainedExercises().map { rows -> rows.map(TrainedExerciseRow::toDomain) }
}
