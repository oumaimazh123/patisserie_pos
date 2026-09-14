package ma.elaroui.pos.shared.rules

import ma.elaroui.pos.shared.domain.Category

/**
 * Data structure representing a node in the visual category tree.
 */
data class CategoryTreeNode(
    val category: Category,
    val level: Int,
    val children: List<CategoryTreeNode> = emptyList()
)

/**
 * Domain rules and validation for Category hierarchy.
 *
 * Rules:
 * - Maximum depth: 3 levels (Level 1 = Root, Level 2 = Subcategory, Level 3 = Sub-subcategory)
 * - A level 3 category CANNOT have children (prevent 4th level).
 * - Cycles (direct or indirect) are strictly prohibited.
 */
object CategoryHierarchyRules {

    const val MAX_DEPTH = 4
    const val ERROR_LEVEL_4_CANNOT_HAVE_CHILDREN = "Cette catégorie est déjà au niveau 4. Une catégorie de niveau 4 ne peut pas avoir de sous-catégorie."
    const val ERROR_LEVEL_3_CANNOT_HAVE_CHILDREN = ERROR_LEVEL_4_CANNOT_HAVE_CHILDREN
    const val ERROR_CYCLE_DETECTED = "Une catégorie ne peut pas être son propre parent ou créer une boucle hiérarchique."
    const val ERROR_IMAGE_REQUIRED = "L'image de la catégorie est obligatoire."
    const val ERROR_NAME_REQUIRED = "Le nom de la catégorie est obligatoire."

    /**
     * Calculates the 1-based hierarchy level for the given category ID.
     * Returns:
     * - 0 if [categoryId] is null
     * - 1 for a root category (`parentId == null`)
     * - 2 for a subcategory whose parent is root
     * - 3 for a subcategory whose parent is at level 2
     * - 4+ if deeper
     * - -1 if a cycle is detected
     */
    fun calculateLevel(categoryId: Long?, allCategories: List<Category>): Int {
        if (categoryId == null) return 0
        val categoryMap = allCategories.associateBy { it.id }
        var currentId: Long? = categoryId
        var level = 0
        val visited = mutableSetOf<Long>()

        while (currentId != null) {
            if (!visited.add(currentId)) {
                return -1 // Cycle detected
            }
            level++
            val cat = categoryMap[currentId] ?: break
            currentId = cat.parentId
        }
        return level
    }

    /**
     * Returns true if a category is allowed to have children (i.e. currently at Level 1 or Level 2).
     * Returns false if the category is at Level 3 or higher.
     */
    fun canCategoryHaveChildren(category: Category, allCategories: List<Category>): Boolean {
        return canCategoryHaveChildren(category.id, allCategories)
    }

    fun canCategoryHaveChildren(categoryId: Long, allCategories: List<Category>): Boolean {
        val level = calculateLevel(categoryId, allCategories)
        return level in 1 until MAX_DEPTH
    }

    /**
     * Recursively retrieves all descendant IDs (children, grandchildren, etc.) of a given category ID.
     */
    fun getAllDescendantIds(categoryId: Long, allCategories: List<Category>): Set<Long> {
        val childrenByParent = allCategories.groupBy { it.parentId }
        val result = mutableSetOf<Long>()

        fun collect(parentId: Long) {
            val children = childrenByParent[parentId].orEmpty()
            for (child in children) {
                if (result.add(child.id)) {
                    collect(child.id)
                }
            }
        }

        collect(categoryId)
        return result
    }

    /**
     * Returns the ordered breadcrumb list of categories from Root down to the specified category.
     * e.g. [Pâtisserie (L1), Gâteaux (L2), Gâteaux individuels (L3)]
     */
    fun getBreadcrumbPath(categoryId: Long?, allCategories: List<Category>): List<Category> {
        if (categoryId == null) return emptyList()
        val categoryMap = allCategories.associateBy { it.id }
        val path = mutableListOf<Category>()
        var currentId: Long? = categoryId
        val visited = mutableSetOf<Long>()

        while (currentId != null) {
            if (!visited.add(currentId)) break // Prevent infinite loop on corrupted data
            val cat = categoryMap[currentId] ?: break
            path.add(cat)
            currentId = cat.parentId
        }
        return path.reversed()
    }

    /**
     * Validates whether [category] can be added or updated within the hierarchy.
     */
    fun validateCategoryHierarchy(
        category: Category,
        allCategories: List<Category>,
        requireImage: Boolean = true
    ): Result<Unit> {
        if (category.name.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_NAME_REQUIRED))
        }
        if (requireImage && category.imagePath.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_IMAGE_REQUIRED))
        }

        // Self-parenting check
        if (category.id != 0L && category.parentId == category.id) {
            return Result.failure(IllegalArgumentException(ERROR_CYCLE_DETECTED))
        }

        // Check cycle with descendants
        if (category.id != 0L && category.parentId != null) {
            val descendants = getAllDescendantIds(category.id, allCategories)
            if (category.parentId in descendants) {
                return Result.failure(IllegalArgumentException(ERROR_CYCLE_DETECTED))
            }
        }

        // Check depth
        if (category.parentId != null) {
            val parentLevel = calculateLevel(category.parentId, allCategories)
            if (parentLevel >= MAX_DEPTH) {
                return Result.failure(IllegalArgumentException(ERROR_LEVEL_3_CANNOT_HAVE_CHILDREN))
            }

            // Also check if any existing descendants of this category would exceed MAX_DEPTH
            if (category.id != 0L) {
                val subtreeMaxDepth = getSubtreeDepth(category.id, allCategories)
                // New level of category will be parentLevel + 1
                // The max depth of any child would be parentLevel + 1 + subtreeMaxDepth
                if (parentLevel + 1 + subtreeMaxDepth > MAX_DEPTH) {
                    return Result.failure(IllegalArgumentException("Le déplacement dépasserait la limite de 4 niveaux pour les sous-catégories existantes."))
                }
            }
        }

        return Result.success(Unit)
    }

    /**
     * Returns the relative depth of the subtree rooted at [categoryId] (0 if it has no children, 1 if only children, 2 if grandchildren).
     */
    private fun getSubtreeDepth(categoryId: Long, allCategories: List<Category>): Int {
        val childrenByParent = allCategories.groupBy { it.parentId }
        fun depth(id: Long): Int {
            val children = childrenByParent[id].orEmpty()
            if (children.isEmpty()) return 0
            return 1 + children.maxOf { depth(it.id) }
        }
        return depth(categoryId)
    }

    /**
     * Builds a tree of categories sorted by displayOrder then name.
     */
    fun buildCategoryTree(allCategories: List<Category>): List<CategoryTreeNode> {
        val childrenByParent = allCategories.groupBy { it.parentId }

        fun buildNode(category: Category, level: Int): CategoryTreeNode {
            val children = childrenByParent[category.id].orEmpty()
                .sortedWith(compareBy<Category> { it.displayOrder }.thenBy { it.name })
                .map { buildNode(it, level + 1) }
            return CategoryTreeNode(category, level, children)
        }

        val roots = childrenByParent[null].orEmpty()
            .sortedWith(compareBy<Category> { it.displayOrder }.thenBy { it.name })

        return roots.map { buildNode(it, 1) }
    }
}
