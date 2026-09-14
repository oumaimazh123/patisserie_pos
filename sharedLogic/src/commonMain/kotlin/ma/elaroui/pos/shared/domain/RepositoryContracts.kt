package ma.elaroui.pos.shared.domain

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun findById(id: Long): User?
    fun observeActive(): Flow<List<User>>
}

interface AuthenticationRepository {
    suspend fun authenticate(credential: String): User?
}

interface CategoryRepository {
    suspend fun findById(id: Long): Category?
    suspend fun findByName(name: String): Category?
    fun observeAll(): Flow<List<Category>>
    suspend fun save(category: Category): Long
}

interface ProductRepository {
    suspend fun findById(id: Long): Product?
    fun observeSellable(): Flow<List<Product>>
    fun observeAll(): Flow<List<Product>>
    suspend fun save(product: Product): Long
}

interface TableRepository {
    suspend fun findById(id: Long): RestaurantTable?
    fun observeActive(): Flow<List<RestaurantTable>>
    fun observeAll(): Flow<List<RestaurantTable>>
    suspend fun save(table: RestaurantTable): Long
    suspend fun updateStatus(id: Long, status: TableStatus)
}

interface OrderRepository {
    suspend fun findById(id: Long): Order?
    fun observeOpen(sessionId: Long): Flow<List<Order>>
    suspend fun countOpenForSession(sessionId: Long): Int = 0
    suspend fun save(order: Order): Long
}

interface PaymentRepository {
    suspend fun findBySubmissionToken(orderId: Long, token: String): Payment?
    suspend fun save(payment: Payment): Long
    suspend fun totalCashForSession(sessionId: Long): Long
    suspend fun totalNonCashForSession(sessionId: Long): Long = 0L
}

interface RegisterSessionRepository {
    /** Administrative lookup only. Operational flows must use [findOpenByUser]. */
    suspend fun findOpen(): RegisterSession?
    suspend fun findOpenByUser(cashierId: Long): RegisterSession?
    suspend fun findById(id: Long): RegisterSession?
    suspend fun save(session: RegisterSession): Long
}

interface CashMovementRepository {
    fun observeForSession(sessionId: Long): Flow<List<CashMovement>>
    suspend fun totalCashIn(sessionId: Long): Long
    suspend fun totalCashOut(sessionId: Long): Long
    suspend fun save(movement: CashMovement): Long
}

interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

interface SettingsRepository {
    suspend fun get(key: String): String?
    suspend fun put(setting: AppSetting)
}
