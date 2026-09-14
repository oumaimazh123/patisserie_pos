package ma.elaroui.pos.desktop.importing

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.shared.domain.Category

class CsvImportsTest {

    private val header = CsvImports.OFFICIAL_HEADERS.joinToString(",")

    @Test
    fun `official catalog import creates 1 to 4 level categories and attaches product to deepest level`() {
        val dir = Files.createTempDirectory("catalog-import-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "P1,Boulangerie,images/boul.jpg,,,,,,,Baguette,باغيت,images/baguette.jpg,Pièce,3.00,0%,Baguette tradition\n" +
                "P2,Boulangerie,images/boul.jpg,Viennoiserie,images/vienn.jpg,,,,,Croissant,كرواسون,images/croissant.jpg,Pièce,6.50,10%,Croissant beurre\n" +
                "P3,Pâtisserie,images/patiss.jpg,Marocaine,images/maroc.jpg,Cornes,images/cornes.jpg,,,Corne de gazelle,كعب غزال,images/kaab.jpg,Kg,180.00,20%,Corne amande\n" +
                "P4,Pâtisserie,images/patiss.jpg,Marocaine,images/maroc.jpg,Cornes,images/cornes.jpg,Prestige,images/prest.jpg,Corne Prestige,كعب غزال بريستيج,images/kaab_p.jpg,Kg,220.00,20%,Corne prestige\n"
        )

        WindowsPosDatabase.open(dir.resolve("pos.db")).use { db ->
            val state = DesktopNavState(db, dir)
            val analysis = state.analyzeCatalogCsv(csv)

            assertEquals(4, analysis.totalRows)
            assertEquals(4, analysis.validRows.size)
            assertTrue(analysis.errors.isEmpty())
            assertTrue(analysis.canImport)

            // Category counts by level
            assertEquals(2, analysis.categoriesCountByLevel[1]) // Boulangerie, Pâtisserie
            assertEquals(2, analysis.categoriesCountByLevel[2]) // Viennoiserie, Marocaine
            assertEquals(1, analysis.categoriesCountByLevel[3]) // Cornes
            assertEquals(1, analysis.categoriesCountByLevel[4]) // Prestige

            // TVA breakdown
            assertEquals(1, analysis.taxBreakdown[0]) // 0%
            assertEquals(1, analysis.taxBreakdown[1000]) // 10%
            assertEquals(2, analysis.taxBreakdown[2000]) // 20%

            val summary = state.executeCatalogImport(analysis, csv)
            assertEquals(4, summary.imported)
            assertEquals(0, summary.failed)

            // Verify P1 attached to L1 Boulangerie
            val boulangerie = state.categories.first { it.name == "Boulangerie" && it.parentId == null }
            val p1 = state.products.first { it.sku == "P1" }
            assertEquals(boulangerie.id, p1.categoryId)
            assertEquals(300L, p1.priceCentimes)
            assertEquals(0, p1.taxRateBasisPoints)
            assertEquals("باغيت", p1.nameArabic)

            // Verify P2 attached to L2 Viennoiserie under Boulangerie
            val viennoiserie = state.categories.first { it.name == "Viennoiserie" && it.parentId == boulangerie.id }
            val p2 = state.products.first { it.sku == "P2" }
            assertEquals(viennoiserie.id, p2.categoryId)
            assertEquals(650L, p2.priceCentimes)
            assertEquals(1000, p2.taxRateBasisPoints)

            // Verify P4 attached to L4 Prestige
            val patisserie = state.categories.first { it.name == "Pâtisserie" && it.parentId == null }
            val marocaine = state.categories.first { it.name == "Marocaine" && it.parentId == patisserie.id }
            val cornes = state.categories.first { it.name == "Cornes" && it.parentId == marocaine.id }
            val prestige = state.categories.first { it.name == "Prestige" && it.parentId == cornes.id }
            val p4 = state.products.first { it.sku == "P4" }
            assertEquals(prestige.id, p4.categoryId)
            assertEquals(22000L, p4.priceCentimes)
            assertEquals(2000, p4.taxRateBasisPoints)
            assertEquals("كعب غزال بريستيج", p4.nameArabic)
        }
    }

    @Test
    fun `homonym categories under different parents can coexist`() {
        val dir = Files.createTempDirectory("catalog-homonym-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "P1,Boulangerie,,Traditionnel,,,,,,Pain Tradition,خبز تقليدي,,Pièce,4.00,0%,\n" +
                "P2,Pâtisserie,,Traditionnel,,,,,,Gâteau Tradition,حلوى تقليدية,,Pièce,15.00,20%,\n"
        )

        WindowsPosDatabase.open(dir.resolve("pos.db")).use { db ->
            val state = DesktopNavState(db, dir)
            val analysis = state.analyzeCatalogCsv(csv)
            assertTrue(analysis.canImport)

            val summary = state.executeCatalogImport(analysis, csv)
            assertEquals(2, summary.imported)

            val boul = state.categories.first { it.name == "Boulangerie" }
            val pat = state.categories.first { it.name == "Pâtisserie" }

            val tradUnderBoul = state.categories.first { it.name == "Traditionnel" && it.parentId == boul.id }
            val tradUnderPat = state.categories.first { it.name == "Traditionnel" && it.parentId == pat.id }

            assertTrue(tradUnderBoul.id != tradUnderPat.id)
            val p1 = state.products.first { it.sku == "P1" }
            val p2 = state.products.first { it.sku == "P2" }
            assertEquals(tradUnderBoul.id, p1.categoryId)
            assertEquals(tradUnderPat.id, p2.categoryId)
        }
    }

    @Test
    fun `strict TVA validation rejects invalid rates and accepts only 0, 10, 20 percent`() {
        val dir = Files.createTempDirectory("catalog-tva-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "OK1,Cat1,,,,,,,,Prod 0%,,,Pièce,10,0%,\n" +
                "OK2,Cat1,,,,,,,,Prod 10%,,,Pièce,10,10%,\n" +
                "OK3,Cat1,,,,,,,,Prod 20%,,,Pièce,10,20%,\n" +
                "BAD1,Cat1,,,,,,,,Prod 5%,,,Pièce,10,5%,\n" +
                "BAD2,Cat1,,,,,,,,Prod 15%,,,Pièce,10,15%,\n" +
                "BAD3,Cat1,,,,,,,,Prod Invalid,,,Pièce,10,autre,\n"
        )

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertEquals(6, analysis.totalRows)
        assertEquals(3, analysis.validRows.size)
        assertEquals(3, analysis.errors.size)
        assertFalse(analysis.canImport)

        assertTrue(analysis.errors.any { it.line == 5 && it.column == "TVA" && it.message.contains("5%") })
        assertTrue(analysis.errors.any { it.line == 6 && it.column == "TVA" && it.message.contains("15%") })
        assertTrue(analysis.errors.any { it.line == 7 && it.column == "TVA" && it.message.contains("autre") })
    }

    @Test
    fun `price ranges and invalid prices are strictly rejected`() {
        val dir = Files.createTempDirectory("catalog-price-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "OK1,Cat1,,,,,,,,Single Price Int,,,Pièce,18,10%,\n" +
                "OK2,Cat1,,,,,,,,Single Price Comma,,,Pièce,\"18,50\",10%,\n" +
                "OK3,Cat1,,,,,,,,Single Price Dot,,,Pièce,18.50,10%,\n" +
                "BAD1,Cat1,,,,,,,,Range Dash,,,Pièce,12-20,10%,\n" +
                "BAD2,Cat1,,,,,,,,Range Space Dash,,,Pièce,12 - 20,10%,\n" +
                "BAD3,Cat1,,,,,,,,Zero Price,,,Pièce,0,10%,\n" +
                "BAD4,Cat1,,,,,,,,Negative Price,,,Pièce,-5,10%,\n"
        )

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertEquals(7, analysis.totalRows)
        assertEquals(3, analysis.validRows.size)
        assertEquals(4, analysis.errors.size)
        assertFalse(analysis.canImport)

        assertTrue(analysis.errors.any { it.line == 5 && it.column == "Price (MAD)" && it.message.contains("Fourchette de prix interdite") })
        assertTrue(analysis.errors.any { it.line == 6 && it.column == "Price (MAD)" && it.message.contains("Fourchette de prix interdite") })
        assertTrue(analysis.errors.any { it.line == 7 && it.column == "Price (MAD)" })
        assertTrue(analysis.errors.any { it.line == 8 && it.column == "Price (MAD)" })
    }

    @Test
    fun `hierarchy gaps are strictly rejected`() {
        val dir = Files.createTempDirectory("catalog-gap-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "OK,Main,,Sub1,,,,,,Valid Row,,,Pièce,10,10%,\n" +
                "BAD_GAP1,Main,,,,Sub2,,,,Gap Sub1 Missing,,,Pièce,10,10%,\n" +
                "BAD_GAP2,Main,,Sub1,,,,Sub3,,Gap Sub2 Missing,,,Pièce,10,10%,\n"
        )

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertEquals(3, analysis.totalRows)
        assertEquals(1, analysis.validRows.size)
        assertEquals(2, analysis.errors.size)
        assertFalse(analysis.canImport)

        assertTrue(analysis.errors.any { it.line == 3 && it.column == "Sub-Category 1" && it.message.contains("Saut de niveau interdit") })
        assertTrue(analysis.errors.any { it.line == 4 && it.column == "Sub-Category 2" && it.message.contains("Saut de niveau interdit") })
    }

    @Test
    fun `duplicate product id within CSV is rejected`() {
        val dir = Files.createTempDirectory("catalog-dup-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "DUP-01,Main,,,,,,,,Item 1,,,Pièce,10,10%,\n" +
                "DUP-01,Main,,,,,,,,Item 2,,,Pièce,12,10%,\n"
        )

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertEquals(2, analysis.totalRows)
        assertEquals(1, analysis.validRows.size)
        assertEquals(1, analysis.errors.size)
        assertFalse(analysis.canImport)
        assertTrue(analysis.errors.any { it.line == 3 && it.column == "Product ID" && it.message.contains("en double") })
    }

    @Test
    fun `official template generator produces compliant CSV`() {
        val template = CsvImports.generateOfficialTemplate()
        val dir = Files.createTempDirectory("template-test")
        val csv = dir.resolve("template.csv")
        Files.writeString(csv, template, Charsets.UTF_8)

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertTrue(analysis.totalRows >= 4)
        assertEquals(analysis.totalRows, analysis.validRows.size)
        assertTrue(analysis.errors.isEmpty())
        assertTrue(analysis.canImport)
    }

    @Test
    fun `web URLs for images do not throw InvalidPathException and are counted in analysis`() {
        val dir = Files.createTempDirectory("catalog-web-url-test")
        val csv = dir.resolve("catalog.csv")
        Files.writeString(
            csv,
            header + "\n" +
                "URL-01,Boulangerie,https://images.unsplash.com/photo-1509440159596?w=800,Viennoiserie,https://images.unsplash.com/photo-1555507?w=800,,,,,Croissant,كرواسون,https://images.unsplash.com/photo-1608198?w=800,Pièce,6.50,10%,Croissant beurre\n"
        )

        val analysis = CsvImports.analyzeCatalogCsv(csv)
        assertEquals(1, analysis.totalRows)
        assertEquals(1, analysis.validRows.size)
        assertTrue(analysis.errors.isEmpty())
        assertTrue(analysis.canImport)
        assertEquals(3, analysis.imagesFoundCount)
    }
}
