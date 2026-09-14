package ma.elaroui.pos.domain.model

data class ReceiptItem(
    val name: String,
    val quantity: Int,
    val unitPriceCentimes: Long,
    val lineTotalCentimes: Long
)

data class ReceiptData(
    val restaurantName: String,
    val restaurantPhone: String?,
    val restaurantAddress: String?,
    val restaurantLogoUri: String? = null,
    val sellerIce: String? = null,
    val sellerTaxId: String? = null,
    val sellerCommercialRegister: String? = null,
    val sellerPatente: String? = null,
    val buyerCompanyName: String? = null,
    val buyerAddress: String? = null,
    val buyerIce: String? = null,
    val wifiName: String? = null,
    val wifiCode: String? = null,
    val orderNumber: String,
    val orderType: OrderType,
    val tableName: String?,
    val registerName: String,
    val cashierName: String,
    val orderCreatedAt: Long,
    val paymentAt: Long,
    val items: List<ReceiptItem>,
    val subtotalCentimes: Long,
    val totalCentimes: Long,
    val paymentMethod: PaymentMethod,
    val receivedAmountCentimes: Long?,
    val changeAmountCentimes: Long?,
    val externalReference: String?,
    val currency: String = "MAD",
    val thankYouMessage: String = "Merci de votre visite et à bientôt !",
    val isReprint: Boolean = false
)

data class PrinterSettings(
    val enabled: Boolean = false,
    val printerType: PrinterType = PrinterType.ANDROID_SYSTEM,
    val printerName: String? = null,
    val printerAddress: String? = null,
    val paperWidth: Int = 80, // 58 or 80 mm
    val copies: Int = 1,
    val autoPrint: Boolean = false,
    val printLogo: Boolean = false,
    val kitchenEnabled: Boolean = false,
    val kitchenPrinterType: PrinterType = PrinterType.ETHERNET_ESCPOS,
    val kitchenPrinterName: String? = null,
    val kitchenPrinterAddress: String? = null,
    val kitchenPaperWidth: Int = 80,
    val kitchenAutoPrint: Boolean = true
)

data class PaymentResult(
    val payment: Payment,
    val completedOrder: Order,
    val changeAmountCentimes: Long,
    val receiptData: ReceiptData
)

data class DashboardStats(
    val todayCashSalesCentimes: Long,
    val todayCardSalesCentimes: Long,
    val todayTotalSalesCentimes: Long,
    val completedOrderCount: Int,
    val cancelledOrderCount: Int,
    val openOrderCount: Int,
    val openSessionCount: Int
)

data class ProductSalesSummary(
    val productName: String,
    val quantitySold: Int,
    val totalSalesCentimes: Long
)

data class DailySalesReport(
    val dateString: String,
    val completedOrderCount: Int,
    val cancelledOrderCount: Int,
    val cashSalesCentimes: Long,
    val cardSalesCentimes: Long,
    val totalSalesCentimes: Long,
    val avgOrderValueCentimes: Long,
    val salesByCashier: Map<String, Long>,
    val salesByOrderType: Map<OrderType, Long>,
    val topProducts: List<ProductSalesSummary>,
    val otherSalesCentimes: Long = 0L,
    val salesByPaymentMethod: Map<PaymentMethod, Long> = emptyMap(),
    val salesByCategory: Map<String, Long> = emptyMap()
)

data class AuditLog(
    val id: Long = 0,
    val actionType: AuditAction,
    val actingUserId: Long,
    val approvedByOwnerId: Long? = null,
    val entityType: String,
    val entityId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val result: String = "SUCCESS"
)
