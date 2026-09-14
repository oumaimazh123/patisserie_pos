package ma.elaroui.pos.desktop.presentation.management.categories

import kotlin.test.*
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.rules.CategoryHierarchyRules
import ma.elaroui.pos.shared.rules.CategoryTreeNode

class CategoryTreeExpansionTest {

    private val sampleCategories = listOf(
        // Level 1 Root: Boissons (id=1)
        Category(id = 1L, name = "Boissons", parentId = null, displayOrder = 1),
        // Level 2 Sub: Café (id=2), Jus (id=3), Thé (id=4)
        Category(id = 2L, name = "Café", parentId = 1L, displayOrder = 1),
        Category(id = 3L, name = "Jus", parentId = 1L, displayOrder = 2),
        Category(id = 4L, name = "Thé", parentId = 1L, displayOrder = 3),
        // Level 3 Sub-sub: Café chaud (id=5), Café froid (id=6)
        Category(id = 5L, name = "Café chaud", parentId = 2L, displayOrder = 1),
        Category(id = 6L, name = "Café froid", parentId = 2L, displayOrder = 2),
        // Level 1 Root: Pâtisserie (id=10)
        Category(id = 10L, name = "Pâtisserie", parentId = null, displayOrder = 2),
        // Level 1 Root: Boulangerie (id=20)
        Category(id = 20L, name = "Boulangerie", parentId = null, displayOrder = 3),
        // Level 1 Root without children: Snacks (id=30)
        Category(id = 30L, name = "Snacks", parentId = null, displayOrder = 4)
    )

    private fun flattenTree(
        nodes: List<CategoryTreeNode>,
        query: String,
        expandedIds: Set<Long>,
        allCategories: List<Category>
    ): List<CategoryTreeNode> {
        val result = mutableListOf<CategoryTreeNode>()
        for (node in nodes) {
            val matchesQuery = query.isBlank() || node.category.name.contains(query, ignoreCase = true)
            val hasMatchingDescendant = query.isNotBlank() && run {
                val descendants = CategoryHierarchyRules.getAllDescendantIds(node.category.id, allCategories)
                allCategories.any { it.id in descendants && it.name.contains(query, ignoreCase = true) }
            }
            if (matchesQuery || hasMatchingDescendant) {
                result.add(node)
                val isExpanded = node.category.id in expandedIds
                if (isExpanded && node.children.isNotEmpty()) {
                    result.addAll(flattenTree(node.children, query, expandedIds, allCategories))
                }
            }
        }
        return result
    }

    private fun computeSearchAncestors(query: String, allCategories: List<Category>): Set<Long> {
        val q = query.trim()
        if (q.isBlank()) return emptySet()
        val matchingCategories = allCategories.filter { it.name.contains(q, ignoreCase = true) }
        val categoryMap = allCategories.associateBy { it.id }
        val ancestors = mutableSetOf<Long>()
        for (matching in matchingCategories) {
            var curr = matching.parentId?.let { categoryMap[it] }
            while (curr != null) {
                ancestors.add(curr.id)
                curr = curr.parentId?.let { categoryMap[it] }
            }
        }
        return ancestors
    }

    @Test
    fun testInitialStateShowsOnlyLevel1Categories() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        val expandedIds = emptySet<Long>()

        val visible = flattenTree(tree, "", expandedIds, sampleCategories)

        val visibleNames = visible.map { it.category.name }
        assertEquals(listOf("Boissons", "Pâtisserie", "Boulangerie", "Snacks"), visibleNames)
        assertTrue(visible.all { it.level == 1 })
    }

    @Test
    fun testExpandingLevel1RevealsLevel2Children() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        var expandedIds = emptySet<Long>()

        // User clicks Boissons (id=1)
        expandedIds = expandedIds + 1L

        val visible = flattenTree(tree, "", expandedIds, sampleCategories)
        val visibleNames = visible.map { it.category.name }

        assertEquals(
            listOf("Boissons", "Café", "Jus", "Thé", "Pâtisserie", "Boulangerie", "Snacks"),
            visibleNames
        )
        // Level 3 children are not yet visible because Café is not expanded
        assertFalse(visibleNames.contains("Café chaud"))
        assertFalse(visibleNames.contains("Café froid"))
    }

    @Test
    fun testExpandingLevel2RevealsLevel3Children() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        var expandedIds = setOf(1L) // Boissons expanded

        // User clicks Café (id=2)
        expandedIds = expandedIds + 2L

        val visible = flattenTree(tree, "", expandedIds, sampleCategories)
        val visibleNames = visible.map { it.category.name }

        assertEquals(
            listOf("Boissons", "Café", "Café chaud", "Café froid", "Jus", "Thé", "Pâtisserie", "Boulangerie", "Snacks"),
            visibleNames
        )
    }

    @Test
    fun testClosingLevel1HidesAllDescendantsAndResetsChildrenExpandedState() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        var expandedIds = setOf(1L, 2L) // Boissons and Café both open

        // User closes Boissons (id=1)
        val descendants = CategoryHierarchyRules.getAllDescendantIds(1L, sampleCategories)
        expandedIds = expandedIds - 1L - descendants

        val visible = flattenTree(tree, "", expandedIds, sampleCategories)
        val visibleNames = visible.map { it.category.name }

        assertEquals(listOf("Boissons", "Pâtisserie", "Boulangerie", "Snacks"), visibleNames)

        // When user re-opens Boissons later, Café should NOT be open automatically
        expandedIds = expandedIds + 1L
        val reopened = flattenTree(tree, "", expandedIds, sampleCategories)
        val reopenedNames = reopened.map { it.category.name }

        assertEquals(
            listOf("Boissons", "Café", "Jus", "Thé", "Pâtisserie", "Boulangerie", "Snacks"),
            reopenedNames
        )
        assertFalse(reopenedNames.contains("Café chaud"))
    }

    @Test
    fun testSearchAutomaticallyExpandsAncestorsOfDeepMatchingCategories() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        val userExpandedIds = emptySet<Long>() // Initially collapsed

        // User searches for "chaud" (which is Level 3 under Boissons -> Café)
        val query = "chaud"
        val autoAncestors = computeSearchAncestors(query, sampleCategories)
        assertEquals(setOf(1L, 2L), autoAncestors) // Boissons (1) and Café (2)

        val effectiveExpanded = userExpandedIds + autoAncestors
        val visible = flattenTree(tree, query, effectiveExpanded, sampleCategories)
        val visibleNames = visible.map { it.category.name }

        // Context hierarchy is preserved: Boissons -> Café -> Café chaud
        assertEquals(listOf("Boissons", "Café", "Café chaud"), visibleNames)
    }

    @Test
    fun testClearingSearchRestoresUserCollapsedState() {
        val tree = CategoryHierarchyRules.buildCategoryTree(sampleCategories)
        val userExpandedIds = emptySet<Long>()

        // Search is cleared (empty query)
        val effectiveExpanded = userExpandedIds // No ancestors added
        val visible = flattenTree(tree, "", effectiveExpanded, sampleCategories)
        val visibleNames = visible.map { it.category.name }

        assertEquals(listOf("Boissons", "Pâtisserie", "Boulangerie", "Snacks"), visibleNames)
    }
}
