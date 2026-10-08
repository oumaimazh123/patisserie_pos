package ma.elaroui.pos.desktop.print

import ma.elaroui.pos.shared.domain.*
import kotlin.test.*

class ThermalTicketRendererTest {
    private val sampleCompany = ReceiptCompany(
        name = "Café Atlas",
        address = "123 avenue Mohammed V Casablanca",
        phone = "0522001122",
        ice = "123456789012345",
        taxId = "IF-22",
        commercialRegister = "RC-33",
        patente = "PAT-44",
        wifiName = "Atlas Guest",
        wifiCode = "secret123"
    )

    private val order = Order(
        id = 1,
        number = "ORD-001",
        type = OrderType.DINE_IN,
        status = OrderStatus.COMPLETED,
        lines = listOf(
            OrderLine(1, "Café Crème", 1500, 2, 1000),
            OrderLine(2, "Omelette Fromage", 2500, 1, 1000)
        ),
        subtotalCentimes = 5500,
        discountCentimes = 0,
        taxCentimes = 500,
        totalCentimes = 5500,
        tableId = 3,
        registerSessionId = 1,
        cashierId = 1
    )

    @Test
    fun rendersCustomerKitchenAndPreparationForBothPaperWidths() {
        listOf(58, 80).forEach { width ->
            val customer = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.CUSTOMER, width)
            val kitchen = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.KITCHEN, width)
            val prep = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.PREPARATION, width)

            assertTrue(customer.isNotEmpty())
            assertTrue(kitchen.isNotEmpty())
            assertTrue(prep.isNotEmpty())

            // ESC/POS init command 0x1B 0x40 ('ESC @')
            assertContentEquals(byteArrayOf(0x1B, 0x40), customer.take(2).toByteArray())
            assertContentEquals(byteArrayOf(0x1B, 0x40), kitchen.take(2).toByteArray())
            assertContentEquals(byteArrayOf(0x1B, 0x40), prep.take(2).toByteArray())
        }
    }

    @Test
    fun rejectsUnsupportedPaperWidth() {
        assertFails { ThermalTicketRenderer.render(order, sampleCompany, TicketKind.CUSTOMER, 72) }
        assertFails { ThermalTicketRenderer.render(order, sampleCompany, TicketKind.KITCHEN, 45) }
        assertFails { ThermalTicketRenderer.render(order, sampleCompany, TicketKind.PREPARATION, 100) }
    }

    @Test
    fun customerReceiptContainsCompanyTaxAndAddressInFooterWithoutWifiAndWrapsAtPaperWidth() {
        listOf(58 to 32, 80 to 48).forEach { (paper, columns) ->
            val preview = ThermalTicketRenderer.previewText(order, sampleCompany, TicketKind.CUSTOMER, paper)
            assertContains(preview, "Café Atlas")
            assertContains(preview, "ICE: 123456789012345")
            assertContains(preview, "IF: IF-22")
            assertContains(preview, "RC: RC-33")
            assertContains(preview, "Patente: PAT-44")
            assertFalse(preview.contains("Wi-Fi"))
            assertFalse(preview.contains("Code: secret123"))
            assertContains(preview, "TOTAL")
            assertContains(preview, "Casablanca")
            val addressIndex = preview.indexOf("Casablanca")
            val totalIndex = preview.indexOf("TOTAL")
            val footerIndex = preview.indexOf("Merci de votre visite !")
            assertTrue(addressIndex > totalIndex, "Address should appear after TOTAL in footer")
            assertTrue(footerIndex > addressIndex, "Thank you note should appear after address in footer")
            assertTrue(preview.lines().all { it.length <= columns }, "Line exceeds $columns columns: ${preview.lines().maxByOrNull { it.length }}")
        }
    }

    @Test
    fun normalInvoiceAndDuplicateHaveFooterThenFiveFeedsThenSingleFinalCut() {
        val footer = "Merci de votre visite !".toByteArray(Charsets.UTF_8)
        listOf(58, 80).forEach { paper ->
            listOf(false, true).forEach { isReprint ->
                val bytes = ThermalTicketRenderer.render(
                    order,
                    sampleCompany,
                    TicketKind.CUSTOMER,
                    paper,
                    isReprint = isReprint
                )
                val footerIndex = bytes.indexOfSubArray(footer)
                val cutIndexes = bytes.indexesOfSubArray(EscPosCommands.CUT)

                assertTrue(footerIndex >= 0)
                assertEquals(listOf(bytes.size - EscPosCommands.CUT.size), cutIndexes)
                val trailingBytes = bytes.copyOfRange(footerIndex + footer.size, cutIndexes.single())
                assertTrue(trailingBytes.size >= EscPosCommands.RECEIPT_END_FEED_LINES)
            }
        }
    }

    @Test
    fun customerPrintMakesStoreAndProductLinesProminentWhilePricesStayRightAligned() {
        val bytes = ThermalTicketRenderer.render(order, sampleCompany, TicketKind.CUSTOMER, 80)
        val store = FrenchEscPosEncoder.encodeText(sampleCompany.name)
        val product = FrenchEscPosEncoder.encodeText("2 x Café Crème")
        val price = FrenchEscPosEncoder.encodeText("30.00 DH")
        val storeIndex = bytes.indexOfSubArray(store)
        val productIndex = bytes.indexOfSubArray(product)
        val priceIndex = bytes.indexOfSubArray(price)

        assertTrue(storeIndex > bytes.indexOfSubArray(EscPosCommands.ALIGN_CENTER))
        assertTrue(productIndex > storeIndex)
        assertTrue(priceIndex > productIndex)

        val previewLines = ThermalTicketRenderer.previewText(order, sampleCompany, TicketKind.CUSTOMER, 80).lines()
        val nameLine = previewLines.first { it.contains("2 x Café Crème") }
        val calcLine = previewLines.first { it.contains("15.00 DH x 2") }
        assertEquals("2 x Café Crème", nameLine)
        assertTrue(calcLine.startsWith("   15.00 DH x 2"))
        assertTrue(calcLine.endsWith("30.00 DH"))
        assertEquals(48, calcLine.length)
    }

    @Test
    fun kitchenTicketOmitsFiscalCompanyAndPriceInformation() {
        listOf(58, 80).forEach { paper ->
            val preview = ThermalTicketRenderer.previewText(order, sampleCompany, TicketKind.KITCHEN, paper)
            assertContains(preview, "TICKET CUISINE / BAR")
            assertContains(preview, "ORD-001")
            assertContains(preview, "TABLE 3", ignoreCase = true)
            assertContains(preview, "Café Crème")

            // Strict operational isolation: NO fiscal/legal or contact info
            assertFalse(preview.contains("ICE: 123456789012345"))
            assertFalse(preview.contains("IF-22"))
            assertFalse(preview.contains("RC-33"))
            assertFalse(preview.contains("PAT-44"))
            assertFalse(preview.contains("0522001122"))
            assertFalse(preview.contains("123 avenue Mohammed V"))
            assertFalse(preview.contains("secret123"))
            assertFalse(preview.contains("TVA"))
            assertFalse(preview.contains("TOTAL TTC"))
            // NO prices on kitchen tickets
            assertFalse(preview.contains("15.00 DH"))
            assertFalse(preview.contains("55.00 DH"))
        }
    }

    @Test
    fun preparationTicketContainsItemsAndTotalWithoutFiscalLegalInformation() {
        listOf(58, 80).forEach { paper ->
            val preview = ThermalTicketRenderer.previewText(order, sampleCompany, TicketKind.PREPARATION, paper)
            assertContains(preview, "TICKET DE PREPARATION")
            assertContains(preview, "ORD-001")
            assertContains(preview, "Table 3")
            assertContains(preview, "Café Crème")
            assertContains(preview, "55.00 DH")

            // Strict operational isolation: NO fiscal/legal or contact info
            assertFalse(preview.contains("ICE: 123456789012345"))
            assertFalse(preview.contains("IF-22"))
            assertFalse(preview.contains("RC-33"))
            assertFalse(preview.contains("PAT-44"))
            assertFalse(preview.contains("0522001122"))
            assertFalse(preview.contains("123 avenue Mohammed V"))
            assertFalse(preview.contains("secret123"))
            assertFalse(preview.contains("TVA"))
            assertFalse(preview.contains("FACTURE"))
        }
    }

    @Test
    fun testProductLineVerticalSpacingSingleProduct() {
        val singleLineOrder = order.copy(
            lines = listOf(OrderLine(1, "Café Crème", 1500, 1, 1000))
        )
        val preview80 = ThermalTicketRenderer.previewText(singleLineOrder, sampleCompany, TicketKind.CUSTOMER, 80)
        assertContains(preview80, "1 x Café Crème")
        assertContains(preview80, "15.00 DH")
        assertContains(preview80, "Merci de votre visite !")
    }

    @Test
    fun testProductLineVerticalSpacingFiveProducts() {
        val fiveLinesOrder = order.copy(
            lines = (1..5).map { OrderLine(it.toLong(), "Produit $it", 1000L * it, 1, 1000) }
        )
        listOf(58, 80).forEach { width ->
            val preview = ThermalTicketRenderer.previewText(fiveLinesOrder, sampleCompany, TicketKind.CUSTOMER, width)
            (1..5).forEach { i ->
                assertContains(preview, "1 x Produit $i")
            }
            // Check that the preview contains the items with empty lines in between
            val lines = preview.lines()
            val prod1Index = lines.indexOfFirst { it.contains("1 x Produit 1") }
            val prod2Index = lines.indexOfFirst { it.contains("1 x Produit 2") }
            val prod3Index = lines.indexOfFirst { it.contains("1 x Produit 3") }
            val prod4Index = lines.indexOfFirst { it.contains("1 x Produit 4") }
            val prod5Index = lines.indexOfFirst { it.contains("1 x Produit 5") }

            assertEquals(prod1Index + 2, prod2Index, "There should be 1 blank line between product 1 and 2")
            assertEquals(prod2Index + 2, prod3Index, "There should be 1 blank line between product 2 and 3")
            assertEquals(prod3Index + 2, prod4Index, "There should be 1 blank line between product 3 and 4")
            assertEquals(prod4Index + 2, prod5Index, "There should be 1 blank line between product 4 and 5")
            assertTrue(lines[prod1Index + 1].isBlank())
        }
    }

    @Test
    fun testProductLineVerticalSpacingTenPlusProducts() {
        val tenPlusLinesOrder = order.copy(
            lines = (1..12).map { OrderLine(it.toLong(), "Article Spécial $it", 500L * it, 1, 1000) }
        )
        listOf(58, 80).forEach { width ->
            val preview = ThermalTicketRenderer.previewText(tenPlusLinesOrder, sampleCompany, TicketKind.CUSTOMER, width)
            (1..12).forEach { i ->
                assertContains(preview, "1 x Article Spécial $i")
            }
            val lines = preview.lines()
            val prod1Index = lines.indexOfFirst { it.contains("1 x Article Spécial 1") }
            val prod12Index = lines.indexOfFirst { it.contains("1 x Article Spécial 12") }
            assertEquals(prod1Index + 11 * 2, prod12Index)
        }
    }

    @Test
    fun testProductLineQuantityGreaterThanOneAndLongNames() {
        val longNamesOrder = order.copy(
            lines = listOf(
                OrderLine(1, "Plat Spécial Chef Surprise du Jour avec Garniture et Sauce", 8500, 3, 2000),
                OrderLine(2, "Jus d'orange pressé frais grande taille 500ml", 2500, 4, 1000)
            )
        )
        listOf(58 to 32, 80 to 48).forEach { (paper, maxCol) ->
            val preview = ThermalTicketRenderer.previewText(longNamesOrder, sampleCompany, TicketKind.CUSTOMER, paper)
            assertContains(preview, "3 x Plat")
            assertContains(preview, "255.00 DH")
            assertContains(preview, "4 x Jus")
            assertContains(preview, "100.00 DH")

            val lines = preview.lines()
            assertTrue(lines.all { it.length <= maxCol })
            val line1 = lines.first { it.contains("85.00 DH x 3") }
            val line2 = lines.first { it.contains("25.00 DH x 4") }
            assertTrue(line1.endsWith("255.00 DH"))
            assertTrue(line2.endsWith("100.00 DH"))
        }
    }

    @Test
    fun testReceiptEstablishmentNameToggleOnAndOff() {
        val companyWithToggleOn = sampleCompany.copy(printEstablishmentName = true)
        val companyWithToggleOff = sampleCompany.copy(printEstablishmentName = false)

        listOf(58, 80).forEach { width ->
            val previewOn = ThermalTicketRenderer.previewText(order, companyWithToggleOn, TicketKind.CUSTOMER, width)
            val previewOff = ThermalTicketRenderer.previewText(order, companyWithToggleOff, TicketKind.CUSTOMER, width)

            // ON: Must contain establishment name
            assertContains(previewOn, "Café Atlas")

            // OFF: Must NOT contain establishment name, but MUST contain address and other legal details
            assertFalse(previewOff.contains("Café Atlas"))
            assertContains(previewOff, "123 avenue Mohammed V")
            assertContains(previewOff, "Tél: 0522001122")
            assertContains(previewOff, "ICE: 123456789012345")
            assertContains(previewOff, "Merci de votre visite !")

            // First line of previewOff must be the phone (since name is off, specialty empty, and address in footer)
            val firstLineOff = previewOff.lines().first()
            assertTrue(firstLineOff.isNotBlank())
            assertTrue(firstLineOff.contains("0522001122"))
            val addressIndex = previewOff.indexOf("123 avenue Mohammed V")
            val footerIndex = previewOff.indexOf("Merci de votre visite !")
            assertTrue(addressIndex > 0)
            assertTrue(footerIndex > addressIndex)
        }
    }

    @Test
    fun testReceiptHeaderWithSpecialtyDisplaysDirectlyUnderName() {
        val companyWithSpecialty = sampleCompany.copy(
            name = "Boulangerie & Pâtisserie Royale",
            specialty = "Boulangerie Pâtisserie Fine & Salon de Thé",
            address = "75 Boulevard Zerktouni Casablanca",
            phone = "0522334455",
            printEstablishmentName = true
        )

        listOf(58 to 32, 80 to 48).forEach { (paperWidth, colWidth) ->
            val preview = ThermalTicketRenderer.previewText(order, companyWithSpecialty, TicketKind.CUSTOMER, paperWidth)
            val lines = preview.lines()

            // Header order verification:
            // Line 0: Establishment Name
            val nameLine = lines[0].trim()
            assertTrue(nameLine.contains("Boulangerie") || nameLine.contains("Pâtisserie Royale"))

            // Line 1: Specialty directly under the establishment name
            val specialtyLine = lines[1].trim()
            assertTrue(specialtyLine.contains("Boulangerie") || specialtyLine.contains("Pâtisserie Fine"))

            val addressIndex = lines.indexOfFirst { it.contains("75 Boulevard Zerktouni") }
            val phoneIndex = lines.indexOfFirst { it.contains("0522334455") }
            val totalIndex = lines.indexOfFirst { it.contains("TOTAL") }
            val footerIndex = lines.indexOfFirst { it.contains("Merci de votre visite !") }

            assertTrue(phoneIndex > 1, "Phone must appear in header after specialty")
            assertTrue(addressIndex > totalIndex, "Address must appear after total in footer")
            assertTrue(footerIndex > addressIndex, "Thank you note must appear after address in footer")

            // Verify binary styling: Name gets DOUBLE_HEIGHT and BOLD, Specialty gets NORMAL_SIZE
            val bytes = ThermalTicketRenderer.render(order, companyWithSpecialty, TicketKind.CUSTOMER, paperWidth)
            assertTrue(bytes.isNotEmpty())

            val nameBytes = FrenchEscPosEncoder.encodeText("Boulangerie")
            val nameIdx = bytes.indexOfSubArray(nameBytes)
            assertTrue(nameIdx > 0, "Establishment name must be in binary output")

            val boldBeforeName = bytes.lastIndexOfSubArray(EscPosCommands.BOLD_ON, nameIdx)
            val doubleHeightBeforeName = bytes.lastIndexOfSubArray(EscPosCommands.DOUBLE_HEIGHT, nameIdx)
            assertTrue(boldBeforeName >= 0, "Establishment name must be preceded by BOLD_ON")
            assertTrue(doubleHeightBeforeName >= 0, "Establishment name must be preceded by DOUBLE_HEIGHT")
        }

        // Test without specialty: No blank line inserted
        val companyWithoutSpecialty = companyWithSpecialty.copy(specialty = "")
        val previewNoSpec = ThermalTicketRenderer.previewText(order, companyWithoutSpecialty, TicketKind.CUSTOMER, 80)
        val linesNoSpec = previewNoSpec.lines()
        assertEquals("Boulangerie & Pâtisserie Royale", linesNoSpec[0].trim())
        assertEquals("Tél: 0522334455", linesNoSpec[1].trim())
        assertFalse(linesNoSpec[1].isBlank(), "No blank line should exist when specialty is empty")
        assertTrue(previewNoSpec.contains("75 Boulevard Zerktouni Casablanca"), "Address must be present in footer")
    }

    @Test
    fun testQuantityBasedLineItemFormattingOn58And80mm() {
        val testOrder = Order(
            id = 99L,
            number = "CMD-0099",
            type = OrderType.COUNTER,
            status = OrderStatus.COMPLETED,
            lines = listOf(
                OrderLine(1L, "Chebakia Miel & Sésame", 7000L, 1, 1000),
                OrderLine(2L, "Ghriba aux Amandes", 6000L, 1, 1000),
                OrderLine(3L, "Cornes de Gazelle", 8500L, 3, 1000),
                OrderLine(4L, "Café Espresso Spécial", 1250L, 10, 1000),
                OrderLine(5L, "Thé à la Menthe Fraîche Traditionnel Marocain Format Grand Verre", 1500L, 2, 1000)
            ),
            subtotalCentimes = 54000L,
            discountCentimes = 0L,
            taxCentimes = 4909L,
            totalCentimes = 54000L,
            tableId = null,
            registerSessionId = 1L,
            cashierId = 1L
        )

        listOf(58 to 32, 80 to 48).forEach { (paperWidth, colWidth) ->
            val preview = ThermalTicketRenderer.previewText(testOrder, sampleCompany, TicketKind.CUSTOMER, paperWidth)
            val lines = preview.lines()

            // 1. Quantity = 1: Single line containing both name and total
            val chebakiaLine = lines.first { it.contains("1 x Chebakia") }
            assertTrue(chebakiaLine.startsWith("1 x Chebakia"))
            assertTrue(chebakiaLine.endsWith("70.00 DH"))
            assertEquals(colWidth, chebakiaLine.length)

            val ghribaLine = lines.first { it.contains("1 x Ghriba aux Amandes") }
            assertTrue(ghribaLine.startsWith("1 x Ghriba"))
            assertTrue(ghribaLine.endsWith("60.00 DH"))
            assertEquals(colWidth, ghribaLine.length)

            // 2. Quantity = 3 (Cornes de Gazelle): 2 lines
            val cornesNameLine = lines.first { it.contains("3 x Cornes de Gazelle") }
            val cornesCalcLine = lines.first { it.contains("85.00 DH x 3") }
            assertEquals("3 x Cornes de Gazelle", cornesNameLine.trim())
            assertTrue(cornesCalcLine.startsWith("   85.00 DH x 3"))
            assertTrue(cornesCalcLine.endsWith("255.00 DH"))
            assertEquals(colWidth, cornesCalcLine.length)

            // 3. Quantity = 10 (Café Espresso): 2 lines with decimal unit price
            val cafeNameLine = lines.first { it.contains("10 x Café Espresso") }
            val cafeCalcLine = lines.first { it.contains("12.50 DH x 10") }
            assertEquals("10 x Café Espresso Spécial", cafeNameLine.trim())
            assertTrue(cafeCalcLine.startsWith("   12.50 DH x 10"))
            assertTrue(cafeCalcLine.endsWith("125.00 DH"))
            assertEquals(colWidth, cafeCalcLine.length)

            // 4. Long name with quantity = 2
            val theCalcLine = lines.first { it.contains("15.00 DH x 2") }
            assertTrue(theCalcLine.startsWith("   15.00 DH x 2"))
            assertTrue(theCalcLine.endsWith("30.00 DH"))
            assertEquals(colWidth, theCalcLine.length)

            // 5. Total must be correct
            val totalLine = lines.first { it.startsWith("TOTAL ") && it.endsWith("540.00 DH") }
            assertEquals(colWidth, totalLine.length)
        }
    }

    private fun ByteArray.indexOfSubArray(value: ByteArray): Int = indices.firstOrNull { index ->
        index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
    } ?: -1

    private fun ByteArray.indexesOfSubArray(value: ByteArray): List<Int> = indices.filter { index ->
        index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
    }

    private fun ByteArray.lastIndexOfSubArray(value: ByteArray, before: Int): Int =
        (0 until before).lastOrNull { index ->
            index + value.size <= size && copyOfRange(index, index + value.size).contentEquals(value)
        } ?: -1
}
