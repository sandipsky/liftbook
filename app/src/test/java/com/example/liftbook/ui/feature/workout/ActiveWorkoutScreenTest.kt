package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.Workout
import com.example.liftbook.ui.theme.LiftBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The active workout screen, driven through its semantics as TalkBack or a thumb would. */
@RunWith(RobolectricTestRunner::class)
class ActiveWorkoutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<ActiveWorkoutAction>()

    private fun show(state: ActiveWorkoutUiState = WorkoutPreviewData.state()) {
        compose.setContent {
            LiftBookTheme {
                ActiveWorkoutScreen(
                    state = state,
                    pickerQueryState = rememberTextFieldState(),
                    now = { WorkoutPreviewData.now },
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun Workout.allDone() = copy(exercises = exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(isCompleted = true) }) })

    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun `shows the workout, its numbers and its sets`() {
        show()

        compose.onNodeWithText("Push").assertIsDisplayed()
        compose.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        // 3 of the 11 sets are done; the time is how long since it started. Each stat reads as
        // one item, in words, as TalkBack speaks it.
        compose.onNodeWithContentDescription("Sets, 3 of 11 sets done").assertIsDisplayed()
        compose.onNodeWithContentDescription("Time, 32 minutes 14 seconds").assertIsDisplayed()
        // Sets arrive pre-filled, as real values in their fields.
        assertTrue(compose.onAllNodesWithText("80").fetchSemanticsNodes().size >= 3)
    }

    @Test
    fun `marking a set done asks for exactly that set`() {
        show()

        compose.onAllNodesWithContentDescription("Set 3 done")[0].performClick()

        assertEquals(listOf(ActiveWorkoutAction.ToggleSetDone("b3")), actions)
    }

    @Test
    fun `a set that sets a record says so where it's marked done, and names the record`() {
        val state = WorkoutPreviewData.state()
        val exercise = state.exercises.first()
        val done = exercise.sets.first { it.set.isCompleted && it.number != null }
        val record = PersonalRecord.HeaviestWeight(80.0, 8)
        show(
            state.copy(
                exercises = listOf(
                    exercise.copy(
                        sets = exercise.sets.map { if (it == done) it.copy(records = listOf(record)) else it },
                        records = listOf(record),
                    ),
                ) + state.exercises.drop(1),
            ),
        )

        val recordState = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Done, personal record")
        compose.onNode(hasContentDescription("Set ${done.number} done") and recordState).assertExists()
        compose.onNodeWithContentDescription("New record: Heaviest weight").assertExists()
    }

    @Test
    fun `finishing with sets undone says they won't be saved, then finishes`() {
        show()

        compose.onNodeWithText("Finish").performClick()
        compose.onNodeWithText("8 sets aren’t marked done and won’t be saved.").assertIsDisplayed()
        dialogButton("Finish").performClick()

        assertEquals(listOf(ActiveWorkoutAction.Finish), actions)
    }

    @Test
    fun `finishing with nothing done offers to discard instead`() {
        val untouched = WorkoutPreviewData.workout.let { workout ->
            workout.copy(exercises = workout.exercises.map { exercise -> exercise.copy(sets = exercise.sets.map { it.copy(isCompleted = false) }) })
        }
        show(WorkoutPreviewData.state(workout = untouched))

        compose.onNodeWithText("Finish").performClick()
        compose.onNodeWithText("No sets done yet").assertIsDisplayed()
        dialogButton("Discard workout").performClick()

        assertEquals(listOf(ActiveWorkoutAction.Discard), actions)
    }

    @Test
    fun `finishing with every set done doesn't ask`() {
        show(WorkoutPreviewData.state(workout = WorkoutPreviewData.workout.allDone()))

        compose.onNodeWithText("Finish").performClick()
        compose.waitForIdle()

        assertEquals(listOf(ActiveWorkoutAction.Finish), actions)
    }

    @Test
    fun `the rest shows what's left, and can be lengthened or skipped`() {
        show()

        compose.onNodeWithContentDescription("53 seconds of rest left").assertIsDisplayed()
        compose.onNodeWithText("+30s").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(listOf(ActiveWorkoutAction.AdjustRest(30), ActiveWorkoutAction.SkipRest), actions)
    }

    @Test
    fun `an empty workout says what to do, and the button does it`() {
        val empty = WorkoutPreviewData.workout.copy(exercises = emptyList(), rest = null)
        show(WorkoutPreviewData.state(workout = empty))

        compose.onNodeWithText("Nothing added yet").assertIsDisplayed()
        compose.onNodeWithText("Add exercises").performClick()

        assertEquals(listOf(ActiveWorkoutAction.OpenPicker), actions)
    }

    @Test
    fun `reordering lists the exercises compactly and Done ends it`() {
        show(WorkoutPreviewData.state(isReordering = true))

        compose.onNodeWithText("Drag an exercise by its handle to move it.").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()

        assertEquals(listOf(ActiveWorkoutAction.StopReordering), actions)
    }
}
