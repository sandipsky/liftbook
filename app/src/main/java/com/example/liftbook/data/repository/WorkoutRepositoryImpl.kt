package com.example.liftbook.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.ExerciseDao
import com.example.liftbook.data.local.dao.RoutineDao
import com.example.liftbook.data.local.dao.SetDao
import com.example.liftbook.data.local.dao.WorkoutDao
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.WorkoutListRow
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.data.mapper.toListItem
import com.example.liftbook.data.mapper.toMetrics
import com.example.liftbook.domain.calculator.PlannedSet
import com.example.liftbook.domain.calculator.SetPrefill
import com.example.liftbook.domain.calculator.WorkoutNames
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.domain.calculator.WorkoutTimes
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutEdits
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.domain.model.WorkoutRevision
import com.example.liftbook.domain.model.toValues
import com.example.liftbook.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val database: LiftBookDatabase,
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) : WorkoutRepository {

    override fun observeActiveWorkout(): Flow<Workout?> = workoutDao.observeActive().map { it?.toDomain() }

    override fun observeWorkout(id: String): Flow<Workout?> = workoutDao.observeById(id).map { it?.toDomain() }

    override suspend fun startFromRoutine(routineId: String): StartWorkoutResult = database.withTransaction {
        // The check and the inserts share one transaction, so two starts can never both
        // succeed: Room can't express "at most one row with finishedAt IS NULL" as an index.
        workoutDao.getActive()?.let { active ->
            return@withTransaction if (active.routineId == routineId) {
                StartWorkoutResult.Resumed(active.id)
            } else {
                StartWorkoutResult.OtherWorkoutActive(active.id, active.name)
            }
        }
        val routine = routineDao.getById(routineId)?.toDomain()
            ?: return@withTransaction StartWorkoutResult.RoutineNotFound

        val workoutId = insertWorkout(name = routine.name, routineId = routine.id)
        val rows = routine.exercises.mapIndexed { position, item ->
            WorkoutExerciseEntity(
                id = newId(),
                workoutId = workoutId,
                exerciseId = item.exercise.id,
                position = position,
                note = null,
                restSecondsOverride = item.restSecondsOverride,
            )
        }
        workoutDao.insertExercises(rows)
        setDao.insertAll(
            routine.exercises.zip(rows) { item, row ->
                val planned = SetPrefill.forRoutineExercise(item.target, item.exercise.type, lastSession(item.exercise.id))
                row.setsFor(planned)
            }.flatten(),
        )
        StartWorkoutResult.Started(workoutId)
    }

    override suspend fun startEmpty(name: String): StartWorkoutResult = database.withTransaction {
        workoutDao.getActive()?.let { active ->
            return@withTransaction StartWorkoutResult.OtherWorkoutActive(active.id, active.name)
        }
        StartWorkoutResult.Started(insertWorkout(name = name, routineId = null))
    }

    override suspend fun discardActiveWorkout(workoutId: String) = workoutDao.deleteIfActive(workoutId)

    override suspend fun addExercises(workoutId: String, exerciseIds: List<String>): List<String> =
        database.withTransaction {
            var position = workoutDao.maxExercisePosition(workoutId) + 1
            val rows = mutableListOf<WorkoutExerciseEntity>()
            val sets = mutableListOf<WorkoutSetEntity>()
            exerciseIds.forEach { exerciseId ->
                val exercise = exerciseDao.getById(exerciseId) ?: return@forEach
                val row = WorkoutExerciseEntity(
                    id = newId(),
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    position = position++,
                    note = null,
                    restSecondsOverride = null,
                )
                rows += row
                sets += row.setsFor(SetPrefill.forAddedExercise(exercise.type, lastSession(exerciseId)))
            }
            workoutDao.insertExercises(rows)
            setDao.insertAll(sets)
            rows.map { it.id }
        }

    override suspend fun removeExercise(workoutExerciseId: String) = workoutDao.deleteExercise(workoutExerciseId)

    override suspend fun reorderExercises(workoutId: String, orderedIds: List<String>) = database.withTransaction {
        val existing = workoutDao.getExercises(workoutId)
        val byId = existing.associateBy { it.id }
        val listed = orderedIds.distinct().mapNotNull(byId::get)
        val order = listed + existing.filterNot { it in listed }
        order.forEachIndexed { position, row ->
            if (row.position != position) workoutDao.setExercisePosition(row.id, position)
        }
    }

    override suspend fun addSet(workoutExerciseId: String): String = database.withTransaction {
        val row = requireNotNull(workoutDao.getExercise(workoutExerciseId)) { "No workout exercise $workoutExerciseId" }
        val type = requireNotNull(exerciseDao.getById(row.exerciseId)) { "No exercise ${row.exerciseId}" }.type
        val existing = setDao.getForWorkoutExercise(workoutExerciseId)
        val values = SetPrefill.forAddedSet(
            type,
            existing.map { SetPrefill.SetCandidate(it.setType, it.isCompleted, it.values()) },
        )
        val set = row.setAt(position = (existing.maxOfOrNull { it.position } ?: -1) + 1, PlannedSet(values = values))
        setDao.insertAll(listOf(set))
        set.id
    }

    override suspend fun removeSet(setId: String) = setDao.delete(setId)

    override suspend fun setSetType(setId: String, setType: SetType) = setDao.setType(setId, setType)

    override suspend fun saveEdits(workoutId: String, edits: WorkoutEdits) {
        if (edits.isEmpty) return
        database.withTransaction {
            edits.setValues.forEach { (id, values) -> setDao.setValues(id, values) }
            edits.exerciseNotes.forEach { (id, note) -> workoutDao.setExerciseNote(id, note) }
            if (edits.workoutNoteChanged) workoutDao.setNote(workoutId, edits.workoutNote)
        }
    }

    override suspend fun completeSet(
        setId: String,
        values: SetValues,
        completedAt: Instant,
        rest: RestTimer?,
        carryForward: Map<String, SetValues>,
    ) = database.withTransaction {
        val set = setDao.getById(setId) ?: return@withTransaction
        setDao.complete(
            id = setId,
            completedAt = completedAt,
            weightKg = values.weightKg,
            reps = values.reps,
            durationSeconds = values.durationSeconds,
            distanceMeters = values.distanceMeters,
        )
        carryForward.forEach { (id, carried) -> setDao.setValues(id, carried) }
        workoutDao.setRest(set.workoutId, rest?.startedAt, rest?.endsAt)
    }

    override suspend fun uncompleteSet(setId: String): Boolean = database.withTransaction {
        val set = setDao.getById(setId) ?: return@withTransaction false
        setDao.uncomplete(setId)
        // Completing a set stamps the rest it starts with the same instant, so a match means
        // this set started the rest that's running.
        val startedByThisSet = set.completedAt != null && workoutDao.getRestStartedAt(set.workoutId) == set.completedAt
        if (startedByThisSet) workoutDao.setRest(set.workoutId, null, null)
        startedByThisSet
    }

    override suspend fun setRest(workoutId: String, rest: RestTimer?) =
        workoutDao.setRest(workoutId, rest?.startedAt, rest?.endsAt)

    override suspend fun setExerciseRest(workoutExerciseId: String, seconds: Int?) = database.withTransaction {
        val row = workoutDao.getExercise(workoutExerciseId) ?: return@withTransaction
        exerciseDao.setDefaultRestSeconds(row.exerciseId, seconds)
        // The user's choice applies now, over whatever the routine set for this workout.
        workoutDao.setExerciseRestOverride(workoutExerciseId, null)
    }

    override suspend fun finishWorkout(workoutId: String, finishedAt: Instant): Boolean = database.withTransaction {
        if (workoutDao.getActive()?.id != workoutId) return@withTransaction false
        setDao.deleteIncomplete(workoutId)
        workoutDao.deleteEmptyExercises(workoutId)
        workoutDao.finish(workoutId, finishedAt) > 0
    }

    override suspend fun previousSets(exerciseIds: Set<String>, before: Instant): Map<String, List<LoggedSet>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        return setDao.previousWorkingSets(exerciseIds, before)
            .groupBy({ it.set.exerciseId }) { row -> row.set.toMetrics(row.exerciseType)?.let { LoggedSet(row.set.setType, it) } }
            .mapValues { (_, sets) -> sets.filterNotNull() }
    }

    override fun observeFinishedWorkouts(): Flow<PagingData<WorkoutListItem>> =
        Pager(PagingConfig(pageSize = HISTORY_PAGE_SIZE, enablePlaceholders = false)) { workoutDao.finishedWorkouts() }
            .flow
            .map { page -> page.map(WorkoutListRow::toListItem) }

    override fun observeFinishedBetween(from: Instant, until: Instant): Flow<List<WorkoutListItem>> =
        workoutDao.observeFinishedBetween(from, until).map { rows -> rows.map(WorkoutListRow::toListItem) }

    override suspend fun saveRevision(workoutId: String, revision: WorkoutRevision): Boolean = database.withTransaction {
        val workout = workoutDao.getById(workoutId) ?: return@withTransaction false
        val finishedAt = workout.finishedAt ?: return@withTransaction false
        val name = WorkoutNames.normalize(revision.name)
        require(name.isNotEmpty()) { "A workout needs a name" }
        require(revision.finishedAt.isAfter(revision.startedAt)) { "A workout ends after it starts" }
        val from = WorkoutSpan(workout.startedAt, finishedAt)
        val to = WorkoutSpan(revision.startedAt, revision.finishedAt)
        val exercises = revision.exercises.filter { it.sets.isNotEmpty() || it.note != null }
        val existingExercises = workoutDao.getExercises(workoutId).associateBy { it.id }
        val existingSets = setDao.getForWorkout(workoutId).associateBy { it.id }

        // Removed exercises take their sets with them (CASCADE); removed sets of kept ones go here.
        (existingExercises.keys - exercises.mapTo(HashSet()) { it.id }).forEach { workoutDao.deleteExercise(it) }
        val removedSets = existingSets.keys - exercises.flatMapTo(HashSet()) { exercise -> exercise.sets.map { it.id } }
        if (removedSets.isNotEmpty()) setDao.deleteAll(removedSets)

        val insertedExercises = mutableListOf<WorkoutExerciseEntity>()
        val updatedExercises = mutableListOf<WorkoutExerciseEntity>()
        val insertedSets = mutableListOf<WorkoutSetEntity>()
        val updatedSets = mutableListOf<WorkoutSetEntity>()
        exercises.forEachIndexed { position, exercise ->
            val existing = existingExercises[exercise.id]
            val row = existing?.copy(position = position, note = exercise.note) ?: WorkoutExerciseEntity(
                id = exercise.id,
                workoutId = workoutId,
                exerciseId = exercise.exerciseId,
                position = position,
                note = exercise.note,
                restSecondsOverride = null,
            )
            when {
                existing == null -> insertedExercises += row
                row != existing -> updatedExercises += row
            }
            exercise.sets.forEachIndexed { setPosition, set ->
                val old = existingSets[set.id]
                require(old == null || old.workoutExerciseId == row.id) { "Set ${set.id} belongs to another exercise" }
                val values = set.metrics.toValues()
                val entity = WorkoutSetEntity(
                    id = set.id,
                    workoutExerciseId = row.id,
                    workoutId = workoutId,
                    exerciseId = row.exerciseId,
                    position = setPosition,
                    setType = set.setType,
                    isCompleted = true,
                    weightKg = values.weightKg,
                    reps = values.reps,
                    durationSeconds = values.durationSeconds,
                    distanceMeters = values.distanceMeters,
                    completedAt = WorkoutTimes.completedAt(old?.completedAt, from, to),
                )
                when {
                    old == null -> insertedSets += entity
                    entity != old -> updatedSets += entity
                }
            }
        }
        // Exercises before their sets, for the foreign key.
        workoutDao.insertExercises(insertedExercises)
        workoutDao.updateExercises(updatedExercises)
        setDao.insertAll(insertedSets)
        setDao.updateAll(updatedSets)
        workoutDao.update(workout.copy(name = name, startedAt = to.start, finishedAt = to.end, note = revision.note))
        true
    }

    override suspend fun deleteFinishedWorkout(workoutId: String) = workoutDao.deleteIfFinished(workoutId)

    private suspend fun insertWorkout(name: String, routineId: String?): String {
        val id = newId()
        workoutDao.insert(
            WorkoutEntity(id = id, name = name, routineId = routineId, startedAt = clock.instant(), finishedAt = null, note = null),
        )
        return id
    }

    /** The sets completed the last time the exercise was done, or none. */
    private suspend fun lastSession(exerciseId: String): List<LoggedSet> =
        setDao.getLatestExerciseSession(exerciseId)?.toDomain()?.sets.orEmpty()

    private fun WorkoutExerciseEntity.setsFor(planned: List<PlannedSet>): List<WorkoutSetEntity> =
        planned.mapIndexed { position, set -> setAt(position, set) }

    /** A not-yet-completed set of this workout exercise. */
    private fun WorkoutExerciseEntity.setAt(position: Int, planned: PlannedSet) = WorkoutSetEntity(
        id = newId(),
        workoutExerciseId = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        position = position,
        setType = planned.setType,
        isCompleted = false,
        weightKg = planned.values.weightKg,
        reps = planned.values.reps,
        durationSeconds = planned.values.durationSeconds,
        distanceMeters = planned.values.distanceMeters,
        completedAt = null,
    )

    private suspend fun SetDao.setValues(id: String, values: SetValues) =
        setValues(id, values.weightKg, values.reps, values.durationSeconds, values.distanceMeters)

    private fun WorkoutSetEntity.values() = SetValues(weightKg, reps, durationSeconds, distanceMeters)

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val HISTORY_PAGE_SIZE = 20
    }
}
