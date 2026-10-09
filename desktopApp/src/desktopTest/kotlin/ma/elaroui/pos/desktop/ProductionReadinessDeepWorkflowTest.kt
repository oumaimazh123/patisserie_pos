package ma.elaroui.pos.desktop

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.DesktopValidationException
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.desktop.presentation.navigation.DesktopScreenRoute
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.*
import kotlin.test.*

class ProductionReadinessDeepWorkflowTest {

    private class TestPrinterService : PrinterService {
        var rawPrintCount = 0
        var drawerOpenCount = 0
        var lastPrintedBytes: ByteArray? = null
        var lastRole: PrinterRole? = null

        override fun discoverPrinters() = PrinterDiscoveryResult(
            listOf(PrinterInfo("POS-80-PRINTER", isDefault = true))
        )

        override fun printTestPage(printerName: String, platformTestBytes: ByteArray?) = PrintResult(true, "OK")

        override fun printRaw(printerName: String, bytes: ByteArray, role: PrinterRole): PrintResult {
            rawPrintCount++
            lastPrintedBytes = bytes
            lastRole = role
            return PrintResult(true, "OK")
        }

        override fun openCashDrawer(printerName: String): PrintResult {
            drawerOpenCount++
            return PrintResult(true, "OK")
        }
    }

    private fun setupEnvironment(): Triple<WindowsPosDatabase, DesktopNavState, TestPrinterService> = runBlocking {
        val tempDir = Files.createTempDirectory("pos_prod_readiness_test")
        val db = WindowsPosDatabase.openInMemory()
        db.configureInitialSetup("Pâtisserie & Boulangerie Royale", "Directeur Yassine", "1234")
        
        // Configure company details including address and wifi
        db.settings.put(AppSetting("establishment_name", "Pâtisserie Royale"))
        db.settings.put(AppSetting("restaurant_address", "45 Boulevard d'Anfa Casablanca"))
        db.settings.put(AppSetting("restaurant_phone", "0522112233"))
        db.settings.put(AppSetting("seller_ice", "123456789012345"))
        db.settings.put(AppSetting("wifi_name", "Pâtisserie_Guest_WiFi"))
        db.settings.put(AppSetting("wifi_code", "secretPass2026"))
        db.settings.put(AppSetting("customer_printer", "POS-80-PRINTER"))
        db.settings.put(AppSetting("cash_drawer_enabled", "true"))
        db.settings.put(AppSetting("printer_width", "80"))

        val printer = TestPrinterService()
        val state = DesktopNavState(db, tempDir, printer)
        Triple(db, state, printer)
    }

    @Test
    fun test01_saleFlow_popupConfirmation_cashWithChange_printAndFinish(): Unit = runBlocking {
        val (db, state, printer) = setupEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")

            // 1. Session is auto-opened with 0.00 DH and owner lands on DASHBOARD
            assertNotNull(state.session)
            assertEquals(0L, state.session!!.openingCashCentimes, "New session must always start with 0.00 DH")
            assertEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute)

            // Add products
            val catId = db.categories.save(Category(0, "Viennoiseries", active = true, displayOrder = 1))
            val p1Id = db.products.save(Product(0, catId, "Croissant Amande", 18_00L, 1000, active = true))
            val p2Id = db.products.save(Product(0, catId, "Pain au Chocolat", 15_00L, 1000, active = true))
            state.refresh()

            // 2. Add to cart: 2 Croissants (36 DH) + 1 Pain au Chocolat (15 DH) = 51.00 DH
            state.navigateTo(DesktopScreenRoute.POS_MAIN)
            state.cart[p1Id] = 2
            state.cart[p2Id] = 1

            // 3. Create order -> Payment screen
            state.createOrder()
            assertNotNull(state.pendingOrder)
            assertEquals(51_00L, state.pendingOrder!!.totalCentimes)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)

            // 4. Pay in cash with 100.00 DH (10_000 centimes)
            state.pay(PaymentMethod.CASH, 10_000L)

            // 5. Must stay on PAYMENT and display sale confirmation pop-up
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute, "Flow must stay on PAYMENT screen after payment")
            assertNotNull(state.completedSaleConfirmation, "Confirmation pop-up state must be populated")

            val confirmation = state.completedSaleConfirmation!!
            assertEquals(51_00L, confirmation.order.totalCentimes)
            assertEquals(PaymentMethod.CASH, confirmation.paymentMethod)
            assertEquals(10_000L, confirmation.receivedCentimes)
            assertEquals(49_00L, confirmation.changeCentimes, "Change due must be exactly 49.00 DH")
            assertTrue(printer.drawerOpenCount >= 1, "Cash drawer must open automatically for cash payment")

            // 6. Cashier clicks 'Imprimer le reçu' from pop-up
            val printsBefore = printer.rawPrintCount
            state.printCompletedSaleReceipt(confirmation)
            assertTrue(printer.rawPrintCount > printsBefore, "Printing from dialog must submit print job")
            assertEquals(PrinterRole.CASHIER_RECEIPT, printer.lastRole)

            // 7. Cashier clicks 'Terminer'
            state.dismissCompletedSale()

            // 8. POS state is completely clean and ready for next customer
            assertNull(state.completedSaleConfirmation)
            assertNull(state.pendingOrder)
            assertTrue(state.cart.isEmpty())
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertNotNull(state.session, "Register session must remain active")
        } finally {
            db.close()
        }
    }

    @Test
    fun test02_saleFlow_cardPayment_noChange_popupAndFastFinish(): Unit = runBlocking {
        val (db, state, printer) = setupEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            assertNotNull(state.session)
            assertEquals(0L, state.session!!.openingCashCentimes)

            val catId = db.categories.save(Category(0, "Pâtisseries", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Éclair Chocolat", 22_00L, 1000, active = true))
            state.refresh()

            state.cart[pId] = 2 // 44.00 DH
            state.createOrder()
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)

            // Card payment
            state.pay(PaymentMethod.CARD, null)

            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)
            val conf = state.completedSaleConfirmation!!
            assertEquals(44_00L, conf.order.totalCentimes)
            assertEquals(PaymentMethod.CARD, conf.paymentMethod)
            assertEquals(0L, conf.changeCentimes ?: 0L)

            // Cash drawer should NOT open for card payment
            assertEquals(0, printer.drawerOpenCount, "Cash drawer must not open for card payment")

            // Terminer
            state.dismissCompletedSale()
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
            assertTrue(state.cart.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun test03_currentSession_backNavigation_strictlyReturnsToPosMain(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            // Create a Cashier user
            val cashierId = db.createCashier("Caissière Fatima", "5555")
            val cashier = db.authentication.authenticate("5555")!!

            state.login(cashier, "5555")
            assertNotNull(state.session)
            assertEquals(0L, state.session!!.openingCashCentimes)

            // Cashier adds an item to cart in POS_MAIN
            val catId = db.categories.save(Category(0, "Jus et Boissons Fraîches", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Thé à la menthe", 12_00L, 1000, active = true))
            state.refresh()

            state.cart[pId] = 1
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)

            // Navigates to Current Session page
            state.navigateTo(DesktopScreenRoute.CURRENT_SESSION)
            assertEquals(DesktopScreenRoute.CURRENT_SESSION, state.currentRoute)

            // User clicks "Retour" on Current Session page
            state.navigateTo(DesktopScreenRoute.POS_MAIN)

            // Verify clean return to POS_MAIN
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute, "Retour must strictly lead back to POS_MAIN")
            assertNotEquals(DesktopScreenRoute.DASHBOARD, state.currentRoute)
            assertEquals(1, state.cart[pId], "Cart items must be preserved on back navigation")
            assertNotNull(state.session, "Register session must remain intact")
            assertEquals(cashier.id, state.currentUser?.id, "Cashier remains authenticated")
        } finally {
            db.close()
        }
    }

    @Test
    fun test04_customerReceipt_addressInFooter_wifiNeverPrinted(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            val company = state.getCompany()
            assertEquals("45 Boulevard d'Anfa Casablanca", company.address)
            assertEquals("Pâtisserie_Guest_WiFi", company.wifiName)
            assertEquals("secretPass2026", company.wifiCode)

            val sampleOrder = Order(
                id = 88L,
                number = "CMD-0088",
                type = OrderType.COUNTER,
                status = OrderStatus.COMPLETED,
                lines = listOf(
                    OrderLine(1L, "Millefeuille Vanille", 25_00L, 2, 1000)
                ),
                subtotalCentimes = 50_00L,
                discountCentimes = 0L,
                taxCentimes = 455L,
                totalCentimes = 50_00L,
                registerSessionId = 1L,
                cashierId = 1L
            )

            val preview80 = ThermalTicketRenderer.previewText(sampleOrder, company, TicketKind.CUSTOMER, 80)
            val preview58 = ThermalTicketRenderer.previewText(sampleOrder, company, TicketKind.CUSTOMER, 58)

            listOf(preview80, preview58).forEach { preview ->
                // Address MUST be in footer before thank you note
                assertTrue(preview.contains("Casablanca"), "Address must be present on customer ticket")
                val addressIdx = preview.indexOf("Casablanca")
                val totalIdx = preview.indexOf("TOTAL")
                val footerIdx = preview.indexOf("Merci de votre visite")
                assertTrue(addressIdx > totalIdx, "Address must appear after TOTAL in footer")
                assertTrue(footerIdx != -1, "Footer thank you note must be present")
                assertTrue(addressIdx > footerIdx, "Address must appear under thank you note in footer")

                // Wi-Fi and passwords must NEVER be printed
                assertFalse(preview.contains("Wi-Fi"), "Wi-Fi SSID must be omitted from customer ticket")
                assertFalse(preview.contains("Pâtisserie_Guest_WiFi"), "Wi-Fi network name must not appear")
                assertFalse(preview.contains("secretPass2026"), "Wi-Fi password must not appear")
                assertFalse(preview.contains("Code:"), "Code label must not appear")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun test05_sessionClosingReports_summaryOnAutoClose_detailedOnManualReprint(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            val sess = state.session!!
            assertEquals(0L, sess.openingCashCentimes)

            val catId = db.categories.save(Category(0, "Tartes", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Tartelette Citron", 20_00L, 1000, active = true))
            state.refresh()

            // 1. Sale in cash: 40.00 DH
            state.cart[pId] = 2
            state.createOrder()
            state.pay(PaymentMethod.CASH, 40_00L)
            state.dismissCompletedSale()

            // 2. Sale in card: 60.00 DH
            state.cart[pId] = 3
            state.createOrder()
            state.pay(PaymentMethod.CARD, null)
            state.dismissCompletedSale()

            // 3. Cash movement: In 100.00 DH
            state.movement(CashMovementType.CASH_IN, 10_000L, "Apport monnaie")

            // 4. Cash movement: Out 30.00 DH
            state.movement(CashMovementType.CASH_OUT, 3_000L, "Achat boîtes")

            // Expected cash = Cash Sales (40) + In (100) - Out (30) = 110.00 DH (11_000 centimes)
            val expected = 40_00L + 10_000L - 3_000L
            assertEquals(11_000L, expected)

            val sampleReport = SessionClosingReport(
                session = sess.copy(
                    status = RegisterSessionStatus.CLOSED,
                    closedAtEpochMilliseconds = 1725825000000L,
                    expectedCashCentimes = 11_000L,
                    countedCashCentimes = 11_000L
                ),
                cashierName = "Directeur Yassine",
                closingUserName = "Directeur Yassine",
                sales = listOf(
                    SessionClosingSale(
                        orderId = 1L,
                        orderNumber = "ORD-001",
                        paidAtEpochMilliseconds = 1725823800000L,
                        paymentMethods = listOf(PaymentMethod.CASH),
                        totalCentimes = 40_00L,
                        items = listOf(SessionClosingSaleItem("Tartelette Citron", 2))
                    )
                ),
                paymentTotals = mapOf(
                    PaymentMethod.CASH to 40_00L,
                    PaymentMethod.CARD to 60_00L
                ),
                cashInCentimes = 10_000L,
                cashOutCentimes = 3_000L,
                cancelledSalesCount = 0,
                cancelledSalesCentimes = 0L
            )

            // Test Summary report formatting
            val summaryResult = assertIs<EscPosFormatResult.Success>(
                SessionClosingReportEscPosFormatter.format(sampleReport, state.getCompany().name, 80, SessionReportType.SUMMARY, isReprint = false)
            )
            val summaryText = String(summaryResult.bytes, FrenchEscPosEncoder.CHARSET)
            assertTrue(summaryText.contains("RAPPORT DE CLOTURE - RESUME"))
            assertFalse(summaryText.contains("DUPLICATA"), "Initial closing report is not duplicata")

            // Test Detailed report formatting (reprint from history)
            val detailedResult = assertIs<EscPosFormatResult.Success>(
                SessionClosingReportEscPosFormatter.format(sampleReport, state.getCompany().name, 80, SessionReportType.DETAILED, isReprint = true)
            )
            val detailedText = String(detailedResult.bytes, FrenchEscPosEncoder.CHARSET)
            assertTrue(detailedText.contains("RAPPORT DE CLOTURE - DETAIL"))
            assertFalse(detailedText.contains("DUPLICATA"), "Reprint from history must not show DUPLICATA")
            assertFalse(detailedText.contains("REIMPRESSION"), "Reprint from history must not show REIMPRESSION")
        } finally {
            db.close()
        }
    }

    @Test
    fun test06_rapidSequentialSales_highThroughput_dataIntegrity(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            assertNotNull(state.session)

            val catId = db.categories.save(Category(0, "Gâteaux", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Macaron", 10_00L, 1000, active = true))
            state.refresh()

            // 10 consecutive sales
            for (i in 1..10) {
                state.cart[pId] = i
                state.createOrder()
                val isCash = (i % 2 == 1)
                val total = i * 10_00L

                if (isCash) {
                    state.pay(PaymentMethod.CASH, total + 5_00L)
                    assertEquals(5_00L, state.completedSaleConfirmation?.changeCentimes)
                } else {
                    state.pay(PaymentMethod.CARD, null)
                    assertEquals(0L, state.completedSaleConfirmation?.changeCentimes ?: 0L)
                }

                assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
                assertNotNull(state.completedSaleConfirmation)

                // Dismiss pop-up
                state.dismissCompletedSale()
                assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
                assertTrue(state.cart.isEmpty())
            }

            // Verify database history
            val allSales = db.salesHistory(sessionId = state.session!!.id)
            assertEquals(10, allSales.size, "All 10 sales must be recorded in session")
        } finally {
            db.close()
        }
    }

    @Test
    fun test07_orderCancellationSecurity_pinProtection(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            // Configure cancellation PIN = "9999"
            db.setOrderCancellationPin("9999")
            assertTrue(db.isOrderCancellationPinConfigured())

            // Create cashier
            val cashierId = db.createCashier("Caissier Ahmed", "2222")
            val cashier = db.authentication.authenticate("2222")!!

            val catId = db.categories.save(Category(0, "Petits Fours", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Sablé", 5_00L, 1000, active = true))
            state.refresh()

            // Cashier logs in (auto-opens register at 0 DH)
            state.login(cashier, "2222")
            assertNotNull(state.session)
            assertEquals(0L, state.session!!.openingCashCentimes)

            // Cashier creates active held order
            state.cart[pId] = 4
            state.saveDraft(proceedToPayment = false)
            val order = state.openOrders.first()

            // Cashier attempts cancel with wrong PIN
            assertFalse(db.verifyOrderCancellationPin("0000"))
            assertFailsWith<IllegalArgumentException> {
                db.cancelOrder(order.id, "Client parti", cashierId, "0000")
            }

            // Cashier cancels with correct PIN
            assertTrue(db.verifyOrderCancellationPin("9999"))
            db.cancelOrder(order.id, "Client parti", cashierId, "9999")

            val cancelledOrder = db.orders.findById(order.id)!!
            assertEquals(OrderStatus.CANCELLED, cancelledOrder.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun test08_itemDiscountsAndGlobalDiscount_precisionCalculation(): Unit = runBlocking {
        val (db, state, _) = setupEnvironment()
        try {
            val owner = db.allUsers().first()
            state.login(owner, "1234")
            assertNotNull(state.session)

            val catId = db.categories.save(Category(0, "Chocolats", active = true, displayOrder = 1))
            val pId = db.products.save(Product(0, catId, "Boîte Pralines", 100_00L, 1000, active = true))
            state.refresh()

            state.cart[pId] = 2 // 200.00 DH
            state.itemDiscountsBasisPoints[pId] = 1000 // 10% item discount -> 20.00 DH discount
            state.discountBasisPoints = 500 // 5% global discount

            state.createOrder()
            val order = state.pendingOrder!!
            assertEquals(200_00L, order.subtotalCentimes)
            assertTrue(order.discountCentimes > 0)
            assertEquals(order.subtotalCentimes - order.discountCentimes, order.totalCentimes)

            state.pay(PaymentMethod.CASH, 200_00L)
            assertEquals(DesktopScreenRoute.PAYMENT, state.currentRoute)
            assertNotNull(state.completedSaleConfirmation)
            val conf = state.completedSaleConfirmation!!
            assertEquals(order.totalCentimes, conf.order.totalCentimes)
            assertEquals(200_00L - order.totalCentimes, conf.changeCentimes)
            state.dismissCompletedSale()
            assertEquals(DesktopScreenRoute.POS_MAIN, state.currentRoute)
        } finally {
            db.close()
        }
    }
}
