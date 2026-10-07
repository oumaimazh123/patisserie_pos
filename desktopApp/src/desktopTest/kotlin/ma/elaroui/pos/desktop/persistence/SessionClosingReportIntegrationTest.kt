package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.print.PrintResult
import ma.elaroui.pos.desktop.print.PrinterDiscoveryResult
import ma.elaroui.pos.desktop.print.PrinterHealthStatus
import ma.elaroui.pos.desktop.print.PrinterRole
import ma.elaroui.pos.desktop.print.PrinterService
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CloseRegisterSession
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.AppSetting
import ma.elaroui.pos.shared.domain.CashMovement
import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.OrderStatus
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.domain.UserRole
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.SESSION_CLOSING_REPORT_SETTING
import ma.elaroui.pos.shared.rules.SessionClosingReportRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionClosingReportIntegrationTest {
    @Test
    fun `report reads only paid completed sales from the closed session`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val cashierId = db.createCashier("Amina", "1234")
            val otherCashierId = db.createCashier("Youssef", "5678")
            val clock = FixedClock(1_000L)
            assertIs<UseCaseResult.Success<*>>(OpenRegisterSession(db.sessions, clock).execute(10L, 1L, cashierId, 0L))
            assertIs<UseCaseResult.Success<*>>(OpenRegisterSession(db.sessions, clock).execute(20L, 1L, otherCashierId, 0L))
            val categoryId = db.categories.save(Category(0L, "Épicerie", true, 1))
            val productId = db.products.save(Product(0L, categoryId, "Article", 1_000L, 0))

            val cashOrder = createOrder(db, 101L, "A-CASH", 10L, cashierId, productId)
            val cardOrder = createOrder(db, 102L, "A-CARD", 10L, cashierId, productId)
            val otherOrder = createOrder(db, 201L, "B-CARD", 20L, otherCashierId, productId)
            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(cashOrder.id, PaymentMethod.CASH, 1_000L, "cash-a", cashierId))
            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(cardOrder.id, PaymentMethod.CARD, null, "card-a", cashierId))
            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(otherOrder.id, PaymentMethod.CARD, null, "card-b", otherCashierId))

            db.orders.save(cashOrder.copy(id = 103L, number = "A-UNPAID", status = OrderStatus.COMPLETED))
            db.orders.save(cashOrder.copy(id = 104L, number = "A-CANCELLED", status = OrderStatus.CANCELLED, totalCentimes = 500L))
            db.cashMovements.save(CashMovement(1L, 10L, CashMovementType.CASH_IN, 500L, "Apport", null, cashierId, 2_000L))
            db.cashMovements.save(CashMovement(2L, 10L, CashMovementType.CASH_OUT, 200L, "Retrait", null, cashierId, 3_000L))

            clock.value = 9_000L
            val closed = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(10L, RegisterClosingInput(1_300L, 1_300L, 0L, closingUserId = 1L))
            assertIs<UseCaseResult.Success<*>>(closed)

            val report = db.sessionClosingReport(10L)
            assertEquals(listOf("A-CASH", "A-CARD"), report.sales.map { it.orderNumber })
            assertEquals(2, report.completedSalesCount)
            assertEquals(1_000L, report.paymentTotals[PaymentMethod.CASH])
            assertEquals(1_000L, report.paymentTotals[PaymentMethod.CARD])
            assertEquals(2_000L, report.grandTotalSalesCentimes)
            assertEquals(500L, report.cashInCentimes)
            assertEquals(200L, report.cashOutCentimes)
            assertEquals(1, report.cancelledSalesCount)
            assertEquals(500L, report.cancelledSalesCentimes)
            assertEquals(RegisterSessionStatus.OPEN, db.sessions.findById(20L)?.status)
        }
    }

    @Test
    fun `automatic print failure never rolls back successful closure and can be retried`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val owner = db.allUsers().first { it.role == UserRole.OWNER }
            OpenRegisterSession(db.sessions, FixedClock(1_000L)).execute(30L, 1L, owner.id, 5_000L)
            db.settings.put(AppSetting(SESSION_CLOSING_REPORT_SETTING, "true"))
            db.settings.put(AppSetting("customer_printer", "TEST"))
            val printer = FakePrinter(PrintResult(false, "offline", "offline"))
            printer.throwOnPrint = true
            val state = DesktopNavState(db, Path.of("."), printer)
            state.currentUser = owner
            state.session = db.sessions.findById(30L)

            state.closeRegister(RegisterClosingInput(5_000L, 5_000L, 0L))

            assertEquals(RegisterSessionStatus.CLOSED, db.sessions.findById(30L)?.status)
            assertEquals(30L, state.pendingClosingReportRetrySessionId)
            assertEquals(1, printer.jobs)

            printer.throwOnPrint = false
            printer.result = PrintResult(true, "ok")
            assertTrue(state.printSessionClosingReport(30L).success)
            assertEquals(2, printer.jobs)
        }
    }

    @Test
    fun `disabled setting closes without printing`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val owner = db.allUsers().first { it.role == UserRole.OWNER }
            OpenRegisterSession(db.sessions, FixedClock(1_000L)).execute(40L, 1L, owner.id, 0L)
            db.settings.put(AppSetting(SESSION_CLOSING_REPORT_SETTING, "false"))
            val printer = FakePrinter(PrintResult(true, "ok"))
            val state = DesktopNavState(db, Path.of("."), printer)
            state.currentUser = owner
            state.session = db.sessions.findById(40L)

            state.closeRegister(RegisterClosingInput(0L, 0L, 0L))

            assertEquals(RegisterSessionStatus.CLOSED, db.sessions.findById(40L)?.status)
            assertEquals(0, printer.jobs)
            assertNull(state.pendingClosingReportRetrySessionId)
        }
    }

    @Test
    fun `owner setting persists and cashier cannot modify it`() = runBlocking {
        val directory = Files.createTempDirectory("closing-report-setting")
        val path = directory.resolve("pos.db")
        var db = WindowsPosDatabase.open(path)
        db.configureInitialSetup("Magasin test", "Owner", "9999")
        val owner = db.allUsers().first { it.role == UserRole.OWNER }
        val cashierId = db.createCashier("Cashier", "1234")
        var state = DesktopNavState(db, directory, FakePrinter(PrintResult(true, "ok")))
        state.currentUser = owner
        state.setAutomaticSessionClosingReport(true)
        db.close()

        db = WindowsPosDatabase.open(path)
        assertEquals("true", db.settings.get(SESSION_CLOSING_REPORT_SETTING))
        state = DesktopNavState(db, directory, FakePrinter(PrintResult(true, "ok")))
        state.currentUser = db.allUsers().first { it.id == cashierId }
        assertFailsWith<IllegalStateException> { state.setAutomaticSessionClosingReport(false) }
        assertEquals("true", db.settings.get(SESSION_CLOSING_REPORT_SETTING))
        db.close()
    }

    @Test
    fun `default setting without explicit configuration is ON and prints closing report automatically`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val owner = db.allUsers().first { it.role == UserRole.OWNER }
            OpenRegisterSession(db.sessions, FixedClock(1_000L)).execute(45L, 1L, owner.id, 2_000L)

            assertNull(db.settings.get(SESSION_CLOSING_REPORT_SETTING))
            assertTrue(SessionClosingReportRules.isAutoPrintEnabled(null), "Should be ON by default")

            val printer = FakePrinter(PrintResult(true, "ok"))
            val state = DesktopNavState(db, Path.of("."), printer)
            state.currentUser = owner
            state.session = db.sessions.findById(45L)

            state.closeRegister(RegisterClosingInput(2_000L, 2_000L, 0L))

            assertEquals(RegisterSessionStatus.CLOSED, db.sessions.findById(45L)?.status)
            assertEquals(1, printer.jobs)
            assertNull(state.pendingClosingReportRetrySessionId)
        }
    }

    @Test
    fun `rapid duplicate closeRegister calls trigger exactly one print job`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val owner = db.allUsers().first { it.role == UserRole.OWNER }
            OpenRegisterSession(db.sessions, FixedClock(1_000L)).execute(46L, 1L, owner.id, 1_000L)

            val printer = FakePrinter(PrintResult(true, "ok"))
            val state = DesktopNavState(db, Path.of("."), printer)
            state.currentUser = owner
            state.session = db.sessions.findById(46L)

            state.closeRegister(RegisterClosingInput(1_000L, 1_000L, 0L))
            state.closeRegister(RegisterClosingInput(1_000L, 1_000L, 0L))

            assertEquals(RegisterSessionStatus.CLOSED, db.sessions.findById(46L)?.status)
            assertEquals(1, printer.jobs, "Never print closing report twice")
        }
    }

    @Test
    fun `session closing report includes exact item lines with quantities for all orders`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            db.configureInitialSetup("Magasin test", "Owner", "9999")
            val cashierId = db.createCashier("Amina", "1234")
            val clock = FixedClock(1_000L)
            assertIs<UseCaseResult.Success<*>>(OpenRegisterSession(db.sessions, clock).execute(50L, 1L, cashierId, 0L))
            val catId = db.categories.findByName("Boissons")?.id ?: db.categories.save(Category(0L, "Boissons Test", true, 1))
            val p1 = db.products.save(Product(0L, catId, "Coca-Cola", 1_500L, 0))
            val p2 = db.products.save(Product(0L, catId, "Eau", 500L, 0))
            val p3 = db.products.save(Product(0L, catId, "Sandwich Poulet", 3_500L, 0))

            // Order 1: 2x Coca-Cola, 1x Sandwich Poulet, 3x Eau
            val order1 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    501L, "1234", OrderType.COUNTER, 50L,
                    listOf(p1 to 2, p3 to 1, p2 to 3),
                    null, cashierId
                )
            ).value

            // Order 2: 1x Sandwich Poulet, 2x Eau
            val order2 = assertIs<UseCaseResult.Success<Order>>(
                CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                    502L, "1235", OrderType.COUNTER, 50L,
                    listOf(p3 to 1, p2 to 2),
                    null, cashierId
                )
            ).value

            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(order1.id, PaymentMethod.CASH, 8_000L, "pay-1", cashierId))
            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(order2.id, PaymentMethod.CARD, null, "pay-2", cashierId))

            clock.value = 5_000L
            val closed = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(50L, RegisterClosingInput(12_500L, 12_500L, 0L, closingUserId = cashierId))
            assertIs<UseCaseResult.Success<*>>(closed)

            val report = db.sessionClosingReport(50L)
            assertEquals(2, report.sales.size)

            val sale1 = report.sales[0]
            assertEquals("1234", sale1.orderNumber)
            assertEquals(3, sale1.items.size)
            assertEquals("Coca-Cola", sale1.items[0].productName)
            assertEquals(2, sale1.items[0].quantity)
            assertEquals("Sandwich Poulet", sale1.items[1].productName)
            assertEquals(1, sale1.items[1].quantity)
            assertEquals("Eau", sale1.items[2].productName)
            assertEquals(3, sale1.items[2].quantity)

            val sale2 = report.sales[1]
            assertEquals("1235", sale2.orderNumber)
            assertEquals(2, sale2.items.size)
            assertEquals("Sandwich Poulet", sale2.items[0].productName)
            assertEquals(1, sale2.items[0].quantity)
            assertEquals("Eau", sale2.items[1].productName)
            assertEquals(2, sale2.items[1].quantity)

            // Verify printing produces exact content
            val printer = FakePrinter(PrintResult(true, "ok"))
            val state = DesktopNavState(db, Path.of("."), printer)
            state.currentUser = db.allUsers().first { it.id == cashierId }
            val printResult = state.printSessionClosingReport(50L, type = ma.elaroui.pos.shared.rules.SessionReportType.DETAILED, automatic = false)
            assertTrue(printResult.success)
            assertEquals(1, printer.jobs)
            assertTrue(printer.lastBytes != null)
            val printedText = String(printer.lastBytes!!, ma.elaroui.pos.desktop.print.FrenchEscPosEncoder.CHARSET)
            assertTrue(printedText.contains("Commande #1234"))
            assertTrue(printedText.contains("2 x Coca-Cola"))
            assertTrue(printedText.contains("1 x Sandwich Poulet"))
            assertTrue(printedText.contains("3 x Eau"))
            assertTrue(printedText.contains("Total commande : 80,00 DH"))
            assertTrue(printedText.contains("Commande #1235"))
            assertTrue(printedText.contains("1 x Sandwich Poulet"))
            assertTrue(printedText.contains("2 x Eau"))
            assertTrue(printedText.contains("Total commande : 45,00 DH"))
        }
    }

    private suspend fun createOrder(
        db: WindowsPosDatabase,
        id: Long,
        number: String,
        sessionId: Long,
        cashierId: Long,
        productId: Long
    ): Order = assertIs<UseCaseResult.Success<Order>>(
        CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
            .execute(id, number, OrderType.COUNTER, sessionId, listOf(productId to 1), null, cashierId)
    ).value

    private class FixedClock(var value: Long) : Clock {
        override fun now() = EpochMilliseconds(value)
    }

    private class FakePrinter(var result: PrintResult) : PrinterService {
        var jobs = 0
        var throwOnPrint = false
        var lastBytes: ByteArray? = null
        override fun discoverPrinters() = PrinterDiscoveryResult(emptyList())
        override fun checkPrinterHealth(printerName: String) = PrinterHealthStatus.connected("ready")
        override fun printTestPage(printerName: String, platformTestBytes: ByteArray?) = result
        override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult {
            jobs++
            lastBytes = bytes
            if (throwOnPrint) error("simulated printer failure")
            return result
        }
    }
}
