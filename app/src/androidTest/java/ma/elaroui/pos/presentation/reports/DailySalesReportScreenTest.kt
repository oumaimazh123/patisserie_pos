package ma.elaroui.pos.presentation.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import ma.elaroui.pos.domain.model.DailySalesReport
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.ProductSalesSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DailySalesReportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun populatedReport_showsControlsSummariesAndDetailedSections() {
        var dateRequested = false
        var exportRequested = false

        composeRule.setContent {
            MaterialTheme {
                DailySalesReportContent(
                    uiState = DailySalesUiState(
                        report = sampleReport(),
                        isLoading = false
                    ),
                    onBack = {},
                    onChooseDate = { dateRequested = true },
                    onPreviousDay = {},
                    onNextDay = {},
                    onToday = {},
                    onRefresh = {},
                    onExport = { exportRequested = true },
                    onClearError = {},
                    onClearExportMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("sales_report_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("sales_report_date_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("sales_report_export_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Total ventes").assertIsDisplayed()
        composeRule.onNodeWithTag("sales_report_content")
            .performScrollToIndex(8)
        composeRule.onNodeWithText("Top produits vendus")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("sales_report_content")
            .performScrollToIndex(9)
        composeRule.onNodeWithText("Café crème")
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(dateRequested)
            assertTrue(exportRequested)
        }
    }

    @Test
    fun zeroSalesReport_showsClearEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                DailySalesReportContent(
                    uiState = DailySalesUiState(
                        report = sampleReport().copy(
                            completedOrderCount = 0,
                            totalSalesCentimes = 0L,
                            cashSalesCentimes = 0L,
                            cardSalesCentimes = 0L,
                            avgOrderValueCentimes = 0L,
                            salesByCashier = emptyMap(),
                            salesByOrderType = emptyMap(),
                            topProducts = emptyList()
                        ),
                        isLoading = false
                    ),
                    onBack = {},
                    onChooseDate = {},
                    onPreviousDay = {},
                    onNextDay = {},
                    onToday = {},
                    onRefresh = {},
                    onExport = {},
                    onClearError = {},
                    onClearExportMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("sales_report_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Aucune vente clôturée").assertIsDisplayed()
    }

    private fun sampleReport() = DailySalesReport(
        dateString = "15/07/2026",
        completedOrderCount = 3,
        cancelledOrderCount = 1,
        cashSalesCentimes = 2_500L,
        cardSalesCentimes = 2_000L,
        totalSalesCentimes = 4_500L,
        avgOrderValueCentimes = 1_500L,
        salesByCashier = mapOf("Sara" to 3_000L, "Amine" to 1_500L),
        salesByOrderType = mapOf(
            OrderType.DINE_IN to 2_500L,
            OrderType.TAKEAWAY to 2_000L
        ),
        topProducts = listOf(
            ProductSalesSummary(
                productName = "Café crème",
                quantitySold = 2,
                totalSalesCentimes = 3_000L
            )
        )
    )
}
