package ma.elaroui.pos.shared.display

import kotlin.test.*

class VfdFormattingAndProtocolTest {

    private val defaultConfig = CustomerDisplayConfig(
        columns = 20,
        rows = 2,
        welcomeLine1 = "BIENVENUE",
        welcomeLine2 = "HYPER CAISSE",
        thankYouLine1 = "MERCI POUR VOTRE",
        thankYouLine2 = "VISITE"
    )

    // Test 28: Maximum line length
    @Test
    fun testMaximumLineLengthNeverExceeded() {
        val longLabel = "TRES TRES LONG PRODUIT AVEC NOM DEPASSANT VINGT CARACTERES"
        val formatted = VfdMessageFormatter.formatKeyValue(longLabel, "150.00", 20)
        assertEquals(20, formatted.length)
        assertTrue(formatted.endsWith("150.00"))

        val line = VfdMessageFormatter.fitLine("TEXTE TROP LONG QUI NE DEVRAIT JAMAIS DEPASSER LA LONGUEUR MAXIMALE", 20)
        assertEquals(20, line.length)
    }

    // Test 29: Two-line formatting
    @Test
    fun testTwoLineFormattingExactWidth() {
        val state = CustomerDisplayState.Idle("BIENVENUE", "HYPER CAISSE")
        val result = VfdMessageFormatter.format(state, defaultConfig)
        assertEquals(20, result.line1.length)
        assertEquals(20, result.line2.length)
        assertEquals("BIENVENUE".padEnd(20, ' '), result.line1)
        assertEquals("HYPER CAISSE".padEnd(20, ' '), result.line2)
    }

    // Test 30: Price alignment (label left, amount right)
    @Test
    fun testPriceAlignmentRightAligned() {
        val formatted = VfdMessageFormatter.formatKeyValue("TOTAL", "125.00", 20)
        assertEquals(20, formatted.length)
        assertTrue(formatted.startsWith("TOTAL"))
        assertTrue(formatted.endsWith("125.00"))
        assertEquals("TOTAL         125.00", formatted)

        val dueFormatted = VfdMessageFormatter.formatKeyValue("A PAYER", "125.00", 20)
        assertEquals("A PAYER       125.00", dueFormatted)
    }

    // Test 31: Decimal formatting
    @Test
    fun testDecimalFormattingWithMoneyRules() {
        val state = CustomerDisplayState.DuringSale(totalCentimes = 12550L, dueCentimes = 12550L)
        val formatted = VfdMessageFormatter.format(state, defaultConfig)
        assertTrue(formatted.line1.endsWith("125.50"))
        assertTrue(formatted.line2.endsWith("125.50"))
    }

    // Test 32: Unsupported characters safely replaced
    @Test
    fun testUnsupportedCharactersSafelyHandled() {
        val input = "Special \u0000 \u0007 \u001F chars"
        val sanitized = VfdMessageFormatter.transliterateAccents(input)
        assertFalse(sanitized.contains('\u0000'))
        assertFalse(sanitized.contains('\u0007'))
        assertFalse(sanitized.contains('\u001F'))
    }

    // Test 33: French accents transliterated
    @Test
    fun testFrenchAccentsTransliterated() {
        val accented = "Café crème thé à emporter réglage pâte"
        val clean = VfdMessageFormatter.transliterateAccents(accented)
        assertEquals("Cafe creme the a emporter reglage pate", clean)

        val upperAccented = "ÉTABLISSEMENT ÂGÉ ÇA"
        val upperClean = VfdMessageFormatter.transliterateAccents(upperAccented)
        assertEquals("ETABLISSEMENT AGE CA", upperClean)
    }

    // Test 34: Large monetary amounts
    @Test
    fun testLargeMonetaryAmountsFormattedSafely() {
        val state = CustomerDisplayState.DuringSale(totalCentimes = 1_000_000_00L) // 1,000,000.00
        val formatted = VfdMessageFormatter.format(state, defaultConfig)
        assertEquals(20, formatted.line1.length)
        assertTrue(formatted.line1.endsWith("1000000.00"))
    }

    // Test 35: Clearing previous display content
    @Test
    fun testShortStringPadsSpacesToClearPreviousDisplay() {
        val shortText = "OK"
        val fitted = VfdMessageFormatter.fitLine(shortText, 20)
        assertEquals(20, fitted.length)
        assertEquals("OK                  ", fitted)
    }

    // Driver tests for all supported protocols
    @Test
    fun testEscPosDriverByteSequence() {
        val driver = VfdDriverRegistry.getDriver(VfdProtocol.ESC_POS)
        val lines = FormattedDisplayLines("TOTAL         125.00", "A PAYER       125.00")
        val bytes = driver.writeLines(lines, 20)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x0C, bytes[0].toInt()) // CLR
    }

    @Test
    fun testCd5220DriverByteSequence() {
        val driver = VfdDriverRegistry.getDriver(VfdProtocol.CD5220)
        val lines = FormattedDisplayLines("TOTAL         125.00", "A PAYER       125.00")
        val bytes = driver.writeLines(lines, 20)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x0C, bytes[0].toInt()) // CLR
    }

    @Test
    fun testDsp800DriverByteSequence() {
        val driver = VfdDriverRegistry.getDriver(VfdProtocol.DSP800)
        val lines = FormattedDisplayLines("TOTAL         125.00", "A PAYER       125.00")
        val bytes = driver.writeLines(lines, 20)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testUtcStandardDriverByteSequence() {
        val driver = VfdDriverRegistry.getDriver(VfdProtocol.UTC_STANDARD)
        val lines = FormattedDisplayLines("TOTAL         125.00", "A PAYER       125.00")
        val bytes = driver.writeLines(lines, 20)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testPlainTextDriverByteSequence() {
        val driver = VfdDriverRegistry.getDriver(VfdProtocol.PLAIN_TEXT)
        val lines = FormattedDisplayLines("TOTAL         125.00", "A PAYER       125.00")
        val bytes = driver.writeLines(lines, 20)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun testTestMessageFormattingAndLengthLimits() {
        val line1 = VfdMessageFormatter.fitLine(CustomerDisplayDefaults.TEST_LINE_1, 20)
        val line2 = VfdMessageFormatter.fitLine(CustomerDisplayDefaults.TEST_LINE_2, 20)

        assertEquals(20, line1.length)
        assertEquals(20, line2.length)
        assertEquals("AURA CAISSE         ", line1)
        assertEquals("BY ZAKARIA EL EAROUI", line2)
    }

    @Test
    fun testAllDriversTestMessageByteSequenceClearsDisplay() {
        VfdProtocol.entries.forEach { proto ->
            val driver = VfdDriverRegistry.getDriver(proto)
            val bytes = driver.testMessage(20)
            assertTrue(bytes.isNotEmpty(), "Driver for $proto returned empty test bytes")
            val asString = bytes.map { if (it.toInt().toChar().isLetterOrDigit() || it.toInt().toChar() == ' ') it.toInt().toChar() else ' ' }.joinToString("")
            assertTrue(asString.contains("AURA CAISSE"), "Driver for $proto missing line 1 in output: $asString")
            assertTrue(asString.contains("BY ZAKARIA EL EAROUI"), "Driver for $proto missing line 2 in output: $asString")
        }
    }
}
