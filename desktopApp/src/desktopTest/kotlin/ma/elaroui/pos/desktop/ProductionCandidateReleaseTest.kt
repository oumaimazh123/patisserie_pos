package ma.elaroui.pos.desktop

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.license.WindowsLicenseManager
import ma.elaroui.pos.desktop.license.WindowsLicenseStatus
import ma.elaroui.pos.desktop.license.WindowsSecureStore
import ma.elaroui.pos.desktop.persistence.DesktopValidationException
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.print.DesktopPrinterConfig
import ma.elaroui.pos.desktop.print.ThermalTicketRenderer
import ma.elaroui.pos.desktop.print.TicketKind
import ma.elaroui.pos.desktop.print.WindowsThermalPrinter
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.*
import kotlin.test.*

private object FixedTestClock : ma.elaroui.pos.shared.Clock {
    override fun now() = ma.elaroui.pos.shared.EpochMilliseconds(1_700_000_000_000L)
}

class ProductionCandidateReleaseTest {

    @Test
    fun test01_freshInstallationAndFirstLaunch(): Unit = runBlocking {
        val dir = Files.createTempDirectory("pos-release-fresh-install")
        val dbPath = dir.resolve("pos.db")
        WindowsPosDatabase.open(dbPath).use { db ->
            assertFalse(db.settings.get("setup_complete") == "true", "Fresh database must report unconfigured")
            assertTrue(db.categories.observeAll().first().isEmpty(), "Fresh databases must not contain mock categories")
            assertTrue(db.products.observeAll().first().isEmpty(), "A fresh store catalogue must be empty")
            assertTrue(db.tables.observeActive().first().isEmpty(), "A fresh retail install must not create tables")
            assertTrue(db.areas().isEmpty(), "A fresh retail install must not create dining areas")
        }
    }

    @Test
    fun test02_ownerSetupAndValidationRules(): Unit = runBlocking {
        val dir = Files.createTempDirectory("pos-release-owner-setup")
        val dbPath = dir.resolve("pos.db")
        WindowsPosDatabase.open(dbPath).use { db ->
            // Invalid PIN length
            assertFails { db.configureInitialSetup("Café Royal", "Owner", "123") }
            assertFails { db.configureInitialSetup("Café Royal", "Owner", "1234567") }
            assertFails { db.configureInitialSetup("Café Royal", "Owner", "12a4") }

            // Valid 6-digit PIN setup
            db.configureInitialSetup("Café Royal", "Owner Zakaria", "987654")
            assertEquals("true", db.settings.get("setup_complete"))
            val owner = db.authentication.authenticate("987654")
            assertNotNull(owner)
            assertEquals("Owner Zakaria", owner.name)
            assertEquals(UserRole.OWNER, owner.role)
        }
    }

    @Test
    fun test03_cashierLifecycleAndPinEnforcement(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // 4, 5, and 6 digit PINs
            val cashier4 = db.createCashier("Cashier 4D", "4444")
            val cashier5 = db.createCashier("Cashier 5D", "55555")
            val cashier6 = db.createCashier("Cashier 6D", "666666")

            assertEquals(cashier4, db.authentication.authenticate("4444")?.id)
            assertEquals(cashier5, db.authentication.authenticate("55555")?.id)
            assertEquals(cashier6, db.authentication.authenticate("666666")?.id)

            // Wrong PIN handling
            assertNull(db.authentication.authenticate("0000"))
            assertNull(db.authentication.authenticate(""))

            // Duplicate PIN validation (between cashiers and with owner PIN 1234)
            val dupOwner = assertFailsWith<DesktopValidationException> {
                db.createCashier("Duplicate Owner", "1234")
            }
            assertTrue(dupOwner.message!!.contains("déjà utilisé"))

            val dupCashier = assertFailsWith<DesktopValidationException> {
                db.createCashier("Duplicate 4D", "4444")
            }
            assertTrue(dupCashier.message!!.contains("déjà utilisé"))

            // Activation and Deactivation
            db.updateCashier(cashier4, active = false)
            assertNull(db.authentication.authenticate("4444"), "Deactivated cashier must not be able to log in")
            assertTrue(db.allUsers().any { it.id == cashier4 && !it.active }, "Deactivated cashier must remain in database")

            // Re-activating cashier
            db.updateCashier(cashier4, active = true)
            assertNotNull(db.authentication.authenticate("4444"), "Re-activated cashier can log in")
        }
    }

    @Test
    fun test04_registerSessionAndCashMovements(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // Register opening
            val sessionResult = OpenRegisterSession(db.sessions, FixedTestClock).execute(1, 1, 1, 50_000) // 500.00 DH
            assertIs<UseCaseResult.Success<RegisterSession>>(sessionResult)
            val session = sessionResult.value
            assertEquals(RegisterSessionStatus.OPEN, session.status)
            assertEquals(50_000, session.openingCashCentimes)

            // Duplicate register session opening must be rejected
            val dupOpen = OpenRegisterSession(db.sessions, FixedTestClock).execute(2, 1, 1, 10_000)
            assertIs<UseCaseResult.Failure>(dupOpen)

            // Cash IN movement
            val cashIn = RecordCashMovement(db.sessions, db.payments, db.cashMovements, FixedTestClock)
                .execute(session.id, CashMovementType.CASH_IN, 20_000, "Apport monnaie", "Monnaie de réserve", 1)
            assertIs<UseCaseResult.Success<CashMovement>>(cashIn)

            // Cash OUT movement
            val cashOut = RecordCashMovement(db.sessions, db.payments, db.cashMovements, FixedTestClock)
                .execute(session.id, CashMovementType.CASH_OUT, 5_000, "Achat fournitures", "Papier ticket", 1)
            assertIs<UseCaseResult.Success<CashMovement>>(cashOut)

            // Close register session with counted cash: Expected = 500 + 200 - 50 = 650.00 DH (65000 centimes)
            // If counted = 640.00 DH (64000 centimes), difference = -10.00 DH (-1000 centimes)
            val closeResult = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, FixedTestClock)
                .execute(session.id, 64_000)
            assertIs<UseCaseResult.Success<RegisterSession>>(closeResult)
            val closed = closeResult.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(65_000, closed.expectedCashCentimes)
            assertEquals(64_000, closed.countedCashCentimes)
            assertEquals(-1_000, closed.differenceCentimes)
        }
    }

    @Test
    fun test05_productAndCategoryManagementWithImages(): Unit = runBlocking {
        val dir = Files.createTempDirectory("pos-prod-mgmt")
        val imageFile = dir.resolve("sample_product.jpg")
        Files.write(imageFile, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        WindowsPosDatabase.openInMemory().use { db ->
            val catId = db.categories.save(Category(0, "Jus Naturels Extra", active = true, displayOrder = 1))
            assertTrue(catId > 0)

            val prod = Product(
                id = 0,
                categoryId = catId,
                name = "Jus d'Orange Pressé",
                priceCentimes = 2_200,
                taxRateBasisPoints = 1_000,
                imagePath = imageFile.toString(),
                available = true,
                active = true
            )
            val prodId = db.products.save(prod)
            assertTrue(prodId > 0)

            val saved = db.products.findById(prodId)
            assertNotNull(saved)
            assertEquals("Jus d'Orange Pressé", saved.name)
            assertEquals(imageFile.toString(), saved.imagePath)

            // Disable product
            db.products.save(saved.copy(available = false))
            assertFalse(db.products.findById(prodId)!!.available)

            // Remove image
            db.products.save(saved.copy(imagePath = null))
            assertNull(db.products.findById(prodId)!!.imagePath)
        }
    }

    @Test
    fun test06_csvImportsHandlingValidAndMalformedData(): Unit {
        val dir = Files.createTempDirectory("pos-csv-test")
        val validCsv = dir.resolve("valid_products.csv")
        val header = CsvImports.OFFICIAL_HEADERS.joinToString(",")
        Files.writeString(
            validCsv,
            header + "\n" +
            "B1,Boissons chaudes,,,,,,,,Thé à la menthe,,,Pièce,\"15,00\",10%,\n" +
            "B2,Boissons chaudes,,,,,,,,Café Crème,,,Pièce,16.00,10%,\n" +
            "B1,Boissons chaudes,,,,,,,,Thé à la menthe,,,Pièce,\"15,00\",10%,\n" + // duplicate Product ID
            "B3,Boissons chaudes,,,,,,,,Invalid Price,,,Pièce,abc,10%,\n" +       // bad price
            "B4,Boissons chaudes,,,,,,,,Negative Price,,,Pièce,-5.00,10%,\n"     // negative price
        )

        val analysis = CsvImports.analyzeCatalogCsv(validCsv)
        assertEquals(2, analysis.validRows.size)
        assertEquals("Thé à la menthe", analysis.validRows[0].productNameFrench)
        assertEquals(1500L, analysis.validRows[0].priceCentimes)
        assertEquals("Café Crème", analysis.validRows[1].productNameFrench)
        assertEquals(1600L, analysis.validRows[1].priceCentimes)
        assertTrue(analysis.errors.any { it.line == 4 && it.column == "Product ID" })
        assertTrue(analysis.errors.any { it.line == 5 && it.column == "Price (MAD)" })
        assertTrue(analysis.errors.any { it.line == 6 && it.column == "Price (MAD)" })
    }

    @Test
    fun test07_diningAreasAndTableStateTransitions(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val area = DiningArea(id = 0, name = "Terrasse Mer", active = true, displayOrder = 1, imagePath = "terrasse.jpg")
            val areaId = db.saveArea(area)
            assertTrue(areaId > 0)

            val table = RestaurantTable(id = 0, areaId = areaId, name = "T-01", status = TableStatus.AVAILABLE, active = true, displayOrder = 1)
            val tableId = db.tables.save(table)
            assertTrue(tableId > 0)

            OpenRegisterSession(db.sessions, FixedTestClock).execute(1, 1, 1, 10_000)

            // Create order assigned to table -> Table becomes OCCUPIED
            val orderResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(100, "ORD-TBL-1", OrderType.DINE_IN, 1, listOf(1L to 2), tableId = tableId, cashierId = 1)
            assertIs<UseCaseResult.Success<Order>>(orderResult)
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(tableId)?.status)

            // Move order to another table T-02
            val table2Id = db.tables.save(RestaurantTable(id = 0, areaId = areaId, name = "T-02", status = TableStatus.AVAILABLE, active = true, displayOrder = 2))
            db.moveOrder(100, table2Id)
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(tableId)?.status)
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(table2Id)?.status)

            // Cancel order -> Target table becomes AVAILABLE
            db.cancelOrder(100, "Client parti", 1, null)
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(table2Id)?.status)
            assertEquals(OrderStatus.CANCELLED, db.orders.findById(100)?.status)
        }
    }

    @Test
    fun test08_posCartCalculationsAndOrderFulfillment(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, FixedTestClock).execute(1, 1, 1, 10_000)

            // Product 1: 15.00 DH, 20% TVA -> 2 items = 30.00 DH
            // Product 2: 25.00 DH, 10% TVA -> 1 item = 25.00 DH
            val prod1 = db.products.findById(1)!!.copy(priceCentimes = 1_500, taxRateBasisPoints = 2_000)
            val prod2 = db.products.findById(2)!!.copy(priceCentimes = 2_500, taxRateBasisPoints = 1_000)
            db.products.save(prod1)
            db.products.save(prod2)

            val orderResult = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(200, "ORD-CART-1", OrderType.TAKEAWAY, 1, listOf(prod1.id to 2, prod2.id to 1), tableId = null, cashierId = 1)
            assertIs<UseCaseResult.Success<Order>>(orderResult)
            val order = orderResult.value

            assertEquals(5_500, order.totalCentimes) // 3000 + 2500 = 55.00 DH
            assertEquals(5_500, order.subtotalCentimes)
            assertTrue(order.taxCentimes > 0)
        }
    }

    @Test
    fun test09_paymentsDuplicatePreventionAndChangeCalculation(): Unit = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            OpenRegisterSession(db.sessions, FixedTestClock).execute(1, 1, 1, 10_000)

            val prod = db.products.findById(1)!!.copy(priceCentimes = 4_200)
            db.products.save(prod)

            val order = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders)
                .execute(300, "ORD-PAY-1", OrderType.COUNTER, 1, listOf(prod.id to 1), tableId = null, cashierId = 1)
            val orderVal = assertIs<UseCaseResult.Success<Order>>(order).value

            // Cash payment: Total = 42.00 DH, Received = 50.00 DH (5000 centimes) -> Change = 8.00 DH (800 centimes)
            val paymentResult = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(orderVal.id, PaymentMethod.CASH, 5_000, "token-pay-300")
            assertIs<UseCaseResult.Success<Payment>>(paymentResult)
            val payment = paymentResult.value

            assertEquals(4_200, payment.amountCentimes)
            assertEquals(5_000, payment.receivedCentimes)
            assertEquals(800, payment.changeCentimes)
            assertEquals(PaymentStatus.COMPLETED, payment.status)
            assertEquals(OrderStatus.COMPLETED, db.orders.findById(300)?.status)

            // Duplicate payment submission token idempotency
            val duplicateAttempt = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
                .execute(orderVal.id, PaymentMethod.CASH, 5_000, "token-pay-300")
            assertIs<UseCaseResult.Success<Payment>>(duplicateAttempt)
            assertEquals(payment.id, duplicateAttempt.value.id)
            assertEquals(payment.amountCentimes, duplicateAttempt.value.amountCentimes)
        }
    }

    @Test
    fun test10_thermalPrinterResilienceAndFormatting(): Unit {
        val sampleOrder = Order(
            id = 500,
            number = "ORD-TEST-500",
            type = OrderType.DINE_IN,
            status = OrderStatus.COMPLETED,
            lines = listOf(OrderLine(1, "Espresso", 1_500, 2, 2_000)),
            subtotalCentimes = 3_000,
            discountCentimes = 0,
            taxCentimes = 500,
            totalCentimes = 3_000,
            tableId = 1,
            registerSessionId = 1,
            cashierId = 1
        )
        val company = ReceiptCompany(
            name = "Café Central",
            address = "Avenue Hassan II, Rabat",
            phone = "0537000000",
            ice = "001122334455667",
            taxId = "12345678",
            commercialRegister = "87654",
            patente = "998877",
            wifiName = "Cafe_Central_Guest",
            wifiCode = "coffee2026"
        )

        // 80mm format
        val ticket80 = ThermalTicketRenderer.previewText(sampleOrder, company, TicketKind.CUSTOMER, 80)
        assertTrue(ticket80.contains("Café Central"))
        assertTrue(ticket80.contains("001122334455667"))
        assertTrue(ticket80.contains("2 x Espresso"))
        assertTrue(ticket80.contains("TOTAL"))
        assertTrue(ticket80.contains("30.00 DH"))
        assertFalse(ticket80.contains("Wi-Fi"))

        // 58mm format
        val ticket58 = ThermalTicketRenderer.previewText(sampleOrder, company, TicketKind.CUSTOMER, 58)
        assertTrue(ticket58.contains("Café Central"))
        assertTrue(ticket58.contains("TOTAL"))

        // Safe failure when printer name is invalid / disconnected
        val printer = WindowsThermalPrinter()
        val printResult = printer.print(DesktopPrinterConfig("NonExistentPrinter-XYZ", 80), "test".toByteArray())
        assertTrue(printResult.isFailure, "Printing to disconnected printer must safely return Failure")
        assertNotNull(printResult.exceptionOrNull())
    }

    @Test
    fun test11_backupRestoreAndPersistenceAcrossRestarts(): Unit = runBlocking {
        val dir = Files.createTempDirectory("pos-release-backup-test")
        val liveDb = dir.resolve("live.db")
        val backupDb = dir.resolve("backup.db")

        WindowsPosDatabase.open(liveDb).use { db ->
            db.configureInitialSetup("Restaurant Atlas", "Propriétaire", "7890")
            db.createCashier("Caissier Ahmed", "2345")
            db.settings.put(AppSetting("restaurant_address", "12 Rue Agdal, Rabat"))
            db.backupTo(backupDb)

            // Make modifications after backup
            db.settings.put(AppSetting("restaurant_address", "MODIFIED ADDRESS"))
            assertEquals("MODIFIED ADDRESS", db.settings.get("restaurant_address"))

            // Stage restore
            db.stageRestore(backupDb)
        }

        // Reopening live.db must apply the staged restore
        WindowsPosDatabase.open(liveDb).use { restored ->
            assertEquals("Restaurant Atlas", restored.settings.get("establishment_name"))
            assertEquals("12 Rue Agdal, Rabat", restored.settings.get("restaurant_address"))
            assertEquals("Propriétaire", restored.authentication.authenticate("7890")?.name)
            assertEquals("Caissier Ahmed", restored.authentication.authenticate("2345")?.name)
        }
    }

    @Test
    fun test12_licenseManagerTrialActivationAndSecurity(): Unit {
        val dir = Files.createTempDirectory("pos-release-license")
        var now = 10_000_000L
        val store = WindowsSecureStore(dir.resolve("sec_license.dat"))
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val pubKeyPem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(keyPair.public.encoded)}\n-----END PUBLIC KEY-----"

        val licenseManager = WindowsLicenseManager(store, { now }, pubKeyPem)
        assertEquals(WindowsLicenseStatus.TRIAL_ACTIVE, licenseManager.state().status)
        assertEquals(7, licenseManager.state().daysRemaining)

        val deviceId = licenseManager.installationId

        // Generate signed license
        fun generateLicenseKey(targetDeviceId: String, expiry: Long): String {
            val payload = "{\"licenseId\":\"LIC-2026-01\",\"productId\":\"GENERAL_POS_V1\",\"appId\":\"ma.elaroui.generalpos\",\"installationId\":\"$targetDeviceId\",\"customerName\":\"General POS Test\",\"issueDateMs\":$now,\"expirationDateMs\":$expiry,\"licenseType\":\"PERPETUAL\",\"schemaVersion\":\"1.0\"}"
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(keyPair.private)
            signer.update(payload.toByteArray())
            return Base64.getEncoder().encodeToString(payload.toByteArray()) + "." + Base64.getEncoder().encodeToString(signer.sign())
        }

        // Wrong device license
        val wrongDeviceState = licenseManager.activate(generateLicenseKey("OTHER-DEVICE-ID", now + 1_000_000))
        assertEquals(WindowsLicenseStatus.WRONG_DEVICE, wrongDeviceState.status)

        // Valid license activation
        val validState = licenseManager.activate(generateLicenseKey(deviceId, now + 1_000_000))
        assertEquals(WindowsLicenseStatus.VALID, validState.status)

        // Reopen manager and verify persistence
        val reopened = WindowsLicenseManager(store, { now }, pubKeyPem)
        assertEquals(WindowsLicenseStatus.VALID, reopened.state().status)

        // Expiration check
        now += 2_000_000
        assertEquals(WindowsLicenseStatus.EXPIRED, reopened.state().status)
    }
}
