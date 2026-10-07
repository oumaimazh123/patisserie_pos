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

    @Test
    fun testHierarchicalCategoryFiltering() {
        // Hierarchy:
        // Cat 1: Pâtisserie (Root)
        //   Cat 2: Gâteaux (Child of 1)
        //     Cat 3: Tartes (Child of 2)
        // Cat 4: Boissons (Root)
        //   Cat 5: Boissons Chaudes (Child of 4)
        val categories = listOf(
            Category(id = 1L, name = "Pâtisserie", displayOrder = 1, parentId = null),
            Category(id = 2L, name = "Gâteaux", displayOrder = 1, parentId = 1L),
            Category(id = 3L, name = "Tartes", displayOrder = 1, parentId = 2L),
            Category(id = 4L, name = "Boissons", displayOrder = 2, parentId = null),
            Category(id = 5L, name = "Boissons Chaudes", displayOrder = 1, parentId = 4L)
        )

        // Products:
        // Prod 101 -> Cat 1 (direct in root Pâtisserie)
        // Prod 102 -> Cat 2 (in Gâteaux)
        // Prod 103 -> Cat 3 (in Tartes)
        // Prod 104 -> Cat 4 (in Boissons)
        // Prod 105 -> Cat 5 (in Boissons Chaudes)
        val products = listOf(
            Product(id = 101L, categoryId = 1L, name = "Millefeuille", priceCentimes = 1500L, taxRateBasisPoints = 0),
            Product(id = 102L, categoryId = 2L, name = "Forêt Noire", priceCentimes = 2500L, taxRateBasisPoints = 0),
            Product(id = 103L, categoryId = 3L, name = "Tarte Citron", priceCentimes = 2000L, taxRateBasisPoints = 0),
            Product(id = 104L, categoryId = 4L, name = "Eau Minérale", priceCentimes = 500L, taxRateBasisPoints = 0),
            Product(id = 105L, categoryId = 5L, name = "Café Expresso", priceCentimes = 1200L, taxRateBasisPoints = 0)
        )

        // Orders:
        // Sale 1: Contains Prod 101 (Cat 1) + Prod 102 (Cat 2) [Multiple items in same hierarchy branch]
        val sale1 = SalesHistoryRow(
            order = Order(
                id = 1L,
                number = "CMD-001",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(productId = 101L, name = "Millefeuille", unitPriceCentimes = 1500L, quantity = 1, taxRateBasisPoints = 0),
                    OrderLine(productId = 102L, name = "Forêt Noire", unitPriceCentimes = 2500L, quantity = 1, taxRateBasisPoints = 0)
                ),
                subtotalCentimes = 4000L,
                discountCentimes = 0L,
                taxCentimes = 0L,
                totalCentimes = 4000L,
                registerSessionId = 1L,
                cashierId = 1L
            ),
            cashierName = "Ahmed",
            paymentMethod = PaymentMethod.CASH,
            paidAtEpochMillis = 1000L
        )

        // Sale 2: Contains Prod 103 (Cat 3 - deepest child of Cat 1)
        val sale2 = SalesHistoryRow(
            order = Order(
                id = 2L,
                number = "CMD-002",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(productId = 103L, name = "Tarte Citron", unitPriceCentimes = 2000L, quantity = 2, taxRateBasisPoints = 0)
                ),
                subtotalCentimes = 4000L,
                discountCentimes = 0L,
                taxCentimes = 0L,
                totalCentimes = 4000L,
                registerSessionId = 1L,
                cashierId = 1L
            ),
            cashierName = "Sara",
            paymentMethod = PaymentMethod.CARD,
            paidAtEpochMillis = 2000L
        )

        // Sale 3: Contains Prod 105 (Cat 5 - Boissons Chaudes)
        val sale3 = SalesHistoryRow(
            order = Order(
                id = 3L,
                number = "CMD-003",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(productId = 105L, name = "Café Expresso", unitPriceCentimes = 1200L, quantity = 1, taxRateBasisPoints = 0)
                ),
                subtotalCentimes = 1200L,
                discountCentimes = 0L,
                taxCentimes = 0L,
                totalCentimes = 1200L,
                registerSessionId = 1L,
                cashierId = 1L
            ),
            cashierName = "Ahmed",
            paymentMethod = PaymentMethod.CASH,
            paidAtEpochMillis = 3000L
        )

        val sales = listOf(sale1, sale2, sale3)

        // 1. Filtering by Root Cat 1 (Pâtisserie) includes:
        // - sale1 (has Prod 101 in Cat 1 and Prod 102 in Cat 2)
        // - sale2 (has Prod 103 in Cat 3)
        // AND does not duplicate sale1 even though it has two matching products!
        val filteredRoot1 = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = 1L,
            categories = categories,
            products = products
        )
        assertEquals(2, filteredRoot1.size)
        assertEquals(setOf("CMD-001", "CMD-002"), filteredRoot1.map { it.order.number }.toSet())

        // 2. Filtering by Subcategory Cat 2 (Gâteaux) includes:
        // - sale1 (has Prod 102 in Cat 2)
        // - sale2 (has Prod 103 in Cat 3, which is a child of Cat 2)
        // does NOT match if an order only had Cat 1 direct product
        val filteredSubCat2 = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = 2L,
            categories = categories,
            products = products
        )
        assertEquals(2, filteredSubCat2.size)
        assertEquals(setOf("CMD-001", "CMD-002"), filteredSubCat2.map { it.order.number }.toSet())

        // 3. Filtering by specific leaf category Cat 3 (Tartes):
        // Only matches sale2!
        val filteredLeafCat3 = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = 3L,
            categories = categories,
            products = products
        )
        assertEquals(1, filteredLeafCat3.size)
        assertEquals("CMD-002", filteredLeafCat3.first().order.number)

        // 4. Filtering by other branch Cat 4 (Boissons):
        // Should match sale3 (contains Prod 105 in Cat 5, descendant of Cat 4)
        val filteredBoissons = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = 4L,
            categories = categories,
            products = products
        )
        assertEquals(1, filteredBoissons.size)
        assertEquals("CMD-003", filteredBoissons.first().order.number)

        // 5. Combined Filters: Cat 1 (Pâtisserie) + PaymentMethod.CARD
        // sale1 has CASH, sale2 has CARD -> should match only sale2
        val filteredCombined = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.CARD,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = 1L,
            categories = categories,
            products = products
        )
        assertEquals(1, filteredCombined.size)
        assertEquals("CMD-002", filteredCombined.first().order.number)

        // 6. Reset / null category returns all sales
        val filteredReset = filterAndSortSales(
            sales = sales,
            query = "",
            status = null,
            paymentFilter = SalesPaymentFilter.ALL,
            fromEpoch = null,
            toEpoch = null,
            selectedCategoryId = null,
            categories = categories,
            products = products
        )
        assertEquals(3, filteredReset.size)
    }
}
