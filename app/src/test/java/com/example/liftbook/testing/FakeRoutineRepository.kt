package com.example.liftbook.testing

import com.example.liftbook.domain.calculator.RoutineNames
import com.example.liftbook.domain.model.Routine
import com.example.liftbook.domain.model.RoutineDraft
import com.example.liftbook.domain.model.RoutineExercise
import com.example.liftbook.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** An in-memory [RoutineRepository] that resolves exercises against [exercises], as Room would. */
class FakeRoutineRepository(
    private val exercises: FakeExerciseRepository,
    initial: List<Routine> = emptyList(),
) : RoutineRepository {

    private val routines = MutableStateFlow(initial.associateBy { it.id })

    /** When set, the next write throws, as a failing database would. */
    var failNextWrite = false

    private var nextId = 1

    val all: Map<String, Routine> get() = routines.value

    override fun observeRoutines(): Flow<List<Routine>> =
        routines.map { all -> all.values.sortedBy { it.name.lowercase() } }

    override fun observeRoutine(id: String): Flow<Routine?> = routines.map { it[id] }

    override suspend fun getRoutine(id: String): Routine? = routines.value[id]

    override suspend fun createRoutine(draft: RoutineDraft): String {
        failIfAsked()
        val id = "routine-${nextId++}"
        routines.update { it + (id to draft.toRoutine(id, previous = null)) }
        return id
    }

    override suspend fun updateRoutine(id: String, draft: RoutineDraft) {
        failIfAsked()
        val current = requireNotNull(routines.value[id])
        routines.update { it + (id to draft.toRoutine(id, previous = current)) }
    }

    override suspend fun duplicateRoutine(id: String): String {
        failIfAsked()
        val source = requireNotNull(routines.value[id])
        val copyId = "routine-${nextId++}"
        val name = RoutineNames.copyName(source.name, routines.value.values.map { it.name })
        routines.update {
            it + (copyId to source.copy(
                id = copyId,
                name = name,
                lastPerformedAt = null,
                exercises = source.exercises.mapIndexed { index, item -> item.copy(id = "$copyId-$index") },
            ))
        }
        return copyId
    }

    override suspend fun deleteRoutine(id: String) {
        routines.update { it - id }
    }

    private suspend fun RoutineDraft.toRoutine(id: String, previous: Routine?): Routine = Routine(
        id = id,
        name = RoutineNames.normalize(name),
        exercises = exercises.mapIndexed { index, draft ->
            RoutineExercise(
                id = draft.id ?: "$id-new-$index",
                exercise = requireNotNull(this@FakeRoutineRepository.exercises.getExercise(draft.exerciseId)),
                target = draft.target,
            )
        },
        lastPerformedAt = previous?.lastPerformedAt,
        createdAt = previous?.createdAt ?: Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun failIfAsked() {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("Simulated write failure")
        }
    }
}
