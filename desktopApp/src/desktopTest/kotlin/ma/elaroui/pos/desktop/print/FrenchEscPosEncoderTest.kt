package ma.elaroui.pos.desktop.print

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrenchEscPosEncoderTest {

    @Test
    fun verifyEscPosCommandsForRongtaRp330() {
        // ESC @ (Initialize)
        assertContentEquals(byteArrayOf(0x1B, 0x40), EscPosCommands.INITIALIZE)
        // FS . (Cancel Chinese/CJK character mode)
        assertContentEquals(byteArrayOf(0x1C, 0x2E), FrenchEscPosEncoder.CANCEL_CHINESE_MODE)
        // ESC t 19 (Select CP858 Euro code table)
        assertContentEquals(byteArrayOf(0x1B, 0x74, 0x13), FrenchEscPosEncoder.SELECT_CP858_CODE_PAGE)
        // Combined INIT sequence
        val expectedInit = byteArrayOf(0x1B, 0x40, 0x1C, 0x2E, 0x1B, 0x74, 0x13)
        assertContentEquals(expectedInit, FrenchEscPosEncoder.INIT_COMMANDS)
    }

    @Test
    fun verifyFrenchAccentedCharactersCP858ByteMapping() {
        val mapping = mapOf(
            "é" to byteArrayOf(0x82.toByte()),
            "è" to byteArrayOf(0x8A.toByte()),
            "ê" to byteArrayOf(0x88.toByte()),
            "à" to byteArrayOf(0x85.toByte()),
            "â" to byteArrayOf(0x83.toByte()),
            "ç" to byteArrayOf(0x87.toByte()),
            "î" to byteArrayOf(0x8C.toByte()),
            "ï" to byteArrayOf(0x8B.toByte()),
            "ô" to byteArrayOf(0x93.toByte()),
            "ù" to byteArrayOf(0x97.toByte()),
            "û" to byteArrayOf(0x96.toByte()),
            "É" to byteArrayOf(0x90.toByte()),
            "€" to byteArrayOf(0xD5.toByte())
        )

        for ((char, expectedBytes) in mapping) {
            val encoded = FrenchEscPosEncoder.encodeText(char)
            assertContentEquals(expectedBytes, encoded, "Failed encoding for '$char'")
        }
    }

    @Test
    fun verifyExactFifteenRequiredFrenchStrings() {
        val testStrings = listOf(
            "Café",
            "Café crème",
            "Thé",
            "Thé à la menthe",
            "Pâtisserie",
            "Déjeuner",
            "À emporter",
            "Espèces",
            "Réduction",
            "Quantité",
            "Numéro",
            "Déjà payé",
            "Merci pour votre visite",
            "À bientôt",
            "10,00 €"
        )

        for (str in testStrings) {
            assertTrue(FrenchEscPosEncoder.canEncode(str), "String '$str' must be encodable in CP858")
            val bytes = FrenchEscPosEncoder.encodeText(str)
            assertFalse(bytes.contains(0x3F.toByte()), "String '$str' must not produce '?' replacement character")
            // Re-decode from CP858 to verify complete fidelity
            val decoded = String(bytes, FrenchEscPosEncoder.CHARSET)
            assertEquals(str, decoded, "Decoded string must match original for '$str'")
        }
    }

    @Test
    fun verifySpecificSentenceByteEncodings() {
        // "Café" -> C a f é
        assertContentEquals(
            byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0x82.toByte()),
            FrenchEscPosEncoder.encodeText("Café")
        )

        // "10,00 €" -> 1 0 , 0 0 [space] €(0xD5)
        assertContentEquals(
            byteArrayOf('1'.code.toByte(), '0'.code.toByte(), ','.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), ' '.code.toByte(), 0xD5.toByte()),
            FrenchEscPosEncoder.encodeText("10,00 €")
        )
    }

    @Test
    fun verifyFrenchTestTicketBuilding() {
        val content = "TEST FRANCAIS\nCafé crème\n10,00 €"
        val payload = FrenchEscPosEncoder.buildDocument(
            contentBytes = FrenchEscPosEncoder.encodeText(content),
            feedAndCut = true
        )

        assertTrue(payload.startsWith(FrenchEscPosEncoder.INIT_COMMANDS))
        assertTrue(payload.endsWith(EscPosCommands.FEED_AND_CUT))
        val rawContent = payload.copyOfRange(FrenchEscPosEncoder.INIT_COMMANDS.size, payload.size - EscPosCommands.FEED_AND_CUT.size)
        assertEquals(content, String(rawContent, FrenchEscPosEncoder.CHARSET))
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (size < suffix.size) return false
        val offset = size - suffix.size
        for (i in suffix.indices) {
            if (this[offset + i] != suffix[i]) return false
        }
        return true
    }
}
