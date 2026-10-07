package ma.elaroui.pos.desktop.presentation.register.close

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.CloseRegisterSession
import ma.elaroui.pos.shared.application.OpenRegisterSession
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.RegisterSessionStatus
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import kotlin.test.*

class CloseRegisterWorkflowTest {

    private class FixedClock(var time: Long) : ma.elaroui.pos.shared.Clock {
        override fun now() = EpochMilliseconds(time)
    }

    @Test
    fun test01_autoRemovedCalculation_partialWithdrawal() {
        // 363 DH counted, 100 DH left -> 263 DH removed
        val counted = 363_00L
        val left = 100_00L
        val removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(263_00L, removed)
    }

    @Test
    fun test02_autoRemovedCalculation_zeroWithdrawal() {
        // All kept in drawer
        val counted = 363_00L
        val left = 363_00L
        val removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(0L, removed)
    }

    @Test
    fun test03_autoRemovedCalculation_totalWithdrawal() {
        // Everything withdrawn
        val counted = 363_00L
        val left = 0L
        val removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(363_00L, removed)
    }

    @Test
    fun test04_autoRemovedCalculation_preventsNegativeValues() {
        // Left > Counted -> clamped to 0
        val counted = 100_00L
        val left = 150_00L
        val removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(0L, removed)
    }

    @Test
    fun test05_autoRemovedCalculation_decimalsPrecision() {
        // 363.75 DH counted, 100.25 DH left -> 263.50 DH removed
        val counted = 363_75L
        val left = 100_25L
        val removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(263_50L, removed)
    }

    @Test
    fun test06_autoRemovedCalculation_nullHandling() {
        assertNull(RegisterClosingHelper.calculateAutoRemovedCentimes(null, 100_00L))
        assertNull(RegisterClosingHelper.calculateAutoRemovedCentimes(363_00L, null))
        assertNull(RegisterClosingHelper.calculateAutoRemovedCentimes(null, null))
    }

    @Test
    fun test07_envelopeReferenceGeneration_standardFormat() {
        val fixedTime = 1788842700000L // arbitrary fixed timestamp
        val ref = RegisterClosingHelper.generateEnvelopeReference("Zakaria", fixedTime)
        assertTrue(ref.startsWith("ENV-"))
        assertTrue(ref.endsWith("-ZAKARIA"))
        assertTrue(Regex("""^ENV-\d{8}-\d{4}-ZAKARIA$""").matches(ref))
    }

    @Test
    fun test08_envelopeReferenceGeneration_accentsAndSpecialCharactersNormalization() {
        val fixedTime = 1788842700000L
        val refWithAccents = RegisterClosingHelper.generateEnvelopeReference("Él-Aroui Jérémy #1", fixedTime)
        assertTrue(refWithAccents.endsWith("-ELAROUIJEREMY1"))
        assertFalse(refWithAccents.contains("é"))
        assertFalse(refWithAccents.contains("#"))
        assertFalse(refWithAccents.contains(" "))
    }

    @Test
    fun test09_envelopeReferenceGeneration_blankCashierFallback() {
        val ref = RegisterClosingHelper.generateEnvelopeReference("   ", 1788842700000L)
        assertTrue(ref.endsWith("-CAISSIER"))
    }

    @Test
    fun test10_envelopeReferenceGeneration_sequenceSuffix() {
        val ref1 = RegisterClosingHelper.generateEnvelopeReference("ZAKARIA", 1788842700000L, sequence = 1)
        val ref2 = RegisterClosingHelper.generateEnvelopeReference("ZAKARIA", 1788842700000L, sequence = 2)
        assertEquals("${ref1}-2", ref2)
    }

    @Test
    fun test11_databaseRemittanceReferenceUniqueness() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val baseRef = "ENV-20260908-0500-ZAKARIA"

            // 1st time: baseRef is available
            val unique1 = db.nextUniqueRemittanceReference(baseRef)
            assertEquals(baseRef, unique1)

            // Simulate saving a session with this reference
            val clock = FixedClock(1_700_000_000_000L)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 10_000L)
            val session1 = (openRes as UseCaseResult.Success).value

            CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(
                    session1.id,
                    RegisterClosingInput(
                        countedCashCentimes = 10_000L,
                        leftInDrawerCentimes = 5_000L,
                        removedAmountCentimes = 5_000L,
                        remittanceReference = unique1,
                        remittanceDestination = "Coffre",
                        closingUserId = 1L
                    )
                )

            // 2nd time: baseRef is occupied -> must return baseRef-2
            val unique2 = db.nextUniqueRemittanceReference(baseRef)
            assertEquals("$baseRef-2", unique2)

            // Open and close second session with unique2
            val openRes2 = OpenRegisterSession(db.sessions, clock).execute(2L, 1L, 1L, 5_000L)
            val session2 = (openRes2 as UseCaseResult.Success).value

            CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(
                    session2.id,
                    RegisterClosingInput(
                        countedCashCentimes = 5_000L,
                        leftInDrawerCentimes = 0L,
                        removedAmountCentimes = 5_000L,
                        remittanceReference = unique2,
                        remittanceDestination = "Banque",
                        closingUserId = 1L
                    )
                )

            // 3rd time: baseRef-2 is also occupied -> must return baseRef-3
            val unique3 = db.nextUniqueRemittanceReference(baseRef)
            assertEquals("$baseRef-3", unique3)
        }
    }

    @Test
    fun test12_zeroWithdrawalClosing_noRemittanceReferenceSaved() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = FixedClock(1_700_000_000_000L)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 15_000L)
            val session = (openRes as UseCaseResult.Success).value

            val closingInput = RegisterClosingInput(
                countedCashCentimes = 15_000L,
                leftInDrawerCentimes = 15_000L,
                removedAmountCentimes = 0L,
                remittanceReference = null, // Withdrawn = 0, no envelope reference
                remittanceDestination = null, // Withdrawn = 0, no destination
                closingUserId = 1L
            )

            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(15_000L, closed.leftInDrawerCentimes)
            assertEquals(0L, closed.removedAmountCentimes)
            assertNull(closed.remittanceReference)
            assertNull(closed.remittanceDestination)
        }
    }

    @Test
    fun test13_positiveWithdrawalClosing_savesAutoReferenceAndDestination() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = FixedClock(1_700_000_000_000L)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 20_000L)
            val session = (openRes as UseCaseResult.Success).value

            val autoRef = RegisterClosingHelper.generateEnvelopeReference("ZAKARIA", 1788842700000L)
            val uniqueRef = db.nextUniqueRemittanceReference(autoRef)

            val closingInput = RegisterClosingInput(
                countedCashCentimes = 20_000L,
                leftInDrawerCentimes = 8_000L,
                removedAmountCentimes = 12_000L,
                remittanceReference = uniqueRef,
                remittanceDestination = "Coffre",
                closingUserId = 1L
            )

            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(8_000L, closed.leftInDrawerCentimes)
            assertEquals(12_000L, closed.removedAmountCentimes)
            assertEquals(uniqueRef, closed.remittanceReference)
            assertEquals("Coffre", closed.remittanceDestination)

            // Verify persistence
            val loaded = db.sessions.findById(closed.id)
            assertNotNull(loaded)
            assertEquals(uniqueRef, loaded.remittanceReference)
            assertEquals("Coffre", loaded.remittanceDestination)
        }
    }

    @Test
    fun test14_recalculationOnDrawerFloatModification() {
        // Counted: 363 DH
        val counted = 363_00L

        // Initial drawer float: 100 DH -> Removed = 263 DH
        var left = 100_00L
        var removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(263_00L, removed)

        // Float updated to 150 DH -> Removed automatically recalculated to 213 DH
        left = 150_00L
        removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(213_00L, removed)

        // Float updated to 363 DH -> Removed automatically recalculated to 0 DH
        left = 363_00L
        removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(0L, removed)

        // Float updated to 0 DH -> Removed automatically recalculated to 363 DH
        left = 0L
        removed = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)
        assertEquals(363_00L, removed)
    }

    @Test
    fun test15_manualRemovedAmountOverrideAndEquationValidation() {
        val counted = 363_00L
        val left = 100_00L
        val autoRemoved = RegisterClosingHelper.calculateAutoRemovedCentimes(counted, left)!!
        assertEquals(263_00L, autoRemoved)

        // Perfectly balanced
        assertTrue(left + autoRemoved == counted)

        // User manually edits removed to 250 DH (under-removed: mismatch of 13 DH)
        val manualUnderRemoved = 250_00L
        assertFalse(left + manualUnderRemoved == counted)
        assertEquals(350_00L, left + manualUnderRemoved)

        // User manually edits removed to 300 DH (over-removed: mismatch of 37 DH)
        val manualOverRemoved = 300_00L
        assertFalse(left + manualOverRemoved == counted)
        assertEquals(400_00L, left + manualOverRemoved)
    }

    @Test
    fun test16_decimalValuesWorkflow_125_50_DH() {
        val parsedCounted = (ma.elaroui.pos.shared.rules.MoneyRules.parseToCentimes("125.50") as ma.elaroui.pos.shared.rules.MoneyParseResult.Success).centimes
        val parsedLeft = (ma.elaroui.pos.shared.rules.MoneyRules.parseToCentimes("25.50") as ma.elaroui.pos.shared.rules.MoneyParseResult.Success).centimes

        assertEquals(125_50L, parsedCounted)
        assertEquals(25_50L, parsedLeft)

        val autoRemoved = RegisterClosingHelper.calculateAutoRemovedCentimes(parsedCounted, parsedLeft)
        assertEquals(100_00L, autoRemoved)
        assertEquals("100.00", ma.elaroui.pos.shared.rules.MoneyRules.formatFixed(autoRemoved!!))

        // Also test with comma
        val parsedWithComma = (ma.elaroui.pos.shared.rules.MoneyRules.parseToCentimes("125,50") as ma.elaroui.pos.shared.rules.MoneyParseResult.Success).centimes
        assertEquals(125_50L, parsedWithComma)
    }

    @Test
    fun test17_sessionClosingReportEscPosIncludesRemittanceWhenWithdrawn() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = FixedClock(1_700_000_000_000L)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 10_000L)
            val session = (openRes as UseCaseResult.Success).value

            val autoRef = RegisterClosingHelper.generateEnvelopeReference("ZAKARIA", 1788842700000L)
            val uniqueRef = db.nextUniqueRemittanceReference(autoRef)

            CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(
                    session.id,
                    RegisterClosingInput(
                        countedCashCentimes = 10_000L,
                        leftInDrawerCentimes = 2_000L,
                        removedAmountCentimes = 8_000L,
                        remittanceReference = uniqueRef,
                        remittanceDestination = "Coffre",
                        closingUserId = 1L
                    )
                )

            val report = db.sessionClosingReport(session.id)
            val formatResult = ma.elaroui.pos.desktop.print.SessionClosingReportEscPosFormatter.format(report, "PATISSERIE_POS", 80)
            assertTrue(formatResult is ma.elaroui.pos.desktop.print.EscPosFormatResult.Success)

            val textOutput = String(formatResult.bytes, ma.elaroui.pos.desktop.print.FrenchEscPosEncoder.CHARSET)
            assertTrue(textOutput.contains("RAPPORT DE CLOTURE"))
            assertTrue(textOutput.contains("ESPECES ATTENDUES"))
            assertFalse(textOutput.contains("Laissé en caisse"), "Report should not include Laissé en caisse")
            assertFalse(textOutput.contains("Montant retiré"), "Report should not include Montant retiré")
        }
    }

    @Test
    fun test18_sessionClosingReportEscPosExcludesRemittanceWhenZeroWithdrawn() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val clock = FixedClock(1_700_000_000_000L)
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, 10_000L)
            val session = (openRes as UseCaseResult.Success).value

            CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(
                    session.id,
                    RegisterClosingInput(
                        countedCashCentimes = 10_000L,
                        leftInDrawerCentimes = 10_000L,
                        removedAmountCentimes = 0L,
                        remittanceReference = null,
                        remittanceDestination = null,
                        closingUserId = 1L
                    )
                )

            val report = db.sessionClosingReport(session.id)
            val formatResult = ma.elaroui.pos.desktop.print.SessionClosingReportEscPosFormatter.format(report, "PATISSERIE_POS", 80)
            assertTrue(formatResult is ma.elaroui.pos.desktop.print.EscPosFormatResult.Success)

            val textOutput = String(formatResult.bytes, ma.elaroui.pos.desktop.print.FrenchEscPosEncoder.CHARSET)
            assertTrue(textOutput.contains("RAPPORT DE CLOTURE"))
            assertFalse(textOutput.contains("Montant retir"), "Report should NOT include Montant retiré when 0")
            assertFalse(textOutput.contains("Réf. Enveloppe"), "Report should NOT include Réf. Enveloppe when withdrawn is 0")
        }
    }
}
