package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import java.sql.DriverManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.CloseRegisterSession
import ma.elaroui.pos.shared.application.CompletePayment
import ma.elaroui.pos.shared.application.CreateOrder
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.RecordCashMovement
import ma.elaroui.pos.shared.application.TestClock
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.CashMovementType
import ma.elaroui.pos.shared.domain.OrderType
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.print.PrintResult
import ma.elaroui.pos.desktop.print.PrinterDiscoveryResult
import ma.elaroui.pos.desktop.print.PrinterRole
import ma.elaroui.pos.desktop.print.PrinterService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerUserRegisterSessionTest {

    @Test
    fun `A B and C can open concurrently but each user can open only once`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val a = db.createCashier("Cashier A", "2101")
            val b = db.createCashier("Cashier B", "2102")
            val c = db.createCashier("Cashier C", "2103")
            val open = OpenRegisterSession(db.sessions, TestClock)

            val sessionA = success(open.execute(db.nextId("register_sessions"), 1, a, 10_000))
            val sessionB = success(open.execute(db.nextId("register_sessions"), 1, b, 20_000))
            val sessionC = success(open.execute(db.nextId("register_sessions"), 1, c, 30_000))

            assertNotEquals(sessionA.id, sessionB.id)
            assertNotEquals(sessionB.id, sessionC.id)
            assertEquals(sessionA.id, db.sessions.findOpenByUser(a)?.id)
            assertEquals(sessionB.id, db.sessions.findOpenByUser(b)?.id)
            assertEquals(sessionC.id, db.sessions.findOpenByUser(c)?.id)
            assertIs<UseCaseResult.Failure>(open.execute(db.nextId("register_sessions"), 1, a, 0))
            assertIs<UseCaseResult.Failure>(open.execute(db.nextId("register_sessions"), 1, b, 0))
        }
        Unit
    }

    @Test
    fun `sales payments and cash movements remain strictly isolated by session`() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val a = db.createCashier("Isolation A", "2201")
            val b = db.createCashier("Isolation B", "2202")
            val opener = OpenRegisterSession(db.sessions, TestClock)
            val sessionA = success(opener.execute(db.nextId("register_sessions"), 1, a, 10_000))
            val sessionB = success(opener.execute(db.nextId("register_sessions"), 1, b, 20_000))
            val product = db.products.observeSellable().first().first()

            val orderA = success(CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                db.nextId("orders"), "A-ONLY", OrderType.COUNTER, sessionA.id, listOf(product.id to 1), null, a
            ))
            val orderB = success(CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                db.nextId("orders"), "B-ONLY", OrderType.COUNTER, sessionB.id, listOf(product.id to 2), null, b
            ))

            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(orderA.id, PaymentMethod.CASH, orderA.totalCentimes, "pay-a", a))
            assertIs<UseCaseResult.Success<*>>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(orderB.id, PaymentMethod.CARD, null, "pay-b", b))
            assertIs<UseCaseResult.Failure>(CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(orderB.id, PaymentMethod.CARD, null, "cross-user", a))

            val movements = RecordCashMovement(db.sessions, db.payments, db.cashMovements, TestClock)
            assertIs<UseCaseResult.Success<*>>(movements.execute(sessionA.id, CashMovementType.CASH_IN, 500, "APPORT", null, a))
            assertIs<UseCaseResult.Success<*>>(movements.execute(sessionB.id, CashMovementType.CASH_OUT, 700, "RETRAIT", null, b))
            assertIs<UseCaseResult.Failure>(movements.execute(sessionA.id, CashMovementType.CASH_IN, 100, "CROSS", null, b))

            assertEquals(orderA.totalCentimes, db.payments.totalCashForSession(sessionA.id))
            assertEquals(0, db.payments.totalNonCashForSession(sessionA.id))
            assertEquals(0, db.payments.totalCashForSession(sessionB.id))
            assertEquals(orderB.totalCentimes, db.payments.totalNonCashForSession(sessionB.id))
            assertEquals(500, db.cashMovements.totalCashIn(sessionA.id))
            assertEquals(0, db.cashMovements.totalCashOut(sessionA.id))
            assertEquals(0, db.cashMovements.totalCashIn(sessionB.id))
            assertEquals(700, db.cashMovements.totalCashOut(sessionB.id))
        }
        Unit
    }

    @Test
    fun `restart restores each user session and closing A leaves B open`() = runBlocking {
        val dir = Files.createTempDirectory("general-pos-user-sessions")
        val file = dir.resolve("pos.db")
        var a = 0L
        var b = 0L
        var sessionAId = 0L
        var sessionBId = 0L
        WindowsPosDatabase.open(file).use { db ->
            db.configureInitialSetup("Session Store", "Owner", "1234")
            a = db.createCashier("Restart A", "2301")
            b = db.createCashier("Restart B", "2302")
            val opener = OpenRegisterSession(db.sessions, TestClock)
            sessionAId = success(opener.execute(db.nextId("register_sessions"), 1, a, 1_000)).id
            sessionBId = success(opener.execute(db.nextId("register_sessions"), 1, b, 2_000)).id
        }

        WindowsPosDatabase.open(file).use { db ->
            assertEquals(sessionAId, db.sessions.findOpenByUser(a)?.id)
            assertEquals(sessionBId, db.sessions.findOpenByUser(b)?.id)
            val closedA = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, TestClock, db.orders)
                .execute(sessionAId, RegisterClosingInput(1_000, 1_000, 0, closingUserId = a))
            assertIs<UseCaseResult.Success<*>>(closedA)
            assertNull(db.sessions.findOpenByUser(a))
            assertEquals(sessionBId, db.sessions.findOpenByUser(b)?.id)
            assertEquals(RegisterSessionStatus.OPEN, db.sessions.findById(sessionBId)?.status)
        }
        Unit
    }

    @Test
    fun `migration replaces global open index without losing an existing session`() = runBlocking {
        val dir = Files.createTempDirectory("general-pos-session-migration")
        val file = dir.resolve("pos.db")
        var a = 0L
        var existingId = 0L
        WindowsPosDatabase.open(file).use { db ->
            db.configureInitialSetup("Migration Store", "Owner", "1234")
            a = db.createCashier("Legacy A", "2401")
            existingId = success(OpenRegisterSession(db.sessions, TestClock)
                .execute(db.nextId("register_sessions"), 1, a, 5_000)).id
        }

        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { legacy ->
            legacy.createStatement().use {
                it.execute("DROP INDEX IF EXISTS one_open_session_per_cashier")
                it.execute("CREATE UNIQUE INDEX one_open_session_per_register ON register_sessions(register_id) WHERE status='OPEN'")
                it.execute("DELETE FROM schema_migrations WHERE version=7")
            }
        }

        WindowsPosDatabase.open(file).use { db ->
            assertEquals(existingId, db.sessions.findOpenByUser(a)?.id)
            val b = db.createCashier("Migrated B", "2402")
            val sessionB = OpenRegisterSession(db.sessions, TestClock)
                .execute(db.nextId("register_sessions"), 1, b, 6_000)
            assertIs<UseCaseResult.Success<*>>(sessionB)
            assertNotNull(db.sessions.findById(existingId))
            val indexes = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { verify ->
                verify.createStatement().use { statement ->
                    statement.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='register_sessions'").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
            }
            assertTrue("one_open_session_per_cashier" in indexes)
            assertTrue("one_open_session_per_register" !in indexes)
        }
        Unit
    }

    @Test
    fun `logout preserves A session and login selects only the authenticated user session`() {
        val dataDir = Files.createTempDirectory("general-pos-user-switch")
        WindowsPosDatabase.openInMemory().use { db ->
            val aId = db.createCashier("Switch A", "2501")
            val bId = db.createCashier("Switch B", "2502")
            val a = db.allUsers().first { it.id == aId }
            val b = db.allUsers().first { it.id == bId }
            val state = DesktopNavState(db, dataDir)

            state.login(a, "2501")
            assertNull(state.session)
            state.openRegister(1_500)
            val sessionAId = assertNotNull(state.session).id

            state.lock()
            assertNull(state.session)
            assertEquals(sessionAId, runBlocking { db.sessions.findOpenByUser(aId)?.id })

            state.login(b, "2502")
            assertNull(state.session, "B must never inherit A's open session")
            state.openRegister(2_500)
            val sessionBId = assertNotNull(state.session).id
            assertNotEquals(sessionAId, sessionBId)

            state.lock()
            state.login(a, "2501")
            assertEquals(sessionAId, state.session?.id)
            assertEquals(sessionAId, state.resumedSessionNotice?.id)
            assertEquals(sessionBId, runBlocking { db.sessions.findOpenByUser(bId)?.id })
        }
    }

    @Test
    fun `logout anyway is single action preserves open session and never prints`() = runBlocking {
        val dataDir = Files.createTempDirectory("general-pos-logout-anyway")
        WindowsPosDatabase.openInMemory().use { db ->
            val cashierId = db.createCashier("Logout Cashier", "2601")
            val cashier = db.allUsers().first { it.id == cashierId }
            val printer = RecordingPrinterService()
            val state = DesktopNavState(db, dataDir, printer)

            state.login(cashier, "2601")
            state.openRegister(12_345)
            val openBeforeLogout = assertNotNull(state.session)

            assertTrue(state.logoutKeepingRegisterSessionOpen(cashierId))
            assertFalse(state.logoutKeepingRegisterSessionOpen(cashierId), "A second callback must be ignored")

            assertNull(state.currentUser)
            assertNull(state.session)
            assertEquals(DesktopScreenRoute.USER_SELECTION, state.currentRoute)
            val persisted = assertNotNull(db.sessions.findOpenByUser(cashierId))
            assertEquals(openBeforeLogout.id, persisted.id)
            assertEquals(openBeforeLogout.openingCashCentimes, persisted.openingCashCentimes)
            assertEquals(RegisterSessionStatus.OPEN, persisted.status)
            assertEquals(0, printer.rawPrintCount)
        }
    }

    private fun <T> success(result: UseCaseResult<T>): T = when (result) {
        is UseCaseResult.Success -> result.value
        is UseCaseResult.Failure -> error("Expected success but got: ${result.reason}")
    }

    private class RecordingPrinterService : PrinterService {
        var rawPrintCount = 0

        override fun discoverPrinters() = PrinterDiscoveryResult(emptyList())

        override fun printTestPage(printerName: String, platformTestBytes: ByteArray?) =
            PrintResult(true, "unused")

        override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult {
            rawPrintCount += 1
            return PrintResult(true, "unexpected")
        }
    }
}
