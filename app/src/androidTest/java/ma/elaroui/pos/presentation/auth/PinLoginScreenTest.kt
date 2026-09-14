package ma.elaroui.pos.presentation.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PinLoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val owner = User(
        id = 1L,
        name = "Owner",
        role = UserRole.OWNER,
        pinHash = "test-hash"
    )

    @Test
    fun confirmButton_isVisibleAndSubmitsAValidPin() {
        var submitted = false

        composeRule.setContent {
            MaterialTheme {
                PinLoginScreen(
                    uiState = AuthUiState(
                        selectedUser = owner,
                        enteredPin = "1234"
                    ),
                    onBackClick = {},
                    onDigitClick = {},
                    onDeleteClick = {},
                    onClearClick = {},
                    onSubmitPin = { submitted = true }
                )
            }
        }

        composeRule.onNodeWithTag("pin_confirm_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(submitted)
        }
    }

    @Test
    fun confirmButton_isDisabledUntilFourDigitsAreEntered() {
        composeRule.setContent {
            MaterialTheme {
                PinLoginScreen(
                    uiState = AuthUiState(
                        selectedUser = owner,
                        enteredPin = "123"
                    ),
                    onBackClick = {},
                    onDigitClick = {},
                    onDeleteClick = {},
                    onClearClick = {},
                    onSubmitPin = {}
                )
            }
        }

        composeRule.onNodeWithTag("pin_confirm_button")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
