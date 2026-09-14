# 03. Database Schema & Data Models

## 1. Monetary & Decimal Precision Strategy
To eliminate floating-point rounding errors in cash registers, all monetary values (`subtotal`, `total`, `unitPrice`, `lineTotal`, `openingCash`, `countedCash`, `expectedCash`, `receivedAmount`, `changeAmount`) are stored as **`Long` representing minor currency units (cents / centimes: 1 DH = 100 centimes)** or handled via custom `Room` `TypeConverters` converting between `BigDecimal` and `Long`.

---

## 2. Room Database Entities & Schema Definition

### Entity 1: `User`
Stores user accounts for Owners and Cashiers.
```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String, // "OWNER" | "CASHIER"
    val pinHash: String, // Salted SHA-256 hash of PIN code
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Entity 2: `Category`
Product categories (e.g., Hot Drinks, Cold Drinks, Meals, Desserts).
```kotlin
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true
)
```

### Entity 3: `Product`
Product items in the menu catalog.
```kotlin
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
    indices = [Index("categoryId")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val price: Long, // Price in centimes (e.g. 15.00 DH = 1500)
    val tvaRate: Double = 0.10, // 0.10 (10%) or 0.20 (20%)
    val imagePath: String? = null,
    val available: Boolean = true,
    val displayOrder: Int = 0
)
```

### Entity 4: `DiningArea`
Dining floor plan zones (e.g., Terrace, Ground Floor, First Floor).
```kotlin
@Entity(tableName = "dining_areas")
data class DiningAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true
)
```

### Entity 5: `RestaurantTable`
Tables belonging to dining areas.
```kotlin
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
    indices = [Index("areaId")]
)
data class TableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val areaId: Long,
    val name: String, // e.g. "T-01", "Table 5"
    val status: String = "AVAILABLE", // "AVAILABLE" | "OCCUPIED"
    val active: Boolean = true
)
```

### Entity 6: `Register`
Physical cash register terminal definitions.
```kotlin
@Entity(tableName = "registers")
data class RegisterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // e.g. "Main Terminal 01"
    val active: Boolean = true
)
```

### Entity 7: `RegisterSession`
Tracks register shift opening and closing sessions.
```kotlin
@Entity(
    tableName = "register_sessions",
    foreignKeys = [
        ForeignKey(entity = RegisterEntity::class, parentColumns = ["id"], childColumns = ["registerId"]),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cashierId"])
    ],
    indices = [Index("registerId"), Index("cashierId")]
)
data class RegisterSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registerId: Long,
    val cashierId: Long,
    val openedAt: Long = System.currentTimeMillis(),
    val openingCash: Long, // Opening cash in centimes
    val closedAt: Long? = null,
    val expectedCash: Long? = null,
    val countedCash: Long? = null,
    val difference: Long? = null,
    val status: String = "OPEN" // "OPEN" | "CLOSED"
)
```

### Entity 8: `Order`
Main order record.
```kotlin
@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(entity = TableEntity::class, parentColumns = ["id"], childColumns = ["tableId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["cashierId"]),
        ForeignKey(entity = RegisterSessionEntity::class, parentColumns = ["id"], childColumns = ["registerSessionId"])
    ],
    indices = [Index("tableId"), Index("cashierId"), Index("registerSessionId")]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String, // Formatted sequence e.g., "ORD-1042"
    val type: String, // "DINE_IN" | "TAKEAWAY" | "COUNTER"
    val tableId: Long? = null,
    val cashierId: Long,
    val registerSessionId: Long,
    val status: String = "OPEN", // "OPEN" | "COMPLETED" | "CANCELLED"
    val subtotal: Long = 0L,
    val tvaTotal: Long = 0L,
    val total: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val cancelledAt: Long? = null,
    val cancellationReason: String? = null
)
```

### Entity 9: `OrderItem` (Historical Price Snapshot)
Order items saved with snapshots of name, price, and TVA rate at the time of purchase.
```kotlin
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
    indices = [Index("orderId")]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productNameSnapshot: String, // Copied from Product.name
    val unitPriceSnapshot: Long,      // Copied from Product.price
    val tvaRateSnapshot: Double,     // Copied from Product.tvaRate (0.10 or 0.20)
    val quantity: Int = 1,
    val note: String? = null,
    val lineTotal: Long               // unitPriceSnapshot * quantity
)
```

### Entity 10: `Payment`
Payment records attached to completed orders.
```kotlin
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RegisterSessionEntity::class, parentColumns = ["id"], childColumns = ["registerSessionId"])
    ],
    indices = [Index("orderId"), Index("registerSessionId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val registerSessionId: Long,
    val method: String, // "CASH" | "CARD" | "CARNET_CLIENT" | "MOBILE_QR"
    val amount: Long,   // Total amount paid
    val receivedAmount: Long, // Amount given by customer
    val changeAmount: Long,   // Change returned to customer
    val createdAt: Long = System.currentTimeMillis()
)
```
