package ma.elaroui.pos.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val role: UserRole,
    val pinHash: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Product(
    val id: Long = 0,
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

data class DiningArea(
    val id: Long = 0,
    val name: String,
    val displayOrder: Int = 0,
    val active: Boolean = true,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RestaurantTable(
    val id: Long = 0,
    val areaId: Long,
    val name: String,
    val status: TableStatus = TableStatus.AVAILABLE,
    val active: Boolean = true,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Register(
    val id: Long = 0,
    val name: String,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
