package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*

class DiscountRevenueRegressionTest {
    private val clock = object : Clock { override fun now() = EpochMilliseconds(1_000L) }

    @Test fun migrationFromVersionElevenKeepsHistoricalAmountsAndPayments() = runBlocking {
        val path = Files.createTempDirectory("pos-v11-discount-migration").resolve("pos.db")
        WindowsPosDatabase.open(path).use { db ->
            db.configureInitialSetup("Audit", "Owner", "1234")
            val category = db.categories.save(Category(0L, "Legacy"))
            val secondCategory = db.categories.save(Category(0L, "Legacy bread"))
            val product = db.products.save(Product(0L, category, "Legacy cake", 10_000L, 2_000))
            val secondProduct = db.products.save(Product(0L, secondCategory, "Legacy loaf", 10_001L, 0))
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(
                OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 0L)
            ).value
            val order = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    1L, "LEGACY-ORDER", OrderType.COUNTER, session.id,
                    listOf(product to 1, secondProduct to 1), null, 1L,
                    discountBasisPoints = 1_000
                )
            ).value
            assertEquals(18_001L, order.totalCentimes)
            assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order.id, PaymentMethod.CASH, order.totalCentimes, "legacy-payment", 1L
                )
            )
        }
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { c ->
            c.createStatement().use { s ->
                s.execute("ALTER TABLE orders DROP COLUMN discount_basis_points")
                s.execute("ALTER TABLE order_items DROP COLUMN item_discount_basis_points")
                s.execute("ALTER TABLE order_items DROP COLUMN recognized_amount_centimes")
                s.execute("ALTER TABLE order_items DROP COLUMN recognized_tax_centimes")
                s.execute("DELETE FROM schema_migrations WHERE version=12")
            }
        }
        WindowsPosDatabase.open(path).use { db ->
            assertEquals(12, db.schemaVersion())
            val restored = assertNotNull(db.orders.findById(1L))
            assertEquals(18_001L, restored.totalCentimes)
            assertEquals(OrderStatus.COMPLETED, restored.status)
            assertNull(restored.discountBasisPoints)
            assertTrue(restored.lines.all { it.recognizedAmountCentimes == null })
            assertEquals(18_001L, db.payments.totalCashForSession(1L))
            val selected = restored.lines.mapNotNull { it.categoryIdSnapshot }
            assertEquals(2, selected.size)
            val parts = selected.map { db.salesSummary(matchingCategoryIds = setOf(it)) }
            assertEquals(restored.totalCentimes, parts.sumOf { it.salesCentimes })
            assertEquals(restored.taxCentimes, parts.sumOf { it.taxCentimes })
            db.read { c -> c.createStatement().use { s ->
                s.executeQuery("PRAGMA integrity_check").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("ok", rows.getString(1))
                }
            } }
        }
    }

    @Test fun heldDiscountsSurviveRestartModificationAndPayment() = runBlocking {
        val path = Files.createTempDirectory("pos-discount-restart").resolve("pos.db")
        var orderId = 0L
        var taxedId = 0L
        var exemptId = 0L
        WindowsPosDatabase.open(path).use { db ->
            db.configureInitialSetup("Audit", "Owner", "1234")
            val category = db.categories.save(Category(0L, "Pastry"))
            taxedId = db.products.save(Product(0L, category, "Cake", 10_000L, 2_000))
            exemptId = db.products.save(Product(0L, category, "Bread", 10_000L, 0))
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(
                OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 0L)
            ).value
            val held = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    1L, "HELD-DISCOUNT", OrderType.COUNTER, session.id,
                    listOf(taxedId to 1, exemptId to 1), null, 1L,
                    discountBasisPoints = 500,
                    itemDiscountsBasisPoints = mapOf(taxedId to 5_000)
                )
            ).value
            orderId = held.id
            assertEquals(500, held.discountBasisPoints)
            assertEquals(5_000, held.lines.first { it.productId == taxedId }.itemDiscountBasisPoints)
            assertEquals(14_250L, held.totalCentimes)
            assertEquals(held.totalCentimes, held.lines.sumOf { it.recognizedAmountCentimes!! })
            assertEquals(held.taxCentimes, held.lines.sumOf { it.recognizedTaxCentimes!! })
        }
        WindowsPosDatabase.open(path).use { db ->
            val restored = assertNotNull(db.orders.findById(orderId))
            assertEquals(500, restored.discountBasisPoints)
            assertEquals(5_000, restored.lines.first { it.productId == taxedId }.itemDiscountBasisPoints)
            val updated = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    restored.id, restored.number, restored.type, restored.registerSessionId,
                    listOf(taxedId to 2, exemptId to 1), null, restored.cashierId,
                    discountBasisPoints = restored.discountBasisPoints!!,
                    itemDiscountsBasisPoints = restored.lines.associate { it.productId to it.itemDiscountBasisPoints }
                )
            ).value
            assertEquals(19_000L, updated.totalCentimes)
            assertEquals(updated.totalCentimes, updated.lines.sumOf { it.recognizedAmountCentimes!! })
            assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    updated.id, PaymentMethod.CASH, updated.totalCentimes, "discount-restart-payment", 1L
                )
            )
            val completed = assertNotNull(db.orders.findById(orderId))
            assertEquals(OrderStatus.COMPLETED, completed.status)
            assertEquals(19_000L, completed.totalCentimes)
            assertEquals(500, completed.discountBasisPoints)
        }
    }

    @Test fun mixedCategoryRevenueAndVatUseDiscountedLines() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val owner = db.allUsers().first()
            val cakes = db.categories.save(Category(0L, "Cakes"))
            val bread = db.categories.save(Category(0L, "Bread"))
            val cakeId = db.products.save(Product(0L, cakes, "Cake", 10_000L, 2_000))
            val breadId = db.products.save(Product(0L, bread, "Bread", 10_000L, 0))
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(
                OpenRegisterSession(db.sessions, clock).execute(1L, 1L, owner.id, 0L)
            ).value
            assertIs<UseCaseResult.Failure>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    99L, "DUPLICATE-LINE", OrderType.COUNTER, session.id,
                    listOf(cakeId to 1, cakeId to 1), null, owner.id
                )
            )
            val order = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    1L, "MIXED-CATEGORY", OrderType.COUNTER, session.id,
                    listOf(cakeId to 1, breadId to 1), null, owner.id,
                    discountBasisPoints = 500,
                    itemDiscountsBasisPoints = mapOf(cakeId to 5_000)
                )
            ).value
            assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order.id, PaymentMethod.CASH, order.totalCentimes, "mixed-category-payment", owner.id
                )
            )
            val cakeLine = order.lines.first { it.productId == cakeId }
            val breadLine = order.lines.first { it.productId == breadId }
            val cakeSummary = db.salesSummary(matchingCategoryIds = setOf(cakes))
            val breadSummary = db.salesSummary(matchingCategoryIds = setOf(bread))
            assertEquals(cakeLine.recognizedAmountCentimes, cakeSummary.salesCentimes)
            assertEquals(cakeLine.recognizedTaxCentimes, cakeSummary.taxCentimes)
            assertEquals(breadLine.recognizedAmountCentimes, breadSummary.salesCentimes)
            assertEquals(order.totalCentimes, cakeSummary.salesCentimes + breadSummary.salesCentimes)
            val analytics = db.retailSalesAnalytics(0L, Long.MAX_VALUE, matchingCategoryIds = setOf(cakes))
            assertEquals(cakeSummary.salesCentimes, analytics.byProduct.single().amountCentimes)
            assertEquals(cakeSummary.salesCentimes, analytics.byCategory.single().amountCentimes)
            assertEquals(cakeSummary.salesCentimes, analytics.byCashier.single().amountCentimes)
            assertEquals(cakeSummary.salesCentimes, db.salesEvolution(0L, System.currentTimeMillis() + 1_000L, true,
                matchingCategoryIds = setOf(cakes)).sumOf { it.amountCentimes })
        }
    }

    @Test fun identicallyNamedProductsAndCategoriesStaySeparateInAnalytics() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val rootA = db.categories.save(Category(0L, "Root A"))
            val rootB = db.categories.save(Category(0L, "Root B"))
            val catA = db.categories.save(Category(0L, "Shared name", parentId = rootA))
            val catB = db.categories.save(Category(0L, "Shared name", parentId = rootB))
            val prodA = db.products.save(Product(0L, catA, "Cake", 1_000L, 0))
            val prodB = db.products.save(Product(0L, catB, "Cake", 2_000L, 0))
            val session = assertIs<UseCaseResult.Success<RegisterSession>>(
                OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 0L)
            ).value
            val order = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    1L, "SAME-NAMES", OrderType.COUNTER, session.id,
                    listOf(prodA to 1, prodB to 1), null, 1L
                )
            ).value
            assertIs<UseCaseResult.Success<Payment>>(
                CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                    order.id, PaymentMethod.CASH, order.totalCentimes, "same-name-payment", 1L
                )
            )
            val analytics = db.retailSalesAnalytics(0L, Long.MAX_VALUE)
            assertEquals(listOf(2_000L, 1_000L), analytics.byProduct.map { it.amountCentimes })
            assertEquals(listOf(2_000L, 1_000L), analytics.byCategory.map { it.amountCentimes })
        }
    }
}
