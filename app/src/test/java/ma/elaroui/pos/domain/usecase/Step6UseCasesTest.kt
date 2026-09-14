package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.util.SecurityUtils
import ma.elaroui.pos.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Step6UseCasesTest {

    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var productRepository: FakeProductRepository
    private lateinit var tableRepository: FakeTableRepository
    private lateinit var registerRepository: FakeRegisterRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var userRepository: FakeUserRepository

    private lateinit var generateOrderNumberUseCase: GenerateOrderNumberUseCase
    private lateinit var createOrderUseCase: CreateOrderUseCase
    private lateinit var updateOpenOrderUseCase: UpdateOpenOrderUseCase
    private lateinit var moveDineInOrderUseCase: MoveDineInOrderUseCase
    private lateinit var cancelOpenOrderUseCase: CancelOpenOrderUseCase
    private lateinit var verifyOwnerPinUseCase: VerifyOwnerPinUseCase

    private lateinit var ownerUser: User
    private lateinit var cashierUser: User
    private lateinit var testRegister: Register
    private lateinit var openSession: RegisterSession
    private lateinit var testProduct1: Product
    private lateinit var testProduct2: Product
    private lateinit var testArea: DiningArea
    private lateinit var testTable1: RestaurantTable
    private lateinit var testTable2: RestaurantTable

    @Before
    fun setUp() = runTest {
        categoryRepository = FakeCategoryRepository()
        productRepository = FakeProductRepository()
        tableRepository = FakeTableRepository()
        registerRepository = FakeRegisterRepository()
        orderRepository = FakeOrderRepository()
        userRepository = FakeUserRepository()

        val salt = SecurityUtils.generateSalt()
        ownerUser = User(id = 1L, name = "Owner", role = UserRole.OWNER, pinHash = SecurityUtils.hashPin("1234", salt))
        cashierUser = User(id = 2L, name = "Cashier", role = UserRole.CASHIER, pinHash = SecurityUtils.hashPin("0000", salt))
        userRepository.users[1L] = ownerUser
        userRepository.users[2L] = cashierUser

        testRegister = Register(id = 1L, name = "Main Register", active = true)
        registerRepository.registers[1L] = testRegister

        val sessionId = registerRepository.openSession(
            RegisterSession(
                registerId = 1L,
                cashierId = cashierUser.id,
                openedByUserId = cashierUser.id,
                openingCashCentimes = 50000L,
                status = RegisterSessionStatus.OPEN
            )
        )
        openSession = registerRepository.getSessionById(sessionId)!!

        testProduct1 = Product(id = 10L, categoryId = 1L, name = "Café Nous-Nous", priceCentimes = 1500L, available = true, active = true)
        testProduct2 = Product(id = 20L, categoryId = 1L, name = "Tajine Poulet", priceCentimes = 4500L, available = true, active = true)
        productRepository.products[10L] = testProduct1
        productRepository.products[20L] = testProduct2

        testArea = DiningArea(id = 1L, name = "Salle Principal", active = true)
        testTable1 = RestaurantTable(id = 100L, areaId = 1L, name = "T-01", status = TableStatus.AVAILABLE, active = true)
        testTable2 = RestaurantTable(id = 102L, areaId = 1L, name = "T-02", status = TableStatus.AVAILABLE, active = true)
        tableRepository.areas[1L] = testArea
        tableRepository.tables[100L] = testTable1
        tableRepository.tables[102L] = testTable2

        generateOrderNumberUseCase = GenerateOrderNumberUseCase(orderRepository)
        createOrderUseCase = CreateOrderUseCase(orderRepository, registerRepository, productRepository, categoryRepository, tableRepository, generateOrderNumberUseCase)
        updateOpenOrderUseCase = UpdateOpenOrderUseCase(orderRepository, registerRepository, productRepository, categoryRepository)
        moveDineInOrderUseCase = MoveDineInOrderUseCase(orderRepository, tableRepository)
        verifyOwnerPinUseCase = VerifyOwnerPinUseCase(userRepository)
        cancelOpenOrderUseCase = CancelOpenOrderUseCase(orderRepository, tableRepository)
    }

    @Test
    fun cartState_totalsCalculation_correct() {
        val cartItem1 = CartItem(product = testProduct1, quantity = 2, unitPriceCentimes = 1500L) // 3000
        val cartItem2 = CartItem(product = testProduct2, quantity = 1, unitPriceCentimes = 4500L) // 4500
        val cart = CartState(items = listOf(cartItem1, cartItem2))

        assertEquals(3, cart.totalItemsCount)
        assertEquals(7500L, cart.subtotalCentimes)
        assertEquals(7500L, cart.totalCentimes)
    }

    @Test
    fun createOrder_dineIn_occupiesTableAndSnapshotsProduct() = runTest {
        val cartItems = listOf(CartItem(product = testProduct1, quantity = 2))
        val createdOrder = createOrderUseCase(
            type = OrderType.DINE_IN,
            tableId = testTable1.id,
            cartItems = cartItems,
            currentUser = cashierUser
        )

        assertNotNull(createdOrder)
        assertTrue(createdOrder.orderNumber.startsWith("ORD-"))
        assertEquals(OrderType.DINE_IN, createdOrder.type)
        assertEquals(testTable1.id, createdOrder.tableId)
        assertEquals(3000L, createdOrder.totalCentimes)
        assertEquals(OrderStatus.OPEN, createdOrder.status)

        // Verify Table Occupied
        val updatedTable = tableRepository.getTableById(testTable1.id)
        assertEquals(TableStatus.OCCUPIED, updatedTable?.status)
    }

    @Test
    fun createOrder_dineIn_withoutTable_isAllowedAndDoesNotOccupyTable() = runTest {
        val createdOrder = createOrderUseCase(
            type = OrderType.DINE_IN,
            tableId = null,
            cartItems = listOf(CartItem(product = testProduct1, quantity = 1)),
            currentUser = cashierUser
        )

        assertEquals(OrderType.DINE_IN, createdOrder.type)
        assertNull(createdOrder.tableId)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(testTable1.id)?.status)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(testTable2.id)?.status)
    }

    @Test
    fun createOrder_takeaway_noTableAssigned() = runTest {
        val cartItems = listOf(CartItem(product = testProduct2, quantity = 1))
        val createdOrder = createOrderUseCase(
            type = OrderType.TAKEAWAY,
            tableId = null,
            cartItems = cartItems,
            currentUser = cashierUser
        )

        assertEquals(OrderType.TAKEAWAY, createdOrder.type)
        assertNull(createdOrder.tableId)
        assertEquals(4500L, createdOrder.totalCentimes)
    }

    @Test(expected = DomainException::class)
    fun createOrder_noOpenRegisterSession_throwsException() = runTest {
        registerRepository.sessions.clear()
        val userWithoutSession = User(id = 99L, name = "No Session", role = UserRole.CASHIER, pinHash = "hash")
        val cartItems = listOf(CartItem(product = testProduct1, quantity = 1))
        createOrderUseCase(
            type = OrderType.COUNTER,
            tableId = null,
            cartItems = cartItems,
            currentUser = userWithoutSession
        )
    }

    @Test(expected = DomainException::class)
    fun createOrder_unavailableProduct_throwsException() = runTest {
        val unavailableProd = testProduct1.copy(available = false)
        productRepository.products[unavailableProd.id] = unavailableProd

        val cartItems = listOf(CartItem(product = unavailableProd, quantity = 1))
        createOrderUseCase(
            type = OrderType.COUNTER,
            tableId = null,
            cartItems = cartItems,
            currentUser = cashierUser
        )
    }

    @Test
    fun moveDineInOrder_releasesOldTableAndOccupiesDestination() = runTest {
        val cartItems = listOf(CartItem(product = testProduct1, quantity = 1))
        val order = createOrderUseCase(
            type = OrderType.DINE_IN,
            tableId = testTable1.id,
            cartItems = cartItems,
            currentUser = cashierUser
        )

        assertEquals(TableStatus.OCCUPIED, tableRepository.getTableById(testTable1.id)?.status)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(testTable2.id)?.status)

        moveDineInOrderUseCase(
            orderId = order.id,
            destinationTableId = testTable2.id,
            currentUser = cashierUser
        )

        val updatedOrder = orderRepository.getOrderById(order.id)
        assertEquals(testTable2.id, updatedOrder?.tableId)
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(testTable1.id)?.status)
        assertEquals(TableStatus.OCCUPIED, tableRepository.getTableById(testTable2.id)?.status)
    }

    @Test
    fun cancelOpenOrder_withoutOwnerPin_cancelsOrderAndReleasesTable() = runTest {
        val cartItems = listOf(CartItem(product = testProduct1, quantity = 1))
        val order = createOrderUseCase(
            type = OrderType.DINE_IN,
            tableId = testTable1.id,
            cartItems = cartItems,
            currentUser = cashierUser
        )

        cancelOpenOrderUseCase(
            orderId = order.id,
            cancellationReason = "Erreur de commande client",
            currentUser = cashierUser
        )

        val cancelledOrder = orderRepository.getOrderById(order.id)
        assertNotNull(cancelledOrder)
        assertEquals(OrderStatus.CANCELLED, cancelledOrder?.status)
        assertEquals("Erreur de commande client", cancelledOrder?.cancellationReason)
        assertEquals(null, cancelledOrder?.approvedByOwnerId)

        // Verify Table Released
        assertEquals(TableStatus.AVAILABLE, tableRepository.getTableById(testTable1.id)?.status)
    }

    @Test
    fun cancelOpenOrder_withoutReason_isAllowed() = runTest {
        val cartItems = listOf(CartItem(product = testProduct1, quantity = 1))
        val order = createOrderUseCase(
            type = OrderType.DINE_IN,
            tableId = testTable1.id,
            cartItems = cartItems,
            currentUser = cashierUser
        )

        cancelOpenOrderUseCase(
            orderId = order.id,
            cancellationReason = "",
            currentUser = cashierUser
        )

        val cancelledOrder = orderRepository.getOrderById(order.id)
        assertEquals(OrderStatus.CANCELLED, cancelledOrder?.status)
        assertEquals(null, cancelledOrder?.cancellationReason)
    }
}
