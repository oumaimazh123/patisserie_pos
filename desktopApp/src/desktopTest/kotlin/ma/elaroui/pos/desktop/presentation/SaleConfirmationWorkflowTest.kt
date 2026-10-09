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

            // 3. Sale completed confirmation dialog must be displayed on PAYMENT screen
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute, "Flow must remain on PAYMENT screen after payment")
            assertNotNull(state.completedSaleConfirmation)
            val confirmation = state.completedSaleConfirmation!!
            assertEquals(10_00L, confirmation.order.totalCentimes)
            assertEquals(10_00L, confirmation.changeCentimes)

            // 4. Cashier clicks 'Imprimer le ticket' directly from popup
            val printsBefore = printer.rawPrintCount
            val printResult = state.printAndFinishSale(confirmation)
            assertTrue(printResult.success, "Direct print must report success")
            assertEquals(printsBefore + 1, printer.rawPrintCount, "Printer service must receive exactly one print job")
            assertEquals(PrinterRole.CASHIER_RECEIPT, printer.lastRole)

            // 5. Verify UI state: returned to POS_MAIN without opening RECEIPT_PREVIEW
            assertNull(state.completedSaleConfirmation, "Confirmation popup must be closed")
            assertNull(state.pendingOrder, "Pending order must be cleared")
            assertTrue(state.cart.isEmpty(), "Cart must remain empty for next sale")
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Route must transition to POS_MAIN, never RECEIPT_PREVIEW")

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

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute, "Flow must remain on PAYMENT screen after payment")
            assertNotNull(state.completedSaleConfirmation)
            val printsBefore = printer.rawPrintCount

            // Cashier clicks 'Terminer' without printing
            state.dismissCompletedSale()

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
            state.dismissCompletedSale()

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

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute, "Must remain on PAYMENT route")
            val confirm1 = assertNotNull(state.completedSaleConfirmation)
            val initialPrints = printer.rawPrintCount

            // Simulate clicking primary "Imprimer le reçu"
            val printRes = state.printAndFinishSale(confirm1)
            assertTrue(printRes.success, "Primary action Imprimer le reçu must succeed")
            assertEquals(initialPrints + 1, printer.rawPrintCount, "Primary action must trigger thermal print")

            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertTrue(state.cart.isEmpty())

            // Workflow B: Secondary button (Terminer) returns to POS_MAIN without printing
            state.cart[prodId] = 2
            state.createOrder()
            state.pay(PaymentMethod.CARD, 24_00L)

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute, "Must remain on PAYMENT route")
            assertNotNull(state.completedSaleConfirmation)
            val printsBeforeFinish = printer.rawPrintCount

            // Simulate clicking secondary "Terminer"
            state.dismissCompletedSale()

            assertEquals(printsBeforeFinish, printer.rawPrintCount, "Secondary action Terminer must NOT trigger printing")
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertTrue(state.cart.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun test07_duplicatePaymentCall_isSafelyPreventedWhenConfirmationIsAlreadyShown(): Unit = runBlocking {
        val (db, state, _) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Pâtisseries", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Éclair Chocolat", 14_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CASH, 20_00L)

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)

            // Rapid second pay attempt while confirmation is still on screen
            state.pay(PaymentMethod.CASH, 20_00L)

            // Verify only one sale was created in the database
            val sales = db.salesHistory()
            assertEquals(1, sales.size, "Exactly one sale must be created, duplicate payment must be blocked")

            state.dismissCompletedSale()
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
        } finally {
            db.close()
        }
    }

    @Test
    fun test08_categoriesPopupNeverInterferesWithConfirmationOnPaymentScreen(): Unit = runBlocking {
        val (db, state, _) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Viennoiseries", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Pain au Chocolat", 6_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)

            // On payment screen, pay succeeds
            state.pay(PaymentMethod.CASH, 10_00L)

            // 1. Current route remains PAYMENT: POSMainScreen is not active, so category picker popup cannot be open
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)

            // 2. Dismiss confirmation popup
            state.dismissCompletedSale()

            // 3. User navigates to POS_MAIN, and confirmation popup is clean/null
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertFalse(state.autoOpenCategoryPickerOnPosMain, "Category popup must NOT auto-open after returning from completed sale")
        } finally {
            db.close()
        }
    }

    @Test
    fun test09_categoriesPopup_opensAutomaticallyOnNormalEntry_forCashierAndManager(): Unit = runBlocking {
        val (db, state, _) = setupNavEnvironment()
        try {
            // Setup cashier and owner
            db.createCashier("Caissier Test", "5678")
            val cashier = db.allUsers().first { it.role == UserRole.CASHIER }
            val owner = db.allUsers().first { it.role == UserRole.OWNER }

            // 1. Cashier logs in and enters POS normally
            state.login(cashier, "5678")
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertTrue(state.autoOpenCategoryPickerOnPosMain, "Category picker must auto-open on normal Cashier entry")

            // Cashier visits session screen and returns to POS
            state.navigateTo(DesktopScreenRoute.CURRENT_SESSION)
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            assertTrue(state.autoOpenCategoryPickerOnPosMain, "Category picker must auto-open when returning from other screens")

            // 2. Manager logs in
            state.login(owner, "1234")
            assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute)

            // Manager enters POS from Dashboard
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            assertTrue(state.autoOpenCategoryPickerOnPosMain, "Category picker must auto-open on normal Manager entry from Dashboard")

            // Manager visits Settings and returns to POS
            state.navigateTo(DesktopScreenRoute.SETTINGS)
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            assertTrue(state.autoOpenCategoryPickerOnPosMain, "Category picker must auto-open when Manager returns from Settings")
        } finally {
            db.close()
        }
    }

    @Test
    fun test10_categoriesPopup_doesNotOpenAutomaticallyAfterReturningFromCompletedSale(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Gâteaux", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Éclair Café", 15_00L, 1000, active = true))
            state.refresh()

            // Flow 1: Complete sale and click "Imprimer le reçu"
            state.cart[prodId] = 1
            state.createOrder()
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            state.pay(PaymentMethod.CASH, 20_00L)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            val conf1 = assertNotNull(state.completedSaleConfirmation)

            state.printAndFinishSale(conf1)
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertFalse(state.autoOpenCategoryPickerOnPosMain, "Category popup must NOT auto-open after returning from sale via Imprimer le reçu")

            // Flow 2: Subsequent normal navigation to another screen and back to POS restores auto-open
            state.navigateTo(DesktopScreenRoute.DASHBOARD)
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            assertTrue(state.autoOpenCategoryPickerOnPosMain, "Category popup must auto-open on subsequent normal entry")

            // Flow 3: Complete sale and click "Terminer" (without printing)
            state.cart[prodId] = 1
            state.createOrder()
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            state.pay(PaymentMethod.CARD, 15_00L)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)

            state.dismissCompletedSale()
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertFalse(state.autoOpenCategoryPickerOnPosMain, "Category popup must NOT auto-open after returning from sale via Terminer")
        } finally {
            db.close()
        }
    }

    @Test
    fun test11_cleanResetOfTemporaryState_preparesPosForNextCustomer(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Pâtisseries Fines", active = true, displayOrder = 1))
            val p1 = db.products.save(Product(0, catId, "Tarte Fraise", 20_00L, 1000, active = true))
            val p2 = db.products.save(Product(0, catId, "Macaron", 10_00L, 1000, active = true))
            state.refresh()

            // First customer purchase with discounts and table
            state.cart[p1] = 2
            state.itemDiscountsBasisPoints[p1] = 1000 // 10%
            state.discountBasisPoints = 500 // 5% global
            state.tableId = 5L
            state.createOrder()

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            state.pay(PaymentMethod.CASH, 50_00L)
            val conf = assertNotNull(state.completedSaleConfirmation)

            // Click "Imprimer le reçu"
            state.printAndFinishSale(conf)

            // Verify POS is back on POS_MAIN and completely ready for the next customer
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation, "Popup confirmation must be null")
            assertNull(state.pendingOrder, "Pending order must be null")
            assertNull(state.editingOrderId, "Editing order id must be null")
            assertNull(state.tableId, "Table id must be null")
            assertEquals(0, state.discountBasisPoints, "Global discount must be reset to 0")
            assertTrue(state.itemDiscountsBasisPoints.isEmpty(), "Item discounts must be empty")
            assertTrue(state.cart.isEmpty(), "Cart must be empty")
            assertTrue(state.customerDisplayController.state.value is ma.elaroui.pos.shared.display.CustomerDisplayState.Idle, "Customer display must return to idle")

            // Next customer makes a fresh purchase without interference
            state.cart[p2] = 1
            state.createOrder()
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            val nextOrder = assertNotNull(state.pendingOrder)
            assertEquals(10_00L, nextOrder.totalCentimes, "Next customer total must be exactly 10.00 DH without previous discounts or lines")
        } finally {
            db.close()
        }
    }

    @Test
    fun test12_atomicGuardPreventsDuplicatePrintingOnRapidClicks(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Viennoiseries", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Brioche", 8_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CASH, 10_00L)
            val confirmation = assertNotNull(state.completedSaleConfirmation)

            val initialPrints = printer.rawPrintCount

            // First print call
            val res1 = state.printAndFinishSale(confirmation)
            assertTrue(res1.success)

            // Immediate subsequent print call with same confirmation
            val res2 = state.printAndFinishSale(confirmation)
            // Should be suppressed safely and not send extra print job
            assertEquals(initialPrints + 1, printer.rawPrintCount, "Printer must receive exactly one print job despite multiple calls")
        } finally {
            db.close()
        }
    }

    @Test
    fun test13_printerFailureDoesNotAlterSavedSale_allowsLaterReprint(): Unit = runBlocking {
        val (db, state, printer) = setupNavEnvironment(shouldFailPrinter = true)
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            val catId = db.categories.save(Category(0, "Gâteaux", active = true, displayOrder = 1))
            val prodId = db.products.save(Product(0, catId, "Forêt Noire", 25_00L, 1000, active = true))
            state.refresh()

            state.cart[prodId] = 1
            state.createOrder()
            state.pay(PaymentMethod.CARD, 25_00L)
            val conf = assertNotNull(state.completedSaleConfirmation)

            // Printing fails because printer is offline
            val printResult = state.printAndFinishSale(conf)
            assertFalse(printResult.success, "Print result must report failure")

            // But flow still returns to POS_MAIN and keeps sale safely saved
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNull(state.completedSaleConfirmation)
            assertTrue(state.message.isNotBlank(), "Error message must be presented to user")

            val sales = db.salesHistory()
            assertEquals(1, sales.size, "Sale must be safely preserved in SQLite database")
            assertEquals(25_00L, sales[0].order.totalCentimes)

            // When printer is restored online, re-printing the receipt succeeds
            printer.shouldFailPrint = false
            val reprintResult = state.printCompletedSaleReceipt(conf)
            assertTrue(reprintResult.success, "Reprint must succeed once printer is back online")
            assertEquals(1, printer.rawPrintCount, "Printer must receive the receipt print job")
        } finally {
            db.close()
        }
    }
}
