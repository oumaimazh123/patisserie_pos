package ma.elaroui.pos.desktop.presentation.sales

import ma.elaroui.pos.desktop.persistence.SalesHistoryRow
import ma.elaroui.pos.shared.domain.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.test.*

class SalesPaginationAndFilterTest {

    private fun createSampleSale(
        id: Long,
        number: String,
        cashier: String,
        type: OrderType = OrderType.COUNTER,
        status: OrderStatus = OrderStatus.COMPLETED,
        method: PaymentMethod? = PaymentMethod.CASH,
        totalCentimes: Long = 1000L,
        paidAtEpochMillis: Long = 1000000L
    ): SalesHistoryRow {
        val order = Order(
            id = id,
            number = number,
            type = type,
            status = status,
            lines = listOf(OrderLine(1L, "Article", totalCentimes, 1, 0)),
            subtotalCentimes = totalCentimes,
            discountCentimes = 0L,
            taxCentimes = 0L,
            totalCentimes = totalCentimes,
            tableId = if (type == OrderType.DINE_IN) 5L else null,
            registerSessionId = 1L,
            cashierId = 1L
        )
        return SalesHistoryRow(
            order = order,
            cashierName = cashier,
            paymentMethod = method,
            paidAtEpochMillis = paidAtEpochMillis
        )
    }

    @Test
    fun testVisiblePagesCalculation() {
        // 1 page or 0 -> empty (no page navigation buttons needed)
        assertEquals(emptyList(), calculateVisiblePages(1, 1))
        assertEquals(emptyList(), calculateVisiblePages(1, 0))

        // <= 7 pages -> full list
        assertEquals(listOf(1, 2, 3), calculateVisiblePages(1, 3))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), calculateVisiblePages(4, 7))

        // > 7 pages, near start (page 2 of 25)
        val nearStart = calculateVisiblePages(2, 25)
        assertEquals(listOf(1, 2, 3, 4, 5, null, 25), nearStart)

        // > 7 pages, in middle (page 12 of 25)
        val inMiddle = calculateVisiblePages(12, 25)
        assertEquals(listOf(1, null, 11, 12, 13, null, 25), inMiddle)

        // > 7 pages, near end (page 24 of 25)
        val nearEnd = calculateVisiblePages(24, 25)
        assertEquals(listOf(1, null, 21, 22, 23, 24, 25), nearEnd)
    }

    @Test
    fun testComputeSalesDateBounds() {
        val refCalendar = Calendar.getInstance()
        val refNow = refCalendar.timeInMillis

        // ALL
        val (allFrom, allTo) = computeSalesDateBounds(SalesPeriod.ALL, referenceEpoch = refNow)
        assertNull(allFrom)
        assertNull(allTo)

        // TODAY
        val (todayFrom, todayTo) = computeSalesDateBounds(SalesPeriod.TODAY, referenceEpoch = refNow)
        assertNotNull(todayFrom)
        assertNull(todayTo)
        val todayCal = Calendar.getInstance().apply { timeInMillis = todayFrom }
        assertEquals(0, todayCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, todayCal.get(Calendar.MINUTE))

        // CUSTOM
        val (customFrom, customTo) = computeSalesDateBounds(
            SalesPeriod.CUSTOM,
            customStart = "15/09/2026",
            customEnd = "20/09/2026",
            referenceEpoch = refNow
        )
        assertNotNull(customFrom)
        assertNotNull(customTo)
        assertTrue(customFrom < customTo)

        val calFrom = Calendar.getInstance().apply { timeInMillis = customFrom }
        assertEquals(15, calFrom.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, calFrom.get(Calendar.MONTH))
        assertEquals(2026, calFrom.get(Calendar.YEAR))
        assertEquals(0, calFrom.get(Calendar.HOUR_OF_DAY))

        val calTo = Calendar.getInstance().apply { timeInMillis = customTo }
        assertEquals(20, calTo.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calTo.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calTo.get(Calendar.MINUTE))
        assertEquals(59, calTo.get(Calendar.SECOND))
    }

    @Test
    fun testFilterAndSortSales() {
        val sales = listOf(
            createSampleSale(1, "CMD-001", "Amine", OrderType.COUNTER, OrderStatus.COMPLETED, PaymentMethod.CASH, 5000L, 1000L),
            createSampleSale(2, "CMD-002", "Sara", OrderType.DINE_IN, OrderStatus.OPEN, PaymentMethod.CARD, 12000L, 4000L),
            createSampleSale(3, "CMD-003", "Amine", OrderType.TAKEAWAY, OrderStatus.CANCELLED, PaymentMethod.CASH, 3000L, 2000L),
            createSampleSale(4, "CMD-004", "Youssef", OrderType.COUNTER, OrderStatus.COMPLETED, PaymentMethod.CARD, 8000L, 3000L)
        )

        // 1. Search query
        val searchSara = filterAndSortSales(
            sales = sales,
            query = "Sara",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null
        )
        assertEquals(1, searchSara.size)
        assertEquals("CMD-002", searchSara.first().order.number)

        // 2. Status filter
        val completedOnly = filterAndSortSales(
            sales = sales,
            query = "",
            status = OrderStatus.COMPLETED,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null
        )
        assertEquals(2, completedOnly.size)

        // 3. Payment filter (CARD only)
        val cardPayment = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.CARD,
            fromEpoch = null,
            toEpoch = null
        )
        assertEquals(2, cardPayment.size)
        assertEquals(setOf("CMD-002", "CMD-004"), cardPayment.map { it.order.number }.toSet())

        // 4. Default sorting (newest first based on paidAtEpochMillis)
        val allSorted = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null
        )
        assertEquals(listOf("CMD-002", "CMD-004", "CMD-003", "CMD-001"), allSorted.map { it.order.number })
    }

    @Test
    fun testPaginationMath() {
        val totalItems = 128
        val pageSize = 20
        val totalPages = (totalItems + pageSize - 1) / pageSize // 7 pages

        assertEquals(7, totalPages)

        // Page 1 (1-based)
        val page1 = 1
        val start1 = (page1 - 1) * pageSize
        val end1 = minOf(start1 + pageSize, totalItems)
        assertEquals(0, start1)
        assertEquals(20, end1)

        // Page 2
        val page2 = 2
        val start2 = (page2 - 1) * pageSize
        val end2 = minOf(start2 + pageSize, totalItems)
        assertEquals(20, start2)
        assertEquals(40, end2)

        // Last Page (Page 7)
        val page7 = 7
        val start7 = (page7 - 1) * pageSize
        val end7 = minOf(start7 + pageSize, totalItems)
        assertEquals(120, start7)
        assertEquals(128, end7)
    }
}
