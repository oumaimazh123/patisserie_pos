package ma.elaroui.pos.desktop.print

object EscPosCommands {
    val INITIALIZE = byteArrayOf(0x1B, 0x40)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    val DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x10)
    val DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    val SELECT_CP858 = byteArrayOf(0x1B, 0x74, 0x13)
    val CANCEL_CHINESE_MODE = byteArrayOf(0x1C, 0x2E)
    val INIT_CP858 = INITIALIZE + CANCEL_CHINESE_MODE + SELECT_CP858
    const val RECEIPT_END_FEED_LINES = 7
    val CUT = byteArrayOf(0x1D, 0x56, 0x00)
    val FEED_AND_CUT = lineFeeds(RECEIPT_END_FEED_LINES) + CUT

    fun lineFeeds(count: Int = 1): ByteArray = ByteArray(count.coerceAtLeast(0)) { 0x0A }

    /** ESC p m t1 t2: common printer-connected cash drawer pulse on pin 2. */
    fun cashDrawerPulse(pin: Int = 0, onTime: Int = 50, offTime: Int = 250): ByteArray = byteArrayOf(
        0x1B,
        0x70,
        pin.coerceIn(0, 1).toByte(),
        (onTime / 2).coerceIn(0, 255).toByte(),
        (offTime / 2).coerceIn(0, 255).toByte()
    )
}
