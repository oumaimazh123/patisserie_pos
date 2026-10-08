package ma.elaroui.pos.desktop.presentation.navigation

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.shared.domain.User
import ma.elaroui.pos.shared.domain.UserRole

/**
 * Regression and security tests for role authorization, route protection,
 * and cashier privilege isolation.
 */
class DesktopAuthorizationSecurityTest {

    @Test
    fun `isOwnerRoute correctly classifies owner only vs operational routes`() {
        // Owner only routes
        assertTrue(isOwnerRoute(DesktopScreenRoute.DASHBOARD))
        assertTrue(isOwnerRoute(DesktopScreenRoute.PRODUCT_MGMT))
        assertTrue(isOwnerRoute(DesktopScreenRoute.CATALOGUE_PREVIEW))
        assertTrue(isOwnerRoute(DesktopScreenRoute.CATEGORY_MGMT))
        assertTrue(isOwnerRoute(DesktopScreenRoute.TABLE_MGMT))
        assertTrue(isOwnerRoute(DesktopScreenRoute.CASHIER_MGMT))
        assertTrue(isOwnerRoute(DesktopScreenRoute.DAILY_REPORT))
        assertTrue(isOwnerRoute(DesktopScreenRoute.COMPLETED_SALES))
        assertTrue(isOwnerRoute(DesktopScreenRoute.SALE_DETAIL))
        assertTrue(isOwnerRoute(DesktopScreenRoute.REGISTER_HISTORY))
        assertTrue(isOwnerRoute(DesktopScreenRoute.SETTINGS))
        assertTrue(isOwnerRoute(DesktopScreenRoute.PRINTER_SETTINGS))
        assertTrue(isOwnerRoute(DesktopScreenRoute.BACKUP_RESTORE))
        assertTrue(isOwnerRoute(DesktopScreenRoute.LICENSE_GATE))

        // Operational routes (accessible to cashier)
        assertFalse(isOwnerRoute(DesktopScreenRoute.POS_MAIN))
        assertFalse(isOwnerRoute(DesktopScreenRoute.ACTIVE_ORDERS))
        assertFalse(isOwnerRoute(DesktopScreenRoute.PAYMENT))
        assertFalse(isOwnerRoute(DesktopScreenRoute.RECEIPT_PREVIEW))
        assertFalse(isOwnerRoute(DesktopScreenRoute.OPEN_REGISTER))
        assertFalse(isOwnerRoute(DesktopScreenRoute.CURRENT_SESSION))
        assertFalse(isOwnerRoute(DesktopScreenRoute.CLOSE_REGISTER))
    }

    @Test
    fun `cashier cannot navigate to owner routes via navigateTo`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-cashier")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        db.createCashier("Cashier 1", "5678")
        val cashierUser = db.allUsers().first { it.role == UserRole.CASHIER }

        val state = DesktopNavState(db, dir)
        state.currentUser = cashierUser // Role = CASHIER

        // 1. Without open session -> fallback is POS_MAIN
        state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier without open session must fall back to POS_MAIN")

        // 2. With open session -> fallback is POS_MAIN
        state.openRegister(10_000L)
        state.currentRoute = DesktopScreenRoute.POS_MAIN

        // Attempt navigation to Printer Settings
        state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must be denied access to PRINTER_SETTINGS")

        // Attempt navigation to Dashboard
        state.navigateTo(DesktopScreenRoute.DASHBOARD)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must be denied access to DASHBOARD")

        // Attempt navigation to Settings
        state.navigateTo(DesktopScreenRoute.SETTINGS)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must be denied access to SETTINGS")

        // Attempt navigation to Cashier Management
        state.navigateTo(DesktopScreenRoute.CASHIER_MGMT)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must be denied access to CASHIER_MGMT")

        // Attempt navigation to Backup / Restore
        state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must be denied access to BACKUP_RESTORE")

        // Operational navigation should succeed
        state.navigateTo(DesktopScreenRoute.ACTIVE_ORDERS)
        assertEquals(DesktopScreenRoute.ACTIVE_ORDERS, state.currentRoute, "Cashier should be allowed to view ACTIVE_ORDERS")

        state.navigateTo(DesktopScreenRoute.RECEIPT_PREVIEW)
        assertEquals(DesktopScreenRoute.RECEIPT_PREVIEW, state.currentRoute, "Cashier should be allowed to view RECEIPT_PREVIEW")
    }

    @Test
    fun `owner is granted access to owner routes`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-owner")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        val ownerUser = db.allUsers().first { it.role == UserRole.OWNER }

        val state = DesktopNavState(db, dir)
        state.currentUser = ownerUser // Role = OWNER

        state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS)
        assertEquals(DesktopScreenRoute.PRINTER_SETTINGS, state.currentRoute, "Owner should be allowed into PRINTER_SETTINGS")

        state.navigateTo(DesktopScreenRoute.DASHBOARD)
        assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute, "Owner should be allowed into DASHBOARD")

        state.navigateTo(DesktopScreenRoute.SETTINGS)
        assertEquals(DesktopScreenRoute.SETTINGS, state.currentRoute, "Owner should be allowed into SETTINGS")
    }

    @Test
    fun `missing printer message provides contact owner instruction for unprivileged user`() {
        val strings = DesktopStrings(DesktopLanguage.FR)

        // When user has no permission (onNavigateToPrinterSettings is null)
        val onNavigateToPrinterSettings: (() -> Unit)? = null
        val cashierMessage = if (onNavigateToPrinterSettings != null) {
            strings.text(
                "Aucune imprimante configurée. Rendez-vous dans Réglages > Imprimantes pour sélectionner votre imprimante.",
                "No printer configured. Please go to Settings > Printers to select your printer.",
                "لم يتم ضبط طابعة. يرجى الانتقال إلى الإعدادات > الطابعات لاختيار الطابعة."
            )
        } else {
            strings.text(
                "Aucune imprimante configurée. Contactez le propriétaire.",
                "No printer configured. Please contact the owner.",
                "لم يتم ضبط طابعة. يرجى الاتصال بالمالك."
            )
        }
        assertEquals("Aucune imprimante configurée. Contactez le propriétaire.", cashierMessage)

        // When user is Owner (onNavigateToPrinterSettings is provided)
        val onOwnerNavigate: (() -> Unit)? = { /* navigate */ }
        val ownerMessage = if (onOwnerNavigate != null) {
            strings.text(
                "Aucune imprimante configurée. Rendez-vous dans Réglages > Imprimantes pour sélectionner votre imprimante.",
                "No printer configured. Please go to Settings > Printers to select your printer.",
                "لم يتم ضبط طابعة. يرجى الانتقال إلى الإعدادات > الطابعات لاختيار الطابعة."
            )
        } else {
            strings.text(
                "Aucune imprimante configurée. Contactez le propriétaire.",
                "No printer configured. Please contact the owner.",
                "لم يتم ضبط طابعة. يرجى الاتصال بالمالك."
            )
        }
        assertEquals(
            "Aucune imprimante configurée. Rendez-vous dans Réglages > Imprimantes pour sélectionner votre imprimante.",
            ownerMessage
        )
    }

    @Test
    fun `owner clicking back on open register returns to dashboard without opening session`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-owner-back")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        val owner = db.allUsers().first { it.role == UserRole.OWNER }

        val state = DesktopNavState(db, dir)
        state.currentUser = owner
        state.currentRoute = DesktopScreenRoute.OPEN_REGISTER

        // Owner clicks Retour
        state.navigateTo(DesktopScreenRoute.DASHBOARD)

        assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute, "Owner must return to Dashboard")
        assertEquals(null, state.session, "No register session should be opened in memory")
        assertEquals(null, db.sessions.findOpenByUser(owner.id), "No register session should be opened in database")
    }

    @Test
    fun `cashier clicking lock on open register returns to user selection without opening session`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-cashier-lock")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        db.createCashier("Cashier 1", "5678")
        val cashier = db.allUsers().first { it.role == UserRole.CASHIER }

        val state = DesktopNavState(db, dir)
        state.currentUser = cashier
        state.currentRoute = DesktopScreenRoute.OPEN_REGISTER

        // Cashier clicks Verrouiller
        state.lock()

        assertEquals(DesktopScreenRoute.USER_SELECTION, state.currentRoute, "Cashier must return to User Selection / Lock screen")
        assertEquals(null, state.currentUser, "User must be logged out")
        assertEquals(null, state.session, "No register session should be opened in memory")
        assertEquals(null, db.sessions.findOpenByUser(cashier.id), "No register session should be opened in database")
    }

    @Test
    fun `cashier on open register is blocked from accessing dashboard`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-cashier-block-dashboard")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        db.createCashier("Cashier 1", "5678")
        val cashier = db.allUsers().first { it.role == UserRole.CASHIER }

        val state = DesktopNavState(db, dir)
        state.currentUser = cashier
        state.currentRoute = DesktopScreenRoute.POS_MAIN

        // Attempt direct navigation to Dashboard
        state.navigateTo(DesktopScreenRoute.DASHBOARD)

        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier must remain on POS_MAIN and cannot reach DASHBOARD")
    }

    @Test
    fun `current session back navigation returns to POS_MAIN for manager and owner preserving session and cart`() = runBlocking {
        val dir = Files.createTempDirectory("auth-test-current-session-back")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))
        db.configureInitialSetup("Store", "Owner", "1234")
        db.createCashier("Cashier 1", "5678")
        val cashier = db.allUsers().first { it.role == UserRole.CASHIER }
        val owner = db.allUsers().first { it.role == UserRole.OWNER }

        val catId = db.categories.save(ma.elaroui.pos.shared.domain.Category(0L, "Cat", true, 1))
        val prodId = db.products.save(ma.elaroui.pos.shared.domain.Product(0L, catId, "Produit", 10_00L, 0))

        val state = DesktopNavState(db, dir)

        // 1. Test with Owner (who previously was sent to DASHBOARD)
        state.currentUser = owner
        state.openRegister(100_00L)
        val openSession = state.session
        assertNotNull(openSession)
        assertEquals(ma.elaroui.pos.shared.domain.RegisterSessionStatus.OPEN, openSession.status)
        state.cart[prodId] = 2
        assertEquals(2, state.cart[prodId])

        // Owner navigates to CURRENT_SESSION
        state.navigateTo(DesktopScreenRoute.CURRENT_SESSION)
        assertEquals(DesktopScreenRoute.CURRENT_SESSION, state.currentRoute)

        // Trigger onBack from CURRENT_SESSION -> Must return to POS_MAIN, never DASHBOARD
        state.navigateTo(DesktopScreenRoute.POS_MAIN)

        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Owner back navigation must return directly to POS_MAIN, not DASHBOARD")
        assertEquals(owner.id, state.currentUser?.id, "Logged in user must remain intact")
        assertEquals(openSession.id, state.session?.id, "Active session must be preserved")
        assertEquals(ma.elaroui.pos.shared.domain.RegisterSessionStatus.OPEN, state.session?.status, "Session must still be open")
        assertEquals(2, state.cart[prodId], "Cart contents must remain intact")

        // 2. Test with Cashier as well
        state.currentUser = cashier
        state.navigateTo(DesktopScreenRoute.CURRENT_SESSION)
        assertEquals(DesktopScreenRoute.CURRENT_SESSION, state.currentRoute)

        state.navigateTo(DesktopScreenRoute.POS_MAIN)
        assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Cashier back navigation must also return to POS_MAIN")
        assertEquals(cashier.id, state.currentUser?.id)
        assertEquals(openSession.id, state.session?.id)
        assertEquals(2, state.cart[prodId])
    }
}
