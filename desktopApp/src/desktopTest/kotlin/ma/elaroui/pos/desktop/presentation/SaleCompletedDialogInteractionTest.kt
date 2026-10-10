package ma.elaroui.pos.desktop.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.navigation.CompletedSaleConfirmation
import ma.elaroui.pos.desktop.presentation.navigation.SaleCompletedDialog
import ma.elaroui.pos.shared.domain.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SaleCompletedDialogInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedPrintExposesRetryAndFinishRemainsAvailable() {
        val strings = DesktopStrings(DesktopLanguage.EN)
        val order = Order(1L, "SALE-1", OrderType.COUNTER, OrderStatus.COMPLETED,
            listOf(OrderLine(1L, "Cake", 1_000L, 1)), 1_000L, 0L, 0L, 1_000L,
            registerSessionId = 1L, cashierId = 1L)
        val payment = Payment(1L, 1L, 1L, PaymentMethod.CASH, 1_000L, 1_000L, 0L,
            PaymentStatus.COMPLETED, "ui-print", 1L)
        val confirmation = CompletedSaleConfirmation(order, payment, PaymentMethod.CASH, 1_000L, 0L)
        var attempts = 0
        var finishes = 0
        compose.setContent {
            MaterialTheme {
                SaleCompletedDialog(confirmation, strings,
                    onPrintReceipt = { attempts++; false },
                    onFinish = { finishes++ })
            }
        }
        compose.onNodeWithText("🖨️ ${strings.printReceipt}").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("🖨️ Retry printing").assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithText("✓ Finish without printing")
            .performClick()
        compose.runOnIdle { assertEquals(1, finishes) }
    }
}
