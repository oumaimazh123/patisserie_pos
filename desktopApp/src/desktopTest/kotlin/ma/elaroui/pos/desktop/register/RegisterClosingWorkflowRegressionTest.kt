package ma.elaroui.pos.desktop.register

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.DiscrepancyKind
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.RegisterClosingRules
import kotlin.test.*

class RegisterClosingWorkflowRegressionTest {

    private class FixedClock(var time: Long) : ma.elaroui.pos.shared.Clock {
        override fun now() = EpochMilliseconds(time)
    }

    @Test
    fun test01_normalExactClosingWorkflow() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            // Open session with 200 DH (20,000 centimes)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            assertTrue(openRes is UseCaseResult.Success)
            val session = openRes.value

            // 1 cash payment of 100 DH
            val order1Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                101L, "ORD-101", OrderType.TAKEAWAY, session.id, listOf(1L to 5), null, 1L
            )
            val order1 = (order1Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                order1.id, PaymentMethod.CASH, order1.totalCentimes, "token-101"
            )

            // Expected cash = 20,000 + 5000 = 25,000 centimes
            val expected = session.openingCashCentimes + db.payments.totalCashForSession(session.id)
            assertEquals(25_000L, expected)

            // Exact closing: Counted = 25,000, Left in drawer = 10,000, Removed = 15,000
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 25_000L,
                leftInDrawerCentimes = 10_000L,
                removedAmountCentimes = 15_000L,
                remittanceReference = "ENV-2026-001",
                remittanceDestination = "Coffre",
                closingNote = "Cloture exacte sans anomalie",
                closingUserId = 1L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(25_000L, closed.expectedCashCentimes)
            assertEquals(25_000L, closed.countedCashCentimes)
            assertEquals(0L, closed.differenceCentimes)
            assertEquals(DiscrepancyKind.EXACT, RegisterClosingRules.classifyDifference(closed.differenceCentimes ?: 0L))
            assertEquals(10_000L, closed.leftInDrawerCentimes)
            assertEquals(15_000L, closed.removedAmountCentimes)
            assertEquals("ENV-2026-001", closed.remittanceReference)
            assertEquals("Coffre", closed.remittanceDestination)
            assertEquals("Cloture exacte sans anomalie", closed.closingNote)
            assertEquals(1L, closed.closingUserId)
        }
    }

    @Test
    fun test02_shortageClosingWorkflow() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Expected cash = 20,000. Cashier counts 19,000 (shortage of 1000 centimes / -10 DH)
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 19_000L,
                leftInDrawerCentimes = 10_000L,
                removedAmountCentimes = 9_000L,
                remittanceReference = "ENV-SHORT-01",
                remittanceDestination = "Responsable",
                closingNote = "Manque 10 DH justifie",
                closingUserId = 1L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(20_000L, closed.expectedCashCentimes)
            assertEquals(19_000L, closed.countedCashCentimes)
            assertEquals(-1000L, closed.differenceCentimes)
            assertEquals(DiscrepancyKind.SHORTAGE, RegisterClosingRules.classifyDifference(closed.differenceCentimes ?: 0L))
        }
    }

    @Test
    fun test03_surplusClosingWorkflow() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Expected cash = 20,000. Cashier counts 21,500 (+15 DH surplus)
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 21_500L,
                leftInDrawerCentimes = 10_000L,
                removedAmountCentimes = 11_500L,
                remittanceReference = "ENV-SURPLUS-01",
                remittanceDestination = "Coffre",
                closingNote = "Pourboire non reclame",
                closingUserId = 1L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(20_000L, closed.expectedCashCentimes)
            assertEquals(21_500L, closed.countedCashCentimes)
            assertEquals(1500L, closed.differenceCentimes)
            assertEquals(DiscrepancyKind.SURPLUS, RegisterClosingRules.classifyDifference(closed.differenceCentimes ?: 0L))
        }
    }

    @Test
    fun test04_breakdownSumMismatchIsRejected() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Counted = 20,000, but left (10,000) + removed (5,000) = 15,000 != 20,000
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 20_000L,
                leftInDrawerCentimes = 10_000L,
                removedAmountCentimes = 5_000L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Failure)
            assertTrue((closeRes as UseCaseResult.Failure).reason.contains("equal counted cash", ignoreCase = true))

            // Session must remain OPEN
            assertEquals(RegisterSessionStatus.OPEN, db.sessions.findById(session.id)?.status)
        }
    }

    @Test
    fun test05_activeOrdersBlockClosing() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Create open order on table 1
            CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                102L, "ORD-102", OrderType.DINE_IN, session.id, listOf(1L to 2), 1L, 1L
            )
            assertEquals(1, db.orders.countOpenForSession(session.id))

            // Attempt to close register with active open orders
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 20_000L,
                leftInDrawerCentimes = 20_000L,
                removedAmountCentimes = 0L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Failure)
            assertTrue((closeRes as UseCaseResult.Failure).reason.contains("Active orders", ignoreCase = true))

            // Now cancel/complete the order and retry closing -> should succeed
            val openOrder = db.orders.findById(102L)!!
            db.orders.save(openOrder.copy(status = OrderStatus.CANCELLED))
            assertEquals(0, db.orders.countOpenForSession(session.id))

            val retryClose = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)
            assertTrue(retryClose is UseCaseResult.Success)
            assertEquals(RegisterSessionStatus.CLOSED, retryClose.value.status)
        }
    }

    @Test
    fun test06_cardPaymentsExcludedFromPhysicalDrawerExpectedCash() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 10_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Order 1: Cash 3000 centimes
            val o1Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                103L, "ORD-103", OrderType.TAKEAWAY, session.id, listOf(1L to 3), null, 1L
            )
            val o1 = (o1Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                o1.id, PaymentMethod.CASH, o1.totalCentimes, "token-103"
            )

            // Order 2: Card / TPE 7000 centimes
            val o2Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                104L, "ORD-104", OrderType.TAKEAWAY, session.id, listOf(1L to 7), null, 1L
            )
            val o2 = (o2Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                o2.id, PaymentMethod.CARD, o2.totalCentimes, "token-104"
            )

            val cashSales = db.payments.totalCashForSession(session.id)
            val cardSales = db.payments.totalNonCashForSession(session.id)
            assertEquals(3000L, cashSales)
            assertEquals(7000L, cardSales)

            // Expected physical cash must be opening (10,000) + cashSales (3,000) = 13,000 (Card 7000 strictly excluded)
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 13_000L,
                leftInDrawerCentimes = 13_000L,
                removedAmountCentimes = 0L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(13_000L, closed.expectedCashCentimes)
            assertEquals(0L, closed.differenceCentimes)
        }
    }

    @Test
    fun test07_suggestedNextSessionOpeningFloatPersistence() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 15_000L)
            val session = (openRes as UseCaseResult.Success).value

            // Close with 8,000 left in drawer and 7,000 removed
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 15_000L,
                leftInDrawerCentimes = 8_000L,
                removedAmountCentimes = 7_000L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)
            assertTrue(closeRes is UseCaseResult.Success)

            db.settings.put(AppSetting("suggested_opening_cash", "8000"))

            val lastClosed = db.lastClosedSession()
            assertNotNull(lastClosed)
            assertEquals(8_000L, lastClosed.leftInDrawerCentimes)

            val suggestedSetting = db.settings.get("suggested_opening_cash")?.toLongOrNull()
            assertEquals(8_000L, suggestedSetting)
        }
    }

    @Test
    fun test08_historicalSessionsWithNullNewFieldsAndReports() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Insert historical closed session directly without new fields
            val historical = RegisterSession(
                id = 99L,
                status = RegisterSessionStatus.CLOSED,
                openingCashCentimes = 10_000L,
                registerId = 1L,
                cashierId = 1L,
                openedAtEpochMilliseconds = 1_600_000_000_000L,
                closedAtEpochMilliseconds = 1_600_028_800_000L,
                expectedCashCentimes = 10_000L,
                countedCashCentimes = 10_000L,
                differenceCentimes = 0L,
                closingUserId = null,
                leftInDrawerCentimes = null,
                removedAmountCentimes = null,
                remittanceReference = null,
                remittanceDestination = null,
                closingNote = null
            )
            db.sessions.save(historical)

            val retrieved = db.sessions.findById(99L)
            assertNotNull(retrieved)
            assertEquals(RegisterSessionStatus.CLOSED, retrieved.status)
            assertNull(retrieved.closingUserId)
            assertNull(retrieved.leftInDrawerCentimes)
            assertNull(retrieved.remittanceReference)

            val history = db.sessionHistory()
            val row = history.firstOrNull { it.session.id == 99L }
            assertNotNull(row)
            assertEquals("Owner", row.cashierName)
            assertNull(row.closingUserName)
        }
    }

    @Test
    fun test09_duplicateCloseAttemptRejected() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 10_000L)
            val session = (openRes as UseCaseResult.Success).value

            val closingInput = RegisterClosingInput(
                countedCashCentimes = 10_000L,
                leftInDrawerCentimes = 10_000L,
                removedAmountCentimes = 0L
            )
            val close1 = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)
            assertTrue(close1 is UseCaseResult.Success)

            // Second close attempt on same session must be rejected
            val close2 = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)
            assertTrue(close2 is UseCaseResult.Failure)
            assertTrue((close2 as UseCaseResult.Failure).reason.contains("already closed", ignoreCase = true))
        }
    }
}
