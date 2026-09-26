package com.example.liftbook.ui.feature.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.ui.theme.LiftBookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

/** Backup & data, driven through its semantics as TalkBack or a thumb would. */
@RunWith(RobolectricTestRunner::class)
class DataManagementScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<DataManagementAction>()
    private val confirmation = TextFieldState()

    private fun show(state: DataManagementUiState) {
        compose.setContent {
            LiftBookTheme {
                DataManagementScreen(
                    state = state,
                    clearConfirmation = confirmation,
                    snackbarHostState = remember { SnackbarHostState() },
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun state(counts: DataCounts = DataCounts(workouts = 142, routines = 6, weighIns = 31), isConfirmingClear: Boolean = false) =
        DataManagementUiState(
            isLoading = false,
            counts = counts,
            isConfirmingClear = isConfirmingClear,
            today = LocalDate.of(2026, 9, 26),
            zone = ZoneOffset.UTC,
        )

    @Test
    fun `with data on the phone, export is the action at hand`() {
        show(state())

        // Read as TalkBack hears it: the label, then the number.
        compose.onNodeWithContentDescription("Workouts, 142").assertIsDisplayed()
        compose.onNodeWithText("Never").assertIsDisplayed()
        compose.onNodeWithText("Export backup").performClick()

        assertEquals(listOf<DataManagementAction>(DataManagementAction.Export), actions)
    }

    @Test
    fun `an empty phone offers the import that fills it, and nothing to export`() {
        show(state(counts = DataCounts()))

        compose.onNodeWithText("Nothing logged on this phone yet").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Export backup").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Import backup").performClick()

        assertEquals(listOf<DataManagementAction>(DataManagementAction.Import), actions)
    }

    @Test
    fun `clearing stays off until the word is typed`() {
        show(state(isConfirmingClear = true))

        compose.onNodeWithText("Clear all data").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("Delet")
        compose.onNodeWithText("Clear all data").assertIsNotEnabled()
        // Case and a stray space don't matter; the word does.
        compose.onNode(hasSetTextAction()).performTextInput("e ")
        compose.onNodeWithText("Clear all data").assertIsEnabled().performClick()

        assertEquals(listOf<DataManagementAction>(DataManagementAction.ConfirmClear), actions)
    }
}
