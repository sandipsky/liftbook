package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.example.liftbook.domain.calculator.oneRepMax
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetTarget
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.SetValues
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRestTimerScheduler
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.routine
import com.example.liftbook.testing.routineExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val pullUp = exercise("Pull-Up", muscle = MuscleGroup.BACK, equipment = Equipment.NONE, type = ExerciseType.BODYWEIGHT)
    private val exercises = FakeExerciseRepository(listOf(bench, pullUp))
    private val routines = FakeRoutineRepository(
        exercises,
        listOf(
            routine(
                "Push",
                routineExercise(bench, SetTarget(sets = 3, reps = 5, weightKg = 100.0)),
                routineExercise(pullUp, SetTarget(sets = 2, reps = 8)),
                id = "push",
            ),
        ),
    )
    private val workouts = FakeWorkoutRepository(routines, exercises)
    private val settings = FakeSettingsRepository()
    private val alerts = FakeRestTimerScheduler()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC)
    private val now: Instant = clock.instant()

    // Set ids from the fake: workout-1, exercise e0 (bench) and e1 (pull-up), sets s0, s1…
    private val benchSet1 = "workout-1-e0-s0"
    private val benchSet2 = "workout-1-e0-s1"

    private fun TestScope.activeWorkout(): ActiveWorkoutViewModel =
        ActiveWorkoutViewModel(workouts, exercises, settings, alerts, clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun ActiveWorkoutViewModel.set(id: String): ActiveSet =
        uiState.value.exercises.flatMap { it.sets }.first { it.set.id == id }

    private fun storedSet(id: String) = workouts.active.value!!.exercises.flatMap { it.sets }.first { it.id == id }

    @Test
    fun `shows the workout in progress`() = runTest {
        workouts.startFromRoutine("push")

        val state = activeWorkout().uiState.value

        assertFalse(state.isLoading)
        assertEquals("Push", state.workout?.name)
        assertEquals(listOf(bench, pullUp), state.exercises.map { it.item.exercise })
        assertEquals(5, state.progress.totalSets)
    }

    @Test
    fun `with nothing in progress there's no workout`() = runTest {
        val state = activeWorkout().uiState.value

        assertFalse(state.isLoading)
        assertNull(state.workout)
    }

    @Test
    fun `discarding deletes it, and keeps it on screen until the screen closes`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()

        viewModel.events.test {
            viewModel.onAction(ActiveWorkoutAction.Discard)
            assertEquals(ActiveWorkoutEvent.Discarded, awaitItem())
        }
        assertNull(workouts.active.value)
        assertEquals("Push", viewModel.uiState.value.workout?.name)
    }

    @Test
    fun `marking a set done saves what was typed, and starts the rest`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.set(benchSet1).fields.weight.state.setTextAndPlaceCursorAtEnd("102.5")

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        val stored = storedSet(benchSet1)
        assertTrue(stored.isCompleted)
        assertEquals(SetValues(weightKg = 102.5, reps = 5), stored.values)
        assertEquals(now, stored.completedAt)
        assertEquals(RestTimer(now, now.plusSeconds(90)), workouts.active.value!!.rest)
        // The alert is set for the same moment, and names what's next.
        assertEquals(now.plusSeconds(90), alerts.scheduledAt)
        assertEquals(bench.name, alerts.nextExerciseName)
    }

    @Test
    fun `a set missing a value isn't marked done, and what's missing is pointed out`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.set(benchSet1).fields.reps.state.setTextAndPlaceCursorAtEnd("")

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        assertFalse(storedSet(benchSet1).isCompleted)
        assertTrue(viewModel.set(benchSet1).showMissing)
        assertNull(workouts.active.value!!.rest)
        assertNull(alerts.scheduledAt)
    }

    @Test
    fun `a done set's values carry into the empty sets after it`() = runTest {
        workouts.startEmpty("Evening workout")
        val viewModel = activeWorkout()
        viewModel.addExercises(bench.id)
        // Never done before: three empty sets.
        val sets = viewModel.uiState.value.exercises.single().sets
        assertEquals(List(3) { SetValues() }, sets.map { it.set.values })
        sets[0].fields.weight.state.setTextAndPlaceCursorAtEnd("80")
        sets[0].fields.reps.state.setTextAndPlaceCursorAtEnd("8")

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(sets[0].set.id))

        val stored = workouts.active.value!!.exercises.single().sets
        assertEquals(List(3) { SetValues(weightKg = 80.0, reps = 8) }, stored.map { it.values })
        assertEquals(listOf(true, false, false), stored.map { it.isCompleted })
        assertEquals("80", viewModel.set(stored[2].id).fields.weight.text)
    }

    @Test
    fun `a done set that beats every earlier workout is flagged as a record as it's done`() = runTest {
        workouts.history[bench.id] = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5)))
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        assertTrue(viewModel.set(benchSet1).records.isEmpty())

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        assertEquals(
            listOf(PersonalRecord.HeaviestWeight(100.0, 5), PersonalRecord.BestEstimatedOneRepMax(oneRepMax(100.0, 5), 100.0, 5)),
            viewModel.set(benchSet1).records,
        )
        assertEquals(viewModel.set(benchSet1).records, viewModel.uiState.value.exercises.first().records)
    }

    @Test
    fun `a record moves to the set that beats it, and back when that set is undone`() = runTest {
        workouts.history[bench.id] = listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(95.0, 5)))
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))
        viewModel.set(benchSet2).fields.weight.state.setTextAndPlaceCursorAtEnd("102.5")

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet2))

        // Measured against earlier workouts, the same way the summary will be: the heavier set holds both.
        assertTrue(viewModel.set(benchSet1).records.isEmpty())
        assertEquals(2, viewModel.set(benchSet2).records.size)

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet2))

        assertEquals(2, viewModel.set(benchSet1).records.size)
        assertTrue(viewModel.set(benchSet2).records.isEmpty())
    }

    @Test
    fun `nothing is a record the first time an exercise is done`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        assertTrue(viewModel.set(benchSet1).records.isEmpty())
        assertTrue(viewModel.uiState.value.exercises.all { it.records.isEmpty() })
    }

    @Test
    fun `undoing the set that started the rest stops the rest`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        assertFalse(storedSet(benchSet1).isCompleted)
        assertNull(workouts.active.value!!.rest)
        assertNull(alerts.scheduledAt)
    }

    @Test
    fun `an exercise's own rest time is used, and off means no rest`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        val benchId = viewModel.uiState.value.exercises.first().id

        viewModel.onAction(ActiveWorkoutAction.SetExerciseRest(benchId, 180))
        assertEquals(180, viewModel.uiState.value.exercises.first().restSeconds)
        assertTrue(viewModel.uiState.value.exercises.first().hasOwnRest)
        assertEquals(180, workouts.exerciseRests[bench.id])
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))
        assertEquals(now.plusSeconds(180), workouts.active.value!!.rest?.endsAt)

        viewModel.onAction(ActiveWorkoutAction.SetExerciseRest(benchId, 0))
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet2))
        assertNull(workouts.active.value!!.rest)
        assertNull(alerts.scheduledAt)
    }

    @Test
    fun `the default rest applies to exercises without their own`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()

        viewModel.onAction(ActiveWorkoutAction.SetDefaultRest(120))
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        assertEquals(120, settings.userPreferences.value.defaultRestSeconds)
        assertEquals(now.plusSeconds(120), workouts.active.value!!.rest?.endsAt)
    }

    @Test
    fun `a rest can be lengthened, shortened and skipped, and the alert follows`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        viewModel.onAction(ActiveWorkoutAction.AdjustRest(30))
        assertEquals(now.plusSeconds(120), workouts.active.value!!.rest?.endsAt)
        assertEquals(now.plusSeconds(120), alerts.scheduledAt)
        assertEquals(bench.name, alerts.nextExerciseName)

        viewModel.onAction(ActiveWorkoutAction.AdjustRest(-60))
        assertEquals(now.plusSeconds(60), alerts.scheduledAt)

        viewModel.onAction(ActiveWorkoutAction.SkipRest)
        assertNull(workouts.active.value!!.rest)
        assertNull(alerts.scheduledAt)
    }

    @Test
    fun `typing is saved once it pauses`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        runCurrent()
        workouts.savedEdits.clear()

        viewModel.set(benchSet2).fields.weight.state.setTextAndPlaceCursorAtEnd("97.5")
        Snapshot.sendApplyNotifications()
        advanceTimeBy(100)
        assertTrue(workouts.savedEdits.isEmpty())
        advanceTimeBy(500)

        assertEquals(97.5, storedSet(benchSet2).weightKg!!, 0.0)
        assertEquals(1, workouts.savedEdits.size)
    }

    @Test
    fun `notes are saved, trimmed, and a note field stays once shown`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        val benchId = viewModel.uiState.value.exercises.first().id
        assertFalse(viewModel.uiState.value.exercises.first().showNote)

        viewModel.onAction(ActiveWorkoutAction.ShowExerciseNote(benchId))
        viewModel.uiState.value.exercises.first().note.setTextAndPlaceCursorAtEnd("  Pause at the chest ")
        viewModel.uiState.value.workoutNote!!.setTextAndPlaceCursorAtEnd("Short on time")
        viewModel.onAction(ActiveWorkoutAction.SaveNow)

        val workout = workouts.active.value!!
        assertEquals("Pause at the chest", workout.exercises.first().note)
        assertEquals("Short on time", workout.note)
        assertTrue(viewModel.uiState.value.exercises.first().showNote)
    }

    @Test
    fun `exercises added mid-workout start from last time, and the first is brought into view`() = runTest {
        workouts.lastSessions[pullUp.id] = listOf(
            LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps = 12, addedWeightKg = null)),
            LoggedSet(SetType.NORMAL, SetMetrics.Bodyweight(reps = 10, addedWeightKg = null)),
        )
        workouts.startEmpty("Evening workout")
        val viewModel = activeWorkout()

        viewModel.events.test {
            viewModel.onAction(ActiveWorkoutAction.OpenPicker)
            assertEquals(listOf(bench, pullUp).sortedBy { it.name }, viewModel.uiState.value.picker?.results)
            viewModel.onAction(ActiveWorkoutAction.TogglePicked(pullUp.id))
            viewModel.onAction(ActiveWorkoutAction.AddPicked)

            val added = viewModel.uiState.value.exercises.single()
            assertEquals(ActiveWorkoutEvent.ExercisesAdded(added.id), awaitItem())
            assertEquals(listOf(12, 10), added.sets.map { it.set.reps })
        }
        assertNull(viewModel.uiState.value.picker)
    }

    @Test
    fun `an exercise can be removed`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()

        viewModel.onAction(ActiveWorkoutAction.RemoveExercise(viewModel.uiState.value.exercises.first().id))

        assertEquals(listOf(pullUp), viewModel.uiState.value.exercises.map { it.item.exercise })
    }

    @Test
    fun `reordering shows the new order at once and saves it`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()

        viewModel.onAction(ActiveWorkoutAction.StartReordering)
        assertTrue(viewModel.uiState.value.isReordering)
        viewModel.onAction(ActiveWorkoutAction.MoveExercise(from = 0, to = 1))
        assertEquals(listOf(pullUp, bench), viewModel.uiState.value.exercises.map { it.item.exercise })
        viewModel.onAction(ActiveWorkoutAction.StopReordering)

        assertFalse(viewModel.uiState.value.isReordering)
        assertEquals(listOf(pullUp, bench), workouts.active.value!!.exercises.map { it.exercise })
        assertEquals(listOf(pullUp, bench), viewModel.uiState.value.exercises.map { it.item.exercise })
    }

    @Test
    fun `sets can be added, removed and given a type, and warm-ups aren't numbered`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        val benchId = viewModel.uiState.value.exercises.first().id

        viewModel.onAction(ActiveWorkoutAction.AddSet(benchId))
        viewModel.onAction(ActiveWorkoutAction.ChangeSetType(benchSet1, SetType.WARMUP))
        viewModel.onAction(ActiveWorkoutAction.RemoveSet(benchSet2))

        val sets = viewModel.uiState.value.exercises.first().sets
        assertEquals(3, sets.size)
        assertEquals(listOf(null, 1, 2), sets.map { it.number })
        // The added set starts from the last working set.
        assertEquals(SetValues(weightKg = 100.0, reps = 5), sets.last().set.values)
    }

    @Test
    fun `finishing drops the sets not done, and hands over to the summary`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))

        viewModel.events.test {
            viewModel.onAction(ActiveWorkoutAction.Finish)
            assertEquals(ActiveWorkoutEvent.Finished("workout-1"), awaitItem())
        }
        val done = workouts.finished.value.getValue("workout-1")
        assertEquals(now, done.finishedAt)
        assertEquals(listOf(bench), done.exercises.map { it.exercise })
        assertEquals(1, done.exercises.single().sets.size)
        assertNull(workouts.active.value)
        assertNull(alerts.scheduledAt)
        // Still showing the workout while the screen leaves.
        assertEquals("Push", viewModel.uiState.value.workout?.name)
    }

    @Test
    fun `a failed save says so`() = runTest {
        workouts.startFromRoutine("push")
        val viewModel = activeWorkout()
        workouts.failNextWrite = true

        viewModel.events.test {
            viewModel.onAction(ActiveWorkoutAction.ToggleSetDone(benchSet1))
            assertEquals(ActiveWorkoutEvent.SaveFailed, awaitItem())
        }
        assertFalse(storedSet(benchSet1).isCompleted)
    }

    @Test
    fun `the banner names the workout, when it started and any rest`() = runTest {
        val banner = ActiveWorkoutBannerViewModel(workouts, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { banner.activeWorkout.collect {} }
        assertNull(banner.activeWorkout.value)

        workouts.startFromRoutine("push")

        // The fake starts workouts at the epoch.
        assertEquals(ActiveWorkoutBannerState("Push", Instant.EPOCH, rest = null), banner.activeWorkout.value)
    }

    private fun ActiveWorkoutViewModel.addExercises(vararg ids: String) {
        onAction(ActiveWorkoutAction.OpenPicker)
        ids.forEach { onAction(ActiveWorkoutAction.TogglePicked(it)) }
        onAction(ActiveWorkoutAction.AddPicked)
    }
}
