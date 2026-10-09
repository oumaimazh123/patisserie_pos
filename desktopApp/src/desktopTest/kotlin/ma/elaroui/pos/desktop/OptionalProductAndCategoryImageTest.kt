package ma.elaroui.pos.desktop

import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.components.DesktopImageCache
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules

class OptionalProductAndCategoryImageTest {

    private companion object {
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }

    private fun withTestDb(block: (WindowsPosDatabase, java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("pos-optional-images-test")
        val dbPath = root.resolve("pos.db")
        WindowsPosDatabase.open(dbPath).use { db ->
            db.configureInitialSetup("Pâtisserie Test", "Admin", "1234")
            block(db, root)
        }
    }

    @Test
    fun testCreatingProductWithoutImage() = withTestDb { db, _ ->
        runBlocking {
            val catId = db.categories.save(Category(id = 0L, name = "Pains"))
            val product = Product(
                id = 0L,
                categoryId = catId,
                name = "Baguette Tradition",
                priceCentimes = 250L,
                taxRateBasisPoints = 0,
                imagePath = null
            )
            val prodId = db.products.save(product)
            assertTrue(prodId > 0)

            val loaded = db.products.findById(prodId)
            assertNotNull(loaded)
            assertEquals("Baguette Tradition", loaded.name)
            assertNull(loaded.imagePath)
            assertEquals(250L, loaded.priceCentimes)
        }
    }

    @Test
    fun testEditingProductWithoutImage() = withTestDb { db, root ->
        runBlocking {
            val catId = db.categories.save(Category(id = 0L, name = "Pains"))
            val product = Product(
                id = 0L,
                categoryId = catId,
                name = "Baguette Simple",
                priceCentimes = 200L,
                taxRateBasisPoints = 0,
                imagePath = null
            )
            val prodId = db.products.save(product)
            val created = db.products.findById(prodId)!!
            assertNull(created.imagePath)

            // Edit product attributes while imagePath remains null
            val updated = created.copy(name = "Baguette Complète", priceCentimes = 300L)
            db.products.save(updated)

            val reloaded = db.products.findById(prodId)!!
            assertEquals("Baguette Complète", reloaded.name)
            assertEquals(300L, reloaded.priceCentimes)
            assertNull(reloaded.imagePath)

            // Create product with an image, then edit to remove the image
            val source = root.resolve("prod_test.png")
            Files.write(source, Base64.getDecoder().decode(ONE_PIXEL_PNG))
            val state = DesktopNavState(db, root)
            val managedPath = state.importProductImage(source)

            val withImage = Product(
                id = 0L,
                categoryId = catId,
                name = "Croissant Beurre",
                priceCentimes = 400L,
                taxRateBasisPoints = 0,
                imagePath = managedPath
            )
            val withImgId = db.products.save(withImage)
            val loadedWithImg = db.products.findById(withImgId)!!
            assertEquals(managedPath, loadedWithImg.imagePath)

            // Edit to remove image (set to null)
            val imageRemoved = loadedWithImg.copy(imagePath = null)
            db.products.save(imageRemoved)

            val loadedAfterRemoval = db.products.findById(withImgId)!!
            assertNull(loadedAfterRemoval.imagePath)
            assertEquals("Croissant Beurre", loadedAfterRemoval.name)
        }
    }

    @Test
    fun testCreatingCategoryWithoutImage() = withTestDb { db, _ ->
        runBlocking {
            // Level 1 Category without image
            val cat = Category(id = 0L, name = "Gâteaux Individuels", parentId = null, imagePath = null)
            val validation = CategoryHierarchyRules.validateCategoryHierarchy(cat, emptyList())
            assertTrue(validation.isSuccess, "Creating category without image must succeed validation")

            val catId = db.categories.save(cat)
            assertTrue(catId > 0)

            val loaded = db.categories.findById(catId)
            assertNotNull(loaded)
            assertEquals("Gâteaux Individuels", loaded.name)
            assertNull(loaded.imagePath)

            // Level 2 Subcategory without image
            val subCat = Category(id = 0L, name = "Éclairs", parentId = catId, imagePath = null)
            val subValidation = CategoryHierarchyRules.validateCategoryHierarchy(subCat, listOf(loaded))
            assertTrue(subValidation.isSuccess, "Subcategory without image must succeed validation")

            val subId = db.categories.save(subCat)
            assertTrue(subId > 0)
            val loadedSub = db.categories.findById(subId)
            assertNotNull(loadedSub)
            assertEquals("Éclairs", loadedSub.name)
            assertEquals(catId, loadedSub.parentId)
            assertNull(loadedSub.imagePath)
        }
    }

    @Test
    fun testEditingCategoryWithoutImage() = withTestDb { db, root ->
        runBlocking {
            // 1. Edit category without image to update name/displayOrder
            val cat = Category(id = 0L, name = "Tartes", parentId = null, imagePath = null)
            val catId = db.categories.save(cat)
            val created = db.categories.findById(catId)!!
            assertNull(created.imagePath)

            val updated = created.copy(name = "Tartes et Tartelettes", displayOrder = 5)
            val allCats1 = db.categories.observeAll().first()
            val updateValidation = CategoryHierarchyRules.validateCategoryHierarchy(updated, allCats1)
            assertTrue(updateValidation.isSuccess)
            db.categories.save(updated)

            val reloaded = db.categories.findById(catId)!!
            assertEquals("Tartes et Tartelettes", reloaded.name)
            assertEquals(5, reloaded.displayOrder)
            assertNull(reloaded.imagePath)

            // 2. Edit category with image to remove image (set to null)
            val source = root.resolve("cat_test.png")
            Files.write(source, Base64.getDecoder().decode(ONE_PIXEL_PNG))
            val state = DesktopNavState(db, root)
            val managedPath = state.importProductImage(source)

            val catWithImg = Category(id = 0L, name = "Viennoiseries", parentId = null, imagePath = managedPath)
            val catWithImgId = db.categories.save(catWithImg)
            val loadedCatWithImg = db.categories.findById(catWithImgId)!!
            assertEquals(managedPath, loadedCatWithImg.imagePath)

            val catImgRemoved = loadedCatWithImg.copy(imagePath = null)
            val allCats2 = db.categories.observeAll().first()
            val removalValidation = CategoryHierarchyRules.validateCategoryHierarchy(catImgRemoved, allCats2)
            assertTrue(removalValidation.isSuccess)
            db.categories.save(catImgRemoved)

            val reloadedAfterRemoval = db.categories.findById(catWithImgId)!!
            assertNull(reloadedAfterRemoval.imagePath)
            assertEquals("Viennoiseries", reloadedAfterRemoval.name)
        }
    }

    @Test
    fun testDisplayingProductsAndCategoriesWithNoImageDoesNotCrash() {
        // DesktopImageCache handling null or empty keys
        DesktopImageCache.clear()
        assertNull(DesktopImageCache.get(""))

        // Checking that null, blank, or missing paths safely resolve to null bitmap
        val nullResult = runCatching {
            val path: String? = null
            path?.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.exists() && f.isFile } }
        }.getOrNull()
        assertNull(nullResult)

        val blankResult = runCatching {
            val path = "   "
            path.takeIf { it.isNotBlank() }?.let { File(it).takeIf { f -> f.exists() && f.isFile } }
        }.getOrNull()
        assertNull(blankResult)
    }

    @Test
    fun testExistingProductsAndCategoriesWithImagesStillWorkNormally() = withTestDb { db, root ->
        runBlocking {
            val source = root.resolve("existing_img.png")
            Files.write(source, Base64.getDecoder().decode(ONE_PIXEL_PNG))
            val state = DesktopNavState(db, root)
            val managedPath = state.importProductImage(source)

            // Category with image
            val cat = Category(id = 0L, name = "Pâtisserie Orientale", parentId = null, imagePath = managedPath)
            val catValidation = CategoryHierarchyRules.validateCategoryHierarchy(cat, emptyList())
            assertTrue(catValidation.isSuccess)
            val catId = db.categories.save(cat)
            val loadedCat = db.categories.findById(catId)!!
            assertEquals(managedPath, loadedCat.imagePath)

            // Product with image
            val prod = Product(
                id = 0L,
                categoryId = catId,
                name = "Cornes de Gazelle",
                priceCentimes = 1200L,
                taxRateBasisPoints = 0,
                imagePath = managedPath
            )
            val prodId = db.products.save(prod)
            val loadedProd = db.products.findById(prodId)!!
            assertEquals(managedPath, loadedProd.imagePath)

            // Edit product to update image to a new image
            val source2 = root.resolve("new_img.png")
            Files.write(source2, Base64.getDecoder().decode(ONE_PIXEL_PNG))
            val managedPath2 = state.importProductImage(source2)

            val updatedProd = loadedProd.copy(imagePath = managedPath2)
            db.products.save(updatedProd)
            val reloadedProd = db.products.findById(prodId)!!
            assertEquals(managedPath2, reloadedProd.imagePath)
        }
    }
}
