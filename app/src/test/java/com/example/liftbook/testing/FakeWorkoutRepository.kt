package com.example.liftbook.testing

import com.example.liftbook.domain.calculator.PlannedSet
import com.example.liftbook.domain.calculator.SetPrefill
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.domain.model.StartWorkoutResult
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.domain.model.WorkoutEdits
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.domain.model.WorkoutSet
import com.example.liftbook.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * An in-memory [WorkoutRepository] with the real rules: one workout in progress, pre-fill from
 * [lastSessions], sets dropped on finish. Ids are predictable — "workout-1", "workout-1-e0",
 * "workout-1-e0-s0" — so tests can name them.
 */
class FakeWorkoutRepository(
    private val routines: FakeRoutineRepository,
    private val exercises: FakeExerciseRepository? = null,
    /** When workouts start. */
    var now: Instant = Instant.EPOCH,
) : WorkoutRepository {

    val active = MutableStateFlow<Workout?>(null)

    /** Finished workouts by id. */
    val finished = MutableStateFlow<Map<String, Workout>>(emptyMap())

    /** Every routine a workout was started from, in order. */
    val started = mutableListOf<String>()

    /** The sets done the last time each exercise was performed, by exercise id: what pre-fills it. */
    val lastSessions = mutableMapOf<String, List<LoggedSet>>()

    /** Earlier working sets by exercise id: what [previousSets] returns. */
    val history = mutableMapOf<String, List<LoggedSet>>()

    /** Every batch of edits saved, in order. */
    val savedEdits = mutableListOf<WorkoutEdits>()

    /** Each exercise's own rest time as last set, by exercise id. */
    val exerciseRests = mutableMapOf<String, Int?>()

    /** When set, the next start throws, as a failing database would. */
    var failNextStart = false

    /** When set, the next edit to the workout throws. */
    var failNextWrite = false

    private var workoutCount = 0
    private var idCount = 0

    override fun observeActiveWorkout(): Flow<Workout?> = active

    override fun observeWorkout(id: String): Flow<Workout?> =
        combine(active, finished) { current, done -> current?.takeIf { it.id == id } ?: done[id] }

    override suspend fun startFromRoutine(routineId: String): StartWorkoutResult {
        if (failNextStart) {
            failNextStart = false
            throw IllegalStateException("Simulated write failure")
        }
        active.value?.let { current ->
            return if (current.routineId == routineId) {
                StartWorkoutResult.Resumed(current.id)
            } else {
                StartWorkoutResult.OtherWorkoutActive(current.id, current.name)
            }
        }
        val routine = routines.getRoutine(routineId) ?: return StartWorkoutResult.RoutineNotFound
        val id = "workout-${++workoutCount}"
        started += routineId
        active.value = Workout(
            id = id,
            name = routine.name,
            routineId = routineId,
            startedAt = now,
            finishedAt = null,
            exercises = routine.exercises.mapIndexed { index, item ->
                val planned = SetPrefill.forRoutineExercise(item.target, item.exercise.type, lastSessions[item.exercise.id].orEmpty())
                exerciseOf("$id-e$index", item.exercise, planned).copy(restSecondsOverride = item.restSecondsOverride)
            },
        )
        return StartWorkoutResult.Started(id)
    }

    override suspend fun startEmpty(name: String): StartWorkoutResult {
        if (failNextStart) {
            failNextStart = false
            throw IllegalStateException("Simulated write failure")
        }
        active.value?.let { return StartWorkoutResult.OtherWorkoutActive(it.id, it.name) }
        val id = "workout-${++workoutCount}"
        active.value = Workout(id = id, name = name, routineId = null, startedAt = now, finishedAt = null, exercises = emptyList())
        return StartWorkoutResult.Started(id)
    }

    override suspend fun discardActiveWorkout(workoutId: String) {
        if (active.value?.id == workoutId) active.value = null
    }

    override suspend fun addExercises(workoutId: String, exerciseIds: List<String>): List<String> {
        val library = requireNotNull(exercises) { "Pass the exercises to add them" }
        val added = exerciseIds.mapNotNull { id ->
            val exercise = library.getExercise(id) ?: return@mapNotNull null
            exerciseOf("$workoutId-x${++idCount}", exercise, SetPrefill.forAddedExercise(exercise.type, lastSessions[id].orEmpty()))
        }
        edit { it.copy(exercises = it.exercises + added) }
        return added.map { it.id }
    }

    override suspend fun removeExercise(workoutExerciseId: String) = edit { workout ->
        workout.copy(exercises = workout.exercises.filterNot { it.id == workoutExerciseId })
    }

    override suspend fun reorderExercises(workoutId: String, orderedIds: List<String>) = edit { workout ->
        val listed = orderedIds.distinct().mapNotNull { id -> workout.exercises.firstOrNull { it.id == id } }
        workout.copy(exercises = listed + workout.exercises.filterNot { it in listed })
    }

    override suspend fun addSet(workoutExerciseId: String): String {
        val id = "$workoutExerciseId-a${++idCount}"
        editExercise(workoutExerciseId) { exercise ->
            val values = SetPrefill.forAddedSet(
                exercise.exercise.type,
                exercise.sets.map { SetPrefill.SetCandidate(it.setType, it.isCompleted, it.values) },
            )
            exercise.copy(sets = exercise.sets + newSet(id, PlannedSet(values = values)))
        }
        return id
    }

    override suspend fun removeSet(setId: String) = edit { workout ->
        workout.copy(exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.filterNot { it.id == setId }) })
    }

    override suspend fun setSetType(setId: String, setType: SetType) = editSets { if (it.id == setId) it.copy(setType = setType) else it }

    override suspend fun saveEdits(workoutId: String, edits: WorkoutEdits) {
        failIfAsked()
        savedEdits += edits
        edit { workout ->
            workout.copy(
                note = if (edits.workoutNoteChanged) edits.workoutNote else workout.note,
                exercises = workout.exercises.map { exercise ->
                    exercise.copy(
                        note = if (exercise.id in edits.exerciseNotes) edits.exerciseNotes[exercise.id] else exercise.note,
                        sets = exercise.sets.map { set -> edits.setValues[set.id]?.let { set.withValues(it) } ?: set },
                    )
                },
            )
        }
    }

    override suspend fun completeSet(
        setId: String,
        values: SetValues,
        completedAt: Instant,
        rest: RestTimer?,
        carryForward: Map<String, SetValues>,
    ) {
        failIfAsked()
        edit { workout ->
            workout.copy(
                rest = rest,
                exercises = workout.exercises.map { exercise ->
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            when {
                                set.id == setId -> set.withValues(values).copy(isCompleted = true, completedAt = completedAt)
                                set.id in carryForward -> set.withValues(carryForward.getValue(set.id))
                                else -> set
                            }
                        },
                    )
                },
            )
        }
    }

    override suspend fun uncompleteSet(setId: String): Boolean {
        val workout = active.value ?: return false
        val set = workout.exercises.flatMap { it.sets }.firstOrNull { it.id == setId } ?: return false
        val startedByThisSet = set.completedAt != null && workout.rest?.startedAt == set.completedAt
        editSets { if (it.id == setId) it.copy(isCompleted = false, completedAt = null) else it }
        if (startedByThisSet) edit { it.copy(rest = null) }
        return startedByThisSet
    }

    override suspend fun setRest(workoutId: String, rest: RestTimer?) = edit { it.copy(rest = rest) }

    override suspend fun setExerciseRest(workoutExerciseId: String, seconds: Int?) {
        val exercise = active.value?.exercises?.firstOrNull { it.id == workoutExerciseId } ?: return
        exerciseRests[exercise.exercise.id] = seconds
        edit { workout ->
            workout.copy(
                exercises = workout.exercises.map {
                    if (it.exercise.id == exercise.exercise.id) {
                        it.copy(exercise = it.exercise.copy(defaultRestSeconds = seconds), restSecondsOverride = if (it.id == workoutExerciseId) null else it.restSecondsOverride)
                    } else {
                        it
                    }
                },
            )
        }
    }

    override suspend fun finishWorkout(workoutId: String, finishedAt: Instant): Boolean {
        failIfAsked()
        val workout = active.value?.takeIf { it.id == workoutId } ?: return false
        val done = workout.copy(
            finishedAt = finishedAt,
            rest = null,
            exercises = workout.exercises
                .map { exercise -> exercise.copy(sets = exercise.sets.filter { it.isCompleted }) }
                .filter { it.sets.isNotEmpty() || it.note != null },
        )
        active.value = null
        finished.update { it + (workoutId to done) }
        return true
    }

    override suspend fun previousSets(exerciseIds: Set<String>, before: Instant): Map<String, List<LoggedSet>> =
        history.filterKeys { it in exerciseIds }

    private fun exerciseOf(id: String, exercise: Exercise, planned: List<PlannedSet>) =
        WorkoutExercise(id = id, exercise = exercise, sets = planned.mapIndexed { index, set -> newSet("$id-s$index", set) })

    private fun newSet(id: String, planned: PlannedSet) = WorkoutSet(
        id = id,
        setType = planned.setType,
        isCompleted = false,
        weightKg = planned.values.weightKg,
        reps = planned.values.reps,
        durationSeconds = planned.values.durationSeconds,
        distanceMeters = planned.values.distanceMeters,
    )

    private fun WorkoutSet.withValues(values: SetValues) = copy(
        weightKg = values.weightKg,
        reps = values.reps,
        durationSeconds = values.durationSeconds,
        distanceMeters = values.distanceMeters,
    )

    private fun edit(change: (Workout) -> Workout) {
        active.update { it?.let(change) }
    }

    private fun editExercise(id: String, change: (WorkoutExercise) -> WorkoutExercise) = edit { workout ->
        workout.copy(exercises = workout.exercises.map { if (it.id == id) change(it) else it })
    }

    private fun editSets(change: (WorkoutSet) -> WorkoutSet) = edit { workout ->
        workout.copy(exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.map(change)) })
    }

    private fun failIfAsked() {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("Simulated write failure")
        }
    }
}
