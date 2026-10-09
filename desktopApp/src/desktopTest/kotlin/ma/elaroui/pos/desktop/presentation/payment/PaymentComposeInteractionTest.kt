package ma.elaroui.pos.desktop.presentation.payment

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.shared.domain.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PaymentComposeInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val strings = DesktopStrings(DesktopLanguage.FR)
    private val order = Order(
        id = 1, number = "UI-AUDIT", type = OrderType.COUNTER, status = OrderStatus.OPEN,
        lines = listOf(OrderLine(1, "Cake", 7500, 1)), subtotalCentimes = 7500,
        discountCentimes = 0, taxCentimes = 0, totalCentimes = 7500,
        registerSessionId = 1, cashierId = 1
    )

    @Test
    fun cashCannotSubmitUntilEnoughMoneyAndExactAmountSubmitsRealCallback() {
        val submissions = mutableListOf<Pair<PaymentMethod, Long?>>()
        compose.setContent {
            MaterialTheme {
                PaymentScreen(order, strings, { method, received -> submissions += method to received }, {})
            }
        }
        compose.onNodeWithText("Valider le paiement").assertIsNotEnabled()
        compose.onNodeWithText("50 ${strings.currency}").performClick()
        compose.onNodeWithText("Valider le paiement").assertIsNotEnabled()
        compose.onNodeWithText(strings.exactAmount).performClick()
        compose.onNodeWithText("Valider le paiement").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf<Pair<PaymentMethod, Long?>>(PaymentMethod.CASH to 7500L), submissions) }
    }

    @Test
    fun cardSelectionAllowsSubmissionWithoutCashInput() {
        val submissions = mutableListOf<Pair<PaymentMethod, Long?>>()
        compose.setContent {
            MaterialTheme {
                PaymentScreen(order, strings, { method, received -> submissions += method to received }, {})
            }
        }
        compose.onNodeWithText(strings.card).performClick()
        compose.onNodeWithText("Valider le paiement").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf<Pair<PaymentMethod, Long?>>(PaymentMethod.CARD to null), submissions) }
    }
}
