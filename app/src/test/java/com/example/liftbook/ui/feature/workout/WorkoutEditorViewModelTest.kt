package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.UserPreferences
import com.example.liftbook.domain.model.WeightUnit
import com.example.liftbook.domain.model.WorkoutRevision
import com.example.liftbook.testing.FakeExerciseRepository
import com.example.liftbook.testing.FakeRoutineRepository
import com.example.liftbook.testing.FakeSettingsRepository
import com.example.liftbook.testing.FakeWorkoutRepository
import com.example.liftbook.testing.MainDispatcherRule
import com.example.liftbook.testing.doneExercise
import com.example.liftbook.testing.doneSet
import com.example.liftbook.testing.exercise
import com.example.liftbook.testing.finishedWorkout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bench = exercise("Bench Press (Barbell)")
    private val pullUp = exercise("Pull-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT)
    private val squat = exercise("Squat (Barbell)", MuscleGroup.QUADS)
    private val exercises = FakeExerciseRepository(listOf(bench, pullUp, squat))
    private val workouts = FakeWorkoutRepository(FakeRoutineRepository(exercises), exercises)
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)

    private val start = Instant.parse("2026-09-24T18:00:00Z")
    private val push = finishedWorkout(
        "w",
        "Push",
        "2026-09-24T18:00:00Z",
        listOf(
            doneExercise(
                "w-bench",
                bench,
                doneSet("b0", weightKg = 40.0, reps = 10, type = SetType.WARMUP),
                doneSet("b1", weightKg = 80.0, reps = 8),
                doneSet("b2", weightKg = 80.0, reps = 6),
            ),
            doneExercise("w-pull", pullUp, doneSet("p1", reps = 10)),
        ),
        minutes = 60,
        note = "Good one",
    )

    private fun TestScope.editor(id: String = "w", unit: WeightUnit = WeightUnit.KG): WorkoutEditorViewModel =
        WorkoutEditorViewModel(id, workouts, exercises, FakeSettingsRepository(UserPreferences(weightUnit = unit)), clock).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        }

    private fun TextFieldState.type(text: String) {
        setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun WorkoutEditorViewModel.set(id: String): ActiveSet =
        uiState.value.exercises.flatMap { it.sets }.first { it.set.id == id }

    private fun lastRevision(): WorkoutRevision = workouts.revisions.last().second

    private suspend fun WorkoutEditorViewModel.saveExpecting(expected: WorkoutEditorEvent) = events.test {
        onAction(WorkoutEditorAction.Save)
        assertEquals(expected, awaitItem())
    }

    @Test
    fun `opens the workout as it was, with nothing to save`() = runTest {
        workouts.addFinished(push)

        val viewModel = editor()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("Push", viewModel.nameState.text.toString())
        assertEquals(WorkoutSpan(start, start.plusSeconds(3_600)), state.span)
        assertEquals("Good one", state.workoutNote?.text.toString())
        assertEquals(listOf("w-bench", "w-pull"), state.exercises.map { it.id })
        // Warm-ups aren't numbered (FR-3.10).
        assertEquals(listOf(null, 1, 2), state.exercises.first().sets.map { it.number })
        assertEquals("80", viewModel.set("b1").fields.weight.text)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `an edit is saved whole, and undoing it leaves nothing to save`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()
        val reps = viewModel.set("b2").fields.reps.state

        reps.type("7")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        reps.type("6")
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        reps.type("7")
        viewModel.onAction(WorkoutEditorAction.ChangeSetType("b2", SetType.FAILURE))
        viewModel.saveExpecting(WorkoutEditorEvent.Saved)

        val revision = lastRevision()
        assertEquals("Push", revision.name)
        assertEquals("Good one", revision.note)
        val benchSets = revision.exercises.first().sets
        assertEquals(listOf("b0", "b1", "b2"), benchSets.map { it.id })
        assertEquals(SetMetrics.Strength(80.0, 7), benchSets[2].metrics)
        assertEquals(SetType.FAILURE, benchSets[2].setType)
        assertEquals(SetMetrics.Bodyweight(10, null), revision.exercises.last().sets.single().metrics)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `values left alone are saved exactly, even shown in pounds`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor(unit = WeightUnit.LB)

        viewModel.nameState.type("Push day")
        viewModel.saveExpecting(WorkoutEditorEvent.Saved)

        assertEquals("Push day", lastRevision().name)
        // 80 kg shows as 176.37 lb; untouched, it goes back as 80 kg, not 79.9999.
        assertEquals(SetMetrics.Strength(80.0, 8), lastRevision().exercises.first().sets[1].metrics)
    }

    @Test
    fun `a set missing a value blocks the save and is marked`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()

        viewModel.set("p1").fields.reps.state.type("")
        viewModel.saveExpecting(WorkoutEditorEvent.SetsMissing(1))

        assertTrue(viewModel.set("p1").showMissing)
        assertTrue(workouts.revisions.isEmpty())
    }

    @Test
    fun `a blank name blocks the save`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()

        viewModel.nameState.type("   ")
        viewModel.saveExpecting(WorkoutEditorEvent.NameMissing)

        assertTrue(viewModel.uiState.value.showNameError)
        assertTrue(workouts.revisions.isEmpty())
    }

    @Test
    fun `the date moves the workout, and an end before the start runs past midnight`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()

        viewModel.onAction(WorkoutEditorAction.SetDate(LocalDate.of(2026, 9, 20)))
        viewModel.onAction(WorkoutEditorAction.SetEndTime(LocalTime.of(0, 30)))
        viewModel.saveExpecting(WorkoutEditorEvent.Saved)

        assertEquals(Instant.parse("2026-09-20T18:00:00Z"), lastRevision().startedAt)
        assertEquals(Instant.parse("2026-09-21T00:30:00Z"), lastRevision().finishedAt)
    }

    @Test
    fun `sets and exercises can be added, removed and reordered`() = runTest {
        workouts.addFinished(push)
        exercises.setSessions(
            squat.id,
            listOf(ExerciseSession("old", "old-workout", "Legs", Instant.EPOCH, listOf(LoggedSet(SetType.NORMAL, SetMetrics.Strength(100.0, 5))))),
        )
        val viewModel = editor()

        viewModel.onAction(WorkoutEditorAction.AddSet("w-bench"))
        viewModel.onAction(WorkoutEditorAction.RemoveSet("b0"))
        viewModel.onAction(WorkoutEditorAction.OpenPicker)
        viewModel.onAction(WorkoutEditorAction.TogglePicked(squat.id))
        viewModel.events.test {
            viewModel.onAction(WorkoutEditorAction.AddPicked)
            // The screen scrolls to what was added.
            assertTrue(awaitItem() is WorkoutEditorEvent.ExercisesAdded)
        }
        viewModel.onAction(WorkoutEditorAction.RemoveExercise("w-pull"))
        viewModel.onAction(WorkoutEditorAction.MoveExercise(from = 1, to = 0))
        viewModel.saveExpecting(WorkoutEditorEvent.Saved)

        val revision = lastRevision()
        assertEquals(listOf(squat.id, bench.id), revision.exercises.map { it.exerciseId })
        // A new exercise starts from last time; a new set from the set before it.
        assertEquals(listOf(SetMetrics.Strength(100.0, 5)), revision.exercises.first().sets.map { it.metrics })
        val benchSets = revision.exercises.last().sets
        assertEquals(listOf("b1", "b2"), benchSets.take(2).map { it.id })
        assertEquals(SetMetrics.Strength(80.0, 6), benchSets.last().metrics)
    }

    @Test
    fun `with no sets left, deleting the workout is offered instead`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()
        listOf("b0", "b1", "b2", "p1").forEach { viewModel.onAction(WorkoutEditorAction.RemoveSet(it)) }

        viewModel.saveExpecting(WorkoutEditorEvent.NothingLeft)
        viewModel.events.test {
            viewModel.onAction(WorkoutEditorAction.Delete)
            assertEquals(WorkoutEditorEvent.Deleted, awaitItem())
        }

        assertTrue(workouts.revisions.isEmpty())
        assertTrue(workouts.finished.value.isEmpty())
    }

    @Test
    fun `a failed save says so, and keeps the edit`() = runTest {
        workouts.addFinished(push)
        val viewModel = editor()
        viewModel.nameState.type("Push day")
        workouts.failNextWrite = true

        viewModel.saveExpecting(WorkoutEditorEvent.SaveFailed)

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `only a finished workout opens`() = runTest {
        workouts.startEmpty("Now")

        assertTrue(editor("workout-1").uiState.value.isUnavailable)
        assertTrue(editor("gone").uiState.value.isUnavailable)
    }
}
