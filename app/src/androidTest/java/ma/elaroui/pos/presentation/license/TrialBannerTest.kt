package ma.elaroui.pos.presentation.license

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ma.elaroui.pos.core.license.LicenseState
import ma.elaroui.pos.core.license.LicenseStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrialBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trialBanner_showsDemoMessageAndOpensActivation() {
        var activationRequested = false

        composeRule.setContent {
            MaterialTheme {
                TrialBanner(
                    licenseState = LicenseState(
                        status = LicenseStatus.TRIAL_ACTIVE,
                        installationId = "INST-TEST",
                        remainingTrialTimeMs = 6 * 24 * 60 * 60 * 1000L
                    ),
                    onActivate = { activationRequested = true }
                )
            }
        }

        composeRule.onNodeWithTag("trial_banner").assertIsDisplayed()
        composeRule.onNodeWithText("MODE DÉMO — 6 jour(s) restant(s).")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("trial_activate_button")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(activationRequested)
        }
    }
}
