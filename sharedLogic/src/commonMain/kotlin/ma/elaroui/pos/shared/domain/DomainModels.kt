package ma.elaroui.pos.shared.domain

enum class UserRole { OWNER, CASHIER }
enum class Permission {
    USE_POS,
    MANAGE_CATALOGUE,
    MANAGE_TABLES,
    MANAGE_USERS,
    VIEW_REPORTS,
    MANAGE_SETTINGS,
    CLOSE_REGISTER,
    RECORD_CASH_ENTRY,
    RECORD_CASH_WITHDRAWAL
}
enum class OrderType { DINE_IN, TAKEAWAY, COUNTER, PREORDER }
enum class PreparationStatus { PENDING, IN_PREPARATION, READY, DELIVERED }
enum class OrderStatus { OPEN, COMPLETED, CANCELLED }
enum class PaymentMethod { CASH, CARD, CARNET_CLIENT, MOBILE_QR }
enum class PaymentStatus { COMPLETED, VOIDED }
enum class RegisterSessionStatus { OPEN, CLOSED }
enum class CashMovementType { CASH_IN, CASH_OUT }
enum class TableStatus { AVAILABLE, RESERVED, OCCUPIED }

data class User(
    val id: Long,
    val name: String,
    val role: UserRole,
    val active: Boolean = true
)

data class Category(
    val id: Long,
    val name: String,
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val parentId: Long? = null,
    val imagePath: String? = null
) {
    val parentCategoryId: Long? get() = parentId
}

val Category.isRoot: Boolean get() = parentId == null
val Category.isSubcategory: Boolean get() = parentId != null
data class DiningArea(
    val id: Long,
    val name: String,
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val imagePath: String? = null
)
data class RestaurantTable(
    val id: Long,
    val areaId: Long,
    val name: String,
    val status: TableStatus = TableStatus.AVAILABLE,
    val active: Boolean = true,
    val displayOrder: Int = 0
)

data class Product(
    val id: Long,
    val categoryId: Long? = null,
    val name: String,
    val priceCentimes: Long,
    val taxRateBasisPoints: Int,
    val available: Boolean = true,
    val active: Boolean = true,
    val imagePath: String? = null,
    /** Optional merchant reference. Kept nullable for existing catalogues. */
    val sku: String? = null,
    /** Optional scanner value (EAN/UPC or an internal barcode). */
    val barcode: String? = null,
    val nameArabic: String? = null,
    val unit: String? = null,
    val description: String? = null
)

data class OrderLine(
    val productId: Long,
    val name: String,
    val unitPriceCentimes: Long,
    val quantity: Int,
    val taxRateBasisPoints: Int = 0,
    val categoryIdSnapshot: Long? = null,
    val categoryNameSnapshot: String? = null,
    val itemDiscountBasisPoints: Int = 0,
    val recognizedAmountCentimes: Long? = null,
    val recognizedTaxCentimes: Long? = null
)

data class Order(
    val id: Long,
    val number: String,
    val type: OrderType,
    val status: OrderStatus,
    val lines: List<OrderLine>,
    val subtotalCentimes: Long,
    val discountCentimes: Long,
    val taxCentimes: Long,
    val totalCentimes: Long,
    val tableId: Long? = null,
    val registerSessionId: Long,
    val cashierId: Long,
    val createdAtEpochMilliseconds: Long = 0L,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val pickupDateEpochMs: Long? = null,
    val preparationStatus: PreparationStatus = PreparationStatus.PENDING,
    val customNote: String? = null,
    val depositCentimes: Long = 0L,
    /** Null identifies an order saved before discount rules were persisted. */
    val discountBasisPoints: Int? = null
) {
    val balanceDueCentimes: Long
        get() = (totalCentimes - depositCentimes).coerceAtLeast(0L)

    val isFullyPaid: Boolean
        get() = status == OrderStatus.COMPLETED || (depositCentimes >= totalCentimes && totalCentimes > 0L)
}

data class Payment(
    val id: Long,
    val orderId: Long,
    val registerSessionId: Long,
    val method: PaymentMethod,
    val amountCentimes: Long,
    val receivedCentimes: Long,
    val changeCentimes: Long,
    val status: PaymentStatus,
    val submissionToken: String,
    val createdAtEpochMilliseconds: Long
)

data class RegisterSession(
    val id: Long,
    val status: RegisterSessionStatus,
    val openingCashCentimes: Long,
    val registerId: Long,
    val cashierId: Long,
    val openedAtEpochMilliseconds: Long,
    val closedAtEpochMilliseconds: Long?,
    val expectedCashCentimes: Long?,
    val countedCashCentimes: Long?,
    val differenceCentimes: Long?,
    val closingUserId: Long? = null,
    val leftInDrawerCentimes: Long? = null,
    val removedAmountCentimes: Long? = null,
    val remittanceReference: String? = null,
    val remittanceDestination: String? = null,
    val closingNote: String? = null
)

data class CashMovement(
    val id: Long,
    val sessionId: Long,
    val type: CashMovementType,
    val amountCentimes: Long,
    val reason: String,
    val description: String?,
    val createdByUserId: Long,
    val createdAtEpochMilliseconds: Long
)

data class ReceiptCompany(
    val name: String,
    val specialty: String = "",
    val address: String,
    val phone: String,
    val ice: String = "",
    val taxId: String = "",
    val commercialRegister: String = "",
    val patente: String = "",
    val wifiName: String = "",
    val wifiCode: String = "",
    val logoPath: String = "",
    val printEstablishmentName: Boolean = true
)

data class AppSetting(val key: String, val value: String)
