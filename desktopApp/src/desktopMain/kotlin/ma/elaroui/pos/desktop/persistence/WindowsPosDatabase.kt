package ma.elaroui.pos.desktop.persistence

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.sql.SQLException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ma.elaroui.pos.desktop.security.AdminDeletionSecurity
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategorySaleSample
import ma.elaroui.pos.shared.rules.ProductSaleSample
import ma.elaroui.pos.shared.rules.SessionClosingReport
import ma.elaroui.pos.shared.rules.SessionClosingReportRules

data class SalesSummary(val completedOrders: Int, val salesCentimes: Long, val cashCentimes: Long, val cardCentimes: Long, val taxCentimes: Long)

/** Historical rows have no line allocations. Put the rounding remainder on the
 * final line so their allocated amounts still reconcile to the stored order. */
private fun recognizedLineSql(storedColumn: String, orderColumn: String): String =
    "COALESCE(oi.$storedColumn, CASE WHEN o.subtotal_centimes=0 THEN 0 " +
        "WHEN oi.id=(SELECT MAX(last_line.id) FROM order_items last_line WHERE last_line.order_id=o.id) " +
        "THEN o.$orderColumn-COALESCE((SELECT SUM(other.line_total_centimes*o.$orderColumn/o.subtotal_centimes) " +
        "FROM order_items other WHERE other.order_id=o.id AND other.id<>oi.id),0) " +
        "ELSE oi.line_total_centimes*o.$orderColumn/o.subtotal_centimes END)"
data class SalesBreakdown(val label: String, val quantity: Int, val amountCentimes: Long)
data class RetailSalesAnalytics(
    val byProduct: List<SalesBreakdown>,
    val byCategory: List<SalesBreakdown>,
    val byCashier: List<SalesBreakdown>,
    val cancelledSales: Int
)
data class SalesEvolutionPoint(val label: String, val amountCentimes: Long, val orderCount: Int)
data class SalesHistoryRow(val order: Order, val cashierName: String, val paymentMethod: PaymentMethod?, val paidAtEpochMillis: Long?)
data class SessionHistoryRow(
    val session: RegisterSession,
    val cashierName: String,
    val closingUserName: String? = null,
    val cashSalesCentimes: Long = 0L,
    val cardSalesCentimes: Long = 0L,
    val totalSalesCentimes: Long = 0L,
    val cashInCentimes: Long = 0L,
    val cashOutCentimes: Long = 0L
)
class DesktopValidationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

private const val DUPLICATE_PIN_MESSAGE = "Ce code PIN est déjà utilisé par un autre utilisateur."
private const val DUPLICATE_PRODUCT_MESSAGE = "Un produit avec ce nom existe déjà dans cette catégorie."
private const val DUPLICATE_CATEGORY_MESSAGE = "Une catégorie avec ce nom existe déjà."
private const val DUPLICATE_SKU_MESSAGE = "Un produit avec cette référence (SKU) existe déjà."
private const val DUPLICATE_BARCODE_MESSAGE = "Un produit avec ce code-barres existe déjà."
private const val DUPLICATE_AREA_MESSAGE = "Un espace avec ce nom existe déjà."
private const val DUPLICATE_TABLE_MESSAGE = "Une table avec ce nom existe déjà dans cet espace."

class WindowsPosDatabase private constructor(private val connection: Connection, private val databasePath: Path?) : AutoCloseable {
    private val lock = ReentrantLock(true)
    private var transactionDepth = 0

    val users: UserRepository by lazy { Users() }
    val authentication: AuthenticationRepository by lazy { Authentication() }
    val categories: CategoryRepository by lazy { Categories() }
    val products: ProductRepository by lazy { Products() }
    val tables: TableRepository by lazy { Tables() }
    val orders: OrderRepository by lazy { Orders() }
    val payments: PaymentRepository by lazy { Payments() }
    val sessions: RegisterSessionRepository by lazy { Sessions() }
    val cashMovements: CashMovementRepository by lazy { CashMovements() }
    val settings: SettingsRepository by lazy { Settings() }
    val transactions: TransactionRunner by lazy { Transactions() }

    init {
        connection.createStatement().use {
            it.execute("PRAGMA foreign_keys = ON")
            it.execute("PRAGMA journal_mode = WAL")
            it.execute("PRAGMA busy_timeout = 5000")
            it.execute("PRAGMA synchronous = NORMAL")
            it.execute("PRAGMA cache_size = -4000")
            it.execute("PRAGMA temp_store = MEMORY")
        }
        migrate()
        removeUntouchedLegacyBootstrap()
    }

    companion object {
        fun open(path: Path): WindowsPosDatabase {
            path.parent?.let(Files::createDirectories)
            Class.forName("org.sqlite.JDBC")
            applyPendingRestore(path)
            createPreUpdateSafetyBackup(path)
            return WindowsPosDatabase(DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}"), path)
        }

        private fun createPreUpdateSafetyBackup(live: Path) {
            runCatching {
                if (Files.exists(live) && Files.size(live) > 0L) {
                    val backupsDir = live.parent.resolve("backups")
                    Files.createDirectories(backupsDir)
                    val dateTag = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val backupFile = backupsDir.resolve("auto_pre_update_$dateTag.db")
                    // Include committed WAL pages in the safety snapshot after an unclean shutdown.
                    val escaped = backupFile.toAbsolutePath().toString().replace("'", "''")
                    if (!Files.exists(backupFile)) {
                        DriverManager.getConnection("jdbc:sqlite:${live.toAbsolutePath()}").use { db ->
                            db.createStatement().use { it.execute("VACUUM INTO '$escaped'") }
                        }
                    }

                    // Retain only latest 10 auto_pre_update backups to avoid disk growth
                    Files.list(backupsDir).use { stream ->
                        val autoBackups = stream
                            .filter { it.fileName.toString().startsWith("auto_pre_update_") }
                            .sorted { p1, p2 -> Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1)) }
                            .toList()
                        if (autoBackups.size > 10) {
                            autoBackups.drop(10).forEach { runCatching { Files.deleteIfExists(it) } }
                        }
                    }
                }
            }
        }
        fun openInMemory(): WindowsPosDatabase {
            Class.forName("org.sqlite.JDBC")
            return WindowsPosDatabase(DriverManager.getConnection("jdbc:sqlite::memory:"), null).also {
                // This entry point is used exclusively by the pre-existing legacy
                // compatibility suites. Production databases use open(path) and start empty.
                it.seedBaseFixturesForTests()
                it.seedLegacyCompatibilityFixturesForTests()
            }
        }
        object PinHasher {
            fun hash(pin: String): String {
                val salt = ma.elaroui.pos.desktop.security.PinSecurity.generateSalt()
                val hash = ma.elaroui.pos.desktop.security.PinSecurity.hashPin(pin, salt)
                return "pbkdf2:$salt:$hash"
            }

            fun verify(pin: String, storedHash: String?): Boolean {
                if (storedHash.isNullOrBlank()) return false
                return if (storedHash.startsWith("pbkdf2:")) {
                    val parts = storedHash.split(":")
                    if (parts.size != 3) false
                    else {
                        val salt = parts[1]
                        val expectedHash = parts[2]
                        ma.elaroui.pos.desktop.security.PinSecurity.verifyPin(pin, expectedHash, salt)
                    }
                } else {
                    val legacy = MessageDigest.getInstance("SHA-256")
                        .digest(pin.toByteArray(StandardCharsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    MessageDigest.isEqual(legacy.toByteArray(StandardCharsets.UTF_8), storedHash.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
        fun hashCredential(value: String): String = PinHasher.hash(value)

        private fun applyPendingRestore(live: Path) {
            val pending = live.resolveSibling("${live.fileName}.restore-pending")
            if (!Files.exists(pending)) return
            val safety = live.resolveSibling("${live.fileName}.pre-restore-safety")
            val candidate = live.resolveSibling("${live.fileName}.restore-candidate")
            val failed = live.resolveSibling("${live.fileName}.restore-failed")
            val images = live.parent.resolve("images")
            val stagedImages = live.parent.resolve("images.restore-candidate")
            val safetyImages = live.parent.resolve("images.pre-restore-safety")
            var hadImages = false
            try {
                require(isValidSqlite(pending)) { "Pending restore database is invalid" }
                if (Files.exists(live)) Files.copy(live, safety, StandardCopyOption.REPLACE_EXISTING)
                Files.copy(pending, candidate, StandardCopyOption.REPLACE_EXISTING)
                hadImages = extractManagedImages(candidate, stagedImages, images)
                require(isValidSqlite(candidate)) { "Restored database failed integrity verification" }
                if (hadImages && Files.exists(images)) copyTree(images, safetyImages)
                moveReplacing(candidate, live)
                if (hadImages) {
                    deleteTree(images)
                    moveReplacing(stagedImages, images)
                }
                require(isValidSqlite(live)) { "Restored live database failed integrity verification" }
                Files.deleteIfExists(pending)
                Files.deleteIfExists(safety)
                deleteTree(safetyImages)
            } catch (error: Throwable) {
                if (Files.exists(safety)) Files.copy(safety, live, StandardCopyOption.REPLACE_EXISTING)
                if (hadImages && Files.exists(safetyImages)) {
                    deleteTree(images)
                    moveReplacing(safetyImages, images)
                }
                runCatching { moveReplacing(pending, failed) }
                Files.deleteIfExists(candidate)
                deleteTree(stagedImages)
                // Keep the original installation usable; the failed file remains available for diagnosis.
                System.err.println("Restore was rejected and rolled back: ${error.message}")
            } finally {
                Files.deleteIfExists(safety)
                Files.deleteIfExists(candidate)
                deleteTree(stagedImages)
                deleteTree(safetyImages)
            }
        }

        private fun isValidSqlite(path: Path): Boolean = runCatching {
            require(Files.isRegularFile(path) && Files.size(path) > 0L)
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { db ->
                db.createStatement().use { statement ->
                    val intact = statement.executeQuery("PRAGMA integrity_check").use { it.next() && it.getString(1) == "ok" }
                    if (!intact) return@use false
                    val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rows ->
                        buildSet { while (rows.next()) add(rows.getString(1)) }
                    }
                    val required = setOf("schema_migrations", "users", "categories", "products", "registers",
                        "register_sessions", "cash_movements", "orders", "order_items", "payments", "settings",
                        "restaurant_tables", "dining_areas", "audit_logs")
                    if (!tables.containsAll(required)) return@use false
                    val version = statement.executeQuery("SELECT MAX(version) FROM schema_migrations").use { it.next(); it.getInt(1) }
                    if (version !in 1..12) return@use false
                    statement.executeQuery("PRAGMA foreign_key_check").use { !it.next() }
                }
            }
        }.getOrDefault(false)

        private fun extractManagedImages(database: Path, staging: Path, finalRoot: Path): Boolean {
            deleteTree(staging)
            return DriverManager.getConnection("jdbc:sqlite:${database.toAbsolutePath()}").use { db ->
                val exists = db.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='backup_managed_files'").use { it.next() && it.getInt(1) > 0 }
                }
                if (!exists) return@use false
                Files.createDirectories(staging)
                val mappings = mutableMapOf<String, String>()
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT relative_path,data FROM backup_managed_files").use { rows ->
                        while (rows.next()) {
                            val relative = Path.of(rows.getString(1).replace('\\', '/')).normalize()
                            require(!relative.isAbsolute && !relative.startsWith("..")) { "Unsafe image path in backup" }
                            val target = staging.resolve(relative).normalize()
                            require(target.startsWith(staging)) { "Unsafe image path in backup" }
                            target.parent?.let(Files::createDirectories)
                            Files.write(target, rows.getBytes(2))
                            mappings[relative.toString().replace('\\', '/')] = finalRoot.resolve(relative).toString()
                        }
                    }
                }
                rewriteManagedImagePaths(db, "products", mappings)
                rewriteManagedImagePaths(db, "dining_areas", mappings)
                val hasCategoryImage = db.createStatement().use { s ->
                    s.executeQuery("PRAGMA table_info(categories)").use { rows ->
                        var found = false
                        while (rows.next()) if (rows.getString("name") == "image_path") found = true
                        found
                    }
                }
                if (hasCategoryImage) rewriteManagedImagePaths(db, "categories", mappings)
                true
            }
        }

        private fun rewriteManagedImagePaths(db: Connection, table: String, mappings: Map<String, String>) {
            if (mappings.isEmpty()) return
            val updates = mutableListOf<Pair<Long, String>>()
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT id,image_path FROM $table WHERE image_path IS NOT NULL AND image_path<>''").use { rows ->
                    while (rows.next()) {
                        val old = rows.getString(2).replace('\\', '/')
                        val match = mappings.entries.firstOrNull { old.endsWith(it.key) || old.substringAfterLast('/') == it.key.substringAfterLast('/') }
                        match?.let { updates += rows.getLong(1) to it.value }
                    }
                }
            }
            db.prepareStatement("UPDATE $table SET image_path=? WHERE id=?").use { update ->
                updates.forEach { (id, path) -> update.setString(1, path); update.setLong(2, id); update.addBatch() }
                update.executeBatch()
            }
        }

        private fun copyTree(source: Path, target: Path) {
            deleteTree(target)
            Files.walk(source).use { entries -> entries.forEach { entry ->
                val destination = target.resolve(source.relativize(entry))
                if (Files.isDirectory(entry)) Files.createDirectories(destination)
                else { destination.parent?.let(Files::createDirectories); Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING) }
            } }
        }

        private fun deleteTree(root: Path) {
            if (!Files.exists(root)) return
            Files.walk(root).use { entries -> entries.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }

        private fun moveReplacing(source: Path, target: Path) {
            target.parent?.let(Files::createDirectories)
            try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    private fun seedLegacyCompatibilityFixturesForTests() = lock.withLock {
        transactionBlocking {
            val legacyCategories = listOf("Café", "Petit déjeuner", "Boissons")
            legacyCategories.forEachIndexed { index, name ->
                connection.prepareStatement("INSERT OR IGNORE INTO categories(id,name,display_order,active) VALUES(?,?,?,1)").use {
                    it.setLong(1, 5L + index); it.setString(2, name); it.setInt(3, 10 + index); it.executeUpdate()
                }
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT OR IGNORE INTO dining_areas(id,name,display_order,active) VALUES(1,'Legacy Area',0,1)")
            }
            (1L..10L).forEach { id ->
                connection.prepareStatement("INSERT OR IGNORE INTO restaurant_tables(id,area_id,name,status,active,display_order) VALUES(?,1,?,'AVAILABLE',1,?)").use {
                    it.setLong(1, id); it.setString(2, "T-$id"); it.setLong(3, id); it.executeUpdate()
                }
            }
            (1L..34L).forEach { id ->
                connection.prepareStatement("INSERT OR IGNORE INTO products(id,category_id,name,price_centimes,tax_basis_points,available,active,display_order) VALUES(?,5,?,?,1000,1,1,?)").use {
                    it.setLong(1, id)
                    it.setString(2, if (id == 1L) "Legacy Item" else "Legacy Item $id")
                    it.setLong(3, if (id == 1L) 1_000L else 1_500L + id * 100L)
                    it.setLong(4, id)
                    it.executeUpdate()
                }
            }
        }
    }

    private fun removeUntouchedLegacyBootstrap() = lock.withLock {
        val setupComplete = connection.prepareStatement("SELECT value FROM settings WHERE key='setup_complete'").use { statement ->
            statement.executeQuery().use { rows -> rows.next() && rows.getString(1).equals("true", ignoreCase = true) }
        }
        if (setupComplete) return@withLock

        val ownerMatches = connection.prepareStatement(
            "SELECT pin_hash FROM users WHERE id=1 AND name='Owner' AND role='OWNER' AND active=1"
        ).use { statement ->
            statement.executeQuery().use { rows -> rows.next() && PinHasher.verify("1234", rows.getString(1)) }
        }
        val userCount = countRows("users")
        val registerMatches = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM registers WHERE id=1 AND name='Main Register' AND active=1").use { rows ->
                rows.next(); rows.getInt(1) == 1
            }
        }
        val expectedCategories = setOf("General", "Food & Grocery", "Household", "Personal Care")
        val expectedPastryCategories = setOf("Pâtisserie", "Viennoiserie", "Gâteaux & Tartes", "Boissons")
        val categoryNames = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM categories").use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }
            }
        }
        val hasBusinessData = listOf(
            "products", "orders", "register_sessions", "dining_areas", "restaurant_tables"
        ).any { countRows(it) != 0 }

        if (ownerMatches && userCount == 1 && registerMatches && countRows("registers") == 1 &&
            (categoryNames == expectedCategories || categoryNames == expectedPastryCategories) && !hasBusinessData
        ) {
            transactionBlocking {
                connection.createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM categories")
                    statement.executeUpdate("DELETE FROM users")
                    statement.executeUpdate("DELETE FROM registers")
                    statement.executeUpdate("DELETE FROM settings WHERE key='establishment_name' AND (value='General POS' OR value='PATISSERIE_POS')")
                }
            }
        }
    }

    private fun countRows(table: String): Int {
        require(table in setOf("users", "registers", "categories", "products", "orders", "register_sessions", "dining_areas", "restaurant_tables"))
        return connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows -> rows.next(); rows.getInt(1) }
        }
    }

    fun nextId(table: String): Long {
        require(table in setOf("users", "categories", "products", "dining_areas", "restaurant_tables", "register_sessions", "orders"))
        return read { c -> c.createStatement().use { s -> s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM $table").use { it.next(); it.getLong(1) } } }
    }

    fun schemaVersion(): Int = read { c ->
        c.createStatement().use { statement ->
            statement.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_migrations").use { rows ->
                if (rows.next()) rows.getInt(1) else 0
            }
        }
    }

    fun configureInitialSetup(
        establishmentName: String,
        ownerName: String,
        ownerPin: String
    ) = read { c ->
        require(establishmentName.isNotBlank() && ownerName.isNotBlank())
        require(ma.elaroui.pos.shared.rules.PinValidationRules.isValid(ownerPin)) { "Le PIN doit comporter 4 à 6 chiffres." }
        transactionBlocking {
            val ownerId = c.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM users WHERE role='OWNER' ORDER BY id LIMIT 1").use { rows ->
                    if (rows.next()) rows.getLong(1) else null
                }
            }
            if (ownerId == null) {
                val newOwnerId = c.createStatement().use { s -> s.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM users").use { it.next(); it.getLong(1) } }
                c.prepareStatement("INSERT INTO users(id,name,role,pin_hash,active,created_at) VALUES(?,?,'OWNER',?,1,?)").use {
                    it.setLong(1, newOwnerId)
                    it.setString(2, ownerName.trim())
                    it.setString(3, hashCredential(ownerPin))
                    it.setLong(4, System.currentTimeMillis())
                    it.executeUpdate()
                }
            } else {
                c.prepareStatement("UPDATE users SET name=?,pin_hash=?,active=1 WHERE id=?").use {
                    it.setString(1, ownerName.trim())
                    it.setString(2, hashCredential(ownerPin))
                    it.setLong(3, ownerId)
                    it.executeUpdate()
                }
            }
            val registerCount = c.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM registers").use { rows -> rows.next(); rows.getInt(1) }
            }
            if (registerCount == 0) {
                c.createStatement().use { it.executeUpdate("INSERT INTO registers(id,name,active) VALUES(1,'Main Register',1)") }
            }
            c.prepareStatement("INSERT INTO settings(key,value) VALUES('establishment_name',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use { it.setString(1,establishmentName.trim());it.executeUpdate() }
            c.createStatement().use { it.executeUpdate("INSERT INTO settings(key,value) VALUES('setup_complete','true') ON CONFLICT(key) DO UPDATE SET value='true'") }
        }
    }

    fun findProductByBarcode(barcode: String): Product? = (products as? Products)?.findByBarcode(barcode)
    fun findProductBySku(sku: String): Product? = (products as? Products)?.findBySku(sku)

    fun createCashier(name: String, pin: String): Long = read { c ->
        require(name.isNotBlank())
        require(ma.elaroui.pos.shared.rules.PinValidationRules.isValid(pin)) { "Le PIN doit comporter 4 à 6 chiffres." }
        ensurePinAvailable(c, pin, null)
        val id=nextId("users")
        try {
            c.prepareStatement("INSERT INTO users(id,name,role,pin_hash,active,created_at) VALUES(?,?,'CASHIER',?,1,?)").use { it.setLong(1,id);it.setString(2,name.trim());it.setString(3,hashCredential(pin));it.setLong(4,System.currentTimeMillis());it.executeUpdate() }
            (users as? Users)?.refreshState()
        } catch (error: SQLException) { throw mapPinConstraint(error) }
        id
    }

    fun updateCashier(id:Long,name:String?=null,pin:String?=null,active:Boolean?=null)=read{c->
        require(id!=1L)
        if (pin != null) {
            require(ma.elaroui.pos.shared.rules.PinValidationRules.isValid(pin)) { "Le PIN doit comporter 4 à 6 chiffres." }
            ensurePinAvailable(c, pin, id)
        }
        val updates=buildList{if(!name.isNullOrBlank())add("name" to name.trim());if(pin!=null)add("pin_hash" to hashCredential(pin));active?.let{add("active" to if(it)1 else 0)}}
        require(updates.isNotEmpty())
        try {
            c.prepareStatement("UPDATE users SET ${updates.joinToString{it.first+"=?"}} WHERE id=? AND role='CASHIER'").use{p->updates.forEachIndexed{i,v->p.setObject(i+1,v.second)};p.setLong(updates.size+1,id);p.executeUpdate()}
            (users as? Users)?.refreshState()
        }
        catch (error: SQLException) { throw mapPinConstraint(error) }
    }

    private fun ensurePinAvailable(c: Connection, pin: String, excludedUserId: Long?) {
        val sql = "SELECT id, pin_hash FROM users WHERE deleted_at IS NULL" + if (excludedUserId == null) "" else " AND id<>?"
        c.prepareStatement(sql).use { statement ->
            excludedUserId?.let { statement.setLong(1, it) }
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    if (PinHasher.verify(pin, rs.getString(2))) {
                        throw DesktopValidationException(DUPLICATE_PIN_MESSAGE)
                    }
                }
            }
        }
    }

    private fun mapPinConstraint(error: SQLException): Throwable =
        if (error.message.orEmpty().contains("users.pin_hash", ignoreCase = true) ||
            error.message.orEmpty().contains("UNIQUE constraint", ignoreCase = true)
        ) DesktopValidationException(DUPLICATE_PIN_MESSAGE, error) else error

    fun allUsers(): List<User> = read { c -> c.createStatement().use { s -> s.executeQuery("SELECT id,name,role,active FROM users WHERE deleted_at IS NULL ORDER BY name").use { r -> r.map { User(getLong(1),getString(2),UserRole.valueOf(getString(3)),getInt(4)!=0) } } } }
    fun allTables(): List<RestaurantTable> = read { c -> c.createStatement().use { s -> s.executeQuery("SELECT id,area_id,name,status,active,display_order FROM restaurant_tables WHERE deleted_at IS NULL ORDER BY display_order,name").use { r -> r.map { RestaurantTable(getLong(1),getLong(2),getString(3),TableStatus.valueOf(getString(4)),getInt(5)!=0,getInt(6)) } } } }

    fun areas(): List<DiningArea> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,name,active,display_order,image_path FROM dining_areas WHERE deleted_at IS NULL ORDER BY display_order,name").use { r ->
                r.map { DiningArea(getLong(1), getString(2), getInt(3) != 0, getInt(4), getString(5)) }
            }
        }
    }

    private fun ensureAreaUnique(c: Connection, name: String, excludedAreaId: Long?) {
        val sql = "SELECT 1 FROM dining_areas WHERE name=? COLLATE NOCASE AND deleted_at IS NULL" +
            if (excludedAreaId == null) "" else " AND id<>?"
        c.prepareStatement(sql).use { statement ->
            statement.setString(1, name.trim())
            excludedAreaId?.let { statement.setLong(2, it) }
            statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_AREA_MESSAGE) }
        }
    }

    private fun mapAreaConstraint(error: SQLException): Throwable =
        if (error.message.orEmpty().contains("dining_areas.name", ignoreCase = true) ||
            error.message.orEmpty().contains("UNIQUE constraint", ignoreCase = true)
        ) DesktopValidationException(DUPLICATE_AREA_MESSAGE, error) else error

    fun saveArea(area: DiningArea): Long = read { c ->
        val id = if (area.id == 0L) nextId("dining_areas") else area.id
        try {
            ensureAreaUnique(c, area.name, area.id.takeIf { it != 0L })
            upsert(
                c, "dining_areas", id,
                listOf(
                    "name" to area.name.trim(),
                    "active" to area.active,
                    "display_order" to area.displayOrder,
                    "image_path" to area.imagePath
                )
            )
        } catch (error: SQLException) {
            throw mapAreaConstraint(error)
        }
    }

    fun softDeleteArea(id: Long) = read { c ->
        c.prepareStatement("UPDATE dining_areas SET deleted_at=?, active=0 WHERE id=?").use {
            it.setLong(1, System.currentTimeMillis())
            it.setLong(2, id)
            check(it.executeUpdate() == 1)
        }
    }

    fun restoreArea(id: Long) = read { c ->
        c.prepareStatement("UPDATE dining_areas SET deleted_at=NULL, active=1 WHERE id=?").use {
            it.setLong(1, id)
            check(it.executeUpdate() == 1)
        }
    }

    fun deletedAreas(): List<DiningArea> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,name,active,display_order,image_path FROM dining_areas WHERE deleted_at IS NOT NULL ORDER BY name").use { r ->
                r.map { DiningArea(getLong(1), getString(2), getInt(3) != 0, getInt(4), getString(5)) }
            }
        }
    }

    private fun ensureTableUnique(c: Connection, areaId: Long, name: String, excludedTableId: Long?) {
        val sql = "SELECT 1 FROM restaurant_tables WHERE area_id=? AND name=? COLLATE NOCASE AND deleted_at IS NULL" +
            if (excludedTableId == null) "" else " AND id<>?"
        c.prepareStatement(sql).use { statement ->
            statement.setLong(1, areaId)
            statement.setString(2, name.trim())
            excludedTableId?.let { statement.setLong(3, it) }
            statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_TABLE_MESSAGE) }
        }
    }

    private fun mapTableConstraint(error: SQLException): Throwable =
        if (error.message.orEmpty().contains("restaurant_tables.name", ignoreCase = true) ||
            error.message.orEmpty().contains("restaurant_tables.area_id", ignoreCase = true) ||
            error.message.orEmpty().contains("UNIQUE constraint", ignoreCase = true)
        ) DesktopValidationException(DUPLICATE_TABLE_MESSAGE, error) else error

    fun createTable(areaId: Long, name: String): Long = read { c ->
        val id = nextId("restaurant_tables")
        try {
            ensureTableUnique(c, areaId, name, null)
            upsert(c, "restaurant_tables", id, listOf("area_id" to areaId, "name" to name.trim(), "status" to "AVAILABLE", "active" to true, "display_order" to 0))
                .also { (tables as? Tables)?.refreshState() }
        } catch (error: SQLException) {
            throw mapTableConstraint(error)
        }
    }

    fun updateTable(id: Long, name: String, areaId: Long? = null) = read { c ->
        require(name.isNotBlank()) { "Le nom de la table est obligatoire." }
        val targetAreaId = areaId ?: c.prepareStatement("SELECT area_id FROM restaurant_tables WHERE id=?").use { s ->
            s.setLong(1, id)
            s.executeQuery().use { if (it.next()) it.getLong(1) else throw IllegalArgumentException("Table introuvable") }
        }
        ensureTableUnique(c, targetAreaId, name, id)
        try {
            c.prepareStatement("UPDATE restaurant_tables SET name=?, area_id=? WHERE id=?").use {
                it.setString(1, name.trim())
                it.setLong(2, targetAreaId)
                it.setLong(3, id)
                check(it.executeUpdate() == 1)
            }
            (tables as? Tables)?.refreshState()
        } catch (error: SQLException) {
            throw mapTableConstraint(error)
        }
    }

    fun softDeleteTable(id: Long) = read { c ->
        c.prepareStatement("UPDATE restaurant_tables SET deleted_at=?, active=0, status='AVAILABLE' WHERE id=?").use {
            it.setLong(1, System.currentTimeMillis())
            it.setLong(2, id)
            check(it.executeUpdate() == 1)
        }
        (tables as? Tables)?.refreshState()
    }

    fun restoreTable(id: Long) = read { c ->
        c.prepareStatement("UPDATE restaurant_tables SET deleted_at=NULL, active=1 WHERE id=?").use {
            it.setLong(1, id)
            check(it.executeUpdate() == 1)
        }
        (tables as? Tables)?.refreshState()
    }

    fun deletedTables(): List<RestaurantTable> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,area_id,name,status,active,display_order FROM restaurant_tables WHERE deleted_at IS NOT NULL ORDER BY name").use { r ->
                r.map { RestaurantTable(getLong(1), getLong(2), getString(3), TableStatus.valueOf(getString(4)), getInt(5) != 0, getInt(6)) }
            }
        }
    }

    fun softDeleteCashier(id: Long) = read { c ->
        require(id != 1L) { "Le compte propriétaire/administrateur principal ne peut pas être supprimé." }
        c.prepareStatement("UPDATE users SET deleted_at=?, active=0 WHERE id=? AND role='CASHIER'").use {
            it.setLong(1, System.currentTimeMillis())
            it.setLong(2, id)
            check(it.executeUpdate() == 1)
        }
        (users as? Users)?.refreshState()
    }

    fun restoreCashier(id: Long) = read { c ->
        val collision = c.prepareStatement("SELECT 1 FROM users active JOIN users archived ON archived.id=? WHERE active.id<>archived.id AND active.deleted_at IS NULL AND active.pin_hash=archived.pin_hash LIMIT 1").use {
            it.setLong(1, id)
            it.executeQuery().use(ResultSet::next)
        }
        if (collision) throw DesktopValidationException(DUPLICATE_PIN_MESSAGE)
        try {
            c.prepareStatement("UPDATE users SET deleted_at=NULL, active=1 WHERE id=? AND role='CASHIER'").use {
                it.setLong(1, id)
                check(it.executeUpdate() == 1)
            }
        } catch (error: SQLException) { throw mapPinConstraint(error) }
        (users as? Users)?.refreshState()
    }

    fun deletedUsers(): List<User> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,name,role,active FROM users WHERE deleted_at IS NOT NULL AND role='CASHIER' ORDER BY name").use { r ->
                r.map { User(getLong(1), getString(2), UserRole.valueOf(getString(3)), getInt(4) != 0) }
            }
        }
    }

    fun softDeleteProduct(id: Long) = read { c ->
        c.prepareStatement("UPDATE products SET deleted_at=?, active=0, available=0 WHERE id=?").use {
            it.setLong(1, System.currentTimeMillis())
            it.setLong(2, id)
            check(it.executeUpdate() == 1)
        }
        (products as? Products)?.refreshState()
    }

    fun restoreProduct(id: Long) = read { c ->
        val archived = c.prepareStatement("SELECT category_id,name,sku,barcode FROM products WHERE id=? AND deleted_at IS NOT NULL").use {
            it.setLong(1, id)
            it.executeQuery().use { row ->
                if (!row.next()) throw IllegalArgumentException("Produit archivé introuvable.")
                arrayOf(row.getString(1), row.getString(2), row.getString(3), row.getString(4))
            }
        }
        val collision = c.prepareStatement("SELECT 1 FROM products WHERE id<>? AND deleted_at IS NULL AND (category_id=? AND name=? COLLATE NOCASE OR (? IS NOT NULL AND ?<>'' AND sku=?) OR (? IS NOT NULL AND ?<>'' AND barcode=?)) LIMIT 1").use {
            it.setLong(1, id)
            it.setLong(2, archived[0]!!.toLong())
            it.setString(3, archived[1])
            it.setString(4, archived[2]); it.setString(5, archived[2]); it.setString(6, archived[2])
            it.setString(7, archived[3]); it.setString(8, archived[3]); it.setString(9, archived[3])
            it.executeQuery().use(ResultSet::next)
        }
        if (collision) throw DesktopValidationException("Impossible de restaurer ce produit : son nom, SKU ou code-barres est déjà utilisé.")
        c.prepareStatement("UPDATE products SET deleted_at=NULL, active=1, available=1 WHERE id=?").use {
            it.setLong(1, id)
            check(it.executeUpdate() == 1)
        }
        (products as? Products)?.refreshState()
    }

    fun deletedProducts(): List<Product> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,category_id,name,price_centimes,tax_basis_points,available,active,image_path,sku,barcode,name_arabic,unit,description FROM products WHERE deleted_at IS NOT NULL ORDER BY name").use { r ->
                r.map { Product(getLong(1), getLong(2), getString(3), getLong(4), getInt(5), getInt(6) != 0, getInt(7) != 0, getString(8), getString(9), getString(10), getString(11), getString(12), getString(13)) }
            }
        }
    }

    fun saveCategoryWithSubcategory(category: Category, subcategory: Category): Pair<Long, Long> = read { c ->
        transactionBlocking {
            val catRepo = (categories as Categories)
            val catId = if (category.id == 0L) nextId("categories") else category.id
            catRepo.ensureCategoryUnique(c, category.name, null, category.id.takeIf { it != 0L })
            upsert(
                c, "categories", catId, listOf(
                    "name" to category.name.trim(),
                    "active" to category.active,
                    "display_order" to category.displayOrder,
                    "parent_id" to null,
                    "image_path" to category.imagePath
                )
            )

            val existingSubId = if (subcategory.id != 0L) subcategory.id else {
                c.prepareStatement("SELECT id FROM categories WHERE parent_id=? AND deleted_at IS NULL LIMIT 1").use { s ->
                    s.setLong(1, catId)
                    s.executeQuery().use { if (it.next()) it.getLong(1) else 0L }
                }
            }
            val subId = if (existingSubId == 0L) nextId("categories") else existingSubId
            catRepo.ensureCategoryUnique(c, subcategory.name, catId, subId.takeIf { it != 0L })
            upsert(
                c, "categories", subId, listOf(
                    "name" to subcategory.name.trim(),
                    "active" to category.active,
                    "display_order" to subcategory.displayOrder,
                    "parent_id" to catId,
                    "image_path" to subcategory.imagePath
                )
            )
            catRepo.refreshState()
            catId to subId
        }
    }

    fun softDeleteCategory(id: Long) = read { c ->
        transactionBlocking {
            val now = System.currentTimeMillis()
            val sql = """
                WITH RECURSIVE cat_tree(cat_id) AS (
                    SELECT id FROM categories WHERE id = ?
                    UNION ALL
                    SELECT c.id FROM categories c JOIN cat_tree ct ON c.parent_id = ct.cat_id
                )
                UPDATE categories SET deleted_at = ?, active = 0 WHERE id IN (SELECT cat_id FROM cat_tree)
            """.trimIndent()
            c.prepareStatement(sql).use {
                it.setLong(1, id)
                it.setLong(2, now)
                it.executeUpdate()
            }
            val sqlProducts = """
                WITH RECURSIVE cat_tree(cat_id) AS (
                    SELECT id FROM categories WHERE id = ?
                    UNION ALL
                    SELECT c.id FROM categories c JOIN cat_tree ct ON c.parent_id = ct.cat_id
                )
                UPDATE products SET category_id = NULL WHERE category_id IN (SELECT cat_id FROM cat_tree)
            """.trimIndent()
            c.prepareStatement(sqlProducts).use {
                it.setLong(1, id)
                it.executeUpdate()
            }
            (categories as? Categories)?.refreshState()
            (products as? Products)?.refreshState()
        }
    }

    fun restoreCategory(id: Long) = read { c ->
        transactionBlocking {
            val collision = c.prepareStatement("SELECT 1 FROM categories active JOIN categories archived ON archived.id=? WHERE active.id<>archived.id AND active.deleted_at IS NULL AND active.name=archived.name COLLATE NOCASE AND IFNULL(active.parent_id, 0)=IFNULL(archived.parent_id, 0) LIMIT 1").use {
                it.setLong(1, id)
                it.executeQuery().use(ResultSet::next)
            }
            if (collision) throw DesktopValidationException(DUPLICATE_CATEGORY_MESSAGE)
            val sql = """
                WITH RECURSIVE cat_tree(cat_id) AS (
                    SELECT id FROM categories WHERE id = ?
                    UNION ALL
                    SELECT c.id FROM categories c JOIN cat_tree ct ON c.parent_id = ct.cat_id
                )
                UPDATE categories SET deleted_at = NULL, active = 1 WHERE id IN (SELECT cat_id FROM cat_tree)
            """.trimIndent()
            c.prepareStatement(sql).use {
                it.setLong(1, id)
                it.executeUpdate()
            }
            (categories as? Categories)?.refreshState()
        }
    }

    fun deletedCategories(): List<Category> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id,name,active,display_order,parent_id,image_path FROM categories WHERE deleted_at IS NOT NULL ORDER BY name").use { r ->
                r.map { Category(getLong(1), getString(2), getInt(3) != 0, getInt(4), nullLong("parent_id"), getString(6)) }
            }
        }
    }

    fun isOrderCancellationPinConfigured(): Boolean = read { c ->
        c.prepareStatement("SELECT value FROM settings WHERE key='order_cancellation_pin_hash'").use { s ->
            s.executeQuery().use { it.next() && !it.getString(1).isNullOrBlank() }
        }
    }

    fun setOrderCancellationPin(pin: String) = read { c ->
        require(ma.elaroui.pos.shared.rules.PinValidationRules.isValid(pin)) {
            "Le code PIN d'annulation doit comporter 4 à 6 chiffres."
        }
        val salt = ma.elaroui.pos.desktop.security.PinSecurity.generateSalt()
        val hash = ma.elaroui.pos.desktop.security.PinSecurity.hashPin(pin, salt)
        transactionBlocking {
            c.prepareStatement("INSERT INTO settings(key,value) VALUES('order_cancellation_pin_salt',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use {
                it.setString(1, salt)
                it.executeUpdate()
            }
            c.prepareStatement("INSERT INTO settings(key,value) VALUES('order_cancellation_pin_hash',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use {
                it.setString(1, hash)
                it.executeUpdate()
            }
        }
    }

    fun removeOrderCancellationPin() = read { c ->
        transactionBlocking {
            c.prepareStatement("DELETE FROM settings WHERE key IN ('order_cancellation_pin_hash','order_cancellation_pin_salt')").use {
                it.executeUpdate()
            }
        }
    }

    fun cancellationPinSaltAndHash(): Pair<String?, String?> = read { c ->
        val salt = c.prepareStatement("SELECT value FROM settings WHERE key='order_cancellation_pin_salt'").use { s ->
            s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
        val hash = c.prepareStatement("SELECT value FROM settings WHERE key='order_cancellation_pin_hash'").use { s ->
            s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
        salt to hash
    }

    fun verifyOrderCancellationPin(pin: String): Boolean = read { c ->
        if (!ma.elaroui.pos.shared.rules.PinValidationRules.isValid(pin)) return@read false
        val (salt, hash) = cancellationPinSaltAndHash()
        if (salt.isNullOrBlank() || hash.isNullOrBlank()) return@read false
        ma.elaroui.pos.desktop.security.PinSecurity.verifyPin(pin, hash, salt)
    }

    fun updateOwnerPin(newPin: String) = read { c ->
        require(ma.elaroui.pos.shared.rules.PinValidationRules.isValid(newPin)) { "Le PIN doit comporter 4 à 6 chiffres." }
        ensurePinAvailable(c, newPin, 1L)
        try {
            c.prepareStatement("UPDATE users SET pin_hash=? WHERE id=1 AND role='OWNER'").use {
                it.setString(1, hashCredential(newPin))
                check(it.executeUpdate() == 1)
            }
            (users as? Users)?.refreshState()
        } catch (error: SQLException) {
            throw mapPinConstraint(error)
        }
    }

    fun cancelOrder(orderId: Long, reason: String, userId: Long, cancellationPin: String? = null) = read { c ->
        require(reason.isNotBlank()) { "Cancellation reason is required" }
        val actingUserRole = c.prepareStatement("SELECT role FROM users WHERE id=? AND active=1 AND deleted_at IS NULL").use { s ->
            s.setLong(1, userId)
            s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
        requireNotNull(actingUserRole) { "Utilisateur actif introuvable." }
        val isOwner = actingUserRole == "OWNER"
        val pinConfigured = isOrderCancellationPinConfigured()
        val pinRequired = !isOwner && pinConfigured

        if (pinRequired) {
            require(!cancellationPin.isNullOrBlank()) { "Le code PIN d'annulation est requis." }
            require(verifyOrderCancellationPin(cancellationPin)) { "Code PIN d'annulation incorrect." }
        }

        transactionBlocking {
            val oldTable = orderTable(c, orderId)
            val orderNumber = c.prepareStatement("SELECT order_number FROM orders WHERE id=?").use { s ->
                s.setLong(1, orderId)
                s.executeQuery().use { if (it.next()) it.getString(1) else orderId.toString() }
            }
            c.prepareStatement("UPDATE orders SET status='CANCELLED',updated_at=? WHERE id=? AND status='OPEN'").use {
                it.setLong(1, System.currentTimeMillis())
                it.setLong(2, orderId)
                check(it.executeUpdate() == 1)
            }
            releaseTableIfUnused(c, oldTable, orderId)
            c.prepareStatement("INSERT INTO audit_logs(action_type,acting_user_id,entity_type,entity_id,created_at,details) VALUES('ORDER_CANCELLED',?,'ORDER',?,?,?)").use {
                it.setLong(1, userId)
                it.setLong(2, orderId)
                it.setLong(3, System.currentTimeMillis())
                it.setString(4, "reason=${reason.trim()};order_number=$orderNumber;pin_required=$pinRequired")
                it.executeUpdate()
            }
        }
    }
    fun moveOrder(orderId:Long,newTableId:Long?)=read{c->transactionBlocking{val oldTable=orderTable(c,orderId);if(newTableId!=null){c.prepareStatement("SELECT status,active FROM restaurant_tables WHERE id=?").use{it.setLong(1,newTableId);it.executeQuery().use{r->check(r.next()&&r.getInt("active")!=0&&r.getString("status")!="OCCUPIED")}}};c.prepareStatement("UPDATE orders SET table_id=?,updated_at=? WHERE id=? AND status='OPEN'").use{it.setObject(1,newTableId);it.setLong(2,System.currentTimeMillis());it.setLong(3,orderId);check(it.executeUpdate()==1)};releaseTableIfUnused(c,oldTable,orderId);newTableId?.let{setTableStatus(c,it,TableStatus.OCCUPIED)}}}
    fun orderHistory(status: OrderStatus? = null): List<Order> = read { c ->
        val sql = "SELECT id, order_number, type, status, subtotal_centimes, discount_centimes, discount_basis_points, tax_centimes, total_centimes, table_id, register_session_id, cashier_id, created_at, updated_at, customer_name, customer_phone, pickup_date, preparation_status, custom_note, deposit_centimes FROM orders" +
            (status?.let { " WHERE status='${it.name}'" } ?: "") + " ORDER BY updated_at DESC"
        val rawOrders = c.createStatement().use { s ->
            s.executeQuery(sql).use { r ->
                r.map {
                    val id = getLong("id")
                    val createdAt = getLong("created_at").takeIf { it > 0L } ?: getLong("updated_at")
                    val prepStatusStr = getString("preparation_status")
                    val prepStatus = if (!prepStatusStr.isNullOrBlank()) {
                        runCatching { PreparationStatus.valueOf(prepStatusStr) }.getOrDefault(PreparationStatus.PENDING)
                    } else PreparationStatus.PENDING
                    val order = Order(
                        id = id,
                        number = getString("order_number"),
                        type = OrderType.valueOf(getString("type")),
                        status = OrderStatus.valueOf(getString("status")),
                        lines = emptyList(),
                        subtotalCentimes = getLong("subtotal_centimes"),
                        discountCentimes = getLong("discount_centimes"),
                        taxCentimes = getLong("tax_centimes"),
                        totalCentimes = getLong("total_centimes"),
                        tableId = nullLong("table_id"),
                        registerSessionId = getLong("register_session_id"),
                        cashierId = getLong("cashier_id"),
                        createdAtEpochMilliseconds = createdAt,
                        customerName = getString("customer_name"),
                        customerPhone = getString("customer_phone"),
                        pickupDateEpochMs = nullLong("pickup_date"),
                        preparationStatus = prepStatus,
                        customNote = getString("custom_note"),
                        depositCentimes = getLong("deposit_centimes"),
                        discountBasisPoints = nullInt("discount_basis_points")
                    )
                    id to order
                }
            }
        }
        val linesMap = loadOrderLinesBatch(c, rawOrders.map { it.first })
        rawOrders.map { (id, order) -> order.copy(lines = linesMap[id].orEmpty()) }
    }

    fun salesHistory(
        query: String = "",
        status: OrderStatus? = null,
        method: PaymentMethod? = null,
        fromEpoch: Long? = null,
        toEpoch: Long? = null,
        sessionId: Long? = null
    ): List<SalesHistoryRow> = read { c ->
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (query.isNotBlank()) {
            conditions += "(o.order_number LIKE ? OR u.name LIKE ?)"
            args += "%${query.trim()}%"
            args += "%${query.trim()}%"
        }
        status?.let { conditions += "o.status=?"; args += it.name }
        method?.let { conditions += "p.method=?"; args += it.name }
        fromEpoch?.let { conditions += "o.updated_at>=?"; args += it }
        toEpoch?.let { conditions += "o.updated_at<=?"; args += it }
        sessionId?.let { conditions += "o.register_session_id=?"; args += it }
        val sql = "SELECT o.id, o.order_number, o.type, o.status, o.subtotal_centimes, o.discount_centimes, o.discount_basis_points, o.tax_centimes, o.total_centimes, o.table_id, o.register_session_id, o.cashier_id, o.created_at, o.updated_at, o.customer_name, o.customer_phone, o.pickup_date, o.preparation_status, o.custom_note, o.deposit_centimes, u.name AS cashier_name, p.method AS pay_method, p.created_at AS paid_at " +
            "FROM orders o JOIN users u ON u.id=o.cashier_id LEFT JOIN payments p ON p.order_id=o.id AND p.status='COMPLETED'" +
            (if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")) + " ORDER BY o.updated_at DESC"
        val rawRows = c.prepareStatement(sql).use { p ->
            args.forEachIndexed { i, v -> p.setObject(i + 1, v) }
            p.executeQuery().use { r ->
                r.map {
                    val id = getLong("id")
                    val createdAt = getLong("created_at").takeIf { it > 0L } ?: getLong("updated_at")
                    val prepStatusStr = getString("preparation_status")
                    val prepStatus = if (!prepStatusStr.isNullOrBlank()) {
                        runCatching { PreparationStatus.valueOf(prepStatusStr) }.getOrDefault(PreparationStatus.PENDING)
                    } else PreparationStatus.PENDING
                    val order = Order(
                        id = id,
                        number = getString("order_number"),
                        type = OrderType.valueOf(getString("type")),
                        status = OrderStatus.valueOf(getString("status")),
                        lines = emptyList(),
                        subtotalCentimes = getLong("subtotal_centimes"),
                        discountCentimes = getLong("discount_centimes"),
                        taxCentimes = getLong("tax_centimes"),
                        totalCentimes = getLong("total_centimes"),
                        tableId = nullLong("table_id"),
                        registerSessionId = getLong("register_session_id"),
                        cashierId = getLong("cashier_id"),
                        createdAtEpochMilliseconds = createdAt,
                        customerName = getString("customer_name"),
                        customerPhone = getString("customer_phone"),
                        pickupDateEpochMs = nullLong("pickup_date"),
                        preparationStatus = prepStatus,
                        customNote = getString("custom_note"),
                        depositCentimes = getLong("deposit_centimes"),
                        discountBasisPoints = nullInt("discount_basis_points")
                    )
                    Triple(order, getString("cashier_name"), Pair(getString("pay_method")?.let(PaymentMethod::valueOf), nullLong("paid_at")))
                }
            }
        }
        val linesMap = loadOrderLinesBatch(c, rawRows.map { it.first.id })
        rawRows.map { (order, cashierName, payInfo) ->
            SalesHistoryRow(
                order = order.copy(lines = linesMap[order.id].orEmpty()),
                cashierName = cashierName,
                paymentMethod = payInfo.first,
                paidAtEpochMillis = payInfo.second
            )
        }
    }
    fun sessionHistory(): List<SessionHistoryRow> = read { c ->
        val sql = """
            SELECT rs.*,
                   u.name AS cashier_name,
                   cu.name AS closing_user_name,
                   (SELECT COALESCE(SUM(p.amount_centimes), 0) FROM payments p WHERE p.session_id = rs.id AND p.method = 'CASH' AND p.status = 'COMPLETED') AS cash_sales,
                   (SELECT COALESCE(SUM(p.amount_centimes), 0) FROM payments p WHERE p.session_id = rs.id AND p.method <> 'CASH' AND p.status = 'COMPLETED') AS card_sales,
                   (SELECT COALESCE(SUM(cm.amount_centimes), 0) FROM cash_movements cm WHERE cm.session_id = rs.id AND cm.type = 'CASH_IN') AS cash_in,
                   (SELECT COALESCE(SUM(cm.amount_centimes), 0) FROM cash_movements cm WHERE cm.session_id = rs.id AND cm.type = 'CASH_OUT') AS cash_out
            FROM register_sessions rs
            JOIN users u ON u.id = rs.cashier_id
            LEFT JOIN users cu ON cu.id = rs.closing_user_id
            ORDER BY rs.opened_at DESC
        """.trimIndent()
        c.createStatement().use { s ->
            s.executeQuery(sql).use { r ->
                r.map {
                    val cashSales = getLong("cash_sales")
                    val cardSales = getLong("card_sales")
                    val totalSales = cashSales + cardSales
                    val cashIn = getLong("cash_in")
                    val cashOut = getLong("cash_out")
                    val session = RegisterSession(
                        id = getLong("id"),
                        status = RegisterSessionStatus.valueOf(getString("status")),
                        openingCashCentimes = getLong("opening_cash_centimes"),
                        registerId = getLong("register_id"),
                        cashierId = getLong("cashier_id"),
                        openedAtEpochMilliseconds = getLong("opened_at"),
                        closedAtEpochMilliseconds = nullLong("closed_at"),
                        expectedCashCentimes = nullLong("expected_cash_centimes"),
                        countedCashCentimes = nullLong("counted_cash_centimes"),
                        differenceCentimes = nullLong("difference_centimes"),
                        closingUserId = nullLong("closing_user_id"),
                        leftInDrawerCentimes = nullLong("left_in_drawer_centimes"),
                        removedAmountCentimes = nullLong("removed_amount_centimes"),
                        remittanceReference = getString("remittance_reference"),
                        remittanceDestination = getString("remittance_destination"),
                        closingNote = getString("closing_note")
                    )
                    SessionHistoryRow(
                        session = session,
                        cashierName = getString("cashier_name"),
                        closingUserName = getString("closing_user_name"),
                        cashSalesCentimes = cashSales,
                        cardSalesCentimes = cardSales,
                        totalSalesCentimes = totalSales,
                        cashInCentimes = cashIn,
                        cashOutCentimes = cashOut
                    )
                }
            }
        }
    }

    fun sessionClosingReport(sessionId: Long): SessionClosingReport = read { c ->
        val session = sessionQuery(
            c,
            "SELECT * FROM register_sessions WHERE id=? LIMIT 1",
            sessionId
        ) ?: throw IllegalArgumentException("Session #$sessionId not found")

        fun userName(userId: Long?): String? {
            if (userId == null) return null
            return c.prepareStatement("SELECT name FROM users WHERE id=? LIMIT 1").use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getString(1) else "Utilisateur #$userId"
                }
            }
        }

        val orderIds = c.prepareStatement(
            "SELECT id FROM orders WHERE register_session_id=? ORDER BY created_at,id"
        ).use { statement ->
            statement.setLong(1, sessionId)
            statement.executeQuery().use { result -> result.map { getLong(1) } }
        }
        val reportOrders = orderIds.mapNotNull { loadOrder(c, it) }
        val reportPayments = c.prepareStatement(
            "SELECT * FROM payments WHERE session_id=? ORDER BY created_at,id"
        ).use { statement ->
            statement.setLong(1, sessionId)
            statement.executeQuery().use { result ->
                result.map {
                    Payment(
                        id = getLong("id"),
                        orderId = getLong("order_id"),
                        registerSessionId = getLong("session_id"),
                        method = PaymentMethod.valueOf(getString("method")),
                        amountCentimes = getLong("amount_centimes"),
                        receivedCentimes = getLong("received_centimes"),
                        changeCentimes = getLong("change_centimes"),
                        status = PaymentStatus.valueOf(getString("status")),
                        submissionToken = getString("submission_token"),
                        createdAtEpochMilliseconds = getLong("created_at")
                    )
                }
            }
        }
        fun movementTotal(type: CashMovementType): Long = c.prepareStatement(
            "SELECT COALESCE(SUM(amount_centimes),0) FROM cash_movements WHERE session_id=? AND type=?"
        ).use { statement ->
            statement.setLong(1, sessionId)
            statement.setString(2, type.name)
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

        SessionClosingReportRules.build(
            session = session,
            cashierName = userName(session.cashierId) ?: "Utilisateur #${session.cashierId}",
            closingUserName = userName(session.closingUserId),
            orders = reportOrders,
            payments = reportPayments,
            cashInCentimes = movementTotal(CashMovementType.CASH_IN),
            cashOutCentimes = movementTotal(CashMovementType.CASH_OUT)
        )
    }

    fun lastClosedSession(): RegisterSession? = read { c ->
        sessionQuery(c, "SELECT * FROM register_sessions WHERE status='CLOSED' ORDER BY closed_at DESC LIMIT 1", null)
    }

    fun recentCategorySaleSamples(sinceEpochMilliseconds: Long): List<CategorySaleSample> = read { c ->
        c.prepareStatement(
            """
            SELECT COALESCE(oi.category_id_snapshot, p.category_id), oi.quantity, o.updated_at
            FROM order_items oi
            INNER JOIN orders o ON o.id = oi.order_id
            LEFT JOIN products p ON p.id = oi.product_id
            WHERE o.status = 'COMPLETED'
              AND o.updated_at >= ?
              AND COALESCE(oi.category_id_snapshot, p.category_id) IS NOT NULL
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, sinceEpochMilliseconds)
            statement.executeQuery().use { result ->
                result.map {
                    CategorySaleSample(
                        categoryId = getLong(1),
                        quantity = getInt(2),
                        soldAtEpochMilliseconds = getLong(3)
                    )
                }
            }
        }
    }

    fun recentProductSaleSamples(sinceEpochMilliseconds: Long): List<ProductSaleSample> = read { c ->
        c.prepareStatement(
            """
            SELECT oi.product_id, oi.quantity, o.updated_at
            FROM order_items oi
            INNER JOIN orders o ON o.id = oi.order_id
            WHERE o.status = 'COMPLETED'
              AND o.updated_at >= ?
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, sinceEpochMilliseconds)
            statement.executeQuery().use { result ->
                result.map {
                    ProductSaleSample(
                        productId = getLong(1),
                        quantity = getInt(2),
                        soldAtEpochMilliseconds = getLong(3)
                    )
                }
            }
        }
    }

    fun countOpenOrdersForSession(sessionId: Long): Int = read { c ->
        c.prepareStatement("SELECT COUNT(*) FROM orders WHERE register_session_id=? AND status='OPEN'").use { p ->
            p.setLong(1, sessionId)
            p.executeQuery().use { r -> if (r.next()) r.getInt(1) else 0 }
        }
    }

    fun nextUniqueRemittanceReference(baseReference: String): String = read { c ->
        c.prepareStatement(
            "SELECT remittance_reference FROM register_sessions WHERE remittance_reference = ? OR remittance_reference LIKE ?"
        ).use { stmt ->
            stmt.setString(1, baseReference)
            stmt.setString(2, "$baseReference-%")
            stmt.executeQuery().use { rs ->
                val existing = mutableSetOf<String>()
                while (rs.next()) {
                    rs.getString(1)?.let { existing.add(it) }
                }
                if (baseReference !in existing) {
                    baseReference
                } else {
                    var counter = 2
                    while ("$baseReference-$counter" in existing) {
                        counter++
                    }
                    "$baseReference-$counter"
                }
            }
        }
    }

    fun salesSummary(
        fromEpoch: Long? = null,
        toEpoch: Long? = null,
        cashierId: Long? = null,
        orderType: OrderType? = null,
        matchingCategoryIds: Set<Long>? = null
    ): SalesSummary = read { c ->
        if (!matchingCategoryIds.isNullOrEmpty()) {
            val placeholders = matchingCategoryIds.joinToString(",") { "?" }
            val amount = recognizedLineSql("recognized_amount_centimes", "total_centimes")
            val tax = recognizedLineSql("recognized_tax_centimes", "tax_centimes")
            val filters = mutableListOf("o.status='COMPLETED'", "COALESCE(oi.category_id_snapshot,p.category_id) IN ($placeholders)")
            fromEpoch?.let { filters += "o.updated_at>=?" }
            toEpoch?.let { filters += "o.updated_at<=?" }
            cashierId?.let { filters += "o.cashier_id=?" }
            orderType?.let { filters += "o.type=?" }
            val sql = "SELECT COUNT(DISTINCT o.id), COALESCE(SUM($amount),0), COALESCE(SUM($tax),0), " +
                "COALESCE(SUM(CASE WHEN pay.method='CASH' THEN $amount ELSE 0 END),0), " +
                "COALESCE(SUM(CASE WHEN pay.method='CARD' THEN $amount ELSE 0 END),0) " +
                "FROM order_items oi JOIN orders o ON o.id=oi.order_id LEFT JOIN products p ON p.id=oi.product_id " +
                "LEFT JOIN payments pay ON pay.order_id=o.id AND pay.status='COMPLETED' WHERE ${filters.joinToString(" AND ")}"
            return@read c.prepareStatement(sql).use { statement ->
                var index = 1
                matchingCategoryIds.forEach { statement.setLong(index++, it) }
                fromEpoch?.let { statement.setLong(index++, it) }
                toEpoch?.let { statement.setLong(index++, it) }
                cashierId?.let { statement.setLong(index++, it) }
                orderType?.let { statement.setString(index++, it.name) }
                statement.executeQuery().use { rows ->
                    rows.next()
                    SalesSummary(rows.getInt(1), rows.getLong(2), rows.getLong(4), rows.getLong(5), rows.getLong(3))
                }
            }
        }
        val range = buildList {
            fromEpoch?.let { add("o.updated_at >= ?") }
            toEpoch?.let { add("o.updated_at <= ?") }
            cashierId?.let { add("o.cashier_id = ?") }
            orderType?.let { add("o.type = ?") }
            if (!matchingCategoryIds.isNullOrEmpty()) {
                val placeholders = matchingCategoryIds.joinToString(",") { "?" }
                add("EXISTS (SELECT 1 FROM order_items oi LEFT JOIN products p ON p.id = oi.product_id WHERE oi.order_id = o.id AND COALESCE(oi.category_id_snapshot, p.category_id) IN ($placeholders))")
            }
        }
        val where = "o.status='COMPLETED'" + (if (range.isEmpty()) "" else " AND " + range.joinToString(" AND "))

        fun bindRange(p: java.sql.PreparedStatement, start: Int = 1) {
            var i = start
            fromEpoch?.let { p.setLong(i++, it) }
            toEpoch?.let { p.setLong(i++, it) }
            cashierId?.let { p.setLong(i++, it) }
            orderType?.let { p.setString(i++, it.name) }
            if (!matchingCategoryIds.isNullOrEmpty()) {
                for (catId in matchingCategoryIds) {
                    p.setLong(i++, catId)
                }
            }
        }

        val orderValues = c.prepareStatement(
            "SELECT COUNT(*),COALESCE(SUM(o.total_centimes),0),COALESCE(SUM(o.tax_centimes),0) FROM orders o WHERE $where"
        ).use { p ->
            bindRange(p)
            p.executeQuery().use { r ->
                r.next()
                Triple(r.getInt(1), r.getLong(2), r.getLong(3))
            }
        }

        fun paid(method: String) = c.prepareStatement(
            "SELECT COALESCE(SUM(p.amount_centimes),0) FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.method=? AND p.status='COMPLETED' AND $where"
        ).use { p ->
            p.setString(1, method)
            bindRange(p, 2)
            p.executeQuery().use { r ->
                r.next()
                r.getLong(1)
            }
        }

        SalesSummary(orderValues.first, orderValues.second, paid("CASH"), paid("CARD"), orderValues.third)
    }

    fun todaySalesSummary(
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): SalesSummary = read { c ->
        val localDate = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate()
        val fromEpoch = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toEpoch = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1

        val paidCash = c.prepareStatement(
            "SELECT COALESCE(SUM(p.amount_centimes),0) FROM payments p JOIN orders o ON o.id=p.order_id " +
            "WHERE p.method='CASH' AND p.status='COMPLETED' AND o.status='COMPLETED' AND o.updated_at>=? AND o.updated_at<=?"
        ).use { p ->
            p.setLong(1, fromEpoch); p.setLong(2, toEpoch)
            p.executeQuery().use { r -> r.next(); r.getLong(1) }
        }

        val paidCard = c.prepareStatement(
            "SELECT COALESCE(SUM(p.amount_centimes),0) FROM payments p JOIN orders o ON o.id=p.order_id " +
            "WHERE p.method='CARD' AND p.status='COMPLETED' AND o.status='COMPLETED' AND o.updated_at>=? AND o.updated_at<=?"
        ).use { p ->
            p.setLong(1, fromEpoch); p.setLong(2, toEpoch)
            p.executeQuery().use { r -> r.next(); r.getLong(1) }
        }

        val completedCount = c.prepareStatement(
            "SELECT COUNT(DISTINCT o.id) FROM orders o JOIN payments p ON p.order_id=o.id " +
            "WHERE o.status='COMPLETED' AND p.status='COMPLETED' AND o.updated_at>=? AND o.updated_at<=?"
        ).use { p ->
            p.setLong(1, fromEpoch); p.setLong(2, toEpoch)
            p.executeQuery().use { r -> r.next(); r.getInt(1) }
        }

        val storedTax = c.prepareStatement(
            "SELECT COALESCE(SUM(o.tax_centimes),0) FROM orders o " +
            "WHERE o.status='COMPLETED' AND o.updated_at>=? AND o.updated_at<=? AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status='COMPLETED')"
        ).use { p ->
            p.setLong(1, fromEpoch); p.setLong(2, toEpoch)
            p.executeQuery().use { r -> r.next(); r.getLong(1) }
        }

        val totalSales = paidCash + paidCard
        SalesSummary(
            completedOrders = completedCount,
            salesCentimes = totalSales,
            cashCentimes = paidCash,
            cardCentimes = paidCard,
            taxCentimes = storedTax
        )
    }

    fun retailSalesAnalytics(
        fromEpoch: Long,
        toEpoch: Long,
        cashierId: Long? = null,
        orderType: OrderType? = null,
        matchingCategoryIds: Set<Long>? = null
    ): RetailSalesAnalytics = read { c ->
        val recognizedAmount = recognizedLineSql("recognized_amount_centimes", "total_centimes")

        val baseFilter = mutableListOf("o.status='COMPLETED'", "o.updated_at BETWEEN ? AND ?")
        val baseParams = mutableListOf<Any>(fromEpoch, toEpoch)

        cashierId?.let { baseFilter.add("o.cashier_id = ?"); baseParams.add(it) }
        orderType?.let { baseFilter.add("o.type = ?"); baseParams.add(it.name) }

        val hasCatFilter = !matchingCategoryIds.isNullOrEmpty()
        val catPlaceholders = if (hasCatFilter) matchingCategoryIds!!.joinToString(",") { "?" } else ""

        fun bindAll(p: java.sql.PreparedStatement, params: List<Any>, start: Int = 1) {
            var i = start
            for (arg in params) {
                when (arg) {
                    is Long -> p.setLong(i++, arg)
                    is String -> p.setString(i++, arg)
                    is Int -> p.setInt(i++, arg)
                    else -> p.setObject(i++, arg)
                }
            }
        }

        fun breakdown(sql: String, params: List<Any>): List<SalesBreakdown> = c.prepareStatement(sql).use { statement ->
            bindAll(statement, params, 1)
            statement.executeQuery().use { rows ->
                rows.map { SalesBreakdown(getString("label"), getInt("quantity"), getLong("amount")) }
            }
        }

        // 1. byProduct
        val productFilter = baseFilter.toMutableList()
        val productParams = baseParams.toMutableList()
        if (hasCatFilter) {
            productFilter.add("COALESCE(oi.category_id_snapshot, p.category_id) IN ($catPlaceholders)")
            productParams.addAll(matchingCategoryIds!!)
        }
        val byProduct = breakdown(
            "SELECT oi.product_name AS label, SUM(oi.quantity) AS quantity, SUM($recognizedAmount) AS amount " +
                "FROM order_items oi JOIN orders o ON o.id=oi.order_id LEFT JOIN products p ON p.id=oi.product_id " +
                "WHERE ${productFilter.joinToString(" AND ")} " +
                "GROUP BY oi.product_id, oi.product_name ORDER BY amount DESC, label LIMIT 20",
            productParams
        )

        // 2. byCategory
        val categoryFilter = baseFilter.toMutableList()
        val categoryParams = baseParams.toMutableList()
        if (hasCatFilter) {
            categoryFilter.add("COALESCE(oi.category_id_snapshot, p.category_id) IN ($catPlaceholders)")
            categoryParams.addAll(matchingCategoryIds!!)
        }
        val byCategory = breakdown(
            "SELECT COALESCE(oi.category_name_snapshot, c.name, 'Uncategorized') AS label, SUM(oi.quantity) AS quantity, SUM($recognizedAmount) AS amount " +
                "FROM order_items oi JOIN orders o ON o.id=oi.order_id " +
                "LEFT JOIN products p ON p.id=oi.product_id " +
                "LEFT JOIN categories c ON c.id=COALESCE(oi.category_id_snapshot, p.category_id) " +
                "WHERE ${categoryFilter.joinToString(" AND ")} " +
                "GROUP BY COALESCE(oi.category_id_snapshot, p.category_id), COALESCE(oi.category_name_snapshot, c.name, 'Uncategorized') ORDER BY amount DESC, label",
            categoryParams
        )

        // 3. byCashier
        val cashierFilter = baseFilter.toMutableList()
        val cashierParams = baseParams.toMutableList()
        if (hasCatFilter) {
            cashierFilter.add(
                "EXISTS (SELECT 1 FROM order_items oi_c LEFT JOIN products p_c ON p_c.id = oi_c.product_id WHERE oi_c.order_id = o.id AND COALESCE(oi_c.category_id_snapshot, p_c.category_id) IN ($catPlaceholders))"
            )
            cashierParams.addAll(matchingCategoryIds!!)
        }
        val byCashier = if (hasCatFilter) breakdown(
            "SELECT u.name AS label, COUNT(DISTINCT o.id) AS quantity, SUM($recognizedAmount) AS amount " +
                "FROM orders o JOIN users u ON u.id=o.cashier_id JOIN order_items oi ON oi.order_id=o.id " +
                "LEFT JOIN products p ON p.id=oi.product_id WHERE ${baseFilter.joinToString(" AND ")} " +
                "AND COALESCE(oi.category_id_snapshot,p.category_id) IN ($catPlaceholders) " +
                "GROUP BY u.id, u.name ORDER BY amount DESC, u.name",
            baseParams + matchingCategoryIds!!.toList()
        ) else breakdown(
            "SELECT u.name AS label, COUNT(o.id) AS quantity, SUM(o.total_centimes) AS amount " +
                "FROM orders o JOIN users u ON u.id=o.cashier_id " +
                "WHERE ${cashierFilter.joinToString(" AND ")} " +
                "GROUP BY u.id, u.name ORDER BY amount DESC, u.name",
            cashierParams
        )

        // 4. cancelled
        val cancelledFilter = mutableListOf("orders.status='CANCELLED'", "orders.updated_at BETWEEN ? AND ?")
        val cancelledParams = mutableListOf<Any>(fromEpoch, toEpoch)
        cashierId?.let { cancelledFilter.add("orders.cashier_id = ?"); cancelledParams.add(it) }
        orderType?.let { cancelledFilter.add("orders.type = ?"); cancelledParams.add(it.name) }
        if (hasCatFilter) {
            cancelledFilter.add(
                "EXISTS (SELECT 1 FROM order_items oi_c LEFT JOIN products p_c ON p_c.id = oi_c.product_id WHERE oi_c.order_id = orders.id AND COALESCE(oi_c.category_id_snapshot, p_c.category_id) IN ($catPlaceholders))"
            )
            cancelledParams.addAll(matchingCategoryIds!!)
        }
        val cancelled = c.prepareStatement(
            "SELECT COUNT(*) FROM orders WHERE ${cancelledFilter.joinToString(" AND ")}"
        ).use {
            bindAll(it, cancelledParams, 1)
            it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }

        RetailSalesAnalytics(byProduct, byCategory, byCashier, cancelled)
    }

    fun salesEvolution(
        fromEpoch: Long,
        toEpoch: Long,
        isHourly: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
        cashierId: Long? = null,
        orderType: OrderType? = null,
        matchingCategoryIds: Set<Long>? = null
    ): List<SalesEvolutionPoint> = read { c ->
        val conditions = mutableListOf("o.status = 'COMPLETED'", "o.updated_at BETWEEN ? AND ?")
        val params = mutableListOf<Any>(fromEpoch, toEpoch)

        cashierId?.let { conditions.add("o.cashier_id = ?"); params.add(it) }
        orderType?.let { conditions.add("o.type = ?"); params.add(it.name) }
        if (!matchingCategoryIds.isNullOrEmpty()) {
            val catPlaceholders = matchingCategoryIds.joinToString(",") { "?" }
            conditions.add(
                "EXISTS (SELECT 1 FROM order_items oi LEFT JOIN products p ON p.id = oi.product_id WHERE oi.order_id = o.id AND COALESCE(oi.category_id_snapshot, p.category_id) IN ($catPlaceholders))"
            )
            params.addAll(matchingCategoryIds)
        }

        val sql = if (matchingCategoryIds.isNullOrEmpty()) {
            "SELECT o.updated_at, o.total_centimes FROM orders o WHERE ${conditions.joinToString(" AND ")} ORDER BY o.updated_at"
        } else {
            val amount = recognizedLineSql("recognized_amount_centimes", "total_centimes")
            // The category predicate above selected qualifying orders. Filter their
            // lines as well, so the graph matches the category revenue KPI.
            val placeholders = matchingCategoryIds.joinToString(",") { "?" }
            "SELECT o.updated_at, SUM($amount) FROM orders o JOIN order_items oi ON oi.order_id=o.id " +
                "LEFT JOIN products p ON p.id=oi.product_id WHERE ${conditions.joinToString(" AND ")} " +
                "AND COALESCE(oi.category_id_snapshot,p.category_id) IN ($placeholders) " +
                "GROUP BY o.id ORDER BY o.updated_at"
        }
        val rows = c.prepareStatement(sql).use { stmt ->
            var idx = 1
            for (p in params) {
                when (p) {
                    is Long -> stmt.setLong(idx++, p)
                    is String -> stmt.setString(idx++, p)
                    is Int -> stmt.setInt(idx++, p)
                    else -> stmt.setObject(idx++, p)
                }
            }
            if (!matchingCategoryIds.isNullOrEmpty()) {
                matchingCategoryIds.forEach { stmt.setLong(idx++, it) }
            }
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<Pair<Long, Long>>()
                while (rs.next()) {
                    list.add(Pair(rs.getLong(1), rs.getLong(2)))
                }
                list
            }
        }

        if (isHourly) {
            val hourlyMap = mutableMapOf<Int, Pair<Long, Int>>()
            for ((updatedAt, amount) in rows) {
                val zdt = Instant.ofEpochMilli(updatedAt).atZone(zoneId)
                val h = zdt.hour
                val current = hourlyMap.getOrDefault(h, Pair(0L, 0))
                hourlyMap[h] = Pair(current.first + amount, current.second + 1)
            }

            val minHour = if (hourlyMap.isNotEmpty()) (hourlyMap.keys.minOrNull() ?: 8).coerceAtMost(8) else 8
            val maxHour = if (hourlyMap.isNotEmpty()) (hourlyMap.keys.maxOrNull() ?: 20).coerceAtLeast(20) else 20

            (minHour..maxHour).map { h ->
                val data = hourlyMap[h] ?: Pair(0L, 0)
                SalesEvolutionPoint(
                    label = String.format("%02dh", h),
                    amountCentimes = data.first,
                    orderCount = data.second
                )
            }
        } else {
            val startDate = Instant.ofEpochMilli(fromEpoch).atZone(zoneId).toLocalDate()
            val endDate = Instant.ofEpochMilli(toEpoch).atZone(zoneId).toLocalDate()
            val dailyMap = mutableMapOf<LocalDate, Pair<Long, Int>>()
            for ((updatedAt, amount) in rows) {
                val d = Instant.ofEpochMilli(updatedAt).atZone(zoneId).toLocalDate()
                val current = dailyMap.getOrDefault(d, Pair(0L, 0))
                dailyMap[d] = Pair(current.first + amount, current.second + 1)
            }

            val formatter = DateTimeFormatter.ofPattern("dd/MM")
            val points = mutableListOf<SalesEvolutionPoint>()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val data = dailyMap[curr] ?: Pair(0L, 0)
                points.add(
                    SalesEvolutionPoint(
                        label = curr.format(formatter),
                        amountCentimes = data.first,
                        orderCount = data.second
                    )
                )
                curr = curr.plusDays(1)
            }
            points
        }
    }

    fun earliestCompletedSaleEpochMs(): Long? = read { c ->
        c.prepareStatement("SELECT MIN(updated_at) FROM orders WHERE status='COMPLETED'").use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val epoch = rs.getLong(1)
                    if (rs.wasNull() || epoch <= 0L) null else epoch
                } else null
            }
        }
    }

    // =========================================================================
    // ADMINISTRATION DELETION SECURITY & DATA MANAGEMENT
    // =========================================================================

    fun getAdminDeletionPasswordHash(): String? = read { c ->
        c.prepareStatement("SELECT value FROM settings WHERE key='admin_deletion_password_hash'").use { s ->
            s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    fun getAdminDeletionPasswordSalt(): String? = read { c ->
        c.prepareStatement("SELECT value FROM settings WHERE key='admin_deletion_password_salt'").use { s ->
            s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    fun isAdminDeletionPasswordConfigured(): Boolean =
        !getAdminDeletionPasswordHash().isNullOrBlank() && !getAdminDeletionPasswordSalt().isNullOrBlank()

    fun setAdminDeletionPassword(password: String) = read { c ->
        require(password.length >= AdminDeletionSecurity.MIN_PASSWORD_LENGTH) {
            "Le mot de passe d'administration doit comporter au moins ${AdminDeletionSecurity.MIN_PASSWORD_LENGTH} caractères."
        }
        val salt = AdminDeletionSecurity.generateSalt()
        val hash = AdminDeletionSecurity.hashPassword(password, salt)
        transactionBlocking {
            c.prepareStatement("INSERT INTO settings(key,value) VALUES('admin_deletion_password_salt',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use {
                it.setString(1, salt)
                it.executeUpdate()
            }
            c.prepareStatement("INSERT INTO settings(key,value) VALUES('admin_deletion_password_hash',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use {
                it.setString(1, hash)
                it.executeUpdate()
            }
        }
    }

    fun verifyAdminDeletionPassword(password: String): Boolean = read { c ->
        val clean = password.trim()
        if (clean.isBlank()) return@read false

        // 1. Owner PIN verification (allows owner to confirm deletions with their PIN)
        val ownerPins = c.prepareStatement(
            "SELECT pin_hash FROM users WHERE role='OWNER' AND active=1 AND deleted_at IS NULL"
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                val list = mutableListOf<String>()
                while (rs.next()) {
                    rs.getString(1)?.let { list.add(it) }
                }
                list
            }
        }
        for (storedHash in ownerPins) {
            if (PinHasher.verify(clean, storedHash)) {
                return@read true
            }
        }

        // 2. Custom administrative deletion password if configured
        val salt = getAdminDeletionPasswordSalt()
        val hash = getAdminDeletionPasswordHash()
        if (!salt.isNullOrBlank() && !hash.isNullOrBlank()) {
            return@read AdminDeletionSecurity.verifyPassword(clean, hash, salt)
        }

        false
    }

    private fun logDeletionAudit(c: Connection, actingUserId: Long, entityType: String, recordsDeleted: Int, details: String) {
        c.prepareStatement("INSERT INTO audit_logs(action_type,acting_user_id,entity_type,entity_id,created_at,details) VALUES('DATA_DELETION',?,?,?,?,?)").use {
            it.setLong(1, actingUserId)
            it.setString(2, entityType)
            it.setLong(3, recordsDeleted.toLong())
            it.setLong(4, System.currentTimeMillis())
            it.setString(5, details)
            it.executeUpdate()
        }
    }

    fun auditLogs(): List<AuditLogEntry> = read { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id, action_type, acting_user_id, entity_type, entity_id, created_at, details FROM audit_logs ORDER BY created_at DESC, id DESC").use { r ->
                r.map {
                    AuditLogEntry(
                        id = getLong("id"),
                        actionType = getString("action_type"),
                        actingUserId = getLong("acting_user_id"),
                        entityType = getString("entity_type"),
                        entityId = nullLong("entity_id"),
                        createdAtEpochMillis = getLong("created_at"),
                        details = getString("details")
                    )
                }
            }
        }
    }

    fun recordAudit(actionType: String, actingUserId: Long, entityType: String, entityId: Long?, details: String?) = read { c ->
        c.prepareStatement("INSERT INTO audit_logs(action_type,acting_user_id,entity_type,entity_id,created_at,details) VALUES(?,?,?,?,?,?)").use {
            it.setString(1, actionType)
            it.setLong(2, actingUserId)
            it.setString(3, entityType)
            it.setObject(4, entityId)
            it.setLong(5, System.currentTimeMillis())
            it.setString(6, details)
            it.executeUpdate()
        }
    }

    fun getDatabaseMetrics(): DatabaseMetrics = read { c ->
        var prods = 0
        var cats = 0
        var sales = 0
        var pending = 0
        var cashiers = 0
        c.createStatement().use { s ->
            s.executeQuery("SELECT count(*) FROM products WHERE deleted_at IS NULL").use { if (it.next()) prods = it.getInt(1) }
            s.executeQuery("SELECT count(*) FROM categories WHERE deleted_at IS NULL").use { if (it.next()) cats = it.getInt(1) }
            s.executeQuery("SELECT count(*) FROM orders WHERE status IN ('COMPLETED', 'CANCELLED')").use { if (it.next()) sales = it.getInt(1) }
            s.executeQuery("SELECT count(*) FROM orders WHERE status = 'OPEN'").use { if (it.next()) pending = it.getInt(1) }
            s.executeQuery("SELECT count(*) FROM users WHERE role = 'CASHIER' AND deleted_at IS NULL").use { if (it.next()) cashiers = it.getInt(1) }
        }
        DatabaseMetrics(prods, cats, sales, pending, cashiers)
    }

    fun deleteProducts(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            val count = c.prepareStatement("UPDATE products SET deleted_at=?,active=0,available=0 WHERE deleted_at IS NULL").use {
                it.setLong(1, System.currentTimeMillis())
                it.executeUpdate()
            }
            (products as? Products)?.refreshState()
            logDeletionAudit(c, actingUserId, "PRODUCTS", count, "Suppression de tous les produits ($count éléments)")
            count
        }
    }

    fun deleteCategories(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            val now = System.currentTimeMillis()
            val prodCount = c.prepareStatement("UPDATE products SET deleted_at=?,active=0,available=0 WHERE deleted_at IS NULL").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
            val catCount = c.prepareStatement("UPDATE categories SET deleted_at=?,active=0 WHERE deleted_at IS NULL").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
            (products as? Products)?.refreshState()
            (categories as? Categories)?.refreshState()
            logDeletionAudit(c, actingUserId, "CATEGORIES", catCount, "Suppression de toutes les catégories ($catCount catégories, $prodCount produits)")
            catCount
        }
    }

    fun deleteCatalogue(actingUserId: Long = 1L): Pair<Int, Int> = read { c ->
        transactionBlocking {
            val now = System.currentTimeMillis()
            val prodCount = c.prepareStatement("UPDATE products SET deleted_at=?,active=0,available=0 WHERE deleted_at IS NULL").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
            val catCount = c.prepareStatement("UPDATE categories SET deleted_at=?,active=0 WHERE deleted_at IS NULL").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
            (products as? Products)?.refreshState()
            (categories as? Categories)?.refreshState()
            logDeletionAudit(c, actingUserId, "CATALOGUE", catCount + prodCount, "Suppression de tout le catalogue ($catCount catégories, $prodCount produits)")
            catCount to prodCount
        }
    }

    fun deleteSalesHistory(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            c.createStatement().use { s ->
                s.executeUpdate("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE status IN ('COMPLETED', 'CANCELLED') OR status != 'OPEN')")
                s.executeUpdate("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE status IN ('COMPLETED', 'CANCELLED') OR status != 'OPEN')")
            }
            val count = c.createStatement().use { it.executeUpdate("DELETE FROM orders WHERE status IN ('COMPLETED', 'CANCELLED') OR status != 'OPEN'") }
            logDeletionAudit(c, actingUserId, "SALES_HISTORY", count, "Suppression de l'historique des ventes ($count ventes clôturées/annulées)")
            count
        }
    }

    fun deleteSuspendedSales(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            c.createStatement().use { s ->
                s.executeUpdate("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE status = 'OPEN')")
                s.executeUpdate("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE status = 'OPEN')")
            }
            val count = c.createStatement().use { it.executeUpdate("DELETE FROM orders WHERE status = 'OPEN'") }
            c.createStatement().use { it.executeUpdate("UPDATE restaurant_tables SET status='AVAILABLE' WHERE status='OCCUPIED'") }
            (tables as? Tables)?.refreshState()
            logDeletionAudit(c, actingUserId, "SUSPENDED_SALES", count, "Suppression des ventes en attente ($count commandes ouvertes)")
            count
        }
    }

    fun deleteTablesAndAreas(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            c.createStatement().use { it.executeUpdate("UPDATE orders SET table_id=NULL WHERE table_id IS NOT NULL") }
            val tableCount = c.createStatement().use { it.executeUpdate("DELETE FROM restaurant_tables") }
            val areaCount = c.createStatement().use { it.executeUpdate("DELETE FROM dining_areas") }
            (tables as? Tables)?.refreshState()
            logDeletionAudit(c, actingUserId, "TABLES_AREAS", tableCount + areaCount, "Suppression de $tableCount tables et $areaCount salles")
            tableCount + areaCount
        }
    }

    fun deleteCashiers(actingUserId: Long = 1L): Int = read { c ->
        transactionBlocking {
            val count = c.prepareStatement("UPDATE users SET deleted_at=?,active=0 WHERE role='CASHIER' AND deleted_at IS NULL").use {
                it.setLong(1, System.currentTimeMillis())
                it.executeUpdate()
            }
            (users as? Users)?.refreshState()
            logDeletionAudit(c, actingUserId, "CASHIERS", count, "Suppression des caissiers ($count comptes, propriétaire conservé)")
            count
        }
    }

    fun deleteBusinessData(actingUserId: Long = 1L): DeletionSummary = read { c ->
        transactionBlocking {
            var prods = 0
            var cats = 0
            var sales = 0
            var susp = 0
            var tbls = 0
            var areas = 0
            var cashiers = 0

            c.createStatement().use { s ->
                // 1. Transactional child records
                s.executeUpdate("DELETE FROM payments")
                s.executeUpdate("DELETE FROM order_items")
                s.executeUpdate("DELETE FROM cash_movements")

                // 2. Main transactions & operational references
                s.executeUpdate("UPDATE restaurant_tables SET status='AVAILABLE'")
                s.executeUpdate("UPDATE orders SET table_id=NULL")
                sales = s.executeUpdate("DELETE FROM orders WHERE status IN ('COMPLETED', 'CANCELLED')")
                susp = s.executeUpdate("DELETE FROM orders WHERE status = 'OPEN'")
                s.executeUpdate("UPDATE register_sessions SET closing_user_id=1 WHERE closing_user_id IN (SELECT id FROM users WHERE role='CASHIER')")
                s.executeUpdate("UPDATE register_sessions SET cashier_id=1 WHERE cashier_id IN (SELECT id FROM users WHERE role='CASHIER')")
                s.executeUpdate("DELETE FROM register_sessions")

                // 3. Catalogue
                prods = s.executeUpdate("DELETE FROM products")
                cats = s.executeUpdate("DELETE FROM categories")

                // 4. Restaurant layout
                tbls = s.executeUpdate("DELETE FROM restaurant_tables")
                areas = s.executeUpdate("DELETE FROM dining_areas")

                // 5. Users (Cashiers only, Owner strictly preserved)
                s.executeUpdate("UPDATE audit_logs SET acting_user_id=1 WHERE acting_user_id IN (SELECT id FROM users WHERE role='CASHIER')")
                cashiers = s.executeUpdate("DELETE FROM users WHERE role='CASHIER'")
            }

            databasePath?.parent?.resolve("images")?.let { dir ->
                runCatching {
                    if (Files.exists(dir)) {
                        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            }

            (products as? Products)?.refreshState()
            (categories as? Categories)?.refreshState()
            (tables as? Tables)?.refreshState()
            (users as? Users)?.refreshState()

            val summary = DeletionSummary(
                productsDeleted = prods,
                categoriesDeleted = cats,
                salesHistoryDeleted = sales,
                suspendedSalesDeleted = susp,
                tablesDeleted = tbls,
                areasDeleted = areas,
                cashiersDeleted = cashiers,
                message = "Toutes les données commerciales ont été supprimées avec succès."
            )
            logDeletionAudit(c, actingUserId, "BUSINESS_DATA", summary.totalRecordsDeleted, "Suppression de toutes les données commerciales")
            summary
        }
    }

    fun deleteSelective(selection: DataGroupSelection, actingUserId: Long = 1L): DeletionSummary = read { c ->
        transactionBlocking {
            var prodDeleted = 0
            var catDeleted = 0
            var salesDeleted = 0
            var suspDeleted = 0
            var tablesDeleted = 0
            var areasDeleted = 0
            var cashiersDeleted = 0

            // 1. Operational / Suspended Sales
            if (selection.suspendedSales) {
                c.createStatement().use { s ->
                    s.executeUpdate("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE status = 'OPEN')")
                    s.executeUpdate("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE status = 'OPEN')")
                }
                suspDeleted = c.createStatement().use { it.executeUpdate("DELETE FROM orders WHERE status = 'OPEN'") }
                c.createStatement().use { it.executeUpdate("UPDATE restaurant_tables SET status='AVAILABLE' WHERE status='OCCUPIED'") }
            }

            // 2. Main Transactions / Completed Sales History
            if (selection.salesHistory) {
                c.createStatement().use { s ->
                    s.executeUpdate("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE status IN ('COMPLETED', 'CANCELLED'))")
                    s.executeUpdate("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE status IN ('COMPLETED', 'CANCELLED'))")
                }
                salesDeleted = c.createStatement().use { it.executeUpdate("DELETE FROM orders WHERE status IN ('COMPLETED', 'CANCELLED')") }
            }

            // 3. Catalogue
            if (selection.categories) {
                val now = System.currentTimeMillis()
                prodDeleted += c.prepareStatement("UPDATE products SET deleted_at=?,active=0,available=0 WHERE deleted_at IS NULL").use {
                    it.setLong(1, now)
                    it.executeUpdate()
                }
                catDeleted = c.prepareStatement("UPDATE categories SET deleted_at=?,active=0 WHERE deleted_at IS NULL").use {
                    it.setLong(1, now)
                    it.executeUpdate()
                }
            } else if (selection.products) {
                prodDeleted = c.prepareStatement("UPDATE products SET deleted_at=?,active=0,available=0 WHERE deleted_at IS NULL").use {
                    it.setLong(1, System.currentTimeMillis())
                    it.executeUpdate()
                }
            }

            // 4. Restaurant Layout
            if (selection.tablesAndAreas) {
                c.createStatement().use { it.executeUpdate("UPDATE orders SET table_id=NULL WHERE table_id IS NOT NULL") }
                tablesDeleted = c.createStatement().use { it.executeUpdate("DELETE FROM restaurant_tables") }
                areasDeleted = c.createStatement().use { it.executeUpdate("DELETE FROM dining_areas") }
            }

            // 5. Users
            if (selection.cashiers) {
                cashiersDeleted = c.prepareStatement("UPDATE users SET deleted_at=?,active=0 WHERE role='CASHIER' AND deleted_at IS NULL").use {
                    it.setLong(1, System.currentTimeMillis())
                    it.executeUpdate()
                }
            }

            (products as? Products)?.refreshState()
            (categories as? Categories)?.refreshState()
            (tables as? Tables)?.refreshState()
            (users as? Users)?.refreshState()

            val summary = DeletionSummary(
                productsDeleted = prodDeleted,
                categoriesDeleted = catDeleted,
                salesHistoryDeleted = salesDeleted,
                suspendedSalesDeleted = suspDeleted,
                tablesDeleted = tablesDeleted,
                areasDeleted = areasDeleted,
                cashiersDeleted = cashiersDeleted,
                message = "Suppression sélective terminée."
            )
            logDeletionAudit(c, actingUserId, "SELECTIVE_DELETION", summary.totalRecordsDeleted, "Suppression sélective terminée")
            summary
        }
    }

    fun factoryResetData(actingUserId: Long = 1L) = read { c ->
        transactionBlocking {
            val licenseKey = c.prepareStatement("SELECT value FROM settings WHERE key='license_key'").use { s -> s.executeQuery().use { if (it.next()) it.getString(1) else null } }
            val licenseSig = c.prepareStatement("SELECT value FROM settings WHERE key='license_signature'").use { s -> s.executeQuery().use { if (it.next()) it.getString(1) else null } }
            val licenseStatus = c.prepareStatement("SELECT value FROM settings WHERE key='license_status'").use { s -> s.executeQuery().use { if (it.next()) it.getString(1) else null } }
            val adminPassHash = c.prepareStatement("SELECT value FROM settings WHERE key='admin_deletion_password_hash'").use { s -> s.executeQuery().use { if (it.next()) it.getString(1) else null } }
            val adminPassSalt = c.prepareStatement("SELECT value FROM settings WHERE key='admin_deletion_password_salt'").use { s -> s.executeQuery().use { if (it.next()) it.getString(1) else null } }

            c.createStatement().use { s ->
                // 1. Transactional child records
                s.executeUpdate("DELETE FROM payments")
                s.executeUpdate("DELETE FROM order_items")
                s.executeUpdate("DELETE FROM cash_movements")

                // 2. Main transactions
                s.executeUpdate("DELETE FROM orders")
                s.executeUpdate("DELETE FROM register_sessions")

                // 3. Catalogue
                s.executeUpdate("DELETE FROM products")
                s.executeUpdate("DELETE FROM categories")

                // 4. Layout
                s.executeUpdate("DELETE FROM restaurant_tables")
                s.executeUpdate("DELETE FROM dining_areas")

                // 5. System, logs & users
                s.executeUpdate("DELETE FROM audit_logs")
                s.executeUpdate("DELETE FROM registers")
                s.executeUpdate("DELETE FROM users")
                s.executeUpdate("DELETE FROM settings")
            }

            licenseKey?.let { v -> c.prepareStatement("INSERT INTO settings(key,value) VALUES('license_key',?)").use { it.setString(1, v); it.executeUpdate() } }
            licenseSig?.let { v -> c.prepareStatement("INSERT INTO settings(key,value) VALUES('license_signature',?)").use { it.setString(1, v); it.executeUpdate() } }
            licenseStatus?.let { v -> c.prepareStatement("INSERT INTO settings(key,value) VALUES('license_status',?)").use { it.setString(1, v); it.executeUpdate() } }
            adminPassHash?.let { v -> c.prepareStatement("INSERT INTO settings(key,value) VALUES('admin_deletion_password_hash',?)").use { it.setString(1, v); it.executeUpdate() } }
            adminPassSalt?.let { v -> c.prepareStatement("INSERT INTO settings(key,value) VALUES('admin_deletion_password_salt',?)").use { it.setString(1, v); it.executeUpdate() } }
            c.createStatement().use { it.executeUpdate("INSERT INTO settings(key,value) VALUES('setup_complete','false')") }

            databasePath?.parent?.resolve("images")?.let { dir ->
                runCatching {
                    if (Files.exists(dir)) {
                        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                    }
                }
            }

            (products as? Products)?.refreshState()
            (categories as? Categories)?.refreshState()
            (tables as? Tables)?.refreshState()
            (users as? Users)?.refreshState()
        }
    }

    fun backupTo(target: Path) = read { c ->
        target.parent?.let(Files::createDirectories)
        Files.deleteIfExists(target)
        val escaped=target.toAbsolutePath().toString().replace("'","''")
        c.createStatement().use { it.execute("VACUUM INTO '$escaped'") }
        val assetsRoot = databasePath?.parent?.resolve("images")
        if (assetsRoot != null && Files.isDirectory(assetsRoot)) {
            DriverManager.getConnection("jdbc:sqlite:${target.toAbsolutePath()}").use { backup ->
                backup.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS backup_managed_files(relative_path TEXT PRIMARY KEY,data BLOB NOT NULL)") }
                backup.prepareStatement("INSERT OR REPLACE INTO backup_managed_files(relative_path,data) VALUES(?,?)").use { insert ->
                    Files.walk(assetsRoot).use { paths ->
                        paths.filter(Files::isRegularFile).forEach { file ->
                            insert.setString(1, assetsRoot.relativize(file).toString().replace('\\', '/'))
                            insert.setBytes(2, Files.readAllBytes(file))
                            insert.addBatch()
                        }
                    }
                    insert.executeBatch()
                }
            }
        }
    }

    fun validateBackup(source: Path): Boolean = isValidSqlite(source)

    fun stageRestore(source: Path) {
        require(validateBackup(source)) { "Invalid or corrupted backup" }
        val live = requireNotNull(databasePath) { "Restore is unavailable for an in-memory database" }
        val pending = live.resolveSibling("${live.fileName}.restore-pending")
        val temporary = live.resolveSibling("${live.fileName}.restore-pending.tmp")
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
        require(validateBackup(temporary)) { "Staged restore validation failed" }
        try { Files.move(temporary, pending, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, pending, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun columnExists(table: String, column: String): Boolean =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                while (rows.next()) if (rows.getString("name").equals(column, ignoreCase = true)) return@use true
                false
            }
        }

    private fun addColumnIfMissing(table: String, column: String, definition: String) {
        if (!columnExists(table, column)) {
            connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
        }
    }

    private fun migrate() = lock.withLock {
        connection.createStatement().use { statement ->
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)")
        }
        val appliedVersions = connection.createStatement().use { s ->
            s.executeQuery("SELECT version FROM schema_migrations").use { r ->
                buildSet { while (r.next()) add(r.getInt(1)) }
            }
        }
        if (1 !in appliedVersions) transactionBlocking {
            schemaV1().forEach { sql -> connection.createStatement().use { it.execute(sql) } }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(1,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (2 !in appliedVersions) transactionBlocking {
            addColumnIfMissing("dining_areas", "image_path", "TEXT")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(2,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (3 !in appliedVersions) transactionBlocking {
            addColumnIfMissing("register_sessions", "closing_user_id", "INTEGER REFERENCES users(id)")
            addColumnIfMissing("register_sessions", "left_in_drawer_centimes", "INTEGER")
            addColumnIfMissing("register_sessions", "removed_amount_centimes", "INTEGER")
            addColumnIfMissing("register_sessions", "remittance_reference", "TEXT")
            addColumnIfMissing("register_sessions", "remittance_destination", "TEXT")
            addColumnIfMissing("register_sessions", "closing_note", "TEXT")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(3,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (4 !in appliedVersions) transactionBlocking {
            // Add retail fields without rewriting or dropping any legacy table data.
            addColumnIfMissing("products", "sku", "TEXT")
            addColumnIfMissing("products", "barcode", "TEXT")
            connection.createStatement().use {
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_sku ON products(sku)")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
            }
            addColumnIfMissing("order_items", "category_id_snapshot", "INTEGER")
            addColumnIfMissing("order_items", "category_name_snapshot", "TEXT")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(4,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (5 !in appliedVersions) transactionBlocking {
            listOf("products", "categories", "restaurant_tables", "dining_areas", "users").forEach { table ->
                addColumnIfMissing(table, "deleted_at", "INTEGER DEFAULT NULL")
            }
            connection.createStatement().use {
                it.execute("DROP INDEX IF EXISTS idx_products_sku")
                it.execute("DROP INDEX IF EXISTS idx_products_barcode")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_sku ON products(sku) WHERE deleted_at IS NULL AND sku IS NOT NULL AND sku != ''")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode) WHERE deleted_at IS NULL AND barcode IS NOT NULL AND barcode != ''")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_pin ON users(pin_hash) WHERE deleted_at IS NULL")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_name ON categories(name COLLATE NOCASE) WHERE deleted_at IS NULL")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_cat_name ON products(category_id, name COLLATE NOCASE) WHERE deleted_at IS NULL")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_dining_areas_name ON dining_areas(name COLLATE NOCASE) WHERE deleted_at IS NULL")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_restaurant_tables_area_name ON restaurant_tables(area_id, name COLLATE NOCASE) WHERE deleted_at IS NULL")
            }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(5,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (6 !in appliedVersions) transactionBlocking {
            listOf(
                "CREATE INDEX IF NOT EXISTS idx_orders_updated_status ON orders(updated_at DESC, status)",
                "CREATE INDEX IF NOT EXISTS idx_orders_cashier ON orders(cashier_id)",
                "CREATE INDEX IF NOT EXISTS idx_orders_table_id ON orders(table_id)",
                "CREATE INDEX IF NOT EXISTS idx_orders_session_id ON orders(register_session_id)",
                "CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at)",
                "CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items(product_id)"
            ).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(6,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (7 !in appliedVersions) transactionBlocking {
            connection.createStatement().use {
                // Older versions allowed only one OPEN session for the whole register.
                // Replacing the index is metadata-only and preserves every session row.
                it.execute("DROP INDEX IF EXISTS one_open_session_per_register")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS one_open_session_per_cashier ON register_sessions(cashier_id) WHERE status='OPEN'")
            }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(7,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (8 !in appliedVersions) transactionBlocking {
            addColumnIfMissing("categories", "parent_id", "INTEGER REFERENCES categories(id)")
            addColumnIfMissing("orders", "customer_name", "TEXT")
            addColumnIfMissing("orders", "customer_phone", "TEXT")
            addColumnIfMissing("orders", "pickup_date", "INTEGER")
            addColumnIfMissing("orders", "preparation_status", "TEXT DEFAULT 'PENDING'")
            addColumnIfMissing("orders", "custom_note", "TEXT")
            addColumnIfMissing("orders", "deposit_centimes", "INTEGER DEFAULT 0")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(8,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (9 !in appliedVersions) transactionBlocking {
            addColumnIfMissing("categories", "image_path", "TEXT")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(9,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (10 !in appliedVersions) transactionBlocking {
            addColumnIfMissing("products", "name_arabic", "TEXT")
            addColumnIfMissing("products", "unit", "TEXT")
            addColumnIfMissing("products", "description", "TEXT")
            connection.createStatement().use {
                it.execute("DROP INDEX IF EXISTS idx_categories_name")
                it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_parent_name ON categories(IFNULL(parent_id, 0), name COLLATE NOCASE) WHERE deleted_at IS NULL")
            }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(10,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (11 !in appliedVersions) transactionBlocking {
            connection.createStatement().use { s ->
                s.execute("PRAGMA foreign_keys=OFF")
                s.execute("""
                    CREATE TABLE products_new(
                        id INTEGER PRIMARY KEY,
                        category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
                        name TEXT NOT NULL,
                        price_centimes INTEGER NOT NULL CHECK(price_centimes>0),
                        tax_basis_points INTEGER NOT NULL CHECK(tax_basis_points BETWEEN 0 AND 10000),
                        image_path TEXT,
                        available INTEGER NOT NULL DEFAULT 1,
                        active INTEGER NOT NULL DEFAULT 1,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        sku TEXT,
                        barcode TEXT,
                        name_arabic TEXT,
                        unit TEXT,
                        description TEXT,
                        deleted_at INTEGER DEFAULT NULL
                    )
                """.trimIndent())
                s.execute("""
                    INSERT INTO products_new(id, category_id, name, price_centimes, tax_basis_points, image_path, available, active, display_order, sku, barcode, name_arabic, unit, description, deleted_at)
                    SELECT id, category_id, name, price_centimes, tax_basis_points, image_path, available, active, display_order, sku, barcode, name_arabic, unit, description, deleted_at
                    FROM products
                """.trimIndent())
                s.execute("DROP TABLE products")
                s.execute("ALTER TABLE products_new RENAME TO products")
                s.execute("DROP INDEX IF EXISTS idx_products_category_active")
                s.execute("CREATE INDEX IF NOT EXISTS idx_products_category_active ON products(category_id,active,available)")
                s.execute("DROP INDEX IF EXISTS idx_products_sku")
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_sku ON products(sku) WHERE deleted_at IS NULL AND sku IS NOT NULL AND sku != ''")
                s.execute("DROP INDEX IF EXISTS idx_products_barcode")
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode) WHERE deleted_at IS NULL AND barcode IS NOT NULL AND barcode != ''")
                s.execute("DROP INDEX IF EXISTS idx_products_cat_name")
                s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_cat_name ON products(IFNULL(category_id, 0), name COLLATE NOCASE) WHERE deleted_at IS NULL")
                s.execute("PRAGMA foreign_keys=ON")
            }
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(11,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
        if (12 !in appliedVersions) transactionBlocking {
            // Nullable discount inputs distinguish historical orders whose original
            // per-item rules were never stored. No client order or payment is rewritten.
            addColumnIfMissing("orders", "discount_basis_points", "INTEGER")
            addColumnIfMissing("order_items", "item_discount_basis_points", "INTEGER")
            addColumnIfMissing("order_items", "recognized_amount_centimes", "INTEGER")
            addColumnIfMissing("order_items", "recognized_tax_centimes", "INTEGER")
            connection.prepareStatement("INSERT INTO schema_migrations(version,applied_at) VALUES(12,?)").use {
                it.setLong(1, System.currentTimeMillis()); it.executeUpdate()
            }
        }
    }

    private fun seedBaseFixturesForTests(): Unit = lock.withLock {
        val count = connection.createStatement().use { s -> s.executeQuery("SELECT COUNT(*) FROM users").use { it.next(); it.getInt(1) } }
        if (count != 0) return@withLock
        transactionBlocking {
            connection.prepareStatement("INSERT INTO users(id,name,role,pin_hash,active,created_at) VALUES(1,'Owner','OWNER',?,1,?)").use {
                it.setString(1, hashCredential("1234")); it.setLong(2, System.currentTimeMillis()); it.executeUpdate()
            }
            connection.createStatement().use { s ->
                s.executeUpdate("INSERT INTO registers(id,name,active) VALUES(1,'Main Register',1)")
                val categories = listOf("Pâtisserie", "Viennoiserie", "Gâteaux & Tartes", "Boissons")
                categories.forEachIndexed { index, name ->
                    connection.prepareStatement("INSERT INTO categories(id,name,display_order,active) VALUES(?,?,?,1)").use {
                        it.setLong(1, index + 1L); it.setString(2, name); it.setInt(3, index); it.executeUpdate()
                    }
                }
                s.executeUpdate("INSERT INTO settings(key,value) VALUES('establishment_name','PATISSERIE_POS')")
            }
        }
        Unit
    }

    internal fun <T> read(block: (Connection) -> T): T = lock.withLock { block(connection) }
    private fun <T> transactionBlocking(block: () -> T): T {
        val outer = transactionDepth == 0
        if (outer) connection.autoCommit = false
        transactionDepth++
        return try {
            val result = block()
            transactionDepth--
            if (outer) { connection.commit(); connection.autoCommit = true }
            result
        } catch (error: Throwable) {
            transactionDepth--
            if (outer) { connection.rollback(); connection.autoCommit = true }
            throw error
        }
    }

    override fun close() = lock.withLock { connection.close() }

    private inner class Transactions : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            lock.lock()
            val outer = transactionDepth == 0
            if (outer) connection.autoCommit = false
            transactionDepth++
            return try {
                val value = block(); transactionDepth--; if (outer) { connection.commit(); connection.autoCommit = true }; value
            } catch (error: Throwable) {
                transactionDepth--; if (outer) { connection.rollback(); connection.autoCommit = true }; throw error
            } finally { lock.unlock() }
        }
    }

    private inner class Users : UserRepository {
        private val state = MutableStateFlow(load())
        fun refreshState() { state.value = load() }
        private fun load() = read { c -> c.createStatement().use { s -> s.executeQuery("SELECT id,name,role,active FROM users WHERE active=1 AND deleted_at IS NULL ORDER BY name").use { rs -> rs.map { User(getLong(1), getString(2), UserRole.valueOf(getString(3)), getInt(4) != 0) } } } }
        override suspend fun findById(id: Long) = read { c -> c.prepareStatement("SELECT id,name,role,active FROM users WHERE id=?").use { it.setLong(1,id); it.executeQuery().use { r -> if(r.next()) User(r.getLong(1),r.getString(2),UserRole.valueOf(r.getString(3)),r.getInt(4)!=0) else null } } }
        override fun observeActive(): Flow<List<User>> = state
    }
    private inner class Authentication : AuthenticationRepository {
        override suspend fun authenticate(credential: String): User? {
            if (!ma.elaroui.pos.shared.rules.PinValidationRules.isValid(credential)) return null
            return read { c ->
                c.prepareStatement("SELECT id,name,role,active,pin_hash FROM users WHERE active=1 AND deleted_at IS NULL").use { s ->
                    s.executeQuery().use { rs ->
                        while (rs.next()) {
                            val id = rs.getLong(1)
                            val name = rs.getString(2)
                            val role = UserRole.valueOf(rs.getString(3))
                            val storedHash = rs.getString(5)
                            if (PinHasher.verify(credential, storedHash)) {
                                return@read User(id, name, role, true)
                            }
                        }
                        null
                    }
                }
            }
        }
    }
    private inner class Categories : CategoryRepository {
        private val state = MutableStateFlow(load())
        fun refreshState() { state.value = load() }
        private fun load() = read { c -> c.createStatement().use { s -> s.executeQuery("SELECT id,name,active,display_order,parent_id,image_path FROM categories WHERE deleted_at IS NULL ORDER BY display_order,name").use { it.map { Category(getLong(1),getString(2),getInt(3)!=0,getInt(4),nullLong("parent_id"),getString(6)) } } } }
        override suspend fun findById(id: Long) = read { c -> c.prepareStatement("SELECT id,name,active,display_order,parent_id,image_path FROM categories WHERE id=?").use { it.setLong(1, id); it.executeQuery().use { r -> if (r.next()) Category(r.getLong(1), r.getString(2), r.getInt(3) != 0, r.getInt(4), r.nullLong("parent_id"), r.getString(6)) else null } } }
        override suspend fun findByName(name: String) = read { c -> c.prepareStatement("SELECT id,name,active,display_order,parent_id,image_path FROM categories WHERE name=? COLLATE NOCASE AND deleted_at IS NULL").use { it.setString(1, name.trim()); it.executeQuery().use { r -> if (r.next()) Category(r.getLong(1), r.getString(2), r.getInt(3) != 0, r.getInt(4), r.nullLong("parent_id"), r.getString(6)) else null } } }
        override fun observeAll(): Flow<List<Category>> = state

        internal fun ensureCategoryUnique(c: Connection, name: String, parentId: Long?, excludedCategoryId: Long?) {
            val sql = if (parentId == null) {
                "SELECT 1 FROM categories WHERE parent_id IS NULL AND name=? COLLATE NOCASE AND deleted_at IS NULL" +
                    if (excludedCategoryId == null) "" else " AND id<>?"
            } else {
                "SELECT 1 FROM categories WHERE parent_id=? AND name=? COLLATE NOCASE AND deleted_at IS NULL" +
                    if (excludedCategoryId == null) "" else " AND id<>?"
            }
            c.prepareStatement(sql).use { statement ->
                if (parentId == null) {
                    statement.setString(1, name.trim())
                    excludedCategoryId?.let { statement.setLong(2, it) }
                } else {
                    statement.setLong(1, parentId)
                    statement.setString(2, name.trim())
                    excludedCategoryId?.let { statement.setLong(3, it) }
                }
                statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_CATEGORY_MESSAGE) }
            }
        }

        private fun mapCategoryConstraint(error: SQLException): Throwable =
            if (error.message.orEmpty().contains("categories.name", ignoreCase = true) ||
                error.message.orEmpty().contains("UNIQUE constraint", ignoreCase = true)
            ) DesktopValidationException(DUPLICATE_CATEGORY_MESSAGE, error) else error

        override suspend fun save(category: Category): Long = read { c ->
            val id = if (category.id == 0L) nextId("categories") else category.id
            val allExisting = load()
            val validation = CategoryHierarchyRules.validateCategoryHierarchy(category.copy(id = id), allExisting, requireImage = false)
            if (validation.isFailure) {
                throw DesktopValidationException(validation.exceptionOrNull()?.message ?: "Erreur de validation de la catégorie.")
            }
            try {
                ensureCategoryUnique(c, category.name, category.parentId, category.id.takeIf { it != 0L })
                upsert(c, "categories", id, listOf("name" to category.name.trim(), "active" to category.active, "display_order" to category.displayOrder, "parent_id" to category.parentId, "image_path" to category.imagePath)).also { state.value = load() }
            } catch (error: SQLException) {
                throw mapCategoryConstraint(error)
            }
        }
    }
    private inner class Products : ProductRepository {
        private val state = MutableStateFlow(load())
        fun refreshState() { state.value = load() }
        private fun load() = read { c -> c.createStatement().use { s -> s.executeQuery("SELECT id,category_id,name,price_centimes,tax_basis_points,available,active,image_path,sku,barcode,name_arabic,unit,description FROM products WHERE deleted_at IS NULL ORDER BY display_order,name").use { it.map { Product(getLong(1),nullLong(2),getString(3),getLong(4),getInt(5),getInt(6)!=0,getInt(7)!=0,getString(8),getString(9),getString(10),getString(11),getString(12),getString(13)) } } } }
        override suspend fun findById(id: Long) = read { c -> c.prepareStatement("SELECT id,category_id,name,price_centimes,tax_basis_points,available,active,image_path,sku,barcode,name_arabic,unit,description FROM products WHERE id=?").use { it.setLong(1, id); it.executeQuery().use { r -> if (r.next()) Product(r.getLong(1), r.nullLong(2), r.getString(3), r.getLong(4), r.getInt(5), r.getInt(6) != 0, r.getInt(7) != 0, r.getString(8), r.getString(9), r.getString(10), r.getString(11), r.getString(12), r.getString(13)) else null } } }
        fun findByBarcode(barcode: String): Product? = read { c ->
            val clean = barcode.trim()
            if (clean.isBlank()) return@read null
            c.prepareStatement("SELECT id,category_id,name,price_centimes,tax_basis_points,available,active,image_path,sku,barcode,name_arabic,unit,description FROM products WHERE barcode=? AND deleted_at IS NULL").use {
                it.setString(1, clean)
                it.executeQuery().use { r ->
                    if (r.next()) Product(r.getLong(1), r.nullLong(2), r.getString(3), r.getLong(4), r.getInt(5), r.getInt(6) != 0, r.getInt(7) != 0, r.getString(8), r.getString(9), r.getString(10), r.getString(11), r.getString(12), r.getString(13)) else null
                }
            }
        }
        fun findBySku(sku: String): Product? = read { c ->
            val clean = sku.trim()
            if (clean.isBlank()) return@read null
            c.prepareStatement("SELECT id,category_id,name,price_centimes,tax_basis_points,available,active,image_path,sku,barcode,name_arabic,unit,description FROM products WHERE sku=? AND deleted_at IS NULL").use {
                it.setString(1, clean)
                it.executeQuery().use { r ->
                    if (r.next()) Product(r.getLong(1), r.nullLong(2), r.getString(3), r.getLong(4), r.getInt(5), r.getInt(6) != 0, r.getInt(7) != 0, r.getString(8), r.getString(9), r.getString(10), r.getString(11), r.getString(12), r.getString(13)) else null
                }
            }
        }
        override fun observeSellable(): Flow<List<Product>> = state.map { list -> list.filter { it.active && it.available } }
        override fun observeAll(): Flow<List<Product>> = state

        private fun ensureProductUnique(c: Connection, categoryId: Long?, name: String, excludedProductId: Long?, sku: String?, barcode: String?) {
            val sql = "SELECT 1 FROM products WHERE IFNULL(category_id, 0)=IFNULL(?, 0) AND name=? COLLATE NOCASE AND deleted_at IS NULL" +
                if (excludedProductId == null) "" else " AND id<>?"
            c.prepareStatement(sql).use { statement ->
                if (categoryId != null) statement.setLong(1, categoryId) else statement.setNull(1, java.sql.Types.INTEGER)
                statement.setString(2, name.trim())
                excludedProductId?.let { statement.setLong(3, it) }
                statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_PRODUCT_MESSAGE) }
            }
            if (!barcode.isNullOrBlank()) {
                val sqlBarcode = "SELECT 1 FROM products WHERE barcode=? AND deleted_at IS NULL" +
                    if (excludedProductId == null) "" else " AND id<>?"
                c.prepareStatement(sqlBarcode).use { statement ->
                    statement.setString(1, barcode.trim())
                    excludedProductId?.let { statement.setLong(2, it) }
                    statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_BARCODE_MESSAGE) }
                }
            }
            if (!sku.isNullOrBlank()) {
                val sqlSku = "SELECT 1 FROM products WHERE sku=? AND deleted_at IS NULL" +
                    if (excludedProductId == null) "" else " AND id<>?"
                c.prepareStatement(sqlSku).use { statement ->
                    statement.setString(1, sku.trim())
                    excludedProductId?.let { statement.setLong(2, it) }
                    statement.executeQuery().use { if (it.next()) throw DesktopValidationException(DUPLICATE_SKU_MESSAGE) }
                }
            }
        }

        private fun mapProductConstraint(error: SQLException): Throwable {
            val msg = error.message.orEmpty()
            return when {
                msg.contains("products.category_id, products.name", ignoreCase = true) ||
                msg.contains("products.category_id", ignoreCase = true) ||
                msg.contains("products.name", ignoreCase = true) ||
                (msg.contains("UNIQUE constraint", ignoreCase = true) && !msg.contains("sku", ignoreCase = true) && !msg.contains("barcode", ignoreCase = true)) ->
                    DesktopValidationException(DUPLICATE_PRODUCT_MESSAGE, error)
                msg.contains("sku", ignoreCase = true) ->
                    DesktopValidationException(DUPLICATE_SKU_MESSAGE, error)
                msg.contains("barcode", ignoreCase = true) ->
                    DesktopValidationException(DUPLICATE_BARCODE_MESSAGE, error)
                else -> error
            }
        }

        override suspend fun save(product: Product): Long = read { c ->
            val id = if (product.id == 0L) nextId("products") else product.id
            try {
                ensureProductUnique(c, product.categoryId, product.name, product.id.takeIf { it != 0L }, product.sku, product.barcode)
                upsert(
                    c, "products", id,
                    listOf(
                        "category_id" to product.categoryId,
                        "name" to product.name.trim(),
                        "price_centimes" to product.priceCentimes,
                        "tax_basis_points" to product.taxRateBasisPoints,
                        "image_path" to product.imagePath,
                        "available" to product.available,
                        "active" to product.active,
                        "sku" to product.sku?.trim()?.ifBlank { null },
                        "barcode" to product.barcode?.trim()?.ifBlank { null },
                        "name_arabic" to product.nameArabic?.trim()?.ifBlank { null },
                        "unit" to product.unit?.trim()?.ifBlank { null },
                        "description" to product.description?.trim()?.ifBlank { null },
                        "display_order" to 0
                    )
                ).also { state.value = load() }
            } catch (error: SQLException) {
                throw mapProductConstraint(error)
            }
        }
    }
    private inner class Tables : TableRepository {
        private val state = MutableStateFlow(load())
        fun refreshState() { state.value = load() }
        private fun load() = allTables()
        override suspend fun findById(id: Long) = read { c -> c.prepareStatement("SELECT id,area_id,name,status,active,display_order FROM restaurant_tables WHERE id=?").use { it.setLong(1, id); it.executeQuery().use { r -> if (r.next()) RestaurantTable(r.getLong(1), r.getLong(2), r.getString(3), TableStatus.valueOf(r.getString(4)), r.getInt(5) != 0, r.getInt(6)) else null } } }
        override fun observeActive(): Flow<List<RestaurantTable>> = state.map { list -> list.filter { it.active } }
        override fun observeAll(): Flow<List<RestaurantTable>> = state
        override suspend fun save(table: RestaurantTable): Long = read { c ->
            val id = if (table.id == 0L) nextId("restaurant_tables") else table.id
            try {
                ensureTableUnique(c, table.areaId, table.name, table.id.takeIf { it != 0L })
                upsert(c, "restaurant_tables", id, listOf("area_id" to table.areaId, "name" to table.name.trim(), "status" to table.status.name, "active" to table.active, "display_order" to table.displayOrder)).also { state.value = load() }
            } catch (error: SQLException) {
                throw mapTableConstraint(error)
            }
        }
        override suspend fun updateStatus(id: Long, status: TableStatus) = read { c -> setTableStatus(c, id, status).also { state.value = load() } }
    }

    private inner class Sessions : RegisterSessionRepository {
        override suspend fun findOpen(): RegisterSession? = read { c -> sessionQuery(c, "SELECT * FROM register_sessions WHERE status='OPEN' ORDER BY opened_at DESC LIMIT 1", null) }
        override suspend fun findOpenByUser(cashierId: Long): RegisterSession? = read { c ->
            sessionQuery(c, "SELECT * FROM register_sessions WHERE cashier_id=? AND status='OPEN' ORDER BY opened_at DESC LIMIT 1", cashierId)
        }
        override suspend fun findById(id: Long): RegisterSession? = read { c -> sessionQuery(c, "SELECT * FROM register_sessions WHERE id=? LIMIT 1", id) }
        override suspend fun save(session: RegisterSession): Long = read { c ->
            val id = if (session.id == 0L) nextId("register_sessions") else session.id
            upsert(c, "register_sessions", id, listOf(
                "register_id" to session.registerId,
                "cashier_id" to session.cashierId,
                "opened_at" to session.openedAtEpochMilliseconds,
                "closed_at" to session.closedAtEpochMilliseconds,
                "opening_cash_centimes" to session.openingCashCentimes,
                "expected_cash_centimes" to session.expectedCashCentimes,
                "counted_cash_centimes" to session.countedCashCentimes,
                "difference_centimes" to session.differenceCentimes,
                "status" to session.status.name,
                "closing_user_id" to session.closingUserId,
                "left_in_drawer_centimes" to session.leftInDrawerCentimes,
                "removed_amount_centimes" to session.removedAmountCentimes,
                "remittance_reference" to session.remittanceReference,
                "remittance_destination" to session.remittanceDestination,
                "closing_note" to session.closingNote
            ))
        }
    }
    fun openOrdersForSession(sessionId: Long): List<Order> = read { c ->
        val sql = "SELECT id, order_number, type, status, subtotal_centimes, discount_centimes, discount_basis_points, tax_centimes, total_centimes, table_id, register_session_id, cashier_id, created_at, updated_at, customer_name, customer_phone, pickup_date, preparation_status, custom_note, deposit_centimes FROM orders WHERE status='OPEN' AND register_session_id=? ORDER BY updated_at DESC"
        val rawOrders = c.prepareStatement(sql).use { p ->
            p.setLong(1, sessionId)
            p.executeQuery().use { r ->
                r.map {
                    val id = getLong("id")
                    val createdAt = getLong("created_at").takeIf { it > 0L } ?: getLong("updated_at")
                    val prepStatusStr = getString("preparation_status")
                    val prepStatus = if (!prepStatusStr.isNullOrBlank()) {
                        runCatching { PreparationStatus.valueOf(prepStatusStr) }.getOrDefault(PreparationStatus.PENDING)
                    } else PreparationStatus.PENDING
                    val order = Order(
                        id = id,
                        number = getString("order_number"),
                        type = OrderType.valueOf(getString("type")),
                        status = OrderStatus.valueOf(getString("status")),
                        lines = emptyList(),
                        subtotalCentimes = getLong("subtotal_centimes"),
                        discountCentimes = getLong("discount_centimes"),
                        taxCentimes = getLong("tax_centimes"),
                        totalCentimes = getLong("total_centimes"),
                        tableId = nullLong("table_id"),
                        registerSessionId = getLong("register_session_id"),
                        cashierId = getLong("cashier_id"),
                        createdAtEpochMilliseconds = createdAt,
                        customerName = getString("customer_name"),
                        customerPhone = getString("customer_phone"),
                        pickupDateEpochMs = nullLong("pickup_date"),
                        preparationStatus = prepStatus,
                        customNote = getString("custom_note"),
                        depositCentimes = getLong("deposit_centimes"),
                        discountBasisPoints = nullInt("discount_basis_points")
                    )
                    id to order
                }
            }
        }
        val linesMap = loadOrderLinesBatch(c, rawOrders.map { it.first })
        rawOrders.map { (id, order) -> order.copy(lines = linesMap[id].orEmpty()) }
    }

    private inner class Orders : OrderRepository {
        override fun observeOpen(sessionId: Long): Flow<List<Order>> = MutableStateFlow(openOrdersForSession(sessionId))
        override suspend fun countOpenForSession(sessionId: Long): Int = countOpenOrdersForSession(sessionId)
        override suspend fun findById(id: Long): Order? = read { c -> loadOrder(c, id) }
        override suspend fun save(order: Order): Long = read { c ->
            transactionBlocking {
                val id = if (order.id == 0L) nextId("orders") else order.id
                val previous = loadOrder(c, id)
                if (previous != null) {
                    require(previous.status == OrderStatus.OPEN) { "Completed or cancelled orders cannot be overwritten" }
                    require(previous.cashierId == order.cashierId && previous.registerSessionId == order.registerSessionId) {
                        "Order belongs to another user session"
                    }
                }
                val previousTableId = orderTable(c, id)
                val existingCreatedAt = orderCreatedAt(c, id)
                val createdAt = when {
                    existingCreatedAt != null && existingCreatedAt > 0L -> existingCreatedAt
                    order.createdAtEpochMilliseconds > 0L -> order.createdAtEpochMilliseconds
                    else -> System.currentTimeMillis()
                }
                upsert(
                    c, "orders", id,
                    listOf(
                        "order_number" to order.number,
                        "type" to order.type.name,
                        "table_id" to order.tableId,
                        "cashier_id" to order.cashierId,
                        "register_session_id" to order.registerSessionId,
                        "status" to order.status.name,
                        "subtotal_centimes" to order.subtotalCentimes,
                        "discount_centimes" to order.discountCentimes,
                        "discount_basis_points" to order.discountBasisPoints,
                        "tax_centimes" to order.taxCentimes,
                        "total_centimes" to order.totalCentimes,
                        "customer_name" to order.customerName,
                        "customer_phone" to order.customerPhone,
                        "pickup_date" to order.pickupDateEpochMs,
                        "preparation_status" to order.preparationStatus.name,
                        "custom_note" to order.customNote,
                        "deposit_centimes" to order.depositCentimes,
                        "created_at" to createdAt,
                        "updated_at" to System.currentTimeMillis()
                    )
                )
                c.prepareStatement("DELETE FROM order_items WHERE order_id=?").use { it.setLong(1, id); it.executeUpdate() }
                order.lines.forEach { line ->
                    c.prepareStatement("INSERT INTO order_items(order_id,product_id,product_name,unit_price_centimes,tax_basis_points,quantity,line_total_centimes,category_id_snapshot,category_name_snapshot,item_discount_basis_points,recognized_amount_centimes,recognized_tax_centimes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use {
                        it.setLong(1, id); it.setLong(2, line.productId); it.setString(3, line.name); it.setLong(4, line.unitPriceCentimes); it.setInt(5, line.taxRateBasisPoints); it.setInt(6, line.quantity); it.setLong(7, line.unitPriceCentimes * line.quantity); it.setObject(8, line.categoryIdSnapshot); it.setString(9, line.categoryNameSnapshot); it.setObject(10, order.discountBasisPoints?.let { line.itemDiscountBasisPoints }); it.setObject(11, line.recognizedAmountCentimes); it.setObject(12, line.recognizedTaxCentimes); it.executeUpdate()
                    }
                }
                if(previousTableId!=null&&(previousTableId!=order.tableId||order.status!=OrderStatus.OPEN))releaseTableIfUnused(c,previousTableId,id)
                order.tableId?.let { setTableStatus(c,it,if(order.status==OrderStatus.OPEN)TableStatus.OCCUPIED else TableStatus.AVAILABLE) }
                id
            }
        }
    }
    private inner class Payments : PaymentRepository {
        override suspend fun findBySubmissionToken(orderId: Long, token: String): Payment? = read { c ->
            c.prepareStatement("SELECT * FROM payments WHERE order_id=? AND submission_token=? LIMIT 1").use {
                it.setLong(1, orderId); it.setString(2, token)
                val rs = it.executeQuery()
                if (rs.next()) Payment(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("session_id"), PaymentMethod.valueOf(rs.getString("method")), rs.getLong("amount_centimes"), rs.getLong("received_centimes"), rs.getLong("change_centimes"), PaymentStatus.valueOf(rs.getString("status")), rs.getString("submission_token"), rs.getLong("created_at")) else null
            }
        }
        override suspend fun save(payment: Payment): Long = read { c ->
            c.prepareStatement(
                "INSERT INTO payments(order_id,session_id,method,amount_centimes,received_centimes,change_centimes,status,submission_token,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            ).use { stmt ->
                stmt.setLong(1, payment.orderId)
                stmt.setLong(2, payment.registerSessionId)
                stmt.setString(3, payment.method.name)
                stmt.setLong(4, payment.amountCentimes)
                stmt.setLong(5, payment.receivedCentimes)
                stmt.setLong(6, payment.changeCentimes)
                stmt.setString(7, payment.status.name)
                stmt.setString(8, payment.submissionToken)
                stmt.setLong(9, payment.createdAtEpochMilliseconds)
                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) rs.getLong(1) else 1L
                }
            }
        }
        override suspend fun totalCashForSession(sessionId: Long): Long = read { c ->
            c.prepareStatement("SELECT COALESCE(SUM(amount_centimes),0) FROM payments WHERE session_id=? AND method='CASH' AND status='COMPLETED'").use {
                it.setLong(1, sessionId); it.executeQuery().use { r -> r.next(); r.getLong(1) }
            }
        }
        override suspend fun totalNonCashForSession(sessionId: Long): Long = read { c ->
            c.prepareStatement("SELECT COALESCE(SUM(amount_centimes),0) FROM payments WHERE session_id=? AND method<>'CASH' AND status='COMPLETED'").use {
                it.setLong(1, sessionId); it.executeQuery().use { r -> r.next(); r.getLong(1) }
            }
        }
    }
    private inner class CashMovements : CashMovementRepository {
        override fun observeForSession(sessionId: Long): Flow<List<CashMovement>> = MutableStateFlow(read { c ->
            c.prepareStatement("SELECT * FROM cash_movements WHERE session_id=? ORDER BY created_at DESC").use { p ->
                p.setLong(1, sessionId); p.executeQuery().use { r -> r.map { CashMovement(getLong("id"), getLong("session_id"), CashMovementType.valueOf(getString("type")), getLong("amount_centimes"), getString("reason"), getString("description"), getLong("created_by_user_id"), getLong("created_at")) } }
            }
        })
        override suspend fun totalCashIn(sessionId:Long):Long=totalForType(sessionId,CashMovementType.CASH_IN)
        override suspend fun totalCashOut(sessionId:Long):Long=totalForType(sessionId,CashMovementType.CASH_OUT)
        private fun totalForType(sessionId:Long,type:CashMovementType):Long=read{c->c.prepareStatement("SELECT COALESCE(SUM(amount_centimes),0) FROM cash_movements WHERE session_id=? AND type=?").use{p->p.setLong(1,sessionId);p.setString(2,type.name);p.executeQuery().use{r->r.next();r.getLong(1)}}}
        override suspend fun save(movement: CashMovement): Long = read { c ->
            c.prepareStatement(
                "INSERT INTO cash_movements(session_id,type,amount_centimes,reason,description,created_by_user_id,created_at) VALUES(?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            ).use { stmt ->
                stmt.setLong(1, movement.sessionId)
                stmt.setString(2, movement.type.name)
                stmt.setLong(3, movement.amountCentimes)
                stmt.setString(4, movement.reason)
                stmt.setString(5, movement.description)
                stmt.setLong(6, movement.createdByUserId)
                stmt.setLong(7, movement.createdAtEpochMilliseconds)
                stmt.executeUpdate()
                stmt.generatedKeys.use { rs ->
                    if (rs.next()) rs.getLong(1) else 1L
                }
            }
        }
    }
    private inner class Settings : SettingsRepository {
        override suspend fun get(key: String): String? = read { c -> c.prepareStatement("SELECT value FROM settings WHERE key=?").use { it.setString(1, key); it.executeQuery().use { r -> if(r.next()) r.getString(1) else null } } }
        override suspend fun put(setting: AppSetting) = read { c -> c.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use { it.setString(1, setting.key); it.setString(2, setting.value); it.executeUpdate() }; Unit }
    }

    private fun sessionQuery(c:Connection,sql:String,id:Long?):RegisterSession?=c.prepareStatement(sql).use { p->if(id!=null)p.setLong(1,id);p.executeQuery().use { r->if(!r.next())null else RegisterSession(r.getLong("id"),RegisterSessionStatus.valueOf(r.getString("status")),r.getLong("opening_cash_centimes"),r.getLong("register_id"),r.getLong("cashier_id"),r.getLong("opened_at"),r.nullLong("closed_at"),r.nullLong("expected_cash_centimes"),r.nullLong("counted_cash_centimes"),r.nullLong("difference_centimes"),r.nullLong("closing_user_id"),r.nullLong("left_in_drawer_centimes"),r.nullLong("removed_amount_centimes"),r.getString("remittance_reference"),r.getString("remittance_destination"),r.getString("closing_note")) } }
    private fun orderTable(c:Connection,id:Long):Long?=c.prepareStatement("SELECT table_id FROM orders WHERE id=?").use{p->p.setLong(1,id);p.executeQuery().use{r->if(r.next())r.nullLong("table_id") else null}}
    private fun orderCreatedAt(c:Connection,id:Long):Long?=c.prepareStatement("SELECT created_at FROM orders WHERE id=?").use{p->p.setLong(1,id);p.executeQuery().use{r->if(r.next())r.getLong("created_at") else null}}
    private fun setTableStatus(c:Connection,id:Long,status:TableStatus){c.prepareStatement("UPDATE restaurant_tables SET status=? WHERE id=?").use{it.setString(1,status.name);it.setLong(2,id);it.executeUpdate()}}
    private fun releaseTableIfUnused(c:Connection,tableId:Long?,excludingOrderId:Long){if(tableId==null)return;val used=c.prepareStatement("SELECT 1 FROM orders WHERE table_id=? AND status='OPEN' AND id<>? LIMIT 1").use{it.setLong(1,tableId);it.setLong(2,excludingOrderId);it.executeQuery().use(ResultSet::next)};if(!used)setTableStatus(c,tableId,TableStatus.AVAILABLE)}
    private fun loadOrder(c:Connection,id:Long):Order?=c.prepareStatement("SELECT * FROM orders WHERE id=?").use { p->
        p.setLong(1,id);p.executeQuery().use { r->
            if(!r.next())return null
            val lines=c.prepareStatement("SELECT product_id,product_name,unit_price_centimes,quantity,tax_basis_points,category_id_snapshot,category_name_snapshot,item_discount_basis_points,recognized_amount_centimes,recognized_tax_centimes FROM order_items WHERE order_id=? ORDER BY id").use { q->
                q.setLong(1,id);q.executeQuery().use { x->x.map { OrderLine(getLong(1),getString(2),getLong(3),getInt(4),getInt(5),nullLong("category_id_snapshot"),getString("category_name_snapshot"),getInt("item_discount_basis_points"),nullLong("recognized_amount_centimes"),nullLong("recognized_tax_centimes")) } }
            }
            val createdAt=r.getLong("created_at").takeIf{it>0L}?:r.getLong("updated_at")
            val prepStatusStr = r.getString("preparation_status")
            val prepStatus = if (!prepStatusStr.isNullOrBlank()) {
                runCatching { PreparationStatus.valueOf(prepStatusStr) }.getOrDefault(PreparationStatus.PENDING)
            } else PreparationStatus.PENDING
            Order(
                id = id,
                number = r.getString("order_number"),
                type = OrderType.valueOf(r.getString("type")),
                status = OrderStatus.valueOf(r.getString("status")),
                lines = lines,
                subtotalCentimes = r.getLong("subtotal_centimes"),
                discountCentimes = r.getLong("discount_centimes"),
                taxCentimes = r.getLong("tax_centimes"),
                totalCentimes = r.getLong("total_centimes"),
                tableId = r.nullLong("table_id"),
                registerSessionId = r.getLong("register_session_id"),
                cashierId = r.getLong("cashier_id"),
                createdAtEpochMilliseconds = createdAt,
                customerName = r.getString("customer_name"),
                customerPhone = r.getString("customer_phone"),
                pickupDateEpochMs = r.nullLong("pickup_date"),
                preparationStatus = prepStatus,
                customNote = r.getString("custom_note"),
                depositCentimes = r.getLong("deposit_centimes"),
                discountBasisPoints = r.nullInt("discount_basis_points")
            )
        }
    }
    private fun loadOrderLinesBatch(c: Connection, orderIds: List<Long>): Map<Long, List<OrderLine>> {
        if (orderIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<OrderLine>>()
        orderIds.distinct().chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            c.prepareStatement("SELECT order_id, product_id, product_name, unit_price_centimes, quantity, tax_basis_points, category_id_snapshot, category_name_snapshot, item_discount_basis_points, recognized_amount_centimes, recognized_tax_centimes FROM order_items WHERE order_id IN ($placeholders) ORDER BY id").use { stmt ->
                chunk.forEachIndexed { index, id -> stmt.setLong(index + 1, id) }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val orderId = rs.getLong(1)
                        val line = OrderLine(
                            productId = rs.getLong(2),
                            name = rs.getString(3),
                            unitPriceCentimes = rs.getLong(4),
                            quantity = rs.getInt(5),
                            taxRateBasisPoints = rs.getInt(6),
                            categoryIdSnapshot = rs.nullLong("category_id_snapshot"),
                            categoryNameSnapshot = rs.getString("category_name_snapshot"),
                            itemDiscountBasisPoints = rs.getInt("item_discount_basis_points"),
                            recognizedAmountCentimes = rs.nullLong("recognized_amount_centimes"),
                            recognizedTaxCentimes = rs.nullLong("recognized_tax_centimes")
                        )
                        result.getOrPut(orderId) { mutableListOf() }.add(line)
                    }
                }
            }
        }
        return result
    }
    private fun upsert(c:Connection,table:String,id:Long,values:List<Pair<String,Any?>>):Long { val columns=listOf("id")+values.map{it.first};val sql="INSERT INTO $table(${columns.joinToString()}) VALUES(${columns.joinToString{ "?" }}) ON CONFLICT(id) DO UPDATE SET ${values.joinToString { "${it.first}=excluded.${it.first}" }}";c.prepareStatement(sql).use { p->p.setLong(1,id);values.forEachIndexed { i,v->p.setObject(i+2,when(val x=v.second){is Boolean->if(x)1 else 0 else->x}) };p.executeUpdate() };return id }
    private fun ResultSet.nullLong(column:String):Long? { val value=getLong(column);return if(wasNull())null else value }
    private fun ResultSet.nullInt(column:String):Int? { val value=getInt(column);return if(wasNull())null else value }
    private fun ResultSet.nullLong(index:Int):Long? { val value=getLong(index);return if(wasNull())null else value }
    private inline fun <T> ResultSet.map(block:ResultSet.()->T):List<T>{val out=mutableListOf<T>();while(next())out+=block();return out}

    private fun schemaV1() = listOf(
        "CREATE TABLE users(id INTEGER PRIMARY KEY,name TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('OWNER','CASHIER')),pin_hash TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,deleted_at INTEGER DEFAULT NULL)",
        "CREATE TABLE categories(id INTEGER PRIMARY KEY,name TEXT NOT NULL COLLATE NOCASE,display_order INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,deleted_at INTEGER DEFAULT NULL)",
        "CREATE TABLE products(id INTEGER PRIMARY KEY,category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,name TEXT NOT NULL,price_centimes INTEGER NOT NULL CHECK(price_centimes>0),tax_basis_points INTEGER NOT NULL CHECK(tax_basis_points BETWEEN 0 AND 10000),image_path TEXT,available INTEGER NOT NULL DEFAULT 1,active INTEGER NOT NULL DEFAULT 1,display_order INTEGER NOT NULL DEFAULT 0,sku TEXT,barcode TEXT,deleted_at INTEGER DEFAULT NULL)",
        "CREATE TABLE dining_areas(id INTEGER PRIMARY KEY,name TEXT NOT NULL COLLATE NOCASE,display_order INTEGER NOT NULL DEFAULT 0,active INTEGER NOT NULL DEFAULT 1,image_path TEXT,deleted_at INTEGER DEFAULT NULL)",
        "CREATE TABLE restaurant_tables(id INTEGER PRIMARY KEY,area_id INTEGER NOT NULL REFERENCES dining_areas(id),name TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('AVAILABLE','RESERVED','OCCUPIED')),active INTEGER NOT NULL DEFAULT 1,display_order INTEGER NOT NULL DEFAULT 0,deleted_at INTEGER DEFAULT NULL)",
        "CREATE TABLE registers(id INTEGER PRIMARY KEY,name TEXT NOT NULL UNIQUE,active INTEGER NOT NULL DEFAULT 1)",
        "CREATE TABLE register_sessions(id INTEGER PRIMARY KEY,register_id INTEGER NOT NULL REFERENCES registers(id),cashier_id INTEGER NOT NULL REFERENCES users(id),opened_at INTEGER NOT NULL,opening_cash_centimes INTEGER NOT NULL CHECK(opening_cash_centimes>=0),closed_at INTEGER,expected_cash_centimes INTEGER,counted_cash_centimes INTEGER,difference_centimes INTEGER,status TEXT NOT NULL CHECK(status IN ('OPEN','CLOSED')))",
        "CREATE UNIQUE INDEX one_open_session_per_cashier ON register_sessions(cashier_id) WHERE status='OPEN'",
        "CREATE TABLE cash_movements(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER NOT NULL REFERENCES register_sessions(id) ON DELETE CASCADE,type TEXT NOT NULL CHECK(type IN ('CASH_IN','CASH_OUT')),amount_centimes INTEGER NOT NULL CHECK(amount_centimes>0),reason TEXT NOT NULL,description TEXT CHECK(description IS NULL OR length(description)<=200),created_by_user_id INTEGER NOT NULL REFERENCES users(id),created_at INTEGER NOT NULL)",
        "CREATE TABLE orders(id INTEGER PRIMARY KEY,order_number TEXT NOT NULL UNIQUE,type TEXT NOT NULL,table_id INTEGER REFERENCES restaurant_tables(id) ON DELETE SET NULL,cashier_id INTEGER NOT NULL REFERENCES users(id),register_session_id INTEGER NOT NULL REFERENCES register_sessions(id),status TEXT NOT NULL,subtotal_centimes INTEGER NOT NULL,discount_centimes INTEGER NOT NULL DEFAULT 0,tax_centimes INTEGER NOT NULL,total_centimes INTEGER NOT NULL,created_at INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE order_items(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,product_id INTEGER NOT NULL,product_name TEXT NOT NULL,unit_price_centimes INTEGER NOT NULL,tax_basis_points INTEGER NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),line_total_centimes INTEGER NOT NULL,category_id_snapshot INTEGER,category_name_snapshot TEXT,UNIQUE(order_id,product_id))",
        "CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,session_id INTEGER NOT NULL REFERENCES register_sessions(id),method TEXT NOT NULL,amount_centimes INTEGER NOT NULL,received_centimes INTEGER NOT NULL,change_centimes INTEGER NOT NULL,status TEXT NOT NULL,submission_token TEXT NOT NULL,created_at INTEGER NOT NULL,UNIQUE(order_id,submission_token))",
        "CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)",
        "CREATE TABLE audit_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,action_type TEXT NOT NULL,acting_user_id INTEGER NOT NULL REFERENCES users(id),entity_type TEXT NOT NULL,entity_id INTEGER,created_at INTEGER NOT NULL,details TEXT)",
        "CREATE INDEX idx_products_category_active ON products(category_id,active,available)",
        "CREATE UNIQUE INDEX idx_products_sku ON products(sku) WHERE deleted_at IS NULL AND sku IS NOT NULL AND sku != ''",
        "CREATE UNIQUE INDEX idx_products_barcode ON products(barcode) WHERE deleted_at IS NULL AND barcode IS NOT NULL AND barcode != ''",
        "CREATE UNIQUE INDEX idx_products_cat_name ON products(IFNULL(category_id, 0), name COLLATE NOCASE) WHERE deleted_at IS NULL",
        "CREATE UNIQUE INDEX idx_categories_name ON categories(name COLLATE NOCASE) WHERE deleted_at IS NULL",
        "CREATE UNIQUE INDEX idx_users_pin ON users(pin_hash) WHERE deleted_at IS NULL",
        "CREATE UNIQUE INDEX idx_dining_areas_name ON dining_areas(name COLLATE NOCASE) WHERE deleted_at IS NULL",
        "CREATE UNIQUE INDEX idx_restaurant_tables_area_name ON restaurant_tables(area_id, name COLLATE NOCASE) WHERE deleted_at IS NULL",
        "CREATE INDEX idx_tables_area_status ON restaurant_tables(area_id,status,active)",
        "CREATE INDEX idx_orders_session_status ON orders(register_session_id,status)",
        "CREATE INDEX idx_orders_updated_status ON orders(updated_at DESC, status)",
        "CREATE INDEX idx_orders_cashier ON orders(cashier_id)",
        "CREATE INDEX idx_orders_table_id ON orders(table_id)",
        "CREATE INDEX idx_orders_session_id ON orders(register_session_id)",
        "CREATE INDEX idx_payments_session_method ON payments(session_id,method,status)",
        "CREATE INDEX idx_payments_created_at ON payments(created_at)",
        "CREATE INDEX idx_cash_movements_session ON cash_movements(session_id,created_at)",
        "CREATE INDEX idx_order_items_product ON order_items(product_id)"
    )
}
