package ma.elaroui.pos.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.shared.toShared
import ma.elaroui.pos.shared.domain.Permission
import ma.elaroui.pos.shared.rules.PermissionRules
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogueCategoryWithProducts(
    val category: Category,
    val products: List<Product>
)

data class AreaWithTables(
    val area: DiningArea,
    val tables: List<RestaurantTable>
)

// Authorization helper
private fun ensureOwner(currentUser: User) {
    if (!PermissionRules.isAllowed(currentUser.role.toShared(), Permission.MANAGE_SETTINGS)) {
        throw DomainException("Seul le Propriétaire est autorisé à effectuer cette action de configuration.")
    }
}

// ----------------------------------------------------
// CATEGORY USE CASES
// ----------------------------------------------------

@Singleton
class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(forOwner: Boolean = false): Flow<List<Category>> {
        return if (forOwner) categoryRepository.getAllCategoriesForOwner() else categoryRepository.getAllActiveCategories()
    }
}

@Singleton
class CreateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(name: String, currentUser: User): Category {
        ensureOwner(currentUser)
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la catégorie est obligatoire.")
        }
        val existing = categoryRepository.getCategoryByName(trimmed)
        if (existing != null) {
            throw DomainException("Une catégorie nommée '$trimmed' existe déjà.")
        }
        val category = Category(name = trimmed, active = true)
        val newId = categoryRepository.insertCategory(category)
        return category.copy(id = newId)
    }
}

@Singleton
class UpdateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long, newName: String, currentUser: User): Category {
        ensureOwner(currentUser)
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la catégorie ne peut pas être vide.")
        }
        val existing = categoryRepository.getCategoryByName(trimmed)
        if (existing != null && existing.id != categoryId) {
            throw DomainException("Une autre catégorie nommée '$trimmed' existe déjà.")
        }
        val current = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException("Catégorie introuvable.")

        val updated = current.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        categoryRepository.updateCategory(updated)
        return updated
    }
}

@Singleton
class ActivateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val category = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException("Catégorie introuvable.")
        val duplicate = categoryRepository.getCategoryByName(category.name)
        if (duplicate != null && duplicate.id != categoryId) {
            throw DomainException("Une autre catégorie nommée '${category.name}' existe déjà.")
        }
        categoryRepository.activateCategory(categoryId)
    }
}

@Singleton
class DeactivateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(categoryId: Long, currentUser: User) {
        ensureOwner(currentUser)
        categoryRepository.deactivateCategory(categoryId)
    }
}

// ----------------------------------------------------
// PRODUCT USE CASES
// ----------------------------------------------------

@Singleton
class GetProductsUseCase @Inject constructor(
    private val productRepository: ProductRepository
) {
    operator fun invoke(forOwner: Boolean = false): Flow<List<Product>> {
        return if (forOwner) productRepository.getAllProductsForOwner() else productRepository.getAllAvailableProducts()
    }
}

@Singleton
class CreateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        categoryId: Long,
        name: String,
        priceCentimes: Long,
        tvaRate: Double = 0.10,
        imagePath: String? = null,
        sku: String? = null,
        barcode: String? = null,
        available: Boolean = true,
        currentUser: User
    ): Product {
        ensureOwner(currentUser)
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom du produit est obligatoire.")
        }
        if (priceCentimes <= 0) {
            throw DomainException("Le prix du produit doit être supérieur à zéro.")
        }
        if (!tvaRate.isFinite() || tvaRate !in 0.0..1.0) {
            throw DomainException("Le taux de TVA doit être compris entre 0 et 100 %.")
        }
        val category = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException("La catégorie sélectionnée n'existe pas.")
        if (!category.active) {
            throw DomainException("La catégorie sélectionnée est désactivée.")
        }

        val existing = productRepository.getProductByNameInCategory(categoryId, trimmed)
        if (existing != null) {
            throw DomainException("Un produit nommé '$trimmed' existe déjà dans la catégorie '${category.name}'.")
        }

        val product = Product(
            categoryId = categoryId,
            name = trimmed,
            priceCentimes = priceCentimes,
            tvaRate = tvaRate,
            imagePath = imagePath,
            sku = sku?.trim()?.ifBlank { null },
            barcode = barcode?.trim()?.ifBlank { null },
            available = available,
            active = true
        )
        val newId = productRepository.insertProduct(product)
        return product.copy(id = newId)
    }
}

@Singleton
class UpdateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(
        productId: Long,
        categoryId: Long,
        name: String,
        priceCentimes: Long,
        tvaRate: Double = 0.10,
        imagePath: String? = null,
        sku: String? = null,
        barcode: String? = null,
        replaceImage: Boolean = false,
        available: Boolean = true,
        currentUser: User
    ): Product {
        ensureOwner(currentUser)
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom du produit est obligatoire.")
        }
        if (priceCentimes <= 0) {
            throw DomainException("Le prix du produit doit être supérieur à zéro.")
        }
        if (!tvaRate.isFinite() || tvaRate !in 0.0..1.0) {
            throw DomainException("Le taux de TVA doit être compris entre 0 et 100 %.")
        }
        val category = categoryRepository.getCategoryById(categoryId)
            ?: throw DomainException("La catégorie sélectionnée n'existe pas.")
        if (!category.active) {
            throw DomainException("La catégorie sélectionnée est désactivée.")
        }

        val existing = productRepository.getProductByNameInCategory(categoryId, trimmed)
        if (existing != null && existing.id != productId) {
            throw DomainException("Un autre produit nommé '$trimmed' existe déjà dans la catégorie '${category.name}'.")
        }

        val current = productRepository.getProductById(productId)
            ?: throw DomainException("Produit introuvable.")

        val updated = current.copy(
            categoryId = categoryId,
            name = trimmed,
            priceCentimes = priceCentimes,
            tvaRate = tvaRate,
            imagePath = if (replaceImage) imagePath else imagePath ?: current.imagePath,
            sku = sku?.trim()?.ifBlank { null },
            barcode = barcode?.trim()?.ifBlank { null },
            available = available,
            updatedAt = System.currentTimeMillis()
        )

        productRepository.updateProduct(updated)
        return updated
    }
}

@Singleton
class SetProductAvailabilityUseCase @Inject constructor(
    private val productRepository: ProductRepository
) {
    suspend operator fun invoke(productId: Long, available: Boolean, currentUser: User) {
        ensureOwner(currentUser)
        val product = productRepository.getProductById(productId)
            ?: throw DomainException("Produit introuvable.")
        if (!product.active && available) {
            throw DomainException("Réactivez le produit avant de le rendre disponible.")
        }
        productRepository.setProductAvailability(productId, available)
    }
}

@Singleton
class ActivateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend operator fun invoke(productId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val product = productRepository.getProductById(productId)
            ?: throw DomainException("Produit introuvable.")
        val category = categoryRepository.getCategoryById(product.categoryId)
            ?: throw DomainException("Catégorie introuvable.")
        if (!category.active) {
            throw DomainException("Réactivez d'abord la catégorie '${category.name}'.")
        }
        val duplicate = productRepository.getProductByNameInCategory(product.categoryId, product.name)
        if (duplicate != null && duplicate.id != productId) {
            throw DomainException("Un autre produit nommé '${product.name}' existe déjà dans cette catégorie.")
        }
        productRepository.activateProduct(productId)
    }
}

@Singleton
class DeactivateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository
) {
    suspend operator fun invoke(productId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val product = productRepository.getProductById(productId)
            ?: throw DomainException("Produit introuvable.")
        if (product.available) {
            productRepository.setProductAvailability(productId, false)
        }
        productRepository.deactivateProduct(productId)
    }
}

@Singleton
class ObserveSellableCatalogueUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository
) {
    operator fun invoke(): Flow<List<CatalogueCategoryWithProducts>> {
        return combine(
            categoryRepository.getAllActiveCategories(),
            productRepository.getAllProductsForOwner()
        ) { categories, products ->
            val activeProducts = products.filter { it.active }
            categories.mapNotNull { category ->
                val categoryProducts = activeProducts.filter { it.categoryId == category.id }
                category.takeIf { categoryProducts.isNotEmpty() }
                    ?.let { CatalogueCategoryWithProducts(it, categoryProducts) }
            }
        }
    }
}

// ----------------------------------------------------
// DINING AREA USE CASES
// ----------------------------------------------------

@Singleton
class GetDiningAreasUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    operator fun invoke(forOwner: Boolean = false): Flow<List<DiningArea>> {
        return if (forOwner) tableRepository.getAllAreasForOwner() else tableRepository.getAllActiveAreas()
    }
}

@Singleton
class CreateDiningAreaUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(name: String, currentUser: User, imagePath: String? = null): DiningArea {
        ensureOwner(currentUser)
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la zone est obligatoire.")
        }
        val existing = tableRepository.getAreaByName(trimmed)
        if (existing != null) {
            throw DomainException("Une zone de restauration nommée '$trimmed' existe déjà.")
        }
        val area = DiningArea(name = trimmed, active = true, imagePath = imagePath)
        val newId = tableRepository.insertArea(area)
        return area.copy(id = newId)
    }
}

@Singleton
class UpdateDiningAreaUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(
        areaId: Long,
        newName: String,
        currentUser: User,
        imagePath: String? = null,
        replaceImage: Boolean = false
    ): DiningArea {
        ensureOwner(currentUser)
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la zone est obligatoire.")
        }
        val existing = tableRepository.getAreaByName(trimmed)
        if (existing != null && existing.id != areaId) {
            throw DomainException("Une autre zone nommée '$trimmed' existe déjà.")
        }
        val current = tableRepository.getAreaById(areaId)
            ?: throw DomainException("Zone de restauration introuvable.")

        val updated = current.copy(
            name = trimmed,
            imagePath = if (replaceImage) imagePath else current.imagePath,
            updatedAt = System.currentTimeMillis()
        )
        tableRepository.updateArea(updated)
        return updated
    }
}

@Singleton
class ActivateDiningAreaUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(areaId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val area = tableRepository.getAreaById(areaId)
            ?: throw DomainException("Zone de restauration introuvable.")
        val duplicate = tableRepository.getAreaByName(area.name)
        if (duplicate != null && duplicate.id != areaId) {
            throw DomainException("Une autre zone nommée '${area.name}' existe déjà.")
        }
        tableRepository.activateArea(areaId)
    }
}

@Singleton
class DeactivateDiningAreaUseCase @Inject constructor(
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(areaId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val openOrdersCount = orderRepository.getOpenOrderCountForArea(areaId)
        if (openOrdersCount > 0) {
            throw DomainException("Impossible de désactiver une zone contenant des commandes en cours.")
        }
        val unavailableTable = tableRepository.getAllTablesForOwner().first()
            .any { it.areaId == areaId && it.active && it.status != TableStatus.AVAILABLE }
        if (unavailableTable) {
            throw DomainException("Impossible de désactiver une zone contenant une table occupée ou réservée.")
        }
        tableRepository.deactivateArea(areaId)
    }
}

// ----------------------------------------------------
// TABLE & BULK CREATION USE CASES
// ----------------------------------------------------

@Singleton
class GetTablesUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    operator fun invoke(forOwner: Boolean = false): Flow<List<RestaurantTable>> {
        return if (forOwner) tableRepository.getAllTablesForOwner() else tableRepository.getAllActiveTables()
    }
}

@Singleton
class CreateTableUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(areaId: Long, name: String, currentUser: User): RestaurantTable {
        ensureOwner(currentUser)
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la table est obligatoire.")
        }
        val area = tableRepository.getAreaById(areaId)
            ?: throw DomainException("La zone sélectionnée n'existe pas.")
        if (!area.active) {
            throw DomainException("La zone sélectionnée est désactivée.")
        }

        val existing = tableRepository.getTableByNameInArea(areaId, trimmed)
        if (existing != null) {
            throw DomainException("Une table nommée '$trimmed' existe déjà dans la zone '${area.name}'.")
        }

        val table = RestaurantTable(areaId = areaId, name = trimmed, active = true)
        val newId = tableRepository.insertTable(table)
        return table.copy(id = newId)
    }
}

@Singleton
class UpdateTableUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(tableId: Long, newAreaId: Long, newName: String, currentUser: User): RestaurantTable {
        ensureOwner(currentUser)
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            throw DomainException("Le nom de la table est obligatoire.")
        }
        val area = tableRepository.getAreaById(newAreaId)
            ?: throw DomainException("La zone sélectionnée n'existe pas.")
        if (!area.active) {
            throw DomainException("La zone sélectionnée est désactivée.")
        }

        val existing = tableRepository.getTableByNameInArea(newAreaId, trimmed)
        if (existing != null && existing.id != tableId) {
            throw DomainException("Une autre table nommée '$trimmed' existe déjà dans la zone '${area.name}'.")
        }

        val current = tableRepository.getTableById(tableId)
            ?: throw DomainException("Table introuvable.")
        if (current.status != TableStatus.AVAILABLE && current.areaId != newAreaId) {
            throw DomainException("Une table occupée ou réservée ne peut pas changer de zone.")
        }

        val updated = current.copy(areaId = newAreaId, name = trimmed, updatedAt = System.currentTimeMillis())
        tableRepository.updateTable(updated)
        return updated
    }
}

@Singleton
class ActivateTableUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    suspend operator fun invoke(tableId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val table = tableRepository.getTableById(tableId)
            ?: throw DomainException("Table introuvable.")
        val area = tableRepository.getAreaById(table.areaId)
            ?: throw DomainException("Zone de restauration introuvable.")
        if (!area.active) {
            throw DomainException("Réactivez d'abord la zone '${area.name}'.")
        }
        val duplicate = tableRepository.getTableByNameInArea(table.areaId, table.name)
        if (duplicate != null && duplicate.id != tableId) {
            throw DomainException("Une autre table nommée '${table.name}' existe déjà dans cette zone.")
        }
        tableRepository.activateTable(tableId)
    }
}

@Singleton
class DeactivateTableUseCase @Inject constructor(
    private val tableRepository: TableRepository,
    private val orderRepository: OrderRepository
) {
    suspend operator fun invoke(tableId: Long, currentUser: User) {
        ensureOwner(currentUser)
        val table = tableRepository.getTableById(tableId)
            ?: throw DomainException("Table introuvable.")

        if (table.status != TableStatus.AVAILABLE) {
            throw DomainException("Impossible de désactiver une table occupée ou réservée.")
        }
        val openOrdersCount = orderRepository.getOpenOrderCountForTable(tableId)
        if (openOrdersCount > 0) {
            throw DomainException("Impossible de désactiver une table avec une commande en cours.")
        }
        tableRepository.deactivateTable(tableId)
    }
}

@Singleton
class BulkCreateTablesUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    fun generateNames(
        prefix: String,
        startNumber: Int,
        count: Int,
        paddingLength: Int
    ): List<String> {
        val trimmedPrefix = prefix.trim()
        val list = mutableListOf<String>()
        for (i in 0 until count) {
            val num = startNumber + i
            val numStr = if (paddingLength > 0) num.toString().padStart(paddingLength, '0') else num.toString()
            val name = if (trimmedPrefix.isBlank()) numStr else "$trimmedPrefix $numStr"
            list.add(name.trim())
        }
        return list
    }

    suspend operator fun invoke(
        areaId: Long,
        prefix: String = "Table",
        startNumber: Int = 1,
        count: Int,
        paddingLength: Int = 0,
        currentUser: User
    ): List<RestaurantTable> {
        ensureOwner(currentUser)
        if (count !in 1..100) {
            throw DomainException("Le nombre de tables à créer doit être compris entre 1 et 100.")
        }
        if (startNumber < 0) {
            throw DomainException("Le numéro de départ ne peut pas être négatif.")
        }
        if (paddingLength !in 0..6) {
            throw DomainException("Le remplissage zéro doit être compris entre 0 et 6.")
        }

        val area = tableRepository.getAreaById(areaId)
            ?: throw DomainException("La zone sélectionnée n'existe pas.")
        if (!area.active) {
            throw DomainException("La zone sélectionnée est désactivée.")
        }

        val generatedNames = generateNames(prefix, startNumber, count, paddingLength)

        // Check internal duplicates in batch
        if (generatedNames.distinct().size != generatedNames.size) {
            throw DomainException("Noms de tables en double générés dans le même lot.")
        }

        // Check collision against every existing table, including inactive records.
        val existingNames = tableRepository.getExistingTableNamesInArea(areaId).map { it.lowercase() }
        val conflicts = generatedNames.filter { existingNames.contains(it.lowercase()) }

        if (conflicts.isNotEmpty()) {
            throw DomainException("Conflit de noms dans la zone '${area.name}': ${conflicts.joinToString(", ")}")
        }

        val tablesToCreate = generatedNames.mapIndexed { index, name ->
            RestaurantTable(
                areaId = areaId,
                name = name,
                displayOrder = index + 1,
                active = true
            )
        }

        tableRepository.insertTablesBatch(tablesToCreate)
        return tablesToCreate
    }
}

@Singleton
class ObserveSelectableTablesUseCase @Inject constructor(
    private val tableRepository: TableRepository
) {
    operator fun invoke(): Flow<List<AreaWithTables>> {
        return combine(
            tableRepository.getAllActiveAreas(),
            tableRepository.getAllActiveTables()
        ) { areas, tables ->
            areas.map { area ->
                val areaTables = tables.filter { it.areaId == area.id && it.active }
                AreaWithTables(area = area, tables = areaTables)
            }
        }
    }
}
