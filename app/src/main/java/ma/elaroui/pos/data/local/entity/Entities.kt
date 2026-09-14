package ma.elaroui.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String, // "OWNER" | "CASHIER"
    val pinHash: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index("active"), Index("displayOrder")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("categoryId"), Index("active"), Index("available"), Index("displayOrder"),
        Index(value = ["sku"], unique = true), Index(value = ["barcode"], unique = true)
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val priceCentimes: Long,
    val tvaRate: Double = 0.10,
    val imagePath: String? = null,
    val available: Boolean = true,
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sku: String? = null,
    val barcode: String? = null
)

@Entity(
    tableName = "dining_areas",
    indices = [Index("active"), Index("displayOrder")]
)
data class DiningAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "restaurant_tables",
    foreignKeys = [
        ForeignKey(
            entity = DiningAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("areaId"), Index("active"), Index("status"), Index("displayOrder")]
)
data class TableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val areaId: Long,
    val name: String,
    val status: String = "AVAILABLE", // "AVAILABLE" | "OCCUPIED"
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "registers")
data class RegisterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "register_sessions",
    foreignKeys = [
        ForeignKey(entity = RegisterEntity::class, parentColumns = ["id"], childColumns = ["registerId"]),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cashierId"]),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["openedByUserId"])
    ],
    indices = [Index("registerId"), Index("cashierId"), Index("status"), Index("openedAt")]
)
data class RegisterSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registerId: Long,
    val cashierId: Long,
    val openedByUserId: Long = cashierId,
    val openedAt: Long = System.currentTimeMillis(),
    val openingCashCentimes: Long,
    val closedByUserId: Long? = null,
    val closedAt: Long? = null,
    val expectedCashCentimes: Long? = null,
    val countedCashCentimes: Long? = null,
    val differenceCentimes: Long? = null,
    val closingNote: String? = null,
    val ownerApprovedDifference: Boolean = false,
    val approvedByOwnerId: Long? = null,
    val status: String = "OPEN" // "OPEN" | "CLOSED"
)

@Entity(
    tableName = "cash_movements",
    foreignKeys = [
        ForeignKey(
            entity = RegisterSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["registerSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("registerSessionId")]
)
data class CashMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registerSessionId: Long,
    val type: String, // "CASH_IN" | "CASH_OUT"
    val amountCentimes: Long,
    val reason: String,
    val createdByUserId: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(entity = TableEntity::class, parentColumns = ["id"], childColumns = ["tableId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cashierId"]),
        ForeignKey(entity = RegisterSessionEntity::class, parentColumns = ["id"], childColumns = ["registerSessionId"])
    ],
    indices = [
        Index("orderNumber", unique = true),
        Index("status"),
        Index("type"),
        Index("tableId"),
        Index("registerSessionId"),
        Index("cashierId"),
        Index("createdAt")
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val type: String, // "DINE_IN" | "TAKEAWAY" | "COUNTER"
    val tableId: Long? = null,
    val cashierId: Long,
    val registerSessionId: Long,
    val status: String = "OPEN", // "OPEN" | "COMPLETED" | "CANCELLED"
    val subtotalCentimes: Long = 0L,
    val discountCentimes: Long = 0L,
    val tvaTotalCentimes: Long = 0L,
    val totalCentimes: Long = 0L,
    val buyerCompanyName: String? = null,
    val buyerAddress: String? = null,
    val buyerIce: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val completedByUserId: Long? = null,
    val cancelledAt: Long? = null,
    val cancelledByUserId: Long? = null,
    val cancellationReason: String? = null,
    val approvedByOwnerId: Long? = null,
    val version: Int = 1
)

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId"), Index("productId")]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val categoryIdSnapshot: Long? = null,
    val categoryNameSnapshot: String? = null,
    val unitPriceSnapshotCentimes: Long,
    val tvaRateSnapshot: Double = 0.10,
    val quantity: Int = 1,
    val note: String? = null,
    val lineTotalCentimes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RegisterSessionEntity::class, parentColumns = ["id"], childColumns = ["registerSessionId"])
    ],
    indices = [
        Index("orderId"),
        Index("registerSessionId"),
        Index("method"),
        Index("status"),
        Index("createdAt"),
        Index("createdByUserId"),
        Index(value = ["orderId", "submissionToken"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val registerSessionId: Long,
    val method: String, // "CASH" | "CARD"
    val amountCentimes: Long,
    val receivedAmountCentimes: Long,
    val changeAmountCentimes: Long,
    val createdByUserId: Long = 0L,
    val status: String = "COMPLETED", // "COMPLETED" | "VOIDED"
    val externalReference: String? = null,
    val submissionToken: String = "",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [Index("actionType"), Index("actingUserId"), Index("timestamp"), Index("entityType")]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val actingUserId: Long,
    val approvedByOwnerId: Long? = null,
    val entityType: String,
    val entityId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null,
    val result: String = "SUCCESS"
)

@Entity(tableName = "printer_settings")
data class PrinterSettingsEntity(
    @PrimaryKey val id: Long = 1L,
    val enabled: Boolean = false,
    val printerType: String = "ANDROID_SYSTEM", // "ANDROID_SYSTEM" | "BLUETOOTH_ESCPOS"
    val printerName: String? = null,
    val printerAddress: String? = null,
    val paperWidth: Int = 80, // 58 or 80 mm
    val copies: Int = 1,
    val autoPrint: Boolean = false,
    val printLogo: Boolean = false,
    val kitchenEnabled: Boolean = false,
    val kitchenPrinterType: String = "ETHERNET_ESCPOS",
    val kitchenPrinterName: String? = null,
    val kitchenPrinterAddress: String? = null,
    val kitchenPaperWidth: Int = 80,
    val kitchenAutoPrint: Boolean = true
)
