package com.example.liftbook.ui.feature.workout

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.liftbook.domain.calculator.WorkoutSpan
import com.example.liftbook.ui.theme.LiftBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Editing a past workout, driven through its semantics. */
@RunWith(RobolectricTestRunner::class)
class WorkoutEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<WorkoutEditorAction>()

    private fun state(hasUnsavedChanges: Boolean = false): WorkoutEditorUiState {
        val start = WorkoutPreviewData.startedAt
        return WorkoutEditorUiState(
            isLoading = false,
            span = WorkoutSpan(start, start.plusSeconds(64 * 60)),
            exercises = WorkoutPreviewData.state().exercises.map { exercise ->
                exercise.copy(sets = exercise.sets.map { it.copy(set = it.set.copy(isCompleted = true)) })
            },
            workoutNote = TextFieldState(),
            today = LocalDate.of(2026, 9, 25),
            zone = WorkoutPreviewData.zone,
            hasUnsavedChanges = hasUnsavedChanges,
        )
    }

    private fun show(state: WorkoutEditorUiState) {
        compose.setContent {
            LiftBookTheme {
                WorkoutEditorScreen(
                    state = state,
                    nameState = rememberTextFieldState("Push"),
                    pickerQueryState = rememberTextFieldState(),
                    onAction = { actions += it },
                )
            }
        }
    }

    @Test
    fun `sets are edited in place, with no done toggles`() {
        show(state())

        compose.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        // Every set in history is done, so there's nothing to mark.
        assertEquals(0, compose.onAllNodesWithContentDescription("Set 1 done").fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithContentDescription("Set 1, weight in kg", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `save waits for a change`() {
        show(state(hasUnsavedChanges = false))
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
    }

    @Test
    fun `save sends the edit once there is one`() {
        show(state(hasUnsavedChanges = true))

        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()

        assertEquals(listOf<WorkoutEditorAction>(WorkoutEditorAction.Save), actions)
    }

    @Test
    fun `the date opens a picker`() {
        show(state())

        compose.onNodeWithContentDescription("Date", substring = true).performClick()

        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }

    @Test
    fun `closing with unsaved changes asks first`() {
        show(state(hasUnsavedChanges = true))

        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNode(hasText("Discard") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf<WorkoutEditorAction>(WorkoutEditorAction.Close), actions)
    }
}
