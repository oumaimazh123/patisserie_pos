package ma.elaroui.pos.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ma.elaroui.pos.data.local.dao.*
import ma.elaroui.pos.data.mapper.*
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton
import ma.elaroui.pos.shared.rules.CategorySaleSample

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {
    override suspend fun getUserById(id: Long): User? =
        userDao.getUserById(id)?.toDomain()

    override suspend fun getUserByPin(pin: String): User? =
        userDao.getUserByPinHash(pin)?.toDomain()

    override suspend fun getOwnerUser(): User? =
        userDao.getOwnerUser()?.toDomain()

    override fun getAllActiveUsers(): Flow<List<User>> =
        userDao.getAllActiveUsers().map { list -> list.map { it.toDomain() } }

    override suspend fun insertUser(user: User): Long =
        userDao.insertUser(user.toEntity())

    override suspend fun updateUser(user: User) =
        userDao.updateUser(user.toEntity())

    override suspend fun deactivateUser(id: Long) =
        userDao.deactivateUser(id)
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {
    override fun getAllActiveCategories(): Flow<List<Category>> =
        categoryDao.getAllActiveCategories().map { list -> list.map { it.toDomain() } }

    override fun getAllCategoriesForOwner(): Flow<List<Category>> =
        categoryDao.getAllCategoriesForOwner().map { list -> list.map { it.toDomain() } }

    override suspend fun getCategoryById(id: Long): Category? =
        categoryDao.getCategoryById(id)?.toDomain()

    override suspend fun getCategoryByName(name: String): Category? =
        categoryDao.getCategoryByName(name)?.toDomain()

    override suspend fun insertCategory(category: Category): Long =
        categoryDao.insertCategory(category.toEntity())

    override suspend fun updateCategory(category: Category) =
        categoryDao.updateCategory(category.toEntity())

    override suspend fun deactivateCategory(id: Long) =
        categoryDao.deactivateCategory(id)

    override suspend fun activateCategory(id: Long) =
        categoryDao.activateCategory(id)
}

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao
) : ProductRepository {
    override fun getAllAvailableProducts(): Flow<List<Product>> =
        productDao.getAllAvailableProducts().map { list -> list.map { it.toDomain() } }

    override fun getAllProductsForOwner(): Flow<List<Product>> =
        productDao.getAllProductsForOwner().map { list -> list.map { it.toDomain() } }

    override fun getProductsByCategory(categoryId: Long): Flow<List<Product>> =
        productDao.getProductsByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    override suspend fun getProductByNameInCategory(categoryId: Long, name: String): Product? =
        productDao.getProductByNameInCategory(categoryId, name)?.toDomain()

    override suspend fun getProductById(id: Long): Product? =
        productDao.getProductById(id)?.toDomain()

    override suspend fun insertProduct(product: Product): Long =
        productDao.insertProduct(product.toEntity())

    override suspend fun updateProduct(product: Product) =
        productDao.updateProduct(product.toEntity())

    override suspend fun setProductAvailability(id: Long, available: Boolean) =
        productDao.setProductAvailability(id, available)

    override suspend fun deactivateProduct(id: Long) =
        productDao.deactivateProduct(id)

    override suspend fun activateProduct(id: Long) =
        productDao.activateProduct(id)
}

@Singleton
class TableRepositoryImpl @Inject constructor(
    private val tableDao: TableDao
) : TableRepository {
    override fun getAllActiveAreas(): Flow<List<DiningArea>> =
        tableDao.getAllActiveAreas().map { list -> list.map { it.toDomain() } }

    override fun getAllAreasForOwner(): Flow<List<DiningArea>> =
        tableDao.getAllAreasForOwner().map { list -> list.map { it.toDomain() } }

    override suspend fun getAreaById(id: Long): DiningArea? =
        tableDao.getAreaById(id)?.toDomain()

    override suspend fun getAreaByName(name: String): DiningArea? =
        tableDao.getAreaByName(name)?.toDomain()

    override suspend fun insertArea(area: DiningArea): Long =
        tableDao.insertArea(area.toEntity())

    override suspend fun updateArea(area: DiningArea) =
        tableDao.updateArea(area.toEntity())

    override suspend fun deactivateArea(id: Long) =
        tableDao.deactivateArea(id)

    override suspend fun activateArea(id: Long) =
        tableDao.activateArea(id)

    override fun getTablesByArea(areaId: Long): Flow<List<RestaurantTable>> =
        tableDao.getTablesByArea(areaId).map { list -> list.map { it.toDomain() } }

    override fun getAllTablesForOwner(): Flow<List<RestaurantTable>> =
        tableDao.getAllTablesForOwner().map { list -> list.map { it.toDomain() } }

    override fun getAllActiveTables(): Flow<List<RestaurantTable>> =
        tableDao.getAllActiveTables().map { list -> list.map { it.toDomain() } }

    override suspend fun getTableById(id: Long): RestaurantTable? =
        tableDao.getTableById(id)?.toDomain()

    override suspend fun getTableByNameInArea(areaId: Long, name: String): RestaurantTable? =
        tableDao.getTableByNameInArea(areaId, name)?.toDomain()

    override suspend fun getExistingTableNamesInArea(areaId: Long): List<String> =
        tableDao.getExistingTableNamesInArea(areaId)

    override suspend fun insertTable(table: RestaurantTable): Long =
        tableDao.insertTable(table.toEntity())

    override suspend fun insertTablesBatch(tables: List<RestaurantTable>) =
        tableDao.insertTablesBatch(tables.map { it.toEntity() })

    override suspend fun updateTable(table: RestaurantTable) =
        tableDao.updateTable(table.toEntity())

    override suspend fun updateTableStatus(tableId: Long, status: TableStatus) =
        tableDao.updateTableStatus(tableId, status.name)

    override suspend fun deactivateTable(id: Long) =
        tableDao.deactivateTable(id)

    override suspend fun activateTable(id: Long) =
        tableDao.activateTable(id)
}

@Singleton
class RegisterRepositoryImpl @Inject constructor(
    private val registerDao: RegisterDao
) : RegisterRepository {
    override suspend fun getRegisterById(id: Long): Register? =
        registerDao.getRegisterById(id)?.toDomain()

    override suspend fun getRegisterByName(name: String): Register? =
        registerDao.getRegisterByName(name)?.toDomain()

    override fun getAllActiveRegisters(): Flow<List<Register>> =
        registerDao.getAllActiveRegisters().map { list -> list.map { it.toDomain() } }

    override fun getAllRegisters(): Flow<List<Register>> =
        registerDao.getAllRegisters().map { list -> list.map { it.toDomain() } }

    override suspend fun insertRegister(register: Register): Long =
        registerDao.insertRegister(register.toEntity())

    override suspend fun updateRegister(register: Register) =
        registerDao.updateRegister(register.toEntity())

    override suspend fun getActiveSessionForRegister(registerId: Long): RegisterSession? =
        registerDao.getActiveSessionForRegister(registerId)?.toDomain()

    override suspend fun getActiveSessionForCashier(cashierId: Long): RegisterSession? =
        registerDao.getActiveSessionForCashier(cashierId)?.toDomain()

    override suspend fun getAnyActiveSession(): RegisterSession? =
        registerDao.getAnyActiveSession()?.toDomain()

    override suspend fun getSessionById(sessionId: Long): RegisterSession? =
        registerDao.getSessionById(sessionId)?.toDomain()

    override fun getOpenSessions(): Flow<List<RegisterSession>> =
        registerDao.getOpenSessions().map { list -> list.map { it.toDomain() } }

    override fun getAllSessionsHistory(): Flow<List<RegisterSession>> =
        registerDao.getAllSessionsHistory().map { list -> list.map { it.toDomain() } }

    override suspend fun openSession(session: RegisterSession): Long =
        registerDao.openSession(session.toEntity())

    override suspend fun updateSession(session: RegisterSession) =
        registerDao.updateSession(session.toEntity())
}

@Singleton
class CashMovementRepositoryImpl @Inject constructor(
    private val cashMovementDao: CashMovementDao
) : CashMovementRepository {
    override suspend fun insertCashMovement(movement: CashMovement): Long =
        cashMovementDao.insertCashMovement(movement.toEntity())

    override fun getCashMovementsForSession(sessionId: Long): Flow<List<CashMovement>> =
        cashMovementDao.getCashMovementsForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getCashMovementsListForSession(sessionId: Long): List<CashMovement> =
        cashMovementDao.getCashMovementsListForSession(sessionId).map { it.toDomain() }

    override suspend fun getTotalCashInForSession(sessionId: Long): Long =
        cashMovementDao.getTotalCashInForSession(sessionId)

    override suspend fun getTotalCashOutForSession(sessionId: Long): Long =
        cashMovementDao.getTotalCashOutForSession(sessionId)
}

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val paymentDao: PaymentDao
) : OrderRepository {
    override suspend fun getOrderById(id: Long): Order? {
        val entity = orderDao.getOrderById(id) ?: return null
        val items = orderDao.getOrderItems(id).map { it.toDomain() }
        val payments = orderDao.getPaymentsForOrder(id).map { it.toDomain() }
        return entity.toDomain(items = items, payments = payments)
    }

    override suspend fun getOpenOrderByTableId(tableId: Long): Order? {
        val entity = orderDao.getOpenOrderByTableId(tableId) ?: return null
        val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
        val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
        return entity.toDomain(items = items, payments = payments)
    }

    override fun getActiveOrdersForList(): Flow<List<Order>> =
        orderDao.getActiveOrdersForList().map { list ->
            list.map { entity ->
                val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
                val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
                entity.toDomain(items = items, payments = payments)
            }
        }

    override fun getOpenOrdersBySessionId(sessionId: Long): Flow<List<Order>> =
        orderDao.getOpenOrdersBySessionId(sessionId).map { list ->
            list.map { entity ->
                val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
                val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
                entity.toDomain(items = items, payments = payments)
            }
        }

    override fun getOrdersBySessionId(sessionId: Long): Flow<List<Order>> =
        orderDao.getOrdersBySessionId(sessionId).map { list ->
            list.map { entity ->
                val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
                val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
                entity.toDomain(items = items, payments = payments)
            }
        }

    override fun getCompletedOrdersForSession(sessionId: Long): Flow<List<Order>> =
        orderDao.getCompletedOrdersForSession(sessionId).map { list ->
            list.map { entity ->
                val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
                val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
                entity.toDomain(items = items, payments = payments)
            }
        }

    override fun getAllCompletedOrders(): Flow<List<Order>> =
        orderDao.getAllCompletedOrders().map { list ->
            list.map { entity ->
                val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
                val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
                entity.toDomain(items = items, payments = payments)
            }
        }

    override suspend fun getCompletedOrdersForDateRange(startMs: Long, endMs: Long): List<Order> =
        orderDao.getCompletedOrdersForDateRange(startMs, endMs).map { entity ->
            val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
            val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
            entity.toDomain(items = items, payments = payments)
        }

    override suspend fun getCancelledOrdersForDateRange(startMs: Long, endMs: Long): List<Order> =
        orderDao.getCancelledOrdersForDateRange(startMs, endMs).map { entity ->
            val items = orderDao.getOrderItems(entity.id).map { it.toDomain() }
            val payments = orderDao.getPaymentsForOrder(entity.id).map { it.toDomain() }
            entity.toDomain(items = items, payments = payments)
        }

    override suspend fun getCancelledOrderCountForDateRange(startMs: Long, endMs: Long): Int =
        orderDao.getCancelledOrderCountForDateRange(startMs, endMs)

    override suspend fun getDailyOrderCount(datePrefix: String): Int =
        orderDao.getDailyOrderCount(datePrefix)

    override suspend fun getOpenOrderCountForSession(sessionId: Long): Int =
        orderDao.getOpenOrderCountForSession(sessionId)

    override suspend fun getCompletedOrderCountForSession(sessionId: Long): Int =
        orderDao.getCompletedOrderCountForSession(sessionId)

    override suspend fun getCancelledOrderCountForSession(sessionId: Long): Int =
        orderDao.getCancelledOrderCountForSession(sessionId)

    override suspend fun getOpenOrderCountForTable(tableId: Long): Int =
        orderDao.getOpenOrderCountForTable(tableId)

    override suspend fun getOpenOrderCountForArea(areaId: Long): Int =
        orderDao.getOpenOrderCountForArea(areaId)

    override suspend fun insertOrder(order: Order): Long =
        orderDao.insertOrder(order.toEntity())

    override suspend fun updateOrder(order: Order) =
        orderDao.updateOrder(order.toEntity())

    override suspend fun getOrderItems(orderId: Long): List<OrderItem> =
        orderDao.getOrderItems(orderId).map { it.toDomain() }

    override suspend fun insertOrderItem(item: OrderItem): Long =
        orderDao.insertOrderItem(item.toEntity())

    override suspend fun updateOrderItem(item: OrderItem) =
        orderDao.updateOrderItem(item.toEntity())

    override suspend fun deleteOrderItem(itemId: Long) =
        orderDao.deleteOrderItem(itemId)

    override suspend fun insertPayment(payment: Payment): Long =
        paymentDao.insertPayment(payment.toEntity())

    override suspend fun getPaymentsForOrder(orderId: Long): List<Payment> =
        paymentDao.getPaymentsForOrder(orderId).map { it.toDomain() }

    override suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long =
        paymentDao.getTotalCashPaymentsForSession(sessionId)
}

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val paymentDao: PaymentDao
) : PaymentRepository {
    override suspend fun insertPayment(payment: Payment): Long =
        paymentDao.insertPayment(payment.toEntity())

    override suspend fun getPaymentByOrderAndToken(orderId: Long, token: String): Payment? =
        paymentDao.getPaymentByOrderAndToken(orderId, token)?.toDomain()

    override suspend fun getPaymentsForOrder(orderId: Long): List<Payment> =
        paymentDao.getPaymentsForOrder(orderId).map { it.toDomain() }

    override suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long =
        paymentDao.getTotalCashPaymentsForSession(sessionId)

    override suspend fun getTotalCardPaymentsForSession(sessionId: Long): Long =
        paymentDao.getTotalCardPaymentsForSession(sessionId)

    override suspend fun getTotalPaymentsForSession(sessionId: Long): Long =
        paymentDao.getTotalPaymentsForSession(sessionId)

    override suspend fun getCashPaymentsForDateRange(startMs: Long, endMs: Long): Long =
        paymentDao.getCashPaymentsForDateRange(startMs, endMs)

    override suspend fun getCardPaymentsForDateRange(startMs: Long, endMs: Long): Long =
        paymentDao.getCardPaymentsForDateRange(startMs, endMs)
}

@Singleton
class CategorySalesRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao
) : CategorySalesRepository {
    override suspend fun getRecentCategorySales(sinceEpochMilliseconds: Long): List<CategorySaleSample> =
        orderDao.getRecentCategorySaleSamples(sinceEpochMilliseconds).map { row ->
            CategorySaleSample(
                categoryId = row.categoryId,
                quantity = row.quantity,
                soldAtEpochMilliseconds = row.soldAtEpochMilliseconds
            )
        }
}

@Singleton
class PrinterSettingsRepositoryImpl @Inject constructor(
    private val printerSettingsDao: PrinterSettingsDao
) : PrinterSettingsRepository {
    override suspend fun getSettings(): PrinterSettings =
        printerSettingsDao.getSettings()?.toDomain() ?: PrinterSettings()

    override fun observeSettings(): Flow<PrinterSettings> =
        printerSettingsDao.observeSettings().map { it?.toDomain() ?: PrinterSettings() }

    override suspend fun saveSettings(settings: PrinterSettings) =
        printerSettingsDao.upsertSettings(settings.toEntity())
}

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val auditLogDao: AuditLogDao
) : AuditRepository {
    override suspend fun recordLog(log: AuditLog): Long =
        auditLogDao.insertAuditLog(log.toEntity())

    override suspend fun getRecentLogs(limit: Int): List<AuditLog> =
        auditLogDao.getRecentLogs(limit).map { it.toDomain() }
}
