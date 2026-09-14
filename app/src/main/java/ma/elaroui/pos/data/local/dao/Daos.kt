package ma.elaroui.pos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ma.elaroui.pos.data.local.entity.*

data class CategorySaleSampleRow(
    val categoryId: Long,
    val quantity: Int,
    val soldAtEpochMilliseconds: Long
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE pinHash = :pinHash AND active = 1 LIMIT 1")
    suspend fun getUserByPinHash(pinHash: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'OWNER' AND active = 1 LIMIT 1")
    suspend fun getOwnerUser(): UserEntity?

    @Query("SELECT * FROM users WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET active = 0 WHERE id = :id")
    suspend fun deactivateUser(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY displayOrder ASC")
    fun getAllActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY displayOrder ASC")
    fun getAllCategoriesForOwner(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET active = 0 WHERE id = :id")
    suspend fun deactivateCategory(id: Long)

    @Query("UPDATE categories SET active = 1 WHERE id = :id")
    suspend fun activateCategory(id: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE available = 1 AND active = 1 ORDER BY displayOrder ASC")
    fun getAllAvailableProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY displayOrder ASC")
    fun getAllProductsForOwner(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND available = 1 AND active = 1 ORDER BY displayOrder ASC")
    fun getProductsByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getProductByNameInCategory(categoryId: Long, name: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET available = :available WHERE id = :id")
    suspend fun setProductAvailability(id: Long, available: Boolean)

    @Query("UPDATE products SET active = 0 WHERE id = :id")
    suspend fun deactivateProduct(id: Long)

    @Query("UPDATE products SET active = 1 WHERE id = :id")
    suspend fun activateProduct(id: Long)
}

@Dao
interface TableDao {
    @Query("SELECT * FROM dining_areas WHERE active = 1 ORDER BY displayOrder ASC")
    fun getAllActiveAreas(): Flow<List<DiningAreaEntity>>

    @Query("SELECT * FROM dining_areas ORDER BY displayOrder ASC")
    fun getAllAreasForOwner(): Flow<List<DiningAreaEntity>>

    @Query("SELECT * FROM dining_areas WHERE id = :id")
    suspend fun getAreaById(id: Long): DiningAreaEntity?

    @Query("SELECT * FROM dining_areas WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getAreaByName(name: String): DiningAreaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: DiningAreaEntity): Long

    @Update
    suspend fun updateArea(area: DiningAreaEntity)

    @Query("UPDATE dining_areas SET active = 0 WHERE id = :id")
    suspend fun deactivateArea(id: Long)

    @Query("UPDATE dining_areas SET active = 1 WHERE id = :id")
    suspend fun activateArea(id: Long)

    @Query("SELECT * FROM restaurant_tables WHERE areaId = :areaId AND active = 1 ORDER BY displayOrder ASC, name ASC")
    fun getTablesByArea(areaId: Long): Flow<List<TableEntity>>

    @Query("SELECT * FROM restaurant_tables ORDER BY displayOrder ASC, name ASC")
    fun getAllTablesForOwner(): Flow<List<TableEntity>>

    @Query("SELECT * FROM restaurant_tables WHERE active = 1 ORDER BY displayOrder ASC, name ASC")
    fun getAllActiveTables(): Flow<List<TableEntity>>

    @Query("SELECT * FROM restaurant_tables WHERE id = :id")
    suspend fun getTableById(id: Long): TableEntity?

    @Query("SELECT * FROM restaurant_tables WHERE areaId = :areaId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getTableByNameInArea(areaId: Long, name: String): TableEntity?

    @Query("SELECT name FROM restaurant_tables WHERE areaId = :areaId")
    suspend fun getExistingTableNamesInArea(areaId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTablesBatch(tables: List<TableEntity>)

    @Update
    suspend fun updateTable(table: TableEntity)

    @Query("UPDATE restaurant_tables SET status = :status WHERE id = :tableId")
    suspend fun updateTableStatus(tableId: Long, status: String)

    @Query("UPDATE restaurant_tables SET active = 0 WHERE id = :id")
    suspend fun deactivateTable(id: Long)

    @Query("UPDATE restaurant_tables SET active = 1 WHERE id = :id")
    suspend fun activateTable(id: Long)
}

@Dao
interface RegisterDao {
    @Query("SELECT * FROM registers WHERE id = :id")
    suspend fun getRegisterById(id: Long): RegisterEntity?

    @Query("SELECT * FROM registers WHERE LOWER(name) = LOWER(:name) AND active = 1 LIMIT 1")
    suspend fun getRegisterByName(name: String): RegisterEntity?

    @Query("SELECT * FROM registers WHERE active = 1 ORDER BY name ASC")
    fun getAllActiveRegisters(): Flow<List<RegisterEntity>>

    @Query("SELECT * FROM registers ORDER BY name ASC")
    fun getAllRegisters(): Flow<List<RegisterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegister(register: RegisterEntity): Long

    @Update
    suspend fun updateRegister(register: RegisterEntity)

    @Query("SELECT * FROM register_sessions WHERE registerId = :registerId AND status = 'OPEN' LIMIT 1")
    suspend fun getActiveSessionForRegister(registerId: Long): RegisterSessionEntity?

    @Query("SELECT * FROM register_sessions WHERE cashierId = :cashierId AND status = 'OPEN' LIMIT 1")
    suspend fun getActiveSessionForCashier(cashierId: Long): RegisterSessionEntity?

    @Query("SELECT * FROM register_sessions WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getAnyActiveSession(): RegisterSessionEntity?

    @Query("SELECT * FROM register_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): RegisterSessionEntity?

    @Query("SELECT * FROM register_sessions WHERE status = 'OPEN' ORDER BY openedAt DESC")
    fun getOpenSessions(): Flow<List<RegisterSessionEntity>>

    @Query("SELECT * FROM register_sessions ORDER BY openedAt DESC")
    fun getAllSessionsHistory(): Flow<List<RegisterSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun openSession(session: RegisterSessionEntity): Long

    @Update
    suspend fun updateSession(session: RegisterSessionEntity)
}

@Dao
interface CashMovementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: CashMovementEntity): Long

    @Query("SELECT * FROM cash_movements WHERE registerSessionId = :sessionId ORDER BY createdAt ASC")
    fun getCashMovementsForSession(sessionId: Long): Flow<List<CashMovementEntity>>

    @Query("SELECT * FROM cash_movements WHERE registerSessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getCashMovementsListForSession(sessionId: Long): List<CashMovementEntity>

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM cash_movements WHERE registerSessionId = :sessionId AND type = 'CASH_IN'")
    suspend fun getTotalCashInForSession(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM cash_movements WHERE registerSessionId = :sessionId AND type = 'CASH_OUT'")
    suspend fun getTotalCashOutForSession(sessionId: Long): Long
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE tableId = :tableId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenOrderByTableId(tableId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun getActiveOrdersForList(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE registerSessionId = :sessionId AND status = 'OPEN' ORDER BY createdAt DESC")
    fun getOpenOrdersBySessionId(sessionId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE registerSessionId = :sessionId ORDER BY createdAt DESC")
    fun getOrdersBySessionId(sessionId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE registerSessionId = :sessionId AND status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedOrdersForSession(sessionId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getAllCompletedOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'COMPLETED' AND completedAt >= :startMs AND completedAt <= :endMs ORDER BY completedAt DESC")
    suspend fun getCompletedOrdersForDateRange(startMs: Long, endMs: Long): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE status = 'CANCELLED' AND cancelledAt >= :startMs AND cancelledAt <= :endMs ORDER BY cancelledAt DESC")
    suspend fun getCancelledOrdersForDateRange(startMs: Long, endMs: Long): List<OrderEntity>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED' AND cancelledAt >= :startMs AND cancelledAt <= :endMs")
    suspend fun getCancelledOrderCountForDateRange(startMs: Long, endMs: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE orderNumber LIKE 'ORD-' || :datePrefix || '-%'")
    suspend fun getDailyOrderCount(datePrefix: String): Int

    @Query("SELECT COUNT(*) FROM orders WHERE registerSessionId = :sessionId AND status = 'OPEN'")
    suspend fun getOpenOrderCountForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE registerSessionId = :sessionId AND status = 'COMPLETED'")
    suspend fun getCompletedOrderCountForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE registerSessionId = :sessionId AND status = 'CANCELLED'")
    suspend fun getCancelledOrderCountForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE tableId = :tableId AND status = 'OPEN'")
    suspend fun getOpenOrderCountForTable(tableId: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE tableId IN (SELECT id FROM restaurant_tables WHERE areaId = :areaId) AND status = 'OPEN'")
    suspend fun getOpenOrderCountForArea(areaId: Long): Int

    @Query(
        """
        SELECT COALESCE(oi.categoryIdSnapshot, p.categoryId) AS categoryId,
               oi.quantity AS quantity,
               o.completedAt AS soldAtEpochMilliseconds
        FROM order_items oi
        INNER JOIN orders o ON o.id = oi.orderId
        LEFT JOIN products p ON p.id = oi.productId
        WHERE o.status = 'COMPLETED'
          AND o.completedAt IS NOT NULL
          AND o.completedAt >= :sinceEpochMilliseconds
          AND COALESCE(oi.categoryIdSnapshot, p.categoryId) IS NOT NULL
        """
    )
    suspend fun getRecentCategorySaleSamples(sinceEpochMilliseconds: Long): List<CategorySaleSampleRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Update
    suspend fun updateOrderItem(item: OrderItemEntity)

    @Query("DELETE FROM order_items WHERE id = :itemId")
    suspend fun deleteOrderItem(itemId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteAllOrderItems(orderId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE orderId = :orderId")
    suspend fun getPaymentsForOrder(orderId: Long): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE registerSessionId = :sessionId AND method = 'CASH' AND status = 'COMPLETED'")
    suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE registerSessionId = :sessionId AND method = 'CARD' AND status = 'COMPLETED'")
    suspend fun getTotalCardPaymentsForSession(sessionId: Long): Long
}

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE orderId = :orderId AND submissionToken = :token LIMIT 1")
    suspend fun getPaymentByOrderAndToken(orderId: Long, token: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE orderId = :orderId")
    suspend fun getPaymentsForOrder(orderId: Long): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE registerSessionId = :sessionId AND method = 'CASH' AND status = 'COMPLETED'")
    suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE registerSessionId = :sessionId AND method = 'CARD' AND status = 'COMPLETED'")
    suspend fun getTotalCardPaymentsForSession(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE registerSessionId = :sessionId AND status = 'COMPLETED'")
    suspend fun getTotalPaymentsForSession(sessionId: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE createdAt >= :startMs AND createdAt <= :endMs AND method = 'CASH' AND status = 'COMPLETED'")
    suspend fun getCashPaymentsForDateRange(startMs: Long, endMs: Long): Long

    @Query("SELECT COALESCE(SUM(amountCentimes), 0) FROM payments WHERE createdAt >= :startMs AND createdAt <= :endMs AND method = 'CARD' AND status = 'COMPLETED'")
    suspend fun getCardPaymentsForDateRange(startMs: Long, endMs: Long): Long
}

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    suspend fun getLogsForEntity(entityType: String, entityId: Long): List<AuditLogEntity>
}

@Dao
interface PrinterSettingsDao {
    @Query("SELECT * FROM printer_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): PrinterSettingsEntity?

    @Query("SELECT * FROM printer_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<PrinterSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: PrinterSettingsEntity)
}
