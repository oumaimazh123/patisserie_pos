package ma.elaroui.pos.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.shared.domain.Category

class CategoryHierarchyRulesTest {
    @Test
    fun missingParentAndCyclicParentChainAreRejected() {
        val child = Category(id = 0, name = "Child", parentId = 999)
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(child, emptyList()).isFailure)
        assertFalse(CategoryHierarchyRules.canCategoryHaveChildren(999, emptyList()))
        val cycle = listOf(
            Category(id = 1, name = "A", parentId = 2),
            Category(id = 2, name = "B", parentId = 1)
        )
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(child.copy(parentId = 1), cycle).isFailure)
    }


    // Cas 1: Créer Pâtisserie (Niveau 1)
    @Test
    fun `case 1 - creating root category level 1 succeeds`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val validation = CategoryHierarchyRules.validateCategoryHierarchy(root, emptyList())
        assertTrue(validation.isSuccess)
        assertEquals(1, CategoryHierarchyRules.calculateLevel(root.id, listOf(root)))
        assertTrue(CategoryHierarchyRules.canCategoryHaveChildren(root, listOf(root)))
    }

    // Cas 2: Ajouter Pâtisserie -> Gâteaux (Niveau 2)
    @Test
    fun `case 2 - adding level 2 category under level 1 succeeds`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "images/gateaux.png")
        val all = listOf(root, gateaux)

        val validation = CategoryHierarchyRules.validateCategoryHierarchy(gateaux, listOf(root))
        assertTrue(validation.isSuccess)
        assertEquals(2, CategoryHierarchyRules.calculateLevel(gateaux.id, all))
        assertTrue(CategoryHierarchyRules.canCategoryHaveChildren(gateaux, all))
    }

    // Cas 3: Ajouter Pâtisserie -> Gâteaux -> Gâteaux individuels (Niveau 3)
    @Test
    fun `case 3 - adding level 3 category under level 2 succeeds`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "images/gateaux.png")
        val individuels = Category(id = 3L, name = "Gâteaux individuels", parentId = 2L, imagePath = "images/individuels.png")
        val all = listOf(root, gateaux, individuels)

        val validation = CategoryHierarchyRules.validateCategoryHierarchy(individuels, listOf(root, gateaux))
        assertTrue(validation.isSuccess)
        assertEquals(3, CategoryHierarchyRules.calculateLevel(individuels.id, all))
        // Level 3 CAN now have children since MAX_DEPTH is 4
        assertTrue(CategoryHierarchyRules.canCategoryHaveChildren(individuels, all))
    }

    // Cas 4: Ajouter Pâtisserie -> Gâteaux -> Individuels -> Mini gâteaux (Niveau 4 autorisé)
    @Test
    fun `case 4 - adding level 4 under level 3 succeeds and level 5 is rejected`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "images/gateaux.png")
        val individuels = Category(id = 3L, name = "Individuels", parentId = 2L, imagePath = "images/individuels.png")
        val all = listOf(root, gateaux, individuels)

        val miniGateaux = Category(id = 4L, name = "Mini gâteaux", parentId = 3L, imagePath = "images/mini.png")
        val validation = CategoryHierarchyRules.validateCategoryHierarchy(miniGateaux, all)
        assertTrue(validation.isSuccess)
        val allWithL4 = all + miniGateaux
        assertEquals(4, CategoryHierarchyRules.calculateLevel(miniGateaux.id, allWithL4))
        // Level 4 cannot have children
        assertFalse(CategoryHierarchyRules.canCategoryHaveChildren(miniGateaux, allWithL4))

        // Level 5 rejected
        val microGateaux = Category(id = 5L, name = "Micro", parentId = 4L, imagePath = "images/micro.png")
        val valL5 = CategoryHierarchyRules.validateCategoryHierarchy(microGateaux, allWithL4)
        assertTrue(valL5.isFailure)
        assertEquals(CategoryHierarchyRules.ERROR_LEVEL_4_CANNOT_HAVE_CHILDREN, valL5.exceptionOrNull()?.message)
    }

    // Cas 5: Créer plusieurs enfants au niveau 2
    @Test
    fun `case 5 - creating multiple level 2 children under level 1 succeeds`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "images/gateaux.png")
        val viennoiseries = Category(id = 3L, name = "Viennoiseries", parentId = 1L, imagePath = "images/viennoiseries.png")
        val marocaine = Category(id = 4L, name = "Pâtisserie marocaine", parentId = 1L, imagePath = "images/marocaine.png")
        val all = listOf(root, gateaux, viennoiseries, marocaine)

        val descendants = CategoryHierarchyRules.getAllDescendantIds(root.id, all)
        assertEquals(setOf(2L, 3L, 4L), descendants)
        assertEquals(2, CategoryHierarchyRules.calculateLevel(gateaux.id, all))
        assertEquals(2, CategoryHierarchyRules.calculateLevel(viennoiseries.id, all))
        assertEquals(2, CategoryHierarchyRules.calculateLevel(marocaine.id, all))
    }

    // Cas 6: Créer plusieurs enfants au niveau 3
    @Test
    fun `case 6 - creating multiple level 3 children under level 2 succeeds`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/patisserie.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "images/gateaux.png")
        val individuels = Category(id = 3L, name = "Individuels", parentId = 2L, imagePath = "images/indiv.png")
        val familiaux = Category(id = 4L, name = "Familiaux", parentId = 2L, imagePath = "images/fam.png")
        val anniversaire = Category(id = 5L, name = "Anniversaire", parentId = 2L, imagePath = "images/anniv.png")
        val all = listOf(root, gateaux, individuels, familiaux, anniversaire)

        val descendantsOfGateaux = CategoryHierarchyRules.getAllDescendantIds(gateaux.id, all)
        assertEquals(setOf(3L, 4L, 5L), descendantsOfGateaux)

        val allDescendantsOfRoot = CategoryHierarchyRules.getAllDescendantIds(root.id, all)
        assertEquals(setOf(2L, 3L, 4L, 5L), allDescendantsOfRoot)

        assertEquals(3, CategoryHierarchyRules.calculateLevel(individuels.id, all))
        assertEquals(3, CategoryHierarchyRules.calculateLevel(familiaux.id, all))
        assertEquals(3, CategoryHierarchyRules.calculateLevel(anniversaire.id, all))
    }

    // Cas 7: Images optionnelles pour les catégories et nom obligatoire
    @Test
    fun `case 7 - optional image and mandatory name validation`() {
        val noImage = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = null)
        val blankImage = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "   ")
        val withImage = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "images/cat.png")
        val noName = Category(id = 1L, name = "", parentId = null, imagePath = "images/cat.png")
        val noNameNoImage = Category(id = 1L, name = "", parentId = null, imagePath = null)

        // Images are optional: null or blank imagePath must succeed
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(noImage, emptyList()).isSuccess)
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(blankImage, emptyList()).isSuccess)
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(withImage, emptyList()).isSuccess)

        // Name is still mandatory
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(noName, emptyList()).isFailure)
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(noNameNoImage, emptyList()).isFailure)

        // Editing category with or without image succeeds
        val editedWithoutImage = withImage.copy(name = "Pâtisserie Fine", imagePath = null)
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(editedWithoutImage, listOf(withImage)).isSuccess)

        val editedWithImage = noImage.copy(name = "Pâtisserie Fine", imagePath = "images/new_cat.png")
        assertTrue(CategoryHierarchyRules.validateCategoryHierarchy(editedWithImage, listOf(noImage)).isSuccess)
    }

    // Cas 8: Cycle direct et indirect interdit
    @Test
    fun `case 8 - cycle prevention`() {
        val catA = Category(id = 10L, name = "A", parentId = null, imagePath = "img.png")
        val catB = Category(id = 20L, name = "B", parentId = 10L, imagePath = "img.png")
        val all = listOf(catA, catB)

        // Self parent
        val selfParent = catA.copy(parentId = 10L)
        val selfValidation = CategoryHierarchyRules.validateCategoryHierarchy(selfParent, all)
        assertTrue(selfValidation.isFailure)
        assertEquals(CategoryHierarchyRules.ERROR_CYCLE_DETECTED, selfValidation.exceptionOrNull()?.message)

        // Indirect cycle: make A's parent B when B's parent is A
        val indirectCycle = catA.copy(parentId = 20L)
        val indirectValidation = CategoryHierarchyRules.validateCategoryHierarchy(indirectCycle, all)
        assertTrue(indirectValidation.isFailure)
        assertEquals(CategoryHierarchyRules.ERROR_CYCLE_DETECTED, indirectValidation.exceptionOrNull()?.message)
    }

    // Cas 9: Fil d'Ariane ordonné
    @Test
    fun `case 9 - breadcrumb resolution`() {
        val root = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "img1.png")
        val gateaux = Category(id = 2L, name = "Gâteaux", parentId = 1L, imagePath = "img2.png")
        val individuels = Category(id = 3L, name = "Individuels", parentId = 2L, imagePath = "img3.png")
        val all = listOf(root, gateaux, individuels)

        val breadcrumb = CategoryHierarchyRules.getBreadcrumbPath(3L, all)
        assertEquals(listOf("Pâtisserie", "Gâteaux", "Individuels"), breadcrumb.map { it.name })

        val breadcrumbL2 = CategoryHierarchyRules.getBreadcrumbPath(2L, all)
        assertEquals(listOf("Pâtisserie", "Gâteaux"), breadcrumbL2.map { it.name })

        val breadcrumbL1 = CategoryHierarchyRules.getBreadcrumbPath(1L, all)
        assertEquals(listOf("Pâtisserie"), breadcrumbL1.map { it.name })

        assertEquals(emptyList(), CategoryHierarchyRules.getBreadcrumbPath(null, all))
    }

    // Cas 10: Arborescence
    @Test
    fun `case 10 - category tree structure generation`() {
        val root1 = Category(id = 1L, name = "Pâtisserie", parentId = null, imagePath = "img.png", displayOrder = 1)
        val root2 = Category(id = 2L, name = "Boulangerie", parentId = null, imagePath = "img.png", displayOrder = 2)
        val sub1 = Category(id = 11L, name = "Gâteaux", parentId = 1L, imagePath = "img.png", displayOrder = 1)
        val subSub1 = Category(id = 111L, name = "Individuels", parentId = 11L, imagePath = "img.png", displayOrder = 1)
        val all = listOf(root1, root2, sub1, subSub1)

        val tree = CategoryHierarchyRules.buildCategoryTree(all)
        assertEquals(2, tree.size)
        assertEquals("Pâtisserie", tree[0].category.name)
        assertEquals(1, tree[0].level)
        assertEquals(1, tree[0].children.size)
        assertEquals("Gâteaux", tree[0].children[0].category.name)
        assertEquals(2, tree[0].children[0].level)
        assertEquals(1, tree[0].children[0].children.size)
        assertEquals("Individuels", tree[0].children[0].children[0].category.name)
        assertEquals(3, tree[0].children[0].children[0].level)

        assertEquals("Boulangerie", tree[1].category.name)
        assertEquals(0, tree[1].children.size)
    }
}
