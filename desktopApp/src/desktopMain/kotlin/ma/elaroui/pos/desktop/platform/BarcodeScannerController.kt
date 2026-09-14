package ma.elaroui.pos.desktop.platform

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Controller to buffer and process hardware USB / HID barcode scanner inputs.
 * USB scanners act like high-speed keyboards and terminate scans with ENTER or TAB.
 */
class BarcodeScannerController(
    private val timeoutMs: Long = 800L,
    private val onBarcodeScanned: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private var lastKeyTimestampMs: Long = 0L

    /**
     * Feed a KeyEvent into the controller.
     * Returns true if the event was consumed as part of a barcode scan.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        val now = System.currentTimeMillis()
        if (now - lastKeyTimestampMs > timeoutMs && buffer.isNotEmpty()) {
            buffer.clear()
        }
        lastKeyTimestampMs = now

        val key = event.key
        if (key == Key.Enter || key == Key.NumPadEnter || key == Key.Tab) {
            if (buffer.isNotEmpty()) {
                val rawBarcode = buffer.toString().trim()
                buffer.clear()
                if (rawBarcode.isNotBlank()) {
                    onBarcodeScanned(rawBarcode)
                    return true
                }
            }
            return false
        }

        val codePoint = event.utf16CodePoint
        if (codePoint > 0) {
            val chars = Character.toChars(codePoint)
            if (chars.isNotEmpty() && !chars[0].isISOControl()) {
                buffer.append(chars)
                return false
            }
        }

        return false
    }

    /** Directly feeds a character (useful for tests, direct serial/keyboard streams). */
    fun feedChar(c: Char): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastKeyTimestampMs > timeoutMs && buffer.isNotEmpty()) {
            buffer.clear()
        }
        lastKeyTimestampMs = now

        if (c == '\n' || c == '\r' || c == '\t') {
            if (buffer.isNotEmpty()) {
                val rawBarcode = buffer.toString().trim()
                buffer.clear()
                if (rawBarcode.isNotBlank()) {
                    onBarcodeScanned(rawBarcode)
                    return true
                }
            }
            return false
        }

        if (!c.isISOControl()) {
            buffer.append(c)
        }
        return false
    }

    /** Directly feeds a full raw barcode string with a terminator character. */
    fun feedRaw(input: String, terminator: Char = '\n') {
        for (c in input) {
            feedChar(c)
        }
        feedChar(terminator)
    }

    fun clearBuffer() {
        buffer.clear()
        lastKeyTimestampMs = 0L
    }

    val currentBufferContent: String
        get() = buffer.toString()
}
