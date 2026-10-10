package ma.elaroui.pos.desktop.presentation.reports

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.RetailSalesAnalytics
import ma.elaroui.pos.desktop.persistence.SalesSummary
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailySalesReportAsyncInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportKeepsUiResponsiveWhileDatabaseQueryRuns() {
        val strings = DesktopStrings(DesktopLanguage.EN)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queryThread = AtomicReference("")
        try {
            compose.setContent {
                MaterialTheme {
                    DailySalesReportScreen(
                        summaryForRange = { _, _, _, _, _ ->
                            queryThread.set(Thread.currentThread().name)
                            started.countDown()
                            release.await(5, TimeUnit.SECONDS)
                            SalesSummary(1, 1_000L, 1_000L, 0L, 0L)
                        },
                        analyticsForRange = { _, _, _, _, _ ->
                            RetailSalesAnalytics(emptyList(), emptyList(), emptyList(), 0)
                        },
                        strings = strings
                    )
                }
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            compose.onNodeWithText(strings.reportsTitle).assertExists()
            assertFalse(queryThread.get().contains("AWT-EventQueue"))
        } finally {
            release.countDown()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(strings.totalRevenue, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(strings.totalRevenue, useUnmergedTree = true).assertExists()
    }
}
