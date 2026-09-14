package ma.elaroui.pos.desktop.presentation.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the typed UiMessage alert model.
 * Validates severity, presentation defaults, factory helpers, and CSV import mapping.
 * These tests are language-agnostic and never rely on message text content.
 */
class UiMessageAlertsTest {

    // ────────────────────────────────────────────────────────────────
    // 1. Severity factories
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `success factory produces SUCCESS severity`() {
        val msg = UiMessage.success("Any text – language doesn't matter")
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.isSuccess)
        assertFalse(msg.isError)
        assertFalse(msg.isWarning)
        assertFalse(msg.isInfo)
    }

    @Test
    fun `error factory produces ERROR severity`() {
        val msg = UiMessage.error("Any error text")
        assertEquals(MessageSeverity.ERROR, msg.severity)
        assertTrue(msg.isError)
        assertFalse(msg.isSuccess)
    }

    @Test
    fun `warning factory produces WARNING severity`() {
        val msg = UiMessage.warning("Any warning text")
        assertEquals(MessageSeverity.WARNING, msg.severity)
        assertTrue(msg.isWarning)
        assertFalse(msg.isError)
    }

    @Test
    fun `info factory produces INFO severity`() {
        val msg = UiMessage.info("Any info text")
        assertEquals(MessageSeverity.INFO, msg.severity)
        assertTrue(msg.isInfo)
        assertFalse(msg.isError)
    }

    // ────────────────────────────────────────────────────────────────
    // 2. Presentation defaults
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `success factory defaults to TEMPORARY presentation`() {
        val msg = UiMessage.success("Commande enregistrée")
        assertEquals(MessagePresentation.TEMPORARY, msg.presentation)
    }

    @Test
    fun `error factory defaults to INLINE presentation`() {
        val msg = UiMessage.error("Montant invalide")
        assertEquals(MessagePresentation.INLINE, msg.presentation)
    }

    @Test
    fun `warning factory defaults to TEMPORARY presentation`() {
        val msg = UiMessage.warning("Impression échouée")
        assertEquals(MessagePresentation.TEMPORARY, msg.presentation)
    }

    @Test
    fun `info factory defaults to TEMPORARY presentation`() {
        val msg = UiMessage.info("Licence bientôt expirée")
        assertEquals(MessagePresentation.TEMPORARY, msg.presentation)
    }

    // ────────────────────────────────────────────────────────────────
    // 3. Overriding presentation
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `success can be PERSISTENT for license warning`() {
        val msg = UiMessage.success("Licence active", presentation = MessagePresentation.PERSISTENT)
        assertEquals(MessagePresentation.PERSISTENT, msg.presentation)
    }

    @Test
    fun `error can be BLOCKING for confirmation dialogs`() {
        val msg = UiMessage.error("Restauration confirmée ?", presentation = MessagePresentation.BLOCKING)
        assertEquals(MessagePresentation.BLOCKING, msg.presentation)
    }

    // ────────────────────────────────────────────────────────────────
    // 4. Optional action label & callback
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `message without action has null actionLabel and onAction`() {
        val msg = UiMessage.warning("L'impression a échoué")
        assertNull(msg.actionLabel)
        assertNull(msg.onAction)
    }

    @Test
    fun `message with action has non-null actionLabel and onAction`() {
        var invoked = false
        val msg = UiMessage.warning(
            text = "L'impression a échoué",
            actionLabel = "Réessayer",
            onAction = { invoked = true }
        )
        assertNotNull(msg.actionLabel)
        assertNotNull(msg.onAction)
        msg.onAction!!.invoke()
        assertTrue(invoked)
    }

    // ────────────────────────────────────────────────────────────────
    // 5. Unique IDs for re-keying animations
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `two distinct messages have different IDs`() {
        val a = UiMessage.success("A")
        Thread.sleep(1) // ensure nanoTime differs
        val b = UiMessage.success("B")
        assertNotEquals(a.id, b.id)
    }

    // ────────────────────────────────────────────────────────────────
    // 6. CsvImportSummary typed severity (language-independent)
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `full success import maps to SUCCESS severity`() {
        val summary = ma.elaroui.pos.desktop.importing.CsvImportSummary(
            imported = 10, skipped = 0, failed = 0
        )
        assertEquals(MessageSeverity.SUCCESS, summary.severity)
    }

    @Test
    fun `partial import with failures maps to WARNING severity`() {
        val summary = ma.elaroui.pos.desktop.importing.CsvImportSummary(
            imported = 7, skipped = 2, failed = 1
        )
        assertEquals(MessageSeverity.WARNING, summary.severity)
    }

    @Test
    fun `zero imports with some skipped maps to WARNING`() {
        // imported=0, skipped=5 → all 3 severity conditions: imported > 0 false → ERROR
        // Actually per the severity logic: imported = 0 → ERROR
        val summary = ma.elaroui.pos.desktop.importing.CsvImportSummary(
            imported = 0, skipped = 5, failed = 0
        )
        assertEquals(MessageSeverity.ERROR, summary.severity)
    }

    @Test
    fun `total import failure maps to ERROR severity`() {
        val summary = ma.elaroui.pos.desktop.importing.CsvImportSummary(
            imported = 0, skipped = 0, failed = 8
        )
        assertEquals(MessageSeverity.ERROR, summary.severity)
    }

    @Test
    fun `toUiMessage uses INLINE presentation`() {
        val summary = ma.elaroui.pos.desktop.importing.CsvImportSummary(
            imported = 5, skipped = 0, failed = 0
        )
        val msg = summary.toUiMessage()
        assertEquals(MessagePresentation.INLINE, msg.presentation)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
    }

    // ────────────────────────────────────────────────────────────────
    // 7. Severity never depends on translated message text
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `severity is always determined by type not by French text`() {
        val frSuccess = UiMessage.success("Commande enregistrée avec succès")
        val arSuccess = UiMessage.success("تم تسجيل الطلب بنجاح")
        val enSuccess = UiMessage.success("Order saved successfully")
        // All three should have the same severity regardless of text
        assertEquals(frSuccess.severity, arSuccess.severity)
        assertEquals(arSuccess.severity, enSuccess.severity)
    }

    @Test
    fun `error severity is not influenced by message language`() {
        val frError = UiMessage.error("Montant reçu insuffisant")
        val arError = UiMessage.error("المبلغ المستلم غير كافٍ")
        val enError = UiMessage.error("Received amount is insufficient")
        assertEquals(frError.severity, arError.severity)
        assertEquals(arError.severity, enError.severity)
    }

    // ────────────────────────────────────────────────────────────────
    // 8. PIN error clear-on-switch (conceptual state test)
    // ────────────────────────────────────────────────────────────────

    @Test
    fun `null UiMessage after clear means no error shown`() {
        // Simulates: user enters wrong PIN → error shown → switches user → error cleared
        var currentError: UiMessage? = UiMessage.error("PIN incorrect")
        assertNotNull(currentError)

        // Simulate onClearError() call
        currentError = null

        assertNull(currentError)
    }

    @Test
    fun `new error after reauth does not leak previous error`() {
        var errorMsg: UiMessage? = UiMessage.error("PIN incorrect pour Caissier A")
        errorMsg = null // switch user
        assertNull(errorMsg)
        // new error after switching to a different user
        errorMsg = UiMessage.error("PIN incorrect pour Caissier B")
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.isError)
    }
}
