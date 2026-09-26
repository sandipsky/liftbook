package com.example.liftbook.testing

import androidx.paging.PagingData
import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseDraft
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** An in-memory [ExerciseRepository] that enforces the same rules as the real one. */
class FakeExerciseRepository(initial: List<Exercise> = emptyList()) : ExerciseRepository {

    private val exercises = MutableStateFlow(initial.associateBy { it.id })
    private val sessions = MutableStateFlow<Map<String, List<ExerciseSession>>>(emptyMap())

    /** Exercise ids that have logged sets. */
    val exercisesWithSets = mutableSetOf<String>()

    /** When set, the next create or update throws, as a failing database would. */
    var failNextWrite = false

    private var nextId = 1

    val all: Map<String, Exercise> get() = exercises.value

    fun setSessions(exerciseId: String, newestFirst: List<ExerciseSession>) {
        sessions.update { it + (exerciseId to newestFirst) }
    }

    override fun observeLibrary(): Flow<List<Exercise>> =
        exercises.map { all -> all.values.filterNot { it.isArchived }.sortedBy { it.name.lowercase() } }

    override fun observeArchived(): Flow<List<Exercise>> =
        exercises.map { all -> all.values.filter { it.isArchived }.sortedBy { it.name.lowercase() } }

    override fun observeExercise(id: String): Flow<Exercise?> = exercises.map { it[id] }

    override suspend fun getExercise(id: String): Exercise? = exercises.value[id]

    override suspend fun createCustomExercise(draft: ExerciseDraft): String {
        failIfAsked()
        val id = "custom-${nextId++}"
        exercises.update {
            it + (id to Exercise(
                id = id,
                name = ExerciseNames.normalize(draft.name),
                primaryMuscle = draft.primaryMuscle,
                equipment = draft.equipment,
                type = draft.type,
                isCustom = true,
                isArchived = false,
                createdAt = Instant.EPOCH,
            ))
        }
        return id
    }

    override suspend fun updateCustomExercise(id: String, draft: ExerciseDraft) {
        failIfAsked()
        val current = requireNotNull(exercises.value[id])
        require(current.isCustom)
        require(draft.type == current.type || id !in exercisesWithSets)
        exercises.update {
            it + (id to current.copy(
                name = ExerciseNames.normalize(draft.name),
                primaryMuscle = draft.primaryMuscle,
                equipment = draft.equipment,
                type = draft.type,
            ))
        }
    }

    override suspend fun archive(id: String) = setArchived(id, true)

    override suspend fun restore(id: String) = setArchived(id, false)

    override suspend fun hasLoggedSets(id: String): Boolean = id in exercisesWithSets

    override fun observeHistory(exerciseId: String): Flow<PagingData<ExerciseSession>> =
        sessions.map { PagingData.from(it[exerciseId].orEmpty()) }

    override fun observeLastSession(exerciseId: String): Flow<ExerciseSession?> =
        sessions.map { it[exerciseId]?.firstOrNull() }

    private fun setArchived(id: String, archived: Boolean) {
        exercises.update { all -> all[id]?.let { all + (id to it.copy(isArchived = archived)) } ?: all }
    }

    private fun failIfAsked() {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("Simulated write failure")
        }
    }
}
