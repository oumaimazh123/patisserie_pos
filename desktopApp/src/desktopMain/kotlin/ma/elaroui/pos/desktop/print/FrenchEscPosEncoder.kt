package ma.elaroui.pos.desktop.print

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Centralized French ESC/POS encoder.
 *
 * Requirements for thermal receipt printers (including Rongta RP330 over Ethernet/USB/CUPS):
 * 1. Character encoding: CP858 (IBM00858), an extension of PC850 (Latin-1) containing all French accented
 *    characters (é, è, ê, à, â, ç, î, ï, ô, ù, û, ë, ü, etc.), uppercase accented characters (É, À, Ç, etc.),
 *    and the Euro currency symbol (€ at 0xD5).
 * 2. Code page selection: ESC t 19 (0x1B, 0x74, 0x13) to select CP858 code table on the printer hardware.
 * 3. Cancel Chinese/CJK mode: FS . (0x1C, 0x2E) to cancel double-byte Chinese character mode so that
 *    bytes with MSB set (>= 0x80) are parsed as single-byte CP858 characters instead of Chinese glyphs.
 */
object FrenchEscPosEncoder {

    val CHARSET: Charset = runCatching { Charset.forName("CP858") }
        .getOrElse { Charset.forName("IBM00858") }

    /**
     * ESC t 19 (0x1B, 0x74, 0x13) selects CP858 on ESC/POS standard and Rongta RP330 printers.
     */
    val SELECT_CP858_CODE_PAGE: ByteArray = byteArrayOf(0x1B, 0x74, 0x13)

    /**
     * FS . (0x1C, 0x2E) cancels Chinese/CJK mode on Rongta and compatible POS thermal printers.
     */
    val CANCEL_CHINESE_MODE: ByteArray = byteArrayOf(0x1C, 0x2E)

    /**
     * Complete printer initialization prefix for French receipt printing.
     */
    val INIT_COMMANDS: ByteArray = EscPosCommands.INITIALIZE + CANCEL_CHINESE_MODE + SELECT_CP858_CODE_PAGE

    /**
     * Encodes raw text into CP858 byte array preserving all French accents.
     */
    fun encodeText(text: String): ByteArray = text.toByteArray(CHARSET)

    /**
     * Checks whether the given text can be safely encoded in CP858.
     */
    fun canEncode(text: String): Boolean = runCatching {
        val encoder = CHARSET.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        encoder.canEncode(text)
    }.getOrDefault(false)

    /**
     * Builds a full ticket document with French ESC/POS header and feed/cut trailer.
     */
    fun buildDocument(
        contentBytes: ByteArray,
        logoBytes: ByteArray = ByteArray(0),
        feedAndCut: Boolean = true
    ): ByteArray {
        val header = INIT_COMMANDS + logoBytes
        val footer = if (feedAndCut) EscPosCommands.FEED_AND_CUT else ByteArray(0)
        return header + contentBytes + footer
    }
}
