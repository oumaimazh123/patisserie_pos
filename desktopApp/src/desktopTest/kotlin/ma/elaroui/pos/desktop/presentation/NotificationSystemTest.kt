package ma.elaroui.pos.desktop.presentation

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.components.PosAlertColors
import ma.elaroui.pos.desktop.presentation.model.MessagePresentation
import ma.elaroui.pos.desktop.presentation.model.MessageSeverity
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.RegisterClosingInput

class NotificationSystemTest {

    private lateinit var tempDir: java.nio.file.Path
    private lateinit var db: WindowsPosDatabase
    private lateinit var navState: DesktopNavState

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("pos_notification_test")
        val dbPath = tempDir.resolve("test_pos.db")
        db = WindowsPosDatabase.open(dbPath)
        db.configureInitialSetup("Test Resto", "Owner", "1234")
        navState = DesktopNavState(db, tempDir)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `UiMessage factory methods set appropriate severity and defaults`() {
        val successMsg = UiMessage.success("Success text")
        assertEquals(MessageSeverity.SUCCESS, successMsg.severity)
        assertEquals(MessagePresentation.TEMPORARY, successMsg.presentation)
        assertEquals("Success text", successMsg.text)

        val errorMsg = UiMessage.error("Error text")
        assertEquals(MessageSeverity.ERROR, errorMsg.severity)
        assertEquals(MessagePresentation.INLINE, errorMsg.presentation)
        assertEquals("Error text", errorMsg.text)

        val warningMsg = UiMessage.warning("Warning text")
        assertEquals(MessageSeverity.WARNING, warningMsg.severity)
        assertEquals(MessagePresentation.TEMPORARY, warningMsg.presentation)
        assertEquals("Warning text", warningMsg.text)

        val infoMsg = UiMessage.info("Info text")
        assertEquals(MessageSeverity.INFO, infoMsg.severity)
        assertEquals(MessagePresentation.TEMPORARY, infoMsg.presentation)
        assertEquals("Info text", infoMsg.text)
    }

    @Test
    fun `PosAlertColors maps severities to distinct visual themes and icons`() {
        val successIcon = PosAlertColors.icon(MessageSeverity.SUCCESS)
        val errorIcon = PosAlertColors.icon(MessageSeverity.ERROR)
        val warningIcon = PosAlertColors.icon(MessageSeverity.WARNING)
        val infoIcon = PosAlertColors.icon(MessageSeverity.INFO)

        assertEquals("✅", successIcon)
        assertEquals("❌", errorIcon)
        assertEquals("⚠️", warningIcon)
        assertEquals("ℹ️", infoIcon)

        val successBg = PosAlertColors.background(MessageSeverity.SUCCESS)
        val errorBg = PosAlertColors.background(MessageSeverity.ERROR)
        val warningBg = PosAlertColors.background(MessageSeverity.WARNING)
        val infoBg = PosAlertColors.background(MessageSeverity.INFO)

        val successText = PosAlertColors.text(MessageSeverity.SUCCESS)
        val errorText = PosAlertColors.text(MessageSeverity.ERROR)

        assertNotEquals(successBg, errorBg)
        assertNotEquals(successText, errorText)
        assertNotEquals(warningBg, infoBg)
    }

    @Test
    fun `openRegister sets SUCCESS notification with Caisse ouverte`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner

        navState.openRegister(100_00L) // 100.00 DH

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.text.contains("Caisse ouverte"))
        assertEquals(DesktopScreenRoute.POS_MAIN, navState.currentRoute)
    }

    @Test
    fun `saveDraft without open session sets ERROR notification`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.session = null

        navState.saveDraft(proceedToPayment = true)

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.ERROR, msg.severity)
        assertTrue(msg.text.contains("Ouvrez la caisse avant d'enregistrer une vente"))
    }

    @Test
    fun `saveDraft with empty cart sets WARNING notification`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.openRegister(100_00L)
        navState.cart.clear()

        navState.saveDraft(proceedToPayment = true)

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.WARNING, msg.severity)
        assertTrue(msg.text.contains("Le panier est vide"))
    }

    @Test
    fun `holdOrder sets SUCCESS notification with Vente mise en attente`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.openRegister(100_00L)

        val catId = runBlocking { db.categories.save(Category(0, "Boissons", true, 1)) }
        val prodId = runBlocking { db.products.save(Product(0, catId, "Café", 15_00L, 0, available = true, active = true)) }
        navState.refresh()
        navState.cart[prodId] = 2

        navState.holdOrder()

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.text.contains("Vente mise en attente"))
        assertTrue(navState.cart.isEmpty())
    }

    @Test
    fun `pay sets SUCCESS notification with Paiement enregistre avec succes`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.openRegister(100_00L)

        val catId = runBlocking { db.categories.save(Category(0, "Plats", true, 1)) }
        val prodId = runBlocking { db.products.save(Product(0, catId, "Tajine", 50_00L, 0, available = true, active = true)) }
        navState.refresh()
        navState.cart[prodId] = 1

        navState.createOrder()
        assertNotNull(navState.pendingOrder)

        navState.pay(PaymentMethod.CASH, 50_00L)

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.text.contains("enregistrée avec succès") || msg.text.contains("Paiement enregistré avec succès"))
        assertEquals(DesktopScreenRoute.POS_MAIN, navState.currentRoute)
        assertNotNull(navState.completedSaleConfirmation)
    }

    @Test
    fun `movement sets SUCCESS notification with Mouvement enregistre`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.openRegister(100_00L)

        navState.movement(CashMovementType.CASH_IN, 20_00L, "Fond supplémentaire")

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.text.contains("Mouvement enregistré"))
    }

    @Test
    fun `closeRegister sets SUCCESS notification with Caisse fermee avec succes`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.currentUser = owner
        navState.openRegister(100_00L)

        navState.closeRegister(
            RegisterClosingInput(
                countedCashCentimes = 100_00L,
                leftInDrawerCentimes = 50_00L,
                removedAmountCentimes = 50_00L,
                remittanceDestination = "Coffre",
                closingNote = "Clôture normale"
            )
        )

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.SUCCESS, msg.severity)
        assertTrue(msg.text.contains("Caisse fermée avec succès"))
    }

    @Test
    fun `login with wrong pin sets ERROR notification`() {
        val owner = navState.users.first { it.role == UserRole.OWNER }
        navState.login(owner, "9999")

        val msg = navState.uiMessage
        assertNotNull(msg)
        assertEquals(MessageSeverity.ERROR, msg.severity)
        assertEquals(navState.strings.wrongPin, msg.text)
    }

    @Test
    fun `clearMessage resets both uiMessage and legacy message`() {
        navState.showSuccess("Test notification")
        assertNotNull(navState.uiMessage)
        assertEquals("Test notification", navState.message)

        navState.clearMessage()
        assertNull(navState.uiMessage)
        assertEquals("", navState.message)
    }
}
