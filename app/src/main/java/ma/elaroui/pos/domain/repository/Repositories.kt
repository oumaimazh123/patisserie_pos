package ma.elaroui.pos.domain.repository

import kotlinx.coroutines.flow.Flow
import ma.elaroui.pos.shared.rules.CategorySaleSample
import ma.elaroui.pos.domain.model.*

interface UserRepository {
    suspend fun getUserById(id: Long): User?
    suspend fun getUserByPin(pin: String): User?
    suspend fun getOwnerUser(): User?
    fun getAllActiveUsers(): Flow<List<User>>
    suspend fun insertUser(user: User): Long
    suspend fun updateUser(user: User)
    suspend fun deactivateUser(id: Long)
}

interface CategoryRepository {
    fun getAllActiveCategories(): Flow<List<Category>>
    fun getAllCategoriesForOwner(): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun getCategoryByName(name: String): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deactivateCategory(id: Long)
    suspend fun activateCategory(id: Long)
}

interface ProductRepository {
    fun getAllAvailableProducts(): Flow<List<Product>>
    fun getAllProductsForOwner(): Flow<List<Product>>
    fun getProductsByCategory(categoryId: Long): Flow<List<Product>>
    suspend fun getProductByNameInCategory(categoryId: Long, name: String): Product?
    suspend fun getProductById(id: Long): Product?
    suspend fun insertProduct(product: Product): Long
    suspend fun updateProduct(product: Product)
    suspend fun setProductAvailability(id: Long, available: Boolean)
    suspend fun deactivateProduct(id: Long)
    suspend fun activateProduct(id: Long)
}

interface TableRepository {
    fun getAllActiveAreas(): Flow<List<DiningArea>>
    fun getAllAreasForOwner(): Flow<List<DiningArea>>
    suspend fun getAreaById(id: Long): DiningArea?
    suspend fun getAreaByName(name: String): DiningArea?
    suspend fun insertArea(area: DiningArea): Long
    suspend fun updateArea(area: DiningArea)
    suspend fun deactivateArea(id: Long)
    suspend fun activateArea(id: Long)

    fun getTablesByArea(areaId: Long): Flow<List<RestaurantTable>>
    fun getAllTablesForOwner(): Flow<List<RestaurantTable>>
    fun getAllActiveTables(): Flow<List<RestaurantTable>>
    suspend fun getTableById(id: Long): RestaurantTable?
    suspend fun getTableByNameInArea(areaId: Long, name: String): RestaurantTable?
    suspend fun getExistingTableNamesInArea(areaId: Long): List<String>
    suspend fun insertTable(table: RestaurantTable): Long
    suspend fun insertTablesBatch(tables: List<RestaurantTable>)
    suspend fun updateTable(table: RestaurantTable)
    suspend fun updateTableStatus(tableId: Long, status: TableStatus)
    suspend fun deactivateTable(id: Long)
    suspend fun activateTable(id: Long)
}

interface RegisterRepository {
    suspend fun getRegisterById(id: Long): Register?
    suspend fun getRegisterByName(name: String): Register?
    fun getAllActiveRegisters(): Flow<List<Register>>
    fun getAllRegisters(): Flow<List<Register>>
    suspend fun insertRegister(register: Register): Long
    suspend fun updateRegister(register: Register)
    suspend fun getActiveSessionForRegister(registerId: Long): RegisterSession?
    suspend fun getActiveSessionForCashier(cashierId: Long): RegisterSession?
    suspend fun getAnyActiveSession(): RegisterSession?
    suspend fun getSessionById(sessionId: Long): RegisterSession?
    fun getOpenSessions(): Flow<List<RegisterSession>>
    fun getAllSessionsHistory(): Flow<List<RegisterSession>>
    suspend fun openSession(session: RegisterSession): Long
    suspend fun updateSession(session: RegisterSession)
}

interface CashMovementRepository {
    suspend fun insertCashMovement(movement: CashMovement): Long
    fun getCashMovementsForSession(sessionId: Long): Flow<List<CashMovement>>
    suspend fun getCashMovementsListForSession(sessionId: Long): List<CashMovement>
    suspend fun getTotalCashInForSession(sessionId: Long): Long
    suspend fun getTotalCashOutForSession(sessionId: Long): Long
}

interface OrderRepository {
    suspend fun getOrderById(id: Long): Order?
    suspend fun getOpenOrderByTableId(tableId: Long): Order?
    fun getActiveOrdersForList(): Flow<List<Order>>
    fun getOpenOrdersBySessionId(sessionId: Long): Flow<List<Order>>
    fun getOrdersBySessionId(sessionId: Long): Flow<List<Order>>
    fun getCompletedOrdersForSession(sessionId: Long): Flow<List<Order>>
    fun getAllCompletedOrders(): Flow<List<Order>>
    suspend fun getCompletedOrdersForDateRange(startMs: Long, endMs: Long): List<Order>
    suspend fun getCancelledOrdersForDateRange(startMs: Long, endMs: Long): List<Order>
    suspend fun getCancelledOrderCountForDateRange(startMs: Long, endMs: Long): Int
    suspend fun getDailyOrderCount(datePrefix: String): Int
    suspend fun getOpenOrderCountForSession(sessionId: Long): Int
    suspend fun getCompletedOrderCountForSession(sessionId: Long): Int
    suspend fun getCancelledOrderCountForSession(sessionId: Long): Int
    suspend fun getOpenOrderCountForTable(tableId: Long): Int
    suspend fun getOpenOrderCountForArea(areaId: Long): Int
    suspend fun insertOrder(order: Order): Long
    suspend fun updateOrder(order: Order)
    suspend fun getOrderItems(orderId: Long): List<OrderItem>
    suspend fun insertOrderItem(item: OrderItem): Long
    suspend fun updateOrderItem(item: OrderItem)
    suspend fun deleteOrderItem(itemId: Long)
    suspend fun insertPayment(payment: Payment): Long
    suspend fun getPaymentsForOrder(orderId: Long): List<Payment>
    suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long
}

interface PaymentRepository {
    suspend fun insertPayment(payment: Payment): Long
    suspend fun getPaymentByOrderAndToken(orderId: Long, token: String): Payment?
    suspend fun getPaymentsForOrder(orderId: Long): List<Payment>
    suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long
    suspend fun getTotalCardPaymentsForSession(sessionId: Long): Long
    suspend fun getTotalPaymentsForSession(sessionId: Long): Long
    suspend fun getCashPaymentsForDateRange(startMs: Long, endMs: Long): Long
    suspend fun getCardPaymentsForDateRange(startMs: Long, endMs: Long): Long
}

interface CategorySalesRepository {
    suspend fun getRecentCategorySales(sinceEpochMilliseconds: Long): List<CategorySaleSample>
}

interface PrinterSettingsRepository {
    suspend fun getSettings(): PrinterSettings
    fun observeSettings(): Flow<PrinterSettings>
    suspend fun saveSettings(settings: PrinterSettings)
}

interface AuditRepository {
    suspend fun recordLog(log: AuditLog): Long
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLog>
}
