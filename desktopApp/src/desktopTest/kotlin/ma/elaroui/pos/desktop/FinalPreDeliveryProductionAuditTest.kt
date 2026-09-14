package ma.elaroui.pos.desktop

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.backup.DesktopBackupService
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.DiscrepancyKind
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.RegisterClosingRules
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class FinalPreDeliveryProductionAuditTest {

    private class FixedClock(var time: Long) : ma.elaroui.pos.shared.Clock {
        override fun now() = EpochMilliseconds(time)
    }

    @Test
    fun test01_AuthenticationAndRolePermissions() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            val users = db.users.observeActive().first()
            assertTrue(users.isNotEmpty(), "Default users must exist")

            val owner = users.firstOrNull { it.role == UserRole.OWNER }
            assertNotNull(owner, "Owner user must exist")

            val authUser = db.authentication.authenticate("1234")
            assertNotNull(authUser, "Authentication with default PIN 1234 must succeed")
            assertEquals(UserRole.OWNER, authUser.role)

            val wrongAuth = db.authentication.authenticate("9999")
            assertNull(wrongAuth, "Authentication with wrong PIN must fail")
        }
    }

    @Test
    fun test02_ProductsAndCategoriesLifecycle() = runBlocking {
        WindowsPosDatabase.openInMemory().use { db ->
            // 1. Create Category
            val category = Category(101L, "P�tisseries Fines", active = true, displayOrder = 1)
            db.categories.save(category)
            assertEquals("P�tisseries Fines", db.categories.findById(101L)?.name)

            // 2. Create Products
            val prod1 = Product(201L, 101L, "Cornes de Gazelle", 8500L, 1000, available = true, active = true)
            db.products.save(prod1)

            val prod2 = Product(202L, 101L, "Ghriba Amande", 6000L, 1000, available = true, active = false)
            db.products.save(prod2)

            // 3. Verify Active Sellable Filter
            val sellable = db.products.observeSellable().first()
            assertTrue(sellable.any { it.id == 201L })
            assertFalse(sellable.any { it.id == 202L })

            // 4. Update Product
            val updatedP1 = prod1.copy(priceCentimes = 9000L)
            db.products.save(updatedP1)
            assertEquals(9000L, db.products.findById(201L)?.priceCentimes)
        }
    }

    @Test
    fun test03_RegisterSessionOpeningCashBalanceAndClosing() = runBlocking {
        val clock = FixedClock(1_700_000_000_000L)
        WindowsPosDatabase.openInMemory().use { db ->
            val initialCash = 10000L // 100.00 DH

            // 1. Open Session
            val openRes = OpenRegisterSession(db.sessions, clock).execute(1L, 1L, 1L, initialCash)
            assertTrue(openRes is UseCaseResult.Success)
            val session = openRes.value
            assertNotNull(db.sessions.findOpenByUser(1L))

            // 2. Add Cash Order
            val p1 = Product(301L, 1L, "Chebakia", 7000L, 1000, available = true, active = true)
            db.products.save(p1)

            val order1Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                101L, "ORD-101", OrderType.TAKEAWAY, session.id, listOf(301L to 1), null, 1L
            )
            val order1 = (order1Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                order1.id, PaymentMethod.CASH, order1.totalCentimes, "token-101"
            )

            // 3. Add Card Order (50.00 DH) - MUST be excluded from physical cash
            val p2 = Product(302L, 1L, "G�teau", 5000L, 1000, available = true, active = true)
            db.products.save(p2)

            val order2Res = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
                102L, "ORD-102", OrderType.TAKEAWAY, session.id, listOf(302L to 1), null, 1L
            )
            val order2 = (order2Res as UseCaseResult.Success).value
            CompletePayment(db.sessions, db.orders, db.payments, db.transactions).execute(
                order2.id, PaymentMethod.CARD, order2.totalCentimes, "token-102"
            )

            // 4. Verify Expected Physical Cash = 100.00 + 70.00 = 170.00 DH (Card 50.00 excluded)
            val totalCash = db.payments.totalCashForSession(session.id)
            assertEquals(7000L, totalCash)
            val expectedPhysicalDrawerCash = session.openingCashCentimes + totalCash
            assertEquals(17000L, expectedPhysicalDrawerCash)

            // 5. Close Session
            val closingInput = RegisterClosingInput(
                countedCashCentimes = 17000L,
                leftInDrawerCentimes = 5000L,
                removedAmountCentimes = 12000L,
                remittanceReference = "ENV-01",
                remittanceDestination = "Coffre",
                closingNote = "Cl�ture conforme",
                closingUserId = 1L
            )
            val closeRes = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, clock, db.orders)
                .execute(session.id, closingInput)

            assertTrue(closeRes is UseCaseResult.Success)
            val closed = closeRes.value
            assertEquals(RegisterSessionStatus.CLOSED, closed.status)
            assertEquals(17000L, closed.expectedCashCentimes)
            assertEquals(17000L, closed.countedCashCentimes)
            assertEquals(0L, closed.differenceCentimes)
            assertEquals(DiscrepancyKind.EXACT, RegisterClosingRules.classifyDifference(closed.differenceCentimes ?: 0L))
            assertNull(db.sessions.findOpenByUser(1L))
        }
    }

    @Test
    fun test04_ReceiptPrintingRuleQuantity1AndQuantityGreaterThan1() {
        val company = ReceiptCompany(
            name = "P�tisserie & Caf� Royal",
            address = "Boulevard d'Anfa, Casablanca",
            phone = "0522112233",
            ice = "001524368000055",
            taxId = "IF-8877",
            commercialRegister = "RC-9988",
            patente = "PAT-1122",
            printEstablishmentName = true
        )

        val order = Order(
            id = 10L,
            number = "CMD-0010",
            type = OrderType.COUNTER,
            status = OrderStatus.COMPLETED,
            lines = listOf(
                OrderLine(1L, "Chebakia Miel & S�same", 7000L, 1, 1000),
                OrderLine(2L, "Cornes de Gazelle", 8500L, 3, 1000),
                OrderLine(3L, "Caf� Cr�me Sp�cial", 1550L, 10, 1000)
            ),
            subtotalCentimes = 48000L,
            discountCentimes = 0L,
            taxCentimes = 4364L,
            totalCentimes = 48000L,
            registerSessionId = 1L,
            cashierId = 1L
        )

        listOf(58 to 32, 80 to 48).forEach { (paperWidth, colWidth) ->
            val preview = ThermalTicketRenderer.previewText(order, company, TicketKind.CUSTOMER, paperWidth)
            val lines = preview.lines()

            // 1. Single Quantity -> 1 line
            val lineQty1 = lines.first { it.contains("1 x Chebakia") }
            assertTrue(lineQty1.endsWith("70.00 DH"))
            assertEquals(colWidth, lineQty1.length)

            // 2. Quantity 3 -> 2 lines
            val nameQty3 = lines.first { it.contains("3 x Cornes de Gazelle") }
            val calcQty3 = lines.first { it.contains("85.00 DH x 3") }
            assertEquals("3 x Cornes de Gazelle", nameQty3.trim())
            assertTrue(calcQty3.startsWith("   85.00 DH x 3"))
            assertTrue(calcQty3.endsWith("255.00 DH"))
            assertEquals(colWidth, calcQty3.length)

            // 3. Quantity 10 (decimal price) -> 2 lines
            val nameQty10 = lines.first { it.contains("10 x Caf� Cr�me") }
            val calcQty10 = lines.first { it.contains("15.50 DH x 10") }
            assertEquals("10 x Caf� Cr�me Sp�cial", nameQty10.trim())
            assertTrue(calcQty10.startsWith("   15.50 DH x 10"))
            assertTrue(calcQty10.endsWith("155.00 DH"))
            assertEquals(colWidth, calcQty10.length)

            // 4. Binary ESC/POS render contains reset, feed and cut
            val bytes = ThermalTicketRenderer.render(order, company, TicketKind.CUSTOMER, paperWidth)
            assertTrue(bytes.isNotEmpty())
            assertTrue(bytes.take(2).toByteArray().contentEquals(EscPosCommands.INITIALIZE))
            assertTrue(bytes.takeLast(3).toByteArray().contentEquals(byteArrayOf(0x1D, 0x56, 0x00)))
        }
    }

    @Test
    fun test05_PrinterTestLoopGuardAndOfflineSafety() = runBlocking {
        val executionCounter = AtomicInteger(0)
        val inFlightCounter = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val atomicGuard = AtomicBoolean(false)

        val service = LinuxPrinterService(
            commandExecutor = CommandExecutor { _, _, _ ->
                val current = inFlightCounter.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                Thread.sleep(20)
                inFlightCounter.decrementAndGet()
                executionCounter.incrementAndGet()
                CommandResult(0, "request id is POS-PRINTER-01")
            },
            logger = object : ma.elaroui.pos.shared.PlatformLogger {
                override fun log(entry: ma.elaroui.pos.shared.LogEntry) {}
            }
        )

        // Simulate 10 rapid concurrent clicks through the atomic UI guard
        val jobs = (1..10).map {
            async(kotlinx.coroutines.Dispatchers.Default) {
                if (atomicGuard.compareAndSet(false, true)) {
                    try {
                        service.printTestPage("POS-80")
                    } finally {
                        atomicGuard.set(false)
                    }
                }
            }
        }
        jobs.awaitAll()

        assertEquals(1, maxConcurrent.get(), "Concurrency guard must ensure max 1 active test print")
        assertTrue(executionCounter.get() in 1..2)
    }

    @Test
    fun test06_FullBackupAndRestorePreservesAllData() = runBlocking {
        val tempDir = Files.createTempDirectory("final-audit-backup")
        try {
            val paths = DesktopApplicationPathsProvider(
                platform = DesktopPlatform.LINUX,
                userHome = tempDir.toString(),
                temporaryDirectory = tempDir.toString()
            ).paths(createDirectories = true)

            val db = WindowsPosDatabase.open(paths.database)
            db.configureInitialSetup("P�tisserie �toile", "Admin", "1234")
            db.settings.put(AppSetting("establishment_name", "P�tisserie �toile"))
            db.settings.put(AppSetting("seller_ice", "009988776655443"))

            val backupService = DesktopBackupService(db, paths, now = { Instant.ofEpochMilli(1_700_000_000_000L) })
            val backupTarget = paths.backups.resolve("pre-delivery-backup.zip")
            backupService.createBackup(backupTarget)

            assertTrue(Files.isRegularFile(backupTarget))
            val validation = backupService.validate(backupTarget)
            assertTrue(validation.valid, "Backup archive must be valid: ${validation.message}")

            // Stage restore
            backupService.stageRestore(backupTarget)
            db.close()

            // Verify restored db
            val restoredDb = WindowsPosDatabase.open(paths.database)
            assertEquals("P�tisserie �toile", restoredDb.settings.get("establishment_name"))
            assertEquals("009988776655443", restoredDb.settings.get("seller_ice"))
            restoredDb.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
