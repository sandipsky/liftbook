package com.example.liftbook.data.repository

import androidx.room.withTransaction
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.RoutineDao
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.projection.RoutineWithExercises
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.data.mapper.toEntity
import com.example.liftbook.data.mapper.updatedTo
import com.example.liftbook.domain.calculator.RoutineNames
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val database: LiftBookDatabase,
    private val routineDao: RoutineDao,
    private val clock: Clock,
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAll().map { routines -> routines.map(RoutineWithExercises::toDomain) }

    override fun observeRoutine(id: String): Flow<Routine?> = routineDao.observeById(id).map { it?.toDomain() }

    override suspend fun getRoutine(id: String): Routine? = routineDao.getById(id)?.toDomain()

    override suspend fun createRoutine(draft: RoutineDraft): String {
        val name = draft.checkedName()
        val id = newId()
        val now = clock.instant()
        database.withTransaction {
            routineDao.insert(RoutineEntity(id = id, name = name, notes = null, createdAt = now, updatedAt = now))
            routineDao.insertExercises(
                draft.exercises.mapIndexed { position, exercise -> exercise.toEntity(newId(), id, position) },
            )
        }
        return id
    }

    override suspend fun updateRoutine(id: String, draft: RoutineDraft) {
        val name = draft.checkedName()
        database.withTransaction {
            val routine = requireNotNull(routineDao.getEntity(id)) { "No routine with id $id" }
            val existing = routineDao.getExercises(id).associateBy { it.id }
            val updated = mutableListOf<RoutineExerciseEntity>()
            val inserted = mutableListOf<RoutineExerciseEntity>()
            draft.exercises.forEachIndexed { position, exercise ->
                // A row is reused at most once; anything else is new.
                val row = exercise.id?.takeIf { updated.none { kept -> kept.id == it } }?.let(existing::get)
                if (row != null) {
                    updated += row.updatedTo(exercise, position)
                } else {
                    inserted += exercise.toEntity(newId(), id, position)
                }
            }
            val removed = existing.keys - updated.mapTo(HashSet()) { it.id }
            if (removed.isNotEmpty()) routineDao.deleteExercises(removed)
            routineDao.updateExercises(updated)
            routineDao.insertExercises(inserted)
            routineDao.update(routine.copy(name = name, updatedAt = clock.instant()))
        }
    }

    override suspend fun duplicateRoutine(id: String): String = database.withTransaction {
        val source = requireNotNull(routineDao.getEntity(id)) { "No routine with id $id" }
        val copyId = newId()
        val now = clock.instant()
        routineDao.insert(
            source.copy(
                id = copyId,
                name = RoutineNames.copyName(source.name, routineDao.getNames()),
                createdAt = now,
                updatedAt = now,
            ),
        )
        routineDao.insertExercises(routineDao.getExercises(id).map { it.copy(id = newId(), routineId = copyId) })
        copyId
    }

    override suspend fun deleteRoutine(id: String) = routineDao.delete(id)

    /** The editor validates for the user; this guards the invariants for every other caller. */
    private fun RoutineDraft.checkedName(): String {
        val normalized = RoutineNames.normalize(name)
        require(normalized.isNotEmpty()) { "A routine needs a name" }
        require(exercises.all { it.target.sets in SetTarget.MIN_SETS..SetTarget.MAX_SETS }) {
            "Each exercise needs ${SetTarget.MIN_SETS}–${SetTarget.MAX_SETS} sets"
        }
        return normalized
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
