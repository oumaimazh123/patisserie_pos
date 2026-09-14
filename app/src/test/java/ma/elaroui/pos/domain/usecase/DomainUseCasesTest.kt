package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.exception.*
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.session.SessionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeUserRepository : UserRepository {
    val users = mutableMapOf<Long, User>()
    init {
        users[1L] = User(id = 1L, name = "Owner User", role = UserRole.OWNER, pinHash = "1234")
        users[2L] = User(id = 2L, name = "Cashier User", role = UserRole.CASHIER, pinHash = "0000")
    }
    override suspend fun getUserById(id: Long): User? = users[id]
    override suspend fun getUserByPin(pin: String): User? = users.values.find { SecurityUtils.verifyPin(pin, it.pinHash) }
    override suspend fun getOwnerUser(): User? = users.values.find { it.role == UserRole.OWNER }
    override fun getAllActiveUsers(): Flow<List<User>> = flowOf(users.values.toList())
    override suspend fun insertUser(user: User): Long { users[user.id] = user; return user.id }
    override suspend fun updateUser(user: User) { users[user.id] = user }
    override suspend fun deactivateUser(id: Long) {}
}

class FakeCategoryRepository : CategoryRepository {
    val categories = mutableMapOf<Long, Category>()
    private var nextCategoryId = 2L
    init {
        categories[1L] = Category(id = 1L, name = "Desserts", active = true)
    }
    override fun getAllActiveCategories(): Flow<List<Category>> = flowOf(categories.values.filter { it.active })
    override fun getAllCategoriesForOwner(): Flow<List<Category>> = flowOf(categories.values.toList())
    override suspend fun getCategoryById(id: Long): Category? = categories[id]
    override suspend fun getCategoryByName(name: String): Category? = categories.values.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun insertCategory(category: Category): Long {
        val id = category.id.takeIf { it != 0L } ?: nextCategoryId++
        categories[id] = category.copy(id = id)
        return id
    }
    override suspend fun updateCategory(category: Category) { categories[category.id] = category }
    override suspend fun deactivateCategory(id: Long) { categories[id] = categories[id]?.copy(active = false) ?: return }
    override suspend fun activateCategory(id: Long) { categories[id] = categories[id]?.copy(active = true) ?: return }
}

class FakeProductRepository : ProductRepository {
    val products = mutableMapOf<Long, Product>()
    private var nextProductId = 21L
    init {
        products[10L] = Product(id = 10L, categoryId = 1L, name = "Café Nous-Nous", priceCentimes = 1500, available = true, active = true)
        products[20L] = Product(id = 20L, categoryId = 1L, name = "Thé à la Menthe", priceCentimes = 1200, available = false, active = true)
    }
    override fun getAllAvailableProducts(): Flow<List<Product>> = flowOf(products.values.filter { it.available && it.active })
    override fun getAllProductsForOwner(): Flow<List<Product>> = flowOf(products.values.toList())
    override fun getProductsByCategory(categoryId: Long): Flow<List<Product>> = flowOf(products.values.filter { it.categoryId == categoryId })
    override suspend fun getProductByNameInCategory(categoryId: Long, name: String): Product? = products.values.find { it.categoryId == categoryId && it.name.equals(name, ignoreCase = true) }
    override suspend fun getProductById(id: Long): Product? = products[id]
    override suspend fun insertProduct(product: Product): Long {
        val id = product.id.takeIf { it != 0L } ?: nextProductId++
        products[id] = product.copy(id = id)
        return id
    }
    override suspend fun updateProduct(product: Product) { products[product.id] = product }
    override suspend fun setProductAvailability(id: Long, available: Boolean) { products[id] = products[id]?.copy(available = available) ?: return }
    override suspend fun deactivateProduct(id: Long) { products[id] = products[id]?.copy(active = false) ?: return }
    override suspend fun activateProduct(id: Long) { products[id] = products[id]?.copy(active = true) ?: return }
}

class FakeTableRepository : TableRepository {
    val areas = mutableMapOf<Long, DiningArea>()
    val tables = mutableMapOf<Long, RestaurantTable>()
    private var nextAreaId = 2L
    private var nextTableId = 102L
    init {
        areas[1L] = DiningArea(id = 1L, name = "Terrasse", active = true)
        tables[100L] = RestaurantTable(id = 100L, areaId = 1L, name = "T-01", status = TableStatus.AVAILABLE, active = true)
        tables[101L] = RestaurantTable(id = 101L, areaId = 1L, name = "T-02", status = TableStatus.OCCUPIED, active = true)
    }
    override fun getAllActiveAreas(): Flow<List<DiningArea>> = flowOf(areas.values.filter { it.active })
    override fun getAllAreasForOwner(): Flow<List<DiningArea>> = flowOf(areas.values.toList())
    override suspend fun getAreaById(id: Long): DiningArea? = areas[id]
    override suspend fun getAreaByName(name: String): DiningArea? = areas.values.find { it.name.equals(name, ignoreCase = true) }
    override suspend fun insertArea(area: DiningArea): Long {
        val id = area.id.takeIf { it != 0L } ?: nextAreaId++
        areas[id] = area.copy(id = id)
        return id
    }
    override suspend fun updateArea(area: DiningArea) { areas[area.id] = area }
    override suspend fun deactivateArea(id: Long) { areas[id] = areas[id]?.copy(active = false) ?: return }
    override suspend fun activateArea(id: Long) { areas[id] = areas[id]?.copy(active = true) ?: return }

    override fun getTablesByArea(areaId: Long): Flow<List<RestaurantTable>> = flowOf(tables.values.filter { it.areaId == areaId })
    override fun getAllTablesForOwner(): Flow<List<RestaurantTable>> = flowOf(tables.values.toList())
    override fun getAllActiveTables(): Flow<List<RestaurantTable>> = flowOf(tables.values.filter { it.active })
    override suspend fun getTableById(id: Long): RestaurantTable? = tables[id]
    override suspend fun getTableByNameInArea(areaId: Long, name: String): RestaurantTable? = tables.values.find { it.areaId == areaId && it.name.equals(name, ignoreCase = true) }
    override suspend fun getExistingTableNamesInArea(areaId: Long): List<String> =
        tables.values.filter { it.areaId == areaId }.map { it.name }
    override suspend fun insertTable(table: RestaurantTable): Long {
        val id = table.id.takeIf { it != 0L } ?: nextTableId++
        tables[id] = table.copy(id = id)
        return id
    }
    override suspend fun insertTablesBatch(tablesList: List<RestaurantTable>) {
        tablesList.forEach { insertTable(it) }
    }
    override suspend fun updateTable(table: RestaurantTable) { tables[table.id] = table }
    override suspend fun updateTableStatus(tableId: Long, status: TableStatus) {
        tables[tableId] = tables[tableId]?.copy(status = status) ?: return
    }
    override suspend fun deactivateTable(id: Long) { tables[id] = tables[id]?.copy(active = false) ?: return }
    override suspend fun activateTable(id: Long) { tables[id] = tables[id]?.copy(active = true) ?: return }
}

class FakeRegisterRepository : RegisterRepository {
    val registers = mutableMapOf<Long, Register>()
    val sessions = mutableMapOf<Long, RegisterSession>()
    var nextSessionId = 1L

    init {
        registers[1L] = Register(id = 1L, name = "Main Register", active = true)
    }

    override suspend fun getRegisterById(id: Long): Register? = registers[id] ?: Register(id = id, name = "Main Register")
    override suspend fun getRegisterByName(name: String): Register? = registers.values.find { it.name.equals(name, ignoreCase = true) }
    override fun getAllActiveRegisters(): Flow<List<Register>> = flowOf(registers.values.filter { it.active })
    override fun getAllRegisters(): Flow<List<Register>> = flowOf(registers.values.toList())
    override suspend fun insertRegister(register: Register): Long { registers[register.id] = register; return register.id }
    override suspend fun updateRegister(register: Register) { registers[register.id] = register }
    override suspend fun getActiveSessionForRegister(registerId: Long): RegisterSession? =
        sessions.values.find { it.registerId == registerId && it.status == RegisterSessionStatus.OPEN }
    override suspend fun getActiveSessionForCashier(cashierId: Long): RegisterSession? =
        sessions.values.find { it.cashierId == cashierId && it.status == RegisterSessionStatus.OPEN }
    override suspend fun getAnyActiveSession(): RegisterSession? =
        sessions.values.filter { it.status == RegisterSessionStatus.OPEN }.maxByOrNull { it.openedAt }
    override suspend fun getSessionById(sessionId: Long): RegisterSession? = sessions[sessionId]
    override fun getOpenSessions(): Flow<List<RegisterSession>> = flowOf(sessions.values.filter { it.status == RegisterSessionStatus.OPEN })
    override fun getAllSessionsHistory(): Flow<List<RegisterSession>> = flowOf(sessions.values.toList())
    override suspend fun openSession(session: RegisterSession): Long {
        val id = nextSessionId++
        sessions[id] = session.copy(id = id)
        return id
    }
    override suspend fun updateSession(session: RegisterSession) { sessions[session.id] = session }
}

class FakeOrderRepository : OrderRepository {
    val orders = mutableMapOf<Long, Order>()
    val orderItems = mutableMapOf<Long, MutableList<OrderItem>>()
    val payments = mutableMapOf<Long, MutableList<Payment>>()
    var nextOrderId = 1L
    var nextItemId = 1L
    var nextPaymentId = 1L

    override suspend fun getOrderById(id: Long): Order? {
        val order = orders[id] ?: return null
        val items = orderItems[id] ?: emptyList()
        val p = payments[id] ?: emptyList()
        return order.copy(items = items, payments = p)
    }

    override suspend fun getOpenOrderByTableId(tableId: Long): Order? {
        return orders.values.find { it.tableId == tableId && it.status == OrderStatus.OPEN }
    }

    override fun getOpenOrdersBySessionId(sessionId: Long): Flow<List<Order>> =
        flowOf(orders.values.filter { it.registerSessionId == sessionId && it.status == OrderStatus.OPEN })

    override fun getOrdersBySessionId(sessionId: Long): Flow<List<Order>> =
        flowOf(orders.values.filter { it.registerSessionId == sessionId })

    override fun getActiveOrdersForList(): Flow<List<Order>> = flowOf(orders.values.filter { it.status == OrderStatus.OPEN })
    override fun getCompletedOrdersForSession(sessionId: Long): Flow<List<Order>> = flowOf(orders.values.filter { it.registerSessionId == sessionId && it.status == OrderStatus.COMPLETED })
    override fun getAllCompletedOrders(): Flow<List<Order>> = flowOf(orders.values.filter { it.status == OrderStatus.COMPLETED })
    override suspend fun getCompletedOrdersForDateRange(startMs: Long, endMs: Long): List<Order> =
        orders.values
            .filter {
                it.status == OrderStatus.COMPLETED &&
                    it.completedAt != null &&
                    it.completedAt in startMs..endMs
            }
            .map { order ->
                order.copy(
                    items = orderItems[order.id].orEmpty(),
                    payments = payments[order.id].orEmpty()
                )
            }

    override suspend fun getCancelledOrdersForDateRange(startMs: Long, endMs: Long): List<Order> =
        orders.values
            .filter {
                it.status == OrderStatus.CANCELLED &&
                    it.cancelledAt != null &&
                    it.cancelledAt in startMs..endMs
            }
            .map { order ->
                order.copy(
                    items = orderItems[order.id].orEmpty(),
                    payments = payments[order.id].orEmpty()
                )
            }

    override suspend fun getCancelledOrderCountForDateRange(startMs: Long, endMs: Long): Int =
        orders.values.count {
            it.status == OrderStatus.CANCELLED &&
                it.cancelledAt != null &&
                it.cancelledAt in startMs..endMs
        }

    override suspend fun getDailyOrderCount(datePrefix: String): Int = orders.values.count { it.orderNumber.startsWith(datePrefix) }
    override suspend fun getCompletedOrderCountForSession(sessionId: Long): Int = orders.values.count { it.registerSessionId == sessionId && it.status == OrderStatus.COMPLETED }
    override suspend fun getCancelledOrderCountForSession(sessionId: Long): Int = orders.values.count { it.registerSessionId == sessionId && it.status == OrderStatus.CANCELLED }

    override suspend fun getOpenOrderCountForSession(sessionId: Long): Int =
        orders.values.count { it.registerSessionId == sessionId && it.status == OrderStatus.OPEN }

    override suspend fun getOpenOrderCountForTable(tableId: Long): Int =
        orders.values.count { it.tableId == tableId && it.status == OrderStatus.OPEN }

    override suspend fun getOpenOrderCountForArea(areaId: Long): Int = 0

    override suspend fun insertOrder(order: Order): Long {
        val id = nextOrderId++
        orders[id] = order.copy(id = id)
        return id
    }

    override suspend fun updateOrder(order: Order) {
        orders[order.id] = order
    }

    override suspend fun getOrderItems(orderId: Long): List<OrderItem> =
        orderItems[orderId] ?: emptyList()

    override suspend fun insertOrderItem(item: OrderItem): Long {
        val id = nextItemId++
        val list = orderItems.getOrPut(item.orderId) { mutableListOf() }
        list.add(item.copy(id = id))
        return id
    }

    override suspend fun updateOrderItem(item: OrderItem) {
        val list = orderItems[item.orderId] ?: return
        val index = list.indexOfFirst { it.id == item.id }
        if (index != -1) list[index] = item
    }

    override suspend fun deleteOrderItem(itemId: Long) {
        orderItems.values.forEach { list -> list.removeAll { it.id == itemId } }
    }

    override suspend fun insertPayment(payment: Payment): Long {
        val id = nextPaymentId++
        val list = payments.getOrPut(payment.orderId) { mutableListOf() }
        list.add(payment.copy(id = id))
        return id
    }

    override suspend fun getPaymentsForOrder(orderId: Long): List<Payment> =
        payments[orderId] ?: emptyList()

    override suspend fun getTotalCashPaymentsForSession(sessionId: Long): Long =
        payments.values.flatten().filter { it.registerSessionId == sessionId && it.method == PaymentMethod.CASH }.sumOf { it.amountCentimes }
}

class DomainUseCasesTest {

    private lateinit var userRepository: FakeUserRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var tableRepository: FakeTableRepository
    private lateinit var registerRepository: FakeRegisterRepository
    private lateinit var orderRepository: FakeOrderRepository

    private lateinit var cashMovementRepository: FakeCashMovementRepository
    private lateinit var setupPreferences: FakeSetupPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var unpaidOrdersChecker: FakeUnpaidOrdersChecker

    private lateinit var openRegisterSessionUseCase: OpenRegisterSessionUseCase
    private lateinit var closeRegisterSessionUseCase: CloseRegisterSessionUseCase
    private lateinit var createOrderUseCase: CreateOrderUseCase
    private lateinit var addOrderItemUseCase: AddOrderItemUseCase
    private lateinit var recordPaymentUseCase: RecordPaymentUseCase
    private lateinit var completeOrderUseCase: CompleteOrderUseCase
    private lateinit var cancelOrderUseCase: CancelOrderUseCase
    private lateinit var verifyOwnerPinUseCase: VerifyOwnerPinUseCase

    private lateinit var testCashier: User
    private lateinit var testOwner: User

    @Before
    fun setUp() {
        userRepository = FakeUserRepository()
        categoryRepository = FakeCategoryRepository()
        productRepository = FakeProductRepository()
        tableRepository = FakeTableRepository()
        registerRepository = FakeRegisterRepository()
        orderRepository = FakeOrderRepository()
        cashMovementRepository = FakeCashMovementRepository()
        setupPreferences = FakeSetupPreferences()
        sessionManager = SessionManager()
        unpaidOrdersChecker = FakeUnpaidOrdersChecker()

        val salt = SecurityUtils.generateSalt()
        testOwner = User(id = 1L, name = "Owner User", role = UserRole.OWNER, pinHash = SecurityUtils.hashPin("1234", salt))
        testCashier = User(id = 2L, name = "Cashier User", role = UserRole.CASHIER, pinHash = SecurityUtils.hashPin("0000", salt))

        runTest {
            userRepository.insertUser(testOwner)
            userRepository.insertUser(testCashier)
        }
        sessionManager.login(testCashier)

        openRegisterSessionUseCase = OpenRegisterSessionUseCase(registerRepository, userRepository, sessionManager)
        verifyOwnerPinUseCase = VerifyOwnerPinUseCase(userRepository)
        closeRegisterSessionUseCase = CloseRegisterSessionUseCase(
            registerRepository = registerRepository,
            cashMovementRepository = cashMovementRepository,
            orderRepository = orderRepository,
            unpaidOrdersChecker = unpaidOrdersChecker,
            verifyOwnerPinUseCase = verifyOwnerPinUseCase,
            sessionManager = sessionManager
        )
        val generateOrderNumberUseCase = GenerateOrderNumberUseCase(orderRepository)
        createOrderUseCase = CreateOrderUseCase(
            orderRepository = orderRepository,
            registerRepository = registerRepository,
            productRepository = productRepository,
            categoryRepository = categoryRepository,
            tableRepository = tableRepository,
            generateOrderNumberUseCase = generateOrderNumberUseCase
        )
        addOrderItemUseCase = AddOrderItemUseCase(orderRepository, productRepository)
        recordPaymentUseCase = RecordPaymentUseCase(orderRepository)
        completeOrderUseCase = CompleteOrderUseCase(orderRepository, tableRepository)
        cancelOrderUseCase = CancelOrderUseCase(orderRepository, tableRepository, verifyOwnerPinUseCase)
    }

    private val dummyCartItems = listOf(
        CartItem(
            product = Product(id = 10L, categoryId = 1L, name = "Café Nous-Nous", priceCentimes = 1500L, available = true),
            quantity = 1
        )
    )

    @Test
    fun openRegisterSession_success() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        assertNotNull(session)
        assertEquals(1L, session.registerId)
        assertEquals(50000L, session.openingCashCentimes)
        assertEquals(RegisterSessionStatus.OPEN, session.status)
    }

    @Test(expected = SessionAlreadyOpenException::class)
    fun openRegisterSession_alreadyOpen_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        sessionManager.login(testOwner)
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testOwner)
    }

    @Test(expected = DomainException::class)
    fun createOrder_withoutOpenSession_throwsException() = runTest {
        createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)
    }

    @Test
    fun createOrder_dineIn_success_marksTableOccupied() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.DINE_IN, tableId = 100L, cartItems = dummyCartItems, currentUser = testCashier)

        assertNotNull(order)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(TableStatus.OCCUPIED, tableRepository.getTableById(100L)?.status)
    }

    @Test(expected = TableOccupiedException::class)
    fun createOrder_occupiedTable_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        createOrderUseCase(type = OrderType.DINE_IN, tableId = 101L, cartItems = dummyCartItems, currentUser = testCashier)
    }

    @Test
    fun addOrderItem_snapshotsNameAndPrice() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)

        val updatedOrder = addOrderItemUseCase(orderId = order.id, productId = 10L, quantity = 2)

        assertEquals(2, updatedOrder.items.size)
        val item = updatedOrder.items.last()
        assertEquals("Café Nous-Nous", item.productNameSnapshot)
        assertEquals(1500L, item.unitPriceSnapshotCentimes)
        assertEquals(3000L, item.lineTotalCentimes)
        assertEquals(4500L, updatedOrder.totalCentimes)
    }

    @Test(expected = ProductNotAvailableException::class)
    fun addOrderItem_unavailableProduct_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)
        addOrderItemUseCase(orderId = order.id, productId = 20L, quantity = 1)
    }

    @Test
    fun completeOrder_freesTable() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.DINE_IN, tableId = 100L, cartItems = dummyCartItems, currentUser = testCashier)
        addOrderItemUseCase(orderId = order.id, productId = 10L, quantity = 1) // 3000L total

        recordPaymentUseCase(orderId = order.id, method = PaymentMethod.CASH, amountCentimes = 3000L, receivedAmountCentimes = 5000L)
        val completedOrder = completeOrderUseCase(orderId = order.id)

        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(100L)?.status)
    }

    @Test(expected = OrderAlreadyClosedException::class)
    fun addOrderItem_toCompletedOrder_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)
        recordPaymentUseCase(orderId = order.id, method = PaymentMethod.CASH, amountCentimes = 1500L, receivedAmountCentimes = 1500L)
        completeOrderUseCase(orderId = order.id)

        addOrderItemUseCase(orderId = order.id, productId = 10L, quantity = 1)
    }

    @Test(expected = UnpaidOrdersExistException::class)
    fun closeRegister_withUnpaidOrders_throwsException() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)

        unpaidOrdersChecker.unpaidCount = 1
        sessionManager.login(testOwner, session.id)
        closeRegisterSessionUseCase(sessionId = session.id, countedCashCentimes = 50000L, currentUser = testOwner)
    }

    @Test
    fun closeRegister_calculatesExpectedCash() = runTest {
        val session = openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)
        recordPaymentUseCase(orderId = order.id, method = PaymentMethod.CASH, amountCentimes = 1500L, receivedAmountCentimes = 1500L)
        completeOrderUseCase(orderId = order.id)

        unpaidOrdersChecker.unpaidCount = 0
        sessionManager.login(testOwner, session.id)
        val closedSession = closeRegisterSessionUseCase(sessionId = session.id, countedCashCentimes = 51500L, currentUser = testOwner)

        assertEquals(RegisterSessionStatus.CLOSED, closedSession.status)
        assertEquals(51500L, closedSession.expectedCashCentimes)
        assertEquals(51500L, closedSession.countedCashCentimes)
        assertEquals(0L, closedSession.differenceCentimes)
    }

    @Test(expected = InvalidOwnerPinException::class)
    fun cancelOrder_invalidOwnerPin_throwsException() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.TAKEAWAY, tableId = null, cartItems = dummyCartItems, currentUser = testCashier)

        cancelOrderUseCase(orderId = order.id, ownerPin = "9999", reason = "Wrong item")
    }

    @Test
    fun cancelOrder_validOwnerPin_freesTableAndMarksCancelled() = runTest {
        openRegisterSessionUseCase(registerId = 1L, openingCashCentimes = 50000L, authenticatedUser = testCashier)
        val order = createOrderUseCase(type = OrderType.DINE_IN, tableId = 100L, cartItems = dummyCartItems, currentUser = testCashier)

        val cancelledOrder = cancelOrderUseCase(orderId = order.id, ownerPin = "1234", reason = "Customer left")

        assertEquals(OrderStatus.CANCELLED, cancelledOrder.status)
        assertEquals("Customer left", cancelledOrder.cancellationReason)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(100L)?.status)
    }
}
