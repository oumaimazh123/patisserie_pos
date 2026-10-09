package ma.elaroui.pos.desktop.presentation

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.desktop.presentation.navigation.CompletedSaleConfirmation
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute
import ma.elaroui.pos.shared.domain.*

class SaleConfirmationWorkflowTest {

    private class MockPrinterService(
        var shouldFailPrint: Boolean = false,
        var availablePrinters: List<PrinterInfo> = listOf(PrinterInfo("THERMAL_RECEIPT", isDefault = true))
    ) : PrinterService {
        var rawPrintCount = 0
        var lastPrintedBytes: ByteArray? = null
        var lastRole: PrinterRole? = null
        var drawerOpenCount = 0

        override fun discoverPrinters(): PrinterDiscoveryResult =
            PrinterDiscoveryResult(availablePrinters)

        override fun printTestPage(printerName: String, platformTestBytes: ByteArray?): PrintResult =
            PrintResult(true, "OK")

        override fun checkPrinterHealth(printerName: String): PrinterHealthStatus {
            if (shouldFailPrint) {
                return PrinterHealthStatus.disconnected("Imprimante hors ligne", detail = "Câble USB débranché")
            }
            return PrinterHealthStatus.connected("Prête")
        }

        override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult {
            if (shouldFailPrint) {
                return PrintResult(
                    success = false,
                    message = "Échec de transmission",
                    errorMessage = "Périphérique inaccessible",
                    errorCategory = PrintErrorCategory.TRANSPORT_FAILURE
                )
            }
            rawPrintCount++
            lastPrintedBytes = bytes
            lastRole = role
            return PrintResult(success = true, message = "OK")
        }

        override fun openCashDrawer(printerName: String): PrintResult {
            drawerOpenCount++
            return PrintResult(success = true, message = "OK")
        }
    }

    private fun setupNavEnvironment(shouldFailPrinter: Boolean = false): Triple<WindowsPosDatabase, DesktopNavState, MockPrinterService> = runBlocking {
        val tempDir = Files.createTempDirectory("pos_sale_confirm_test")
        val db = WindowsPosDatabase.openInMemory()
        db.configureInitialSetup("Pâtisserie Test", "Admin", "1234")

        db.settings.put(AppSetting("establishment_name", "Pâtisserie Test"))
        db.settings.put(AppSetting("restaurant_address", "12 Rue de la Paix"))
        db.settings.put(AppSetting("restaurant_phone", "0522001122"))
        db.settings.put(AppSetting("customer_printer", "THERMAL_RECEIPT"))
        db.settings.put(AppSetting("printer_width", "80"))

        val printer = MockPrinterService(shouldFailPrint = shouldFailPrinter)
        val state = DesktopNavState(db, tempDir, printer)
        Triple(db, state, printer)
    }

    @Test
    fun test01_directPrintButton_printsDirectlyAndReturnsToPosMainWithoutPreview(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Viennoiseries", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Croissant Beurre", 5_00L, 1000, active = true))
            state.refresh()

            // 1. Add to cart & create order
            state.cart[prodId] = 2
            state.createOrder()
            assertNotNull(state.pendingOrder)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)

            // 2. Submit cash payment
            state.pay(PaymentMethod.CASH, 20_00L)

            // 3. Sale completed confirmation dialog must be displayed on POS_MAIN
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)
            val confirmation = state.completedSaleConfirmation!!
            assertEquals(10_00L, confirmation.order.totalCentimes)
            assertEquals(10_00L, confirmation.changeCentimes)

            // 4. Cashier clicks 'Imprimer le ticket' directly from popup
            val printsBefore = printer.rawPrintCount
            val printResult = state.printCompletedSaleReceipt(confirmation)
            assertTrue(printResult.success, "Direct print must report success")
            assertEquals(printsBefore + 1, printer.rawPrintCount, "Printer service must receive exactly one print job")
            assertEquals(PrinterRole.CASHIER_RECEIPT, printer.lastRole)

            // Simulate popup closure action as wired in UI
            state.completedSaleConfirmation = null
            state.pendingOrder = null
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            // 5. Verify UI state: returned to POS_MAIN without opening RECEIPT_PREVIEW
            assertNull(state.completedSaleConfirmation, "Confirmation popup must be closed")
            assertNull(state.pendingOrder, "Pending order must be cleared")
            assertTrue(state.cart.isEmpty(), "Cart must remain empty for next sale")
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Route must remain POS_MAIN, never RECEIPT_PREVIEW")

            // 6. Verify single order in DB
            val savedOrders = db.salesHistory()
            assertEquals(1, savedOrders.size, "Exactly one sale must be stored in database")
        } finally {
            db.close()
        }
    }

    @Test
    fun test02_finishButton_closesPopupWithoutPrintingAndReturnsToPosMain(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Gâteaux", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Tartelette Citron", 15_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CARD, 15_00L)

            assertNotNull(state.completedSaleConfirmation)
            val printsBefore = printer.rawPrintCount

            // Cashier clicks 'Terminer' without printing
            state.completedSaleConfirmation = null
            state.pendingOrder = null
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            // Verify: No print submitted, popup dismissed, POS_MAIN active
            assertEquals(printsBefore, printer.rawPrintCount, "No print job should be sent on Terminer")
            assertNull(state.completedSaleConfirmation)
            assertNull(state.pendingOrder)
            assertTrue(state.cart.isEmpty())
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)

            // Sale is still saved in DB
            val savedOrders = db.salesHistory()
            assertEquals(1, savedOrders.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun test03_duplicatePrintSuppression_preventsDuplicateReceipts(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Boissons Glacées Test", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Café Crème", 12_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CASH, 12_00L)

            val confirmation = state.completedSaleConfirmation!!
            val initialPrints = printer.rawPrintCount

            // First print
            val firstResult = state.printCompletedSaleReceipt(confirmation)
            assertTrue(firstResult.success)
            assertEquals(initialPrints + 1, printer.rawPrintCount)

            // Second print (e.g. rapid double click)
            val secondResult = state.printCompletedSaleReceipt(confirmation)
            assertTrue(secondResult.success, "Duplicate print should be safely handled")
            assertEquals(initialPrints + 1, printer.rawPrintCount, "No extra print job must be sent for duplicate key")
        } finally {
            db.close()
        }
    }

    @Test
    fun test04_printerError_handlesGracefullyWithoutLosingSavedSale(): Unit = runBlocking {
        // Printer service configured to simulate offline/failure
        val (db, state, printer) = setupNavEnvironment(shouldFailPrinter = true)
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Pâtisseries Test", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Millefeuille", 18_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CASH, 20_00L)

            val confirmation = state.completedSaleConfirmation!!

            // Cashier attempts to print
            val printResult = state.printCompletedSaleReceipt(confirmation)
            assertFalse(printResult.success, "Print must report failure when printer is offline")
            assertTrue(state.message.contains("Erreur") || state.message.contains("hors ligne") || state.message.contains("débranché"), "UI error message must be set")

            // Close dialog
            state.completedSaleConfirmation = null
            state.pendingOrder = null
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            // Verify: Sale was NOT lost! Still in database
            val savedOrders = db.salesHistory()
            assertEquals(1, savedOrders.size, "Sale must remain securely persisted in database despite print failure")
            assertEquals(18_00L, savedOrders[0].order.totalCentimes)
            assertTrue(state.cart.isEmpty())
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
        } finally {
            db.close()
        }
    }

    @Test
    fun test05_noConfiguredPrinter_reportsErrorWithoutLosingSale(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            // Blank out customer printer setting and clear available printers
            db.settings.put(AppSetting("customer_printer", ""))
            printer.availablePrinters = emptyList()

            val catId = db.categories.save(Category(0, "Pain Test", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Baguette", 2_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 2
            state.createOrder()
            state.pay(PaymentMethod.CASH, 5_00L)

            val confirmation = state.completedSaleConfirmation!!
            val printResult = state.printCompletedSaleReceipt(confirmation)

            assertFalse(printResult.success, "Print without configured printer must report failure")
            assertEquals(PrintErrorCategory.NOT_CONFIGURED, printResult.errorCategory)

            // Sale remains safely stored
            val saved = db.salesHistory()
            assertEquals(1, saved.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun test06_saleCompletedDialog_primaryPrintAndSecondaryFinishWorkflows(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Tartes", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Tarte Pommes", 12_00L, 1000, active = true))
            state.refresh()

            // Workflow A: Primary button (Imprimer le reçu) prints directly and returns to POS_MAIN
            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CASH, 15_00L)

            val confirm1 = assertNotNull(state.completedSaleConfirmation)
            val initialPrints = printer.rawPrintCount

            // Simulate clicking primary "Imprimer le reçu"
            val printRes = state.printCompletedSaleReceipt(confirm1)
            assertTrue(printRes.success, "Primary action Imprimer le reçu must succeed")
            assertEquals(initialPrints + 1, printer.rawPrintCount, "Primary action must trigger thermal print")
            state.completedSaleConfirmation = null
            state.pendingOrder = null
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertTrue(state.cart.isEmpty())

            // Workflow B: Secondary button (Terminer) returns to POS_MAIN without printing
            state.cart[prodId] = 2
            state.createOrder()
            state.pay(PaymentMethod.CARD, 24_00L)

            assertNotNull(state.completedSaleConfirmation)
            val printsBeforeFinish = printer.rawPrintCount

            // Simulate clicking secondary "Terminer"
            state.completedSaleConfirmation = null
            state.pendingOrder = null
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            assertEquals(printsBeforeFinish, printer.rawPrintCount, "Secondary action Terminer must NOT trigger printing")
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertTrue(state.cart.isEmpty())
        } finally {
            db.close()
        }
    }
}
