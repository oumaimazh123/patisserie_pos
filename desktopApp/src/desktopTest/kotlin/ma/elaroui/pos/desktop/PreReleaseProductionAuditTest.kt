package ma.elaroui.pos.desktop

import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.backup.DesktopBackupService
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.platform.DesktopSingleInstanceLock
import ma.elaroui.pos.desktop.platform.LinuxAutostartInstaller
import ma.elaroui.pos.desktop.platform.LinuxTouchInputCompatibility
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.MoneyParseError
import ma.elaroui.pos.shared.rules.MoneyParseResult
import ma.elaroui.pos.shared.rules.MoneyRules
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

class PreReleaseProductionAuditTest {

    @Test
    fun testLinuxAutostartInstallerCreatesCorrectDesktopEntry() {
        val tempDir = Files.createTempDirectory("autostart-test")
        try {
            val installer = LinuxAutostartInstaller(tempDir, "/opt/patisserie-pos/bin/PATISSERIE_POS")
            val desktopFile = installer.ensureInstalled()
            assertTrue(Files.isRegularFile(desktopFile))
            val content = Files.readString(desktopFile)
            assertTrue(content.contains("[Desktop Entry]"))
            assertTrue(content.contains("Exec=\"/opt/patisserie-pos/bin/PATISSERIE_POS\""))
            assertTrue(content.contains("StartupWMClass=PATISSERIE_POS"))
            assertTrue(content.contains("Type=Application"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testSingleInstanceLockPreventsDuplicateProcess() {
        val tempDir = Files.createTempDirectory("lock-test")
        try {
            val lockFile = tempDir.resolve("pos.lock")
            val lock1 = DesktopSingleInstanceLock.acquire(lockFile)
            assertNotNull(lock1, "First instance must acquire the lock successfully")

            val lock2 = DesktopSingleInstanceLock.acquire(lockFile)
            assertNull(lock2, "Second instance must be rejected by lock file")

            lock1.close()
            val lock3 = DesktopSingleInstanceLock.acquire(lockFile)
            assertNotNull(lock3, "Lock must be acquirable after first instance releases it")
            lock3.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testLinuxTouchInputEventBridgingLogic() {
        // Touch screens without primary button ID
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.NOBUTTON))
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_RELEASED, MouseEvent.NOBUTTON))
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_CLICKED, MouseEvent.NOBUTTON))

        // Regular mouse click already has BUTTON1, does NOT need bridging
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1))
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1))
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_CLICKED, MouseEvent.BUTTON1))

        // Mouse motion does not need click bridging
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON))
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON))
    }

    @Test
    fun testBackupAndRestoreCycle() = runBlocking {
        val tempDir = Files.createTempDirectory("backup-service-test")
        try {
            val paths = DesktopApplicationPathsProvider(
                platform = DesktopPlatform.LINUX,
                userHome = tempDir.toString(),
                temporaryDirectory = tempDir.toString()
            ).paths(createDirectories = true)

            val db = WindowsPosDatabase.open(paths.database)
            db.configureInitialSetup("Store Alpha", "Manager", "1234")
            db.settings.put(AppSetting("establishment_name", "Store Alpha"))
            db.settings.put(AppSetting("seller_ice", "009988776655443"))

            val backupService = DesktopBackupService(db, paths, now = { Instant.ofEpochMilli(1_700_000_000_000L) })

            val backupTarget = paths.backups.resolve("pos-backup.zip")
            backupService.createBackup(backupTarget)

            assertTrue(Files.isRegularFile(backupTarget))
            val validation = backupService.validate(backupTarget)
            assertTrue(validation.valid, "Backup archive must be valid: ${validation.message}")

            // Stage restore
            backupService.stageRestore(backupTarget)
            db.close()

            // Verify restored db
            val restoredDb = WindowsPosDatabase.open(paths.database)
            assertEquals("Store Alpha", restoredDb.settings.get("establishment_name"))
            assertEquals("009988776655443", restoredDb.settings.get("seller_ice"))
            restoredDb.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun testEscPosBinaryTicketFormatting80mmAnd58mm() {
        val formatter = EscPosFormatterFactory.create()
        val order = Order(
            id = 501L,
            number = "CMD-501",
            type = OrderType.COUNTER,
            status = OrderStatus.COMPLETED,
            lines = listOf(
                OrderLine(1L, "Cafe Espresso", 1200L, 2, 1000),
                OrderLine(2L, "Eau Minerale 50cl", 600L, 1, 1000)
            ),
            subtotalCentimes = 3000L,
            discountCentimes = 150L,
            taxCentimes = 259L,
            totalCentimes = 2850L,
            tableId = null,
            registerSessionId = 1L,
            cashierId = 1L
        )
        val company = ReceiptCompany(
            name = "Cafe Central",
            address = "Avenue Hassan II",
            phone = "0537000000",
            ice = "001122334455667",
            printEstablishmentName = true
        )

        // 80mm format
        val request80 = EscPosPrintRequest(
            order = order,
            company = company,
            kind = TicketKind.CUSTOMER,
            paperWidth = 80,
            isReprint = false,
            paymentMethod = PaymentMethod.CASH,
            receivedCentimes = 3000L,
            changeCentimes = 150L,
            cashierName = "Sara"
        )
        val result80 = formatter.format(request80)
        assertTrue(result80 is EscPosFormatResult.Success, "80mm formatting failed: ${(result80 as? EscPosFormatResult.Failure)?.message}")
        val bytes80 = (result80 as EscPosFormatResult.Success).bytes
        assertTrue(bytes80.isNotEmpty())
        assertEquals(0x1B.toByte(), bytes80[0])
        assertEquals(0x40.toByte(), bytes80[1])

        // 58mm format
        val request58 = EscPosPrintRequest(
            order = order,
            company = company,
            kind = TicketKind.CUSTOMER,
            paperWidth = 58,
            isReprint = false,
            paymentMethod = PaymentMethod.CARD,
            cashierName = "Sara"
        )
        val result58 = formatter.format(request58)
        assertTrue(result58 is EscPosFormatResult.Success, "58mm formatting failed: ${(result58 as? EscPosFormatResult.Failure)?.message}")
        val bytes58 = (result58 as EscPosFormatResult.Success).bytes
        assertTrue(bytes58.isNotEmpty())
    }

    @Test
    fun testMoneyFormattingAndParsingSafety() {
        assertEquals("0.00", MoneyRules.formatFixed(0L))
        assertEquals("12.50", MoneyRules.formatFixed(1250L))
        assertEquals("100.00", MoneyRules.formatFixed(10000L))
        assertEquals("9999.99", MoneyRules.formatFixed(999999L))

        val parse1 = MoneyRules.parseToCentimes("25.50")
        assertTrue(parse1 is MoneyParseResult.Success)
        assertEquals(2550L, parse1.centimes)

        val parse2 = MoneyRules.parseToCentimes("100")
        assertTrue(parse2 is MoneyParseResult.Success)
        assertEquals(10000L, parse2.centimes)

        val parseEmpty = MoneyRules.parseToCentimes("")
        assertTrue(parseEmpty is MoneyParseResult.Failure)
        assertEquals(MoneyParseError.EMPTY, parseEmpty.error)
    }
}
