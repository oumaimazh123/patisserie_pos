package ma.elaroui.pos.desktop.importing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToLong
import ma.elaroui.pos.desktop.presentation.model.MessagePresentation
import ma.elaroui.pos.desktop.presentation.model.MessageSeverity
import ma.elaroui.pos.desktop.presentation.model.UiMessage
import ma.elaroui.pos.shared.domain.Category
import ma.elaroui.pos.shared.domain.Product

data class CsvImportSummary(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val severity: MessageSeverity
        get() = when {
            imported > 0 && failed == 0 && errors.isEmpty() -> MessageSeverity.SUCCESS
            imported > 0 -> MessageSeverity.WARNING
            else -> MessageSeverity.ERROR
        }

    fun display(): String = buildString {
        append("Importés: $imported · Ignorés: $skipped · Échecs: $failed")
        if (errors.isNotEmpty()) {
            append("\nErreurs:\n").append(errors.take(5).joinToString("\n"))
        }
        if (warnings.isNotEmpty() && errors.isEmpty()) {
            append("\nAvertissements: ${warnings.size}")
        }
    }

    fun toUiMessage(): UiMessage = UiMessage(
        text = display(),
        severity = severity,
        presentation = MessagePresentation.INLINE
    )
}

data class CatalogCsvRow(
    val line: Int,
    val productId: String,
    val mainCategory: String,
    val mainCategoryImage: String?,
    val subCategory1: String?,
    val subCategory1Image: String?,
    val subCategory2: String?,
    val subCategory2Image: String?,
    val subCategory3: String?,
    val subCategory3Image: String?,
    val productNameFrench: String,
    val productNameArabic: String?,
    val productImage: String?,
    val unit: String?,
    val priceCentimes: Long,
    val taxBasisPoints: Int,
    val description: String?
) {
    /** Returns the hierarchical category steps (Name to Image) up to 4 levels */
    val categoryLevels: List<Pair<String, String?>> get() = buildList {
        add(mainCategory to mainCategoryImage)
        if (!subCategory1.isNullOrBlank()) add(subCategory1 to subCategory1Image)
        if (!subCategory2.isNullOrBlank()) add(subCategory2 to subCategory2Image)
        if (!subCategory3.isNullOrBlank()) add(subCategory3 to subCategory3Image)
    }

    /** Returns deepest non-empty category name */
    val deepestCategory: String get() = categoryLevels.last().first
}

data class CatalogRowError(val line: Int, val column: String, val message: String)
data class CatalogRowWarning(val line: Int, val column: String, val message: String)

data class CatalogPreviewCategoryNode(
    val name: String,
    val level: Int,
    val imagePath: String?,
    val productCount: Int,
    val isNew: Boolean,
    val children: List<CatalogPreviewCategoryNode> = emptyList()
)

data class CatalogImportAnalysis(
    val totalRows: Int,
    val validRows: List<CatalogCsvRow>,
    val errors: List<CatalogRowError>,
    val warnings: List<CatalogRowWarning>,
    val categoryTree: List<CatalogPreviewCategoryNode>,
    val categoriesCountByLevel: Map<Int, Int>,
    val newCategoriesCount: Int,
    val existingCategoriesCount: Int,
    val productsToImportCount: Int,
    val duplicateProductIdCount: Int,
    val taxBreakdown: Map<Int, Int>, // basis points -> count
    val imagesFoundCount: Int,
    val imagesMissingCount: Int,
    val canImport: Boolean
)

object CsvImports {

    val OFFICIAL_HEADERS = listOf(
        "Product ID",
        "Main Category",
        "Main Category Image",
        "Sub-Category 1",
        "Sub-Category 1 Image",
        "Sub-Category 2",
        "Sub-Category 2 Image",
        "Sub-Category 3",
        "Sub-Category 3 Image",
        "Product Name (French)",
        "Product Name (Arabic)",
        "Product Image",
        "Unit / Packaging",
        "Price (MAD)",
        "TVA",
        "Description"
    )

    private val ALLOWED_TAX_RATES = mapOf(
        "0" to 0,
        "0.0" to 0,
        "0%" to 0,
        "10" to 1000,
        "10.0" to 1000,
        "10%" to 1000,
        "20" to 2000,
        "20.0" to 2000,
        "20%" to 2000
    )

    fun isWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
               trimmed.startsWith("https://", ignoreCase = true)
    }

    fun readLines(path: Path): List<List<String>> {
        require(Files.isRegularFile(path) && path.fileName.toString().endsWith(".csv", true)) {
            "Sélectionnez un fichier CSV valide."
        }
        val content = Files.readString(path, Charsets.UTF_8).removePrefix("\uFEFF")
        if (content.isBlank()) return emptyList()

        val rawLines = parseCsvContent(content)
        return rawLines.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun parseCsvContent(content: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuote = false
        var i = 0
        val len = content.length

        // Detect delimiter based on first line
        val firstLine = content.lines().firstOrNull().orEmpty()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        while (i < len) {
            val c = content[i]
            when {
                c == '"' -> {
                    if (insideQuote && i + 1 < len && content[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        insideQuote = !insideQuote
                    }
                }
                c == delimiter && !insideQuote -> {
                    currentRecord.add(currentField.toString().trim())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !insideQuote -> {
                    if (c == '\r' && i + 1 < len && content[i + 1] == '\n') {
                        i++
                    }
                    currentRecord.add(currentField.toString().trim())
                    currentField.clear()
                    records.add(currentRecord.toList())
                    currentRecord.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString().trim())
            records.add(currentRecord.toList())
        }

        return records
    }

    fun analyzeCatalogCsv(
        path: Path,
        existingCategories: List<Category> = emptyList(),
        existingProducts: List<Product> = emptyList()
    ): CatalogImportAnalysis {
        val records = readLines(path)
        if (records.isEmpty()) {
            return CatalogImportAnalysis(
                totalRows = 0,
                validRows = emptyList(),
                errors = listOf(CatalogRowError(1, "File", "Le fichier CSV est vide.")),
                warnings = emptyList(),
                categoryTree = emptyList(),
                categoriesCountByLevel = emptyMap(),
                newCategoriesCount = 0,
                existingCategoriesCount = 0,
                productsToImportCount = 0,
                duplicateProductIdCount = 0,
                taxBreakdown = emptyMap(),
                imagesFoundCount = 0,
                imagesMissingCount = 0,
                canImport = false
            )
        }

        val headerRow = records.first().map { it.trim() }
        val headerMap = validateHeaders(headerRow)
        if (headerMap.isFailure) {
            return CatalogImportAnalysis(
                totalRows = 0,
                validRows = emptyList(),
                errors = listOf(CatalogRowError(1, "Header", headerMap.exceptionOrNull()?.message ?: "En-têtes CSV invalides")),
                warnings = emptyList(),
                categoryTree = emptyList(),
                categoriesCountByLevel = emptyMap(),
                newCategoriesCount = 0,
                existingCategoriesCount = 0,
                productsToImportCount = 0,
                duplicateProductIdCount = 0,
                taxBreakdown = emptyMap(),
                imagesFoundCount = 0,
                imagesMissingCount = 0,
                canImport = false
            )
        }

        val colIndexes = headerMap.getOrThrow()
        val dataRecords = records.drop(1)
        val validRows = mutableListOf<CatalogCsvRow>()
        val errors = mutableListOf<CatalogRowError>()
        val warnings = mutableListOf<CatalogRowWarning>()

        val seenProductIdsInCsv = mutableSetOf<String>()
        val existingSkus = existingProducts.mapNotNull { it.sku?.trim()?.lowercase() }.toSet()

        var duplicateProductIdCount = 0
        var imagesFound = 0
        var imagesMissing = 0
        val taxCounts = mutableMapOf(0 to 0, 1000 to 0, 2000 to 0)

        val csvParentDir = path.parent ?: Path.of(".")

        dataRecords.forEachIndexed { index, values ->
            val line = index + 2
            fun col(name: String): String = colIndexes[name]?.let { values.getOrNull(it)?.trim() }.orEmpty()

            val productId = col("Product ID")
            val mainCat = col("Main Category")
            val mainCatImg = col("Main Category Image").ifBlank { null }
            val sub1 = col("Sub-Category 1").ifBlank { null }
            val sub1Img = col("Sub-Category 1 Image").ifBlank { null }
            val sub2 = col("Sub-Category 2").ifBlank { null }
            val sub2Img = col("Sub-Category 2 Image").ifBlank { null }
            val sub3 = col("Sub-Category 3").ifBlank { null }
            val sub3Img = col("Sub-Category 3 Image").ifBlank { null }
            val nameFrench = col("Product Name (French)")
            val nameArabic = col("Product Name (Arabic)").ifBlank { null }
            val prodImg = col("Product Image").ifBlank { null }
            val unit = col("Unit / Packaging").ifBlank { null }
            val rawPrice = col("Price (MAD)")
            val rawTva = col("TVA")
            val description = col("Description").ifBlank { null }

            var rowHasError = false

            // 1. Mandatory Product ID
            if (productId.isBlank()) {
                errors.add(CatalogRowError(line, "Product ID", "Le champ 'Product ID' est obligatoire."))
                rowHasError = true
            } else {
                val normalizedId = productId.lowercase()
                if (!seenProductIdsInCsv.add(normalizedId)) {
                    errors.add(CatalogRowError(line, "Product ID", "Product ID '$productId' en double dans le fichier CSV."))
                    duplicateProductIdCount++
                    rowHasError = true
                } else if (normalizedId in existingSkus) {
                    warnings.add(CatalogRowWarning(line, "Product ID", "Le produit avec l'ID/SKU '$productId' existe déjà en base et sera mis à jour."))
                    duplicateProductIdCount++
                }
            }

            // 2. Mandatory Main Category
            if (mainCat.isBlank()) {
                errors.add(CatalogRowError(line, "Main Category", "La catégorie principale 'Main Category' est obligatoire."))
                rowHasError = true
            }

            // 3. Category Hierarchy Continuity (NO GAPS)
            if (sub1 == null && sub2 != null) {
                errors.add(CatalogRowError(line, "Sub-Category 1", "Saut de niveau interdit : 'Sub-Category 1' est vide alors que 'Sub-Category 2' ('$sub2') est renseignée."))
                rowHasError = true
            }
            if (sub2 == null && sub3 != null) {
                errors.add(CatalogRowError(line, "Sub-Category 2", "Saut de niveau interdit : 'Sub-Category 2' est vide alors que 'Sub-Category 3' ('$sub3') est renseignée."))
                rowHasError = true
            }

            // 4. Mandatory Product Name (French)
            if (nameFrench.isBlank()) {
                errors.add(CatalogRowError(line, "Product Name (French)", "Le nom du produit en français est obligatoire."))
                rowHasError = true
            }

            // 5. Single selling price (NO RANGES, strictly positive)
            var parsedPriceCentimes: Long = 0L
            if (rawPrice.isBlank()) {
                errors.add(CatalogRowError(line, "Price (MAD)", "Le prix 'Price (MAD)' est obligatoire."))
                rowHasError = true
            } else if (rawPrice.contains(Regex("[\\-–—]|\\.\\.|\\bà\\b|\\bto\\b", RegexOption.IGNORE_CASE))) {
                errors.add(CatalogRowError(line, "Price (MAD)", "Fourchette de prix interdite ('$rawPrice'). Veuillez indiquer un prix unitaire fixe."))
                rowHasError = true
            } else {
                val normalizedPrice = rawPrice.replace(',', '.').replace(" ", "")
                val priceNumber = normalizedPrice.toDoubleOrNull()
                if (priceNumber == null || priceNumber <= 0.0) {
                    errors.add(CatalogRowError(line, "Price (MAD)", "Prix invalide '$rawPrice'. Le montant doit être un nombre strictement supérieur à 0."))
                    rowHasError = true
                } else {
                    parsedPriceCentimes = (priceNumber * 100.0).roundToLong()
                }
            }

            // 6. Strict TVA Validation (ONLY 0%, 10%, 20%)
            var parsedTaxBasisPoints = 0
            if (rawTva.isBlank()) {
                errors.add(CatalogRowError(line, "TVA", "Le taux de TVA est obligatoire. Taux acceptés : 0%, 10%, 20%."))
                rowHasError = true
            } else {
                val normalizedTva = rawTva.trim().replace(',', '.')
                val basisPoints = ALLOWED_TAX_RATES[normalizedTva]
                if (basisPoints == null) {
                    errors.add(CatalogRowError(line, "TVA", "Taux de TVA invalide '$rawTva'. Seuls les taux 0%, 10% et 20% sont autorisés."))
                    rowHasError = true
                } else {
                    parsedTaxBasisPoints = basisPoints
                }
            }

            // 7. Image file checks (Warnings only, non-blocking)
            fun checkImage(rawPath: String?, colName: String) {
                if (!rawPath.isNullOrBlank()) {
                    val trimmed = rawPath.trim()
                    if (isWebUrl(trimmed)) {
                        imagesFound++
                    } else {
                        val resolved = runCatching {
                            val p = Path.of(trimmed)
                            if (p.isAbsolute) p else csvParentDir.resolve(p)
                        }.getOrNull()

                        if (resolved != null && Files.isRegularFile(resolved)) {
                            imagesFound++
                        } else {
                            imagesMissing++
                            warnings.add(CatalogRowWarning(line, colName, "Image introuvable : '$rawPath'"))
                        }
                    }
                }
            }
            checkImage(mainCatImg, "Main Category Image")
            checkImage(sub1Img, "Sub-Category 1 Image")
            checkImage(sub2Img, "Sub-Category 2 Image")
            checkImage(sub3Img, "Sub-Category 3 Image")
            checkImage(prodImg, "Product Image")

            if (!rowHasError) {
                taxCounts[parsedTaxBasisPoints] = (taxCounts[parsedTaxBasisPoints] ?: 0) + 1
                validRows.add(
                    CatalogCsvRow(
                        line = line,
                        productId = productId,
                        mainCategory = mainCat,
                        mainCategoryImage = mainCatImg,
                        subCategory1 = sub1,
                        subCategory1Image = sub1Img,
                        subCategory2 = sub2,
                        subCategory2Image = sub2Img,
                        subCategory3 = sub3,
                        subCategory3Image = sub3Img,
                        productNameFrench = nameFrench,
                        productNameArabic = nameArabic,
                        productImage = prodImg,
                        unit = unit,
                        priceCentimes = parsedPriceCentimes,
                        taxBasisPoints = parsedTaxBasisPoints,
                        description = description
                    )
                )
            }
        }

        // Build Category Preview Tree & Counts
        val (tree, countsByLevel, newCatCount, existingCatCount) = buildCategoryPreviewTree(validRows, existingCategories)

        return CatalogImportAnalysis(
            totalRows = dataRecords.size,
            validRows = validRows,
            errors = errors,
            warnings = warnings,
            categoryTree = tree,
            categoriesCountByLevel = countsByLevel,
            newCategoriesCount = newCatCount,
            existingCategoriesCount = existingCatCount,
            productsToImportCount = validRows.size,
            duplicateProductIdCount = duplicateProductIdCount,
            taxBreakdown = taxCounts,
            imagesFoundCount = imagesFound,
            imagesMissingCount = imagesMissing,
            canImport = validRows.isNotEmpty() && errors.isEmpty()
        )
    }

    private fun validateHeaders(headers: List<String>): Result<Map<String, Int>> {
        val normalized = headers.map { normalizeHeader(it) }
        val map = mutableMapOf<String, Int>()

        for (official in OFFICIAL_HEADERS) {
            val normOfficial = normalizeHeader(official)
            val idx = normalized.indexOf(normOfficial)
            if (idx == -1) {
                return Result.failure(
                    IllegalArgumentException("Colonne obligatoire manquante : '$official'. Le format officiel requiert 16 colonnes.")
                )
            }
            map[official] = idx
        }

        return Result.success(map)
    }

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase().replace("_", " ").replace("-", " ")

    private data class MutablePreviewNode(
        val name: String,
        val level: Int,
        var imagePath: String?,
        var productCount: Int = 0,
        var isNew: Boolean = true,
        val children: MutableMap<String, MutablePreviewNode> = linkedMapOf()
    )

    private fun buildCategoryPreviewTree(
        rows: List<CatalogCsvRow>,
        existingCategories: List<Category>
    ): Quadruple<List<CatalogPreviewCategoryNode>, Map<Int, Int>, Int, Int> {
        val rootNodes = linkedMapOf<String, MutablePreviewNode>()
        val levelCounts = mutableMapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)

        // Map existing categories by (normalizedName, parentId)
        val existingMap = mutableMapOf<Pair<String, Long?>, Category>()
        existingCategories.forEach { cat ->
            existingMap[cat.name.trim().lowercase() to cat.parentId] = cat
        }

        // Helper to check if a category already exists at path
        fun findExisting(path: List<String>): Category? {
            var parentId: Long? = null
            var current: Category? = null
            for (part in path) {
                current = existingMap[part.trim().lowercase() to parentId] ?: return null
                parentId = current.id
            }
            return current
        }

        val allObservedPaths = mutableSetOf<List<String>>()

        rows.forEach { row ->
            val levels = row.categoryLevels
            var currentMap: MutableMap<String, MutablePreviewNode> = rootNodes
            val currentPath = mutableListOf<String>()

            levels.forEachIndexed { index, (name, image) ->
                val level = index + 1
                currentPath.add(name)
                val pathSnapshot = currentPath.toList()
                allObservedPaths.add(pathSnapshot)

                val existing = findExisting(pathSnapshot)
                val node = currentMap.getOrPut(name.lowercase()) {
                    MutablePreviewNode(
                        name = name,
                        level = level,
                        imagePath = image,
                        isNew = (existing == null)
                    )
                }
                if (node.imagePath == null && image != null) {
                    node.imagePath = image
                }
                if (index == levels.lastIndex) {
                    node.productCount++
                }
                currentMap = node.children
            }
        }

        var newCatCount = 0
        var existingCatCount = 0

        allObservedPaths.forEach { path ->
            val level = path.size
            levelCounts[level] = (levelCounts[level] ?: 0) + 1
            if (findExisting(path) != null) {
                existingCatCount++
            } else {
                newCatCount++
            }
        }

        fun convert(node: MutablePreviewNode): CatalogPreviewCategoryNode =
            CatalogPreviewCategoryNode(
                name = node.name,
                level = node.level,
                imagePath = node.imagePath,
                productCount = node.productCount,
                isNew = node.isNew,
                children = node.children.values.map { convert(it) }
            )

        val tree = rootNodes.values.map { convert(it) }
        return Quadruple(tree, levelCounts, newCatCount, existingCatCount)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Generates the official UTF-8 CSV template populated with Moroccan bakery and pastry examples.
     */
    fun generateOfficialTemplate(): String = buildString {
        append(OFFICIAL_HEADERS.joinToString(",")).append("\r\n")
        append("CRO-001,Boulangerie,images/boulangerie.jpg,Viennoiserie,images/viennoiserie.jpg,,,,,Croissant Pur Beurre,كرواسون بالزبدة,images/croissant.jpg,Pièce,6.50,10%,Croissant croustillant pur beurre de baratte\r\n")
        append("PAIN-001,Boulangerie,images/boulangerie.jpg,Pains Traditionnels,images/pains.jpg,,,,,Pain Complet au Levain,خبز كامل بالخميرة البلدية,images/pain_complet.jpg,Pièce,5.00,0%,Pain artisanal à la farine complète et au levain naturel\r\n")
        append("CG-001,Pâtisserie,images/patisserie.jpg,Pâtisserie Marocaine,images/marocaine.jpg,Cornes de Gazelle,images/cornes.jpg,Amande & Fleur d'oranger,,Cornes de Gazelle d'Amande,كعب غزال باللوز,images/kaab.jpg,Kg,180.00,20%,Pâtisserie marocaine traditionnelle aux amandes et à l'eau de fleur d'oranger\r\n")
        append("TART-001,Pâtisserie,images/patisserie.jpg,Tartes & Tartelettes,images/tartes.jpg,,,,,Tartelette Citron Meringuée,تارت الليمون بالمورانغ,images/tarte_citron.jpg,Pièce,22.00,20%,Pâte sablée croustillante garnie d'une crème citron acidulée et meringue italienne\r\n")
    }
}
