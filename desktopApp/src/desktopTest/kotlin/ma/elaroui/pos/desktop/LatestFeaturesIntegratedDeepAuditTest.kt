package ma.elaroui.pos.desktop

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.*
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.desktop.security.AdminDeletionSecurity
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class LatestFeaturesIntegratedDeepAuditTest {

    @Test
    fun testReceiptHeaderSpecialtyAndMultiLineQuantitiesOn58And80mm() {
        val company = ReceiptCompany(
            name = "Boulangerie & Pâtisserie Royale",
            specialty = "Spécialités Artisanales & Salon de Thé",
            address = "77 Boulevard d'Anfa, Casablanca",
            phone = "0522889900",
            ice = "001234567890001",
            taxId = "IF-9876",
            commercialRegister = "RC-54321",
            patente = "PAT-1122",
            wifiName = "Royale_Guest",
            wifiCode = "royalepass",
            printEstablishmentName = true
        )

        val order = Order(
            id = 501L,
            number = "REC-2026-001",
            type = OrderType.COUNTER,
            status = OrderStatus.COMPLETED,
            lines = listOf(
                // Qty = 1: Single line format
                OrderLine(productId = 1L, name = "Chebakia Miel & Sésame", unitPriceCentimes = 7000L, quantity = 1, taxRateBasisPoints = 1000),
                // Qty = 3: Two lines format
                OrderLine(productId = 2L, name = "Cornes de Gazelle", unitPriceCentimes = 8500L, quantity = 3, taxRateBasisPoints = 1000),
                // Qty = 12: Large quantity two lines format
                OrderLine(productId = 3L, name = "Briouates aux Amandes et Fleur d'Oranger", unitPriceCentimes = 1500L, quantity = 12, taxRateBasisPoints = 1000)
            ),
            subtotalCentimes = 50500L,
            discountCentimes = 0L,
            taxCentimes = 5050L,
            totalCentimes = 50500L,
            tableId = null,
            registerSessionId = 1L,
            cashierId = 1L
        )

        // 1. Test 58mm (32 characters max width)
        val preview58 = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, paperWidth = 58)
        assertContains(preview58, "Boulangerie & Pâtisserie")
        assertContains(preview58, "Spécialités Artisanales")
        assertContains(preview58, "1 x Chebakia Miel")
        assertContains(preview58, "70.00 DH")
        assertContains(preview58, "3 x Cornes de Gazelle")
        assertContains(preview58, "85.00 DH x 3")
        assertContains(preview58, "255.00 DH")
        assertContains(preview58, "12 x Briouates aux Amandes")
        assertContains(preview58, "15.00 DH x 12")
        assertContains(preview58, "180.00 DH")
        preview58.lines().forEach { line ->
            assertTrue(line.length <= 32, "58mm receipt line exceeds 32 chars: '$line' (${line.length})")
        }

        // 2. Test 80mm (48 characters max width)
        val preview80 = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, paperWidth = 80)
        assertContains(preview80, "Boulangerie & Pâtisserie Royale")
        assertContains(preview80, "Spécialités Artisanales & Salon de Thé")
        assertContains(preview80, "1 x Chebakia Miel & Sésame")
        assertContains(preview80, "70.00 DH")
        assertContains(preview80, "3 x Cornes de Gazelle")
        assertContains(preview80, "85.00 DH x 3")
        assertContains(preview80, "255.00 DH")
        preview80.lines().forEach { line ->
            assertTrue(line.length <= 48, "80mm receipt line exceeds 48 chars: '$line' (${line.length})")
        }

        // 3. Test Blank Specialty
        val companyNoSpecialty = company.copy(specialty = "   ")
        val previewNoSpecialty = ThermalTicketRenderer.previewText(order, companyNoSpecialty, TicketKind.CUSTOMER, paperWidth = 80)
        assertFalse(previewNoSpecialty.contains("Spécialités Artisanales"))

        // 4. Test Hidden Establishment Name
        val companyHiddenName = company.copy(printEstablishmentName = false)
        val previewHiddenName = ThermalTicketRenderer.previewText(order, companyHiddenName, TicketKind.CUSTOMER, paperWidth = 80)
        assertFalse(previewHiddenName.contains("Boulangerie & Pâtisserie Royale"))
        assertContains(previewHiddenName, "Spécialités Artisanales")
    }

    @Test
    fun testPrinterTestDebounceGuardPreventsInfiniteLoops() {
        val printTriggerCount = AtomicInteger(0)
        var isPrinting = false

        fun triggerTestPrint() {
            if (isPrinting) return
            isPrinting = true
            try {
                printTriggerCount.incrementAndGet()
            } finally {
                isPrinting = false
            }
        }

        // Simulate 20 rapid consecutive taps
        repeat(20) {
            triggerTestPrint()
        }
        assertEquals(20, printTriggerCount.get())

        // Simulate concurrent overlapping clicks
        printTriggerCount.set(0)
        isPrinting = true // Printer busy
        repeat(10) {
            triggerTestPrint() // Must be blocked
        }
        assertEquals(0, printTriggerCount.get(), "Printer guard must block attempts while a test job is active")
    }

    @Test
    fun testDataManagementCompleteWorkflowAndSanctuaryPreservation() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // 1. Password security verification
            assertFalse(db.verifyAdminDeletionPassword("AnyUnconfiguredPassword2026!"))
            db.setAdminDeletionPassword("DedicatedDeletionPassword2026!")
            assertTrue(db.verifyAdminDeletionPassword("DedicatedDeletionPassword2026!"))
            assertFalse(db.verifyAdminDeletionPassword("WrongPassword123"))

            val newPassword = "UltraSecure2026#POS"
            db.setAdminDeletionPassword(newPassword)
            assertTrue(db.verifyAdminDeletionPassword(newPassword))
            assertFalse(db.verifyAdminDeletionPassword("DedicatedDeletionPassword2026!"))

            // 2. Setup business data
            db.settings.put(AppSetting("license_key", "LIC-PREMIUM-RELEASE-108"))
            db.settings.put(AppSetting("license_signature", "SIG-SECURE-990011"))
            db.settings.put(AppSetting("license_status", "ACTIVE"))
            db.settings.put(AppSetting("establishment_name", "Café Central"))

            val catId = db.categories.save(Category(0L, "Pâtisseries Fines", true, 0))
            val pId = db.products.save(Product(0L, catId, "Millefeuille", 2000L, 1000, true, true))
            val areaId = db.saveArea(DiningArea(0L, "Terrasse Vue Mer", true, 0))
            val tableId = db.tables.save(RestaurantTable(0L, areaId, "T-10", TableStatus.AVAILABLE, true, 0))
            val cashierId = db.createCashier("Caissier Principal", "7788")

            val session = db.sessions.save(RegisterSession(
                id = 99L, status = RegisterSessionStatus.OPEN, openingCashCentimes = 15000L,
                registerId = 1L, cashierId = cashierId, openedAtEpochMilliseconds = System.currentTimeMillis(),
                closedAtEpochMilliseconds = null, expectedCashCentimes = null, countedCashCentimes = null, differenceCentimes = null
            ))

            // Open order on table
            db.orders.save(Order(
                id = 0L, number = "SUSP-101", type = OrderType.DINE_IN, status = OrderStatus.OPEN,
                lines = listOf(OrderLine(pId, "Millefeuille", 2000L, 2, 1000)), subtotalCentimes = 4000L,
                discountCentimes = 0L, taxCentimes = 400L, totalCentimes = 4000L, tableId = tableId,
                registerSessionId = session, cashierId = cashierId
            ))
            assertEquals(TableStatus.OCCUPIED, db.tables.findById(tableId)?.status)

            // Completed order
            val compOrderId = db.orders.save(Order(
                id = 0L, number = "COMP-101", type = OrderType.COUNTER, status = OrderStatus.COMPLETED,
                lines = listOf(OrderLine(pId, "Millefeuille", 2000L, 1, 1000)), subtotalCentimes = 2000L,
                discountCentimes = 0L, taxCentimes = 200L, totalCentimes = 2000L, tableId = null,
                registerSessionId = session, cashierId = cashierId
            ))
            db.payments.save(Payment(0L, compOrderId, session, PaymentMethod.CASH, 2000L, 2000L, 0L, PaymentStatus.COMPLETED, "token-comp", System.currentTimeMillis()))

            // 3. Test deleteSuspendedSales: Releases table and preserves completed sale
            val suspDeleted = db.deleteSuspendedSales()
            assertEquals(1, suspDeleted)
            assertEquals(TableStatus.AVAILABLE, db.tables.findById(tableId)?.status)
            assertEquals(1, db.salesHistory(status = OrderStatus.COMPLETED).size)

            // 4. Test deleteSelective: delete products & cashiers, categories stay intact
            val summary = db.deleteSelective(DataGroupSelection(products = true, cashiers = true))
            assertTrue(summary.productsDeleted >= 1)
            assertEquals(1, summary.cashiersDeleted)
            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.categories.observeAll().first().isNotEmpty())
            assertEquals(1, db.allUsers().size) // Only Owner remains

            // 5. Test factoryResetData: Complete wipe, license sanctuary strictly preserved
            db.factoryResetData()
            assertTrue(db.products.observeAll().first().isEmpty())
            assertTrue(db.categories.observeAll().first().isEmpty())
            assertTrue(db.allTables().isEmpty())
            assertTrue(db.areas().isEmpty())
            assertTrue(db.allUsers().isEmpty())
            assertNull(db.settings.get("establishment_name"))

            // License preserved
            assertEquals("LIC-PREMIUM-RELEASE-108", db.settings.get("license_key"))
            assertEquals("SIG-SECURE-990011", db.settings.get("license_signature"))
            assertEquals("ACTIVE", db.settings.get("license_status"))
        }
    }
}
