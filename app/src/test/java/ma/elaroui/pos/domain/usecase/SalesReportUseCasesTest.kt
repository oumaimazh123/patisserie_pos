package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.test.runTest
import ma.elaroui.pos.domain.model.Order
import ma.elaroui.pos.domain.model.OrderItem
import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.Payment
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.Register
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Calendar

class SalesReportUseCasesTest {

    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var registerRepository: FakeRegisterRepository
    private var reportDateMs: Long = 0L

    @Before
    fun setUp() {
        orderRepository = FakeOrderRepository()
        userRepository = FakeUserRepository()
        registerRepository = FakeRegisterRepository()
        reportDateMs = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        userRepository.users[1L] = User(
            id = 1L,
            name = "Amine",
            role = UserRole.OWNER,
            pinHash = "hash"
        )
        userRepository.users[2L] = User(
            id = 2L,
            name = "Sara",
            role = UserRole.CASHIER,
            pinHash = "hash"
        )

        addCompletedOrder(
            id = 1L,
            cashierId = 1L,
            type = OrderType.DINE_IN,
            totalCentimes = 1_500L,
            paymentMethod = PaymentMethod.CASH,
            productName = "Café",
            quantity = 1
        )
        addCompletedOrder(
            id = 2L,
            cashierId = 2L,
            type = OrderType.TAKEAWAY,
            totalCentimes = 2_500L,
            paymentMethod = PaymentMethod.CARD,
            productName = "Thé à la menthe",
            quantity = 2
        )
        addCompletedOrder(
            id = 3L,
            cashierId = 2L,
            type = OrderType.COUNTER,
            totalCentimes = 500L,
            paymentMethod = PaymentMethod.MOBILE_QR,
            productName = "Eau",
            quantity = 1
        )

        orderRepository.orders[4L] = Order(
            id = 4L,
            orderNumber = "ORD-CANCELLED",
            type = OrderType.COUNTER,
            cashierId = 1L,
            registerSessionId = 42L,
            status = OrderStatus.CANCELLED,
            cancelledAt = reportDateMs,
            totalCentimes = 900L
        )
        orderRepository.orders[5L] = Order(
            id = 5L,
            orderNumber = "ORD-OTHER-DAY",
            type = OrderType.COUNTER,
            cashierId = 1L,
            registerSessionId = 42L,
            status = OrderStatus.COMPLETED,
            completedAt = reportDateMs - (2 * 24 * 60 * 60 * 1000L),
            totalCentimes = 9_999L
        )
    }

    @Test
    fun dailyReport_calculatesPaymentsCountsBreakdownsAndTopProducts() = runTest {
        val report = GetDailySalesReportUseCase(orderRepository, userRepository)(reportDateMs)

        assertEquals(3, report.completedOrderCount)
        assertEquals(1, report.cancelledOrderCount)
        assertEquals(4_500L, report.totalSalesCentimes)
        assertEquals(1_500L, report.cashSalesCentimes)
        assertEquals(2_500L, report.cardSalesCentimes)
        assertEquals(500L, report.otherSalesCentimes)
        assertEquals(1_500L, report.avgOrderValueCentimes)
        assertEquals(1_500L, report.salesByCashier["Amine"])
        assertEquals(3_000L, report.salesByCashier["Sara"])
        assertEquals(1_500L, report.salesByOrderType[OrderType.DINE_IN])
        assertEquals(2_500L, report.salesByOrderType[OrderType.TAKEAWAY])
        assertEquals(500L, report.salesByPaymentMethod[PaymentMethod.MOBILE_QR])
        assertEquals("Thé à la menthe", report.topProducts.first().productName)
        assertEquals(2, report.topProducts.first().quantitySold)
    }

    @Test
    fun csvExport_isExcelFriendlyEscapesTextAndUsesRegisterFromSession() = runTest {
        userRepository.users[1L] = userRepository.users.getValue(1L).copy(
            name = "Sara; \"Matin\""
        )
        registerRepository.registers[7L] = Register(id = 7L, name = "Caisse Terrasse")
        registerRepository.sessions[42L] = RegisterSession(
            id = 42L,
            registerId = 7L,
            cashierId = 1L,
            openingCashCentimes = 10_000L
        )

        val output = ByteArrayOutputStream()
        val start = startOfDay(reportDateMs)
        ExportSalesCsvUseCase(
            orderRepository,
            userRepository,
            registerRepository
        )(output, start, start + (24 * 60 * 60 * 1000L) - 1L)

        val csv = output.toString(Charsets.UTF_8.name())

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"Commande\";\"Type\";\"Caissier\""))
        assertTrue(csv.contains("\"Sara; \"\"Matin\"\"\""))
        assertTrue(csv.contains("\"Caisse Terrasse\""))
        assertTrue(csv.contains("\"Espèces\""))
        assertTrue(csv.contains("\"À emporter\""))
        assertTrue(csv.lines().size >= 5)
    }

    private fun addCompletedOrder(
        id: Long,
        cashierId: Long,
        type: OrderType,
        totalCentimes: Long,
        paymentMethod: PaymentMethod,
        productName: String,
        quantity: Int
    ) {
        orderRepository.orders[id] = Order(
            id = id,
            orderNumber = "ORD-$id",
            type = type,
            cashierId = cashierId,
            registerSessionId = 42L,
            status = OrderStatus.COMPLETED,
            subtotalCentimes = totalCentimes,
            totalCentimes = totalCentimes,
            completedAt = reportDateMs
        )
        orderRepository.orderItems[id] = mutableListOf(
            OrderItem(
                id = id,
                orderId = id,
                productId = id,
                productNameSnapshot = productName,
                unitPriceSnapshotCentimes = totalCentimes / quantity,
                quantity = quantity,
                lineTotalCentimes = totalCentimes
            )
        )
        orderRepository.payments[id] = mutableListOf(
            Payment(
                id = id,
                orderId = id,
                registerSessionId = 42L,
                method = paymentMethod,
                amountCentimes = totalCentimes,
                receivedAmountCentimes = totalCentimes,
                changeAmountCentimes = 0L,
                createdAt = reportDateMs
            )
        )
    }

    private fun startOfDay(dateMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dateMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
