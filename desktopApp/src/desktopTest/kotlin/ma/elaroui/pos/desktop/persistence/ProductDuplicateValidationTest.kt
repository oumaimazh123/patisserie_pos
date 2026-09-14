package ma.elaroui.pos.desktop.persistence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.shared.application.SaveProduct
import ma.elaroui.pos.shared.application.UseCaseResult
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

class ProductDuplicateValidationTest {

    @Test
    fun `creating product with same name in same category throws DesktopValidationException`() = runBlocking {
        val dir = Files.createTempDirectory("prod-dup-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        val catCafe = db.categories.save(Category(0, "Café", active = true, displayOrder = 1))

        // Create initial "Cappuccino"
        val p1 = Product(0, catCafe, "Cappuccino", 1500, 1000, active = true, available = true)
        val id1 = db.products.save(p1)
        assertTrue(id1 > 0)

        // Try to create second "Cappuccino" in the same category
        val p2 = Product(0, catCafe, "Cappuccino", 1600, 1000, active = true, available = true)
        val ex = assertFailsWith<DesktopValidationException> {
            db.products.save(p2)
        }
        assertEquals("Un produit avec ce nom existe déjà dans cette catégorie.", ex.message)

        // Case insensitivity check ("cappuccino" vs "Cappuccino")
        val p3 = Product(0, catCafe, "cappuccino", 1700, 1000, active = true, available = true)
        val exCase = assertFailsWith<DesktopValidationException> {
            db.products.save(p3)
        }
        assertEquals("Un produit avec ce nom existe déjà dans cette catégorie.", exCase.message)
    }

    @Test
    fun `creating product with same name in different category succeeds`() = runBlocking {
        val dir = Files.createTempDirectory("prod-diff-cat-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        val catCafe = db.categories.save(Category(0, "Café", active = true, displayOrder = 1))
        val catDessert = db.categories.save(Category(0, "Dessert", active = true, displayOrder = 2))

        val p1 = Product(0, catCafe, "Glace Vanille", 1500, 1000, active = true, available = true)
        val id1 = db.products.save(p1)
        assertTrue(id1 > 0)

        // Same name in Dessert category should succeed
        val p2 = Product(0, catDessert, "Glace Vanille", 2500, 1000, active = true, available = true)
        val id2 = db.products.save(p2)
        assertTrue(id2 > 0)
    }

    @Test
    fun `modifying existing product with own name succeeds`() = runBlocking {
        val dir = Files.createTempDirectory("prod-self-edit-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        val catCafe = db.categories.save(Category(0, "Café", active = true, displayOrder = 1))
        val p1 = Product(0, catCafe, "Espresso", 1200, 1000, active = true, available = true)
        val id1 = db.products.save(p1)

        // Update price without changing name
        val updated = p1.copy(id = id1, priceCentimes = 1400)
        val savedId = db.products.save(updated)
        assertEquals(id1, savedId)
        assertEquals(1400, db.products.findById(id1)?.priceCentimes)
    }

    @Test
    fun `modifying product to duplicate name of another product in same category throws DesktopValidationException`() = runBlocking {
        val dir = Files.createTempDirectory("prod-rename-dup-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        val catCafe = db.categories.save(Category(0, "Café", active = true, displayOrder = 1))
        val p1 = Product(0, catCafe, "Espresso", 1200, 1000, active = true, available = true)
        val id1 = db.products.save(p1)

        val p2 = Product(0, catCafe, "Latte", 1800, 1000, active = true, available = true)
        val id2 = db.products.save(p2)

        // Attempt to rename Latte (id2) to Espresso (which already exists under id1)
        val renameAttempt = p2.copy(id = id2, name = "Espresso")
        val ex = assertFailsWith<DesktopValidationException> {
            db.products.save(renameAttempt)
        }
        assertEquals("Un produit avec ce nom existe déjà dans cette catégorie.", ex.message)
    }

    @Test
    fun `SaveProduct usecase returns Failure instead of throwing uncaught exception on duplicate`() = runBlocking {
        val dir = Files.createTempDirectory("prod-usecase-dup-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        val catId = db.categories.save(Category(0, "Boissons", active = true, displayOrder = 1))
        val saveUseCase = SaveProduct(db.categories, db.products)

        val p1 = Product(0, catId, "Jus d'Orange", 2000, 1000, active = true, available = true)
        val res1 = saveUseCase.execute(p1)
        assertIs<UseCaseResult.Success<Long>>(res1)

        // Second creation with duplicate name
        val p2 = Product(0, catId, "Jus d'Orange", 2200, 1000, active = true, available = true)
        val res2 = saveUseCase.execute(p2)
        assertIs<UseCaseResult.Failure>(res2)
        assertEquals("Un produit avec ce nom existe déjà dans cette catégorie.", res2.reason)
    }

    @Test
    fun `creating category with duplicate name throws DesktopValidationException`() = runBlocking {
        val dir = Files.createTempDirectory("cat-dup-test")
        val db = WindowsPosDatabase.open(dir.resolve("pos.db"))

        db.categories.save(Category(0, "Pâtisseries", active = true, displayOrder = 1))

        val ex = assertFailsWith<DesktopValidationException> {
            db.categories.save(Category(0, "pâtisseries", active = true, displayOrder = 2))
        }
        assertEquals("Une catégorie avec ce nom existe déjà.", ex.message)
    }
}
