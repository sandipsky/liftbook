package com.example.liftbook.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.ExerciseDao
import com.example.liftbook.data.local.dao.SetDao
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.projection.ExerciseSessionWithSets
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val database: LiftBookDatabase,
    private val exerciseDao: ExerciseDao,
    private val setDao: SetDao,
    private val clock: Clock,
) : ExerciseRepository {

    override fun observeLibrary(): Flow<List<Exercise>> =
        exerciseDao.observeLibrary().map { exercises -> exercises.map(ExerciseEntity::toDomain) }

    override fun observeArchived(): Flow<List<Exercise>> =
        exerciseDao.observeArchived().map { exercises -> exercises.map(ExerciseEntity::toDomain) }

    override fun observeExercise(id: String): Flow<Exercise?> =
        exerciseDao.observeById(id).map { it?.toDomain() }

    override suspend fun getExercise(id: String): Exercise? = exerciseDao.getById(id)?.toDomain()

    override suspend fun createCustomExercise(draft: ExerciseDraft): String {
        val id = UUID.randomUUID().toString()
        exerciseDao.insert(
            ExerciseEntity(
                id = id,
                name = ExerciseNames.normalize(draft.name),
                primaryMuscle = draft.primaryMuscle,
                equipment = draft.equipment,
                type = draft.type,
                isCustom = true,
                isArchived = false,
                defaultRestSeconds = null,
                notes = null,
                createdAt = clock.instant(),
            ),
        )
        return id
    }

    override suspend fun updateCustomExercise(id: String, draft: ExerciseDraft) {
        database.withTransaction {
            val current = requireNotNull(exerciseDao.getById(id)) { "No exercise with id $id" }
            require(current.isCustom) { "Built-in exercises can't be edited (FR-1.3)" }
            require(draft.type == current.type || !setDao.hasSetsForExercise(id)) {
                "An exercise's type can't change once sets are logged against it"
            }
            exerciseDao.update(
                current.copy(
                    name = ExerciseNames.normalize(draft.name),
                    primaryMuscle = draft.primaryMuscle,
                    equipment = draft.equipment,
                    type = draft.type,
                ),
            )
        }
    }

    override suspend fun archive(id: String) = exerciseDao.setArchived(id, archived = true)

    override suspend fun restore(id: String) = exerciseDao.setArchived(id, archived = false)

    override suspend fun hasLoggedSets(id: String): Boolean = setDao.hasSetsForExercise(id)

    override fun observeHistory(exerciseId: String): Flow<PagingData<ExerciseSession>> =
        Pager(PagingConfig(pageSize = HISTORY_PAGE_SIZE, enablePlaceholders = false)) {
            setDao.exerciseSessions(exerciseId)
        }.flow.map { page -> page.map(ExerciseSessionWithSets::toDomain) }

    override fun observeLastSession(exerciseId: String): Flow<ExerciseSession?> =
        setDao.observeLatestExerciseSession(exerciseId).map { it?.toDomain() }

    private companion object {
        const val HISTORY_PAGE_SIZE = 20
    }
}
