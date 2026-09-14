package ma.elaroui.pos.presentation.register

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ma.elaroui.pos.domain.model.RegisterOperationType
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.RegisterSessionOperation
import ma.elaroui.pos.domain.model.RegisterSessionStatus
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.presentation.register.history.RegisterHistoryUiState
import ma.elaroui.pos.presentation.register.history.RegisterSessionsHistoryContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterSessionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun history_showsEssentialSessionInformationAndOpensSession() {
        val session = closedSession()
        var selectedId: Long? = null

        composeRule.setContent {
            MaterialTheme {
                RegisterSessionsHistoryContent(
                    uiState = state(session),
                    onBack = {},
                    onRefresh = {},
                    onSearch = {},
                    onSelectCashier = {},
                    onSelectSession = { selectedId = it.id },
                    onDismissDetail = {},
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_history_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Sessions").assertDoesNotExist()
        composeRule.onNodeWithText("Écart cumulé").assertDoesNotExist()
        composeRule.onNodeWithText("Session #15").assertIsDisplayed()
        composeRule.onNodeWithText("Ouverte par : Sara").assertIsDisplayed()
        composeRule.onNodeWithText("Fond initial").assertIsDisplayed()
        composeRule.onNodeWithText("Attendu").assertIsDisplayed()
        composeRule.onNodeWithText("Compté").assertIsDisplayed()
        composeRule.onNodeWithText("Écart").assertIsDisplayed()
        composeRule.onNodeWithText("FERMÉE").assertIsDisplayed()
        composeRule.onNodeWithTag("register_session_15")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(15L, selectedId)
        }
    }

    @Test
    fun sessionDetail_displaysCashMovementsAndClosingNote() {
        val session = closedSession()
        composeRule.setContent {
            MaterialTheme {
                RegisterSessionsHistoryContent(
                    uiState = state(session).copy(
                        selectedSession = session,
                        selectedSessionOperations = listOf(
                            RegisterSessionOperation(
                                stableId = "cash-1",
                                sessionId = session.id,
                                type = RegisterOperationType.CASH_OUT,
                                occurredAt = session.openedAt + 1_000L,
                                userId = 1L,
                                userName = "Zakaria",
                                description = "Achat urgent",
                                amountCentimes = 1_500L
                            )
                        )
                    ),
                    onBack = {},
                    onRefresh = {},
                    onSearch = {},
                    onSelectCashier = {},
                    onSelectSession = {},
                    onDismissDetail = {},
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_session_detail").assertIsDisplayed()
        composeRule.onNodeWithText("Achat urgent").assertIsDisplayed()
        composeRule.onNodeWithText("Note : Contrôle terminé").assertIsDisplayed()
        composeRule.onNodeWithText("Historique des opérations (1)").assertIsDisplayed()
    }

    @Test
    fun cashierFilter_isAnchoredAndClosedByDefault() {
        var selectedCashierId: Long? = null
        composeRule.setContent {
            MaterialTheme {
                RegisterSessionsHistoryContent(
                    uiState = state(closedSession()),
                    onBack = {},
                    onRefresh = {},
                    onSearch = {},
                    onSelectCashier = { selectedCashierId = it },
                    onSelectSession = {},
                    onDismissDetail = {},
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_history_cashier_menu").assertDoesNotExist()
        composeRule.onNodeWithTag("register_history_cashier_filter")
            .assertTextContains("Caissier : Tous")
            .performClick()
        composeRule.onNodeWithTag("register_history_cashier_menu").assertIsDisplayed()
        composeRule.onNodeWithText("Sara").performClick()
        composeRule.runOnIdle { assertEquals(2L, selectedCashierId) }
        composeRule.onNodeWithTag("register_history_cashier_menu").assertDoesNotExist()
    }

    @Test
    fun search_supportsIme_noResultsAndClear() {
        val session = closedSession()
        composeRule.setContent {
            var uiState by remember { mutableStateOf(state(session)) }
            MaterialTheme {
                RegisterSessionsHistoryContent(
                    uiState = uiState,
                    onBack = {},
                    onRefresh = {},
                    onSearch = { query ->
                        uiState = uiState.copy(
                            searchQuery = query,
                            sessions = if (
                                query.isBlank() ||
                                session.id.toString().contains(query) ||
                                "Sara".contains(query, ignoreCase = true)
                            ) {
                                listOf(session)
                            } else {
                                emptyList()
                            }
                        )
                    },
                    onSelectCashier = {},
                    onSelectSession = {},
                    onDismissDetail = {},
                    onClearError = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_history_search")
            .assertTextContains("Rechercher par numéro de session ou caissier")
            .performTextInput("inconnue")
        composeRule.onNodeWithTag("register_history_search").performImeAction()
        composeRule.onNodeWithTag("register_history_result_count")
            .assertTextContains("0 résultat(s)")
        composeRule.onNodeWithTag("register_history_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("register_history_search_clear").performClick()
        composeRule.onNodeWithTag("register_history_result_count")
            .assertTextContains("1 résultat(s)")
        composeRule.onNodeWithText("Session #15").assertIsDisplayed()
    }

    @Test
    fun tabletLandscape_placesSearchAndFilterOnSameRow_andSupportsRtl() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(modifier = Modifier.requiredWidth(900.dp)) {
                    MaterialTheme {
                        RegisterSessionsHistoryContent(
                            uiState = state(closedSession()),
                            onBack = {},
                            onRefresh = {},
                            onSearch = {},
                            onSelectCashier = {},
                            onSelectSession = {},
                            onDismissDetail = {},
                            onClearError = {}
                        )
                    }
                }
            }
        }

        val searchBounds = composeRule.onNodeWithTag("register_history_search")
            .fetchSemanticsNode().boundsInRoot
        val filterBounds = composeRule.onNodeWithTag("register_history_cashier_filter")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(searchBounds.center.y, filterBounds.center.y, 2f)
        check(searchBounds.left > filterBounds.left) {
            "RTL should mirror the landscape controls"
        }
        composeRule.onNodeWithContentDescription("Actualiser").assertExists()
        composeRule.onNodeWithTag("register_history_cashier_filter").performClick()
        composeRule.onNodeWithTag("register_history_cashier_menu").assertIsDisplayed()
    }

    @Test
    fun compactWidth_stacksSearchAboveCashierFilter() {
        composeRule.setContent {
            Box(modifier = Modifier.requiredWidth(500.dp)) {
                MaterialTheme {
                    RegisterSessionsHistoryContent(
                        uiState = state(closedSession()),
                        onBack = {},
                        onRefresh = {},
                        onSearch = {},
                        onSelectCashier = {},
                        onSelectSession = {},
                        onDismissDetail = {},
                        onClearError = {}
                    )
                }
            }
        }

        val searchBounds = composeRule.onNodeWithTag("register_history_search")
            .fetchSemanticsNode().boundsInRoot
        val filterBounds = composeRule.onNodeWithTag("register_history_cashier_filter")
            .fetchSemanticsNode().boundsInRoot
        check(searchBounds.bottom <= filterBounds.top) {
            "Compact layouts should stack the cashier filter below search"
        }
    }

    private fun state(session: RegisterSession) = RegisterHistoryUiState(
        sessions = listOf(session),
        usersMap = mapOf(
            2L to User(
                id = 2L,
                name = "Sara",
                role = UserRole.CASHIER,
                pinHash = "unused"
            )
        ),
        isLoading = false
    )

    private fun closedSession() = RegisterSession(
        id = 15L,
        registerId = 1L,
        cashierId = 2L,
        openingCashCentimes = 20_000L,
        openedAt = 1_750_000_000_000L,
        closedAt = 1_750_003_600_000L,
        expectedCashCentimes = 35_000L,
        countedCashCentimes = 34_500L,
        differenceCentimes = -500L,
        closingNote = "Contrôle terminé",
        status = RegisterSessionStatus.CLOSED
    )
}
