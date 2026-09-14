package ma.elaroui.pos.presentation.sales

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderItem
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Payment
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.ReceiptData
import ma.elaroui.pos.domain.model.ReceiptItem
import ma.elaroui.pos.domain.model.SalesHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SalesHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedHistory_showsSummaryFiltersAndOpensReadOnlyDetail() {
        var selectedOrderId: Long? = null
        var cancelledTabRequested = false
        val entry = completedEntry()

        composeRule.setContent {
            MaterialTheme {
                CompletedSalesContent(
                    uiState = CompletedSalesUiState(
                        entries = listOf(entry),
                        filteredCount = 1,
                        isLoading = false,
                        isOwner = true,
                        cashierOptions = listOf(SalesHistoryOption(2L, "Sara")),
                        registerOptions = listOf(SalesHistoryOption(3L, "Caisse Terrasse")),
                        summary = SalesHistorySummary(
                            orderCount = 1,
                            totalCentimes = 4_500L,
                            cashCentimes = 4_500L,
                            averageCentimes = 4_500L
                        )
                    ),
                    onSelectSale = { selectedOrderId = it },
                    onBack = {},
                    onRefresh = {},
                    onExport = {},
                    onSelectStatus = {
                        if (it == OrderStatus.CANCELLED) cancelledTabRequested = true
                    },
                    onChooseStartDate = {},
                    onChooseEndDate = {},
                    onCurrentMonth = {},
                    onSearch = {},
                    onSelectCashier = {},
                    onSelectRegister = {},
                    onSelectPayment = {},
                    onSelectOrderType = {},
                    onClearFilters = {},
                    onLoadMore = {},
                    onClearError = {},
                    onClearExportMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("sales_history_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Total ventes").assertIsDisplayed()
        composeRule.onNodeWithTag("sales_history_content")
            .performScrollToNode(hasTestTag("sales_history_order_42"))
        composeRule.onNodeWithTag("sales_history_order_42")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("sales_history_content")
            .performScrollToNode(hasTestTag("sales_history_cancelled_tab"))
        composeRule.onNodeWithTag("sales_history_cancelled_tab")
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(cancelledTabRequested)
            assertEquals(42L, selectedOrderId)
        }
    }

    @Test
    fun cancelledDetail_showsReasonAndNeverOffersReceiptActions() {
        val cancelledOrder = Order(
            id = 77L,
            orderNumber = "ORD-ANN-077",
            type = OrderType.TAKEAWAY,
            cashierId = 2L,
            registerSessionId = 9L,
            status = OrderStatus.CANCELLED,
            totalCentimes = 3_200L,
            items = listOf(sampleItem(orderId = 77L)),
            cancelledAt = 1_750_000_000_000L,
            cancellationReason = "Erreur de saisie"
        )

        composeRule.setContent {
            MaterialTheme {
                SaleDetailContent(
                    uiState = SaleDetailUiState(
                        order = cancelledOrder,
                        receiptData = null,
                        cashierName = "Sara",
                        registerName = "Caisse Terrasse",
                        isLoading = false
                    ),
                    onBack = {},
                    onNavigateToReceiptPreview = {},
                    onReprint = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("cancelled_sale_notice")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Erreur de saisie").assertIsDisplayed()
        composeRule.onNodeWithTag("sale_detail_receipt_preview").assertDoesNotExist()
        composeRule.onNodeWithTag("sale_detail_reprint").assertDoesNotExist()
    }

    @Test
    fun completedDetail_keepsPreviewAndReprintAvailable() {
        val entry = completedEntry()
        val order = entry.order
        val receipt = ReceiptData(
            restaurantName = "Restaurant Test",
            restaurantPhone = null,
            restaurantAddress = null,
            orderNumber = order.orderNumber,
            orderType = order.type,
            tableName = null,
            registerName = entry.registerName,
            cashierName = entry.cashierName,
            orderCreatedAt = order.createdAt,
            paymentAt = checkNotNull(order.completedAt),
            items = listOf(
                ReceiptItem(
                    name = "Café",
                    quantity = 1,
                    unitPriceCentimes = 4_500L,
                    lineTotalCentimes = 4_500L
                )
            ),
            subtotalCentimes = 4_500L,
            totalCentimes = 4_500L,
            paymentMethod = PaymentMethod.CASH,
            receivedAmountCentimes = 5_000L,
            changeAmountCentimes = 500L,
            externalReference = null
        )

        composeRule.setContent {
            MaterialTheme {
                SaleDetailContent(
                    uiState = SaleDetailUiState(
                        order = order,
                        receiptData = receipt,
                        cashierName = entry.cashierName,
                        registerName = entry.registerName,
                        isLoading = false
                    ),
                    onBack = {},
                    onNavigateToReceiptPreview = {},
                    onReprint = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithTag("sale_detail_content")
            .performScrollToNode(hasTestTag("sale_detail_receipt_preview"))
        composeRule.onNodeWithTag("sale_detail_receipt_preview")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("sale_detail_reprint").assertIsDisplayed()
    }

    private fun completedEntry(): SalesHistoryEntry {
        val completedAt = 1_750_000_000_000L
        val payment = Payment(
            id = 1L,
            orderId = 42L,
            registerSessionId = 9L,
            method = PaymentMethod.CASH,
            amountCentimes = 4_500L,
            receivedAmountCentimes = 5_000L,
            changeAmountCentimes = 500L
        )
        return SalesHistoryEntry(
            order = Order(
                id = 42L,
                orderNumber = "ORD-042",
                type = OrderType.DINE_IN,
                cashierId = 2L,
                registerSessionId = 9L,
                status = OrderStatus.COMPLETED,
                subtotalCentimes = 4_500L,
                totalCentimes = 4_500L,
                items = listOf(sampleItem(orderId = 42L)),
                payments = listOf(payment),
                completedAt = completedAt
            ),
            cashierName = "Sara",
            registerId = 3L,
            registerName = "Caisse Terrasse"
        )
    }

    private fun sampleItem(orderId: Long) = OrderItem(
        id = 1L,
        orderId = orderId,
        productId = 1L,
        productNameSnapshot = "Café",
        unitPriceSnapshotCentimes = 4_500L,
        quantity = 1,
        lineTotalCentimes = 4_500L
    )
}
