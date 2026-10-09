package ma.elaroui.pos.shared.display

interface VfdProtocolDriver {
    val protocol: VfdProtocol
    fun initDisplay(): ByteArray
    fun clearDisplay(): ByteArray
    fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray
    fun testMessage(columns: Int): ByteArray
}

class EscPosVfdDriver : VfdProtocolDriver {
    override val protocol: VfdProtocol = VfdProtocol.ESC_POS

    override fun initDisplay(): ByteArray = byteArrayOf(0x1B, 0x40) // ESC @

    override fun clearDisplay(): ByteArray = byteArrayOf(0x0C) // CLR (Form Feed)

    override fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray {
        val out = mutableListOf<Byte>()
        // Initialize & clear
        out.addAll(clearDisplay().toList())

        // Line 1: US $ 1 1
        out.addAll(listOf(0x1F.toByte(), 0x24.toByte(), 0x01.toByte(), 0x01.toByte()))
        lines.line1.take(columns).forEach { out.add(it.code.toByte()) }

        // Line 2: US $ 1 2
        out.addAll(listOf(0x1F.toByte(), 0x24.toByte(), 0x01.toByte(), 0x02.toByte()))
        lines.line2.take(columns).forEach { out.add(it.code.toByte()) }

        return out.toByteArray()
    }

    override fun testMessage(columns: Int): ByteArray {
        val l1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, columns)
        val l2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, columns)
        return writeLines(FormattedDisplayLines(l1, l2), columns)
    }
}

class Cd5220VfdDriver : VfdProtocolDriver {
    override val protocol: VfdProtocol = VfdProtocol.CD5220

    override fun initDisplay(): ByteArray = byteArrayOf(0x1B, 0x40) // ESC @

    override fun clearDisplay(): ByteArray = byteArrayOf(0x0C) // CLR

    override fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(clearDisplay().toList())

        // Move cursor line 1 col 1: ESC l 1 1
        out.addAll(listOf(0x1B.toByte(), 0x6C.toByte(), 0x01.toByte(), 0x01.toByte()))
        lines.line1.take(columns).forEach { out.add(it.code.toByte()) }

        // Move cursor line 2 col 1: ESC l 1 2
        out.addAll(listOf(0x1B.toByte(), 0x6C.toByte(), 0x01.toByte(), 0x02.toByte()))
        lines.line2.take(columns).forEach { out.add(it.code.toByte()) }

        return out.toByteArray()
    }

    override fun testMessage(columns: Int): ByteArray {
        val l1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, columns)
        val l2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, columns)
        return writeLines(FormattedDisplayLines(l1, l2), columns)
    }
}

class Dsp800VfdDriver : VfdProtocolDriver {
    override val protocol: VfdProtocol = VfdProtocol.DSP800

    override fun initDisplay(): ByteArray = byteArrayOf(0x04, 0x01, 0x49, 0x03) // EOT SOH 'I' ETX

    override fun clearDisplay(): ByteArray = byteArrayOf(0x04, 0x01, 0x43, 0x03) // EOT SOH 'C' ETX

    override fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(clearDisplay().toList())

        // Line 1: EOT SOH 'A' ... ETX
        out.addAll(listOf(0x04.toByte(), 0x01.toByte(), 0x41.toByte()))
        lines.line1.take(columns).forEach { out.add(it.code.toByte()) }
        out.add(0x03.toByte())

        // Line 2: EOT SOH 'B' ... ETX
        out.addAll(listOf(0x04.toByte(), 0x01.toByte(), 0x42.toByte()))
        lines.line2.take(columns).forEach { out.add(it.code.toByte()) }
        out.add(0x03.toByte())

        return out.toByteArray()
    }

    override fun testMessage(columns: Int): ByteArray {
        val l1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, columns)
        val l2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, columns)
        return writeLines(FormattedDisplayLines(l1, l2), columns)
    }
}

class UtcStandardVfdDriver : VfdProtocolDriver {
    override val protocol: VfdProtocol = VfdProtocol.UTC_STANDARD

    override fun initDisplay(): ByteArray = byteArrayOf(0x1B, 0x74, 0x00) // ESC t 0

    override fun clearDisplay(): ByteArray = byteArrayOf(0x0C) // FF

    override fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(clearDisplay().toList())

        // Move to line 1
        out.addAll(listOf(0x1F.toByte(), 0x01.toByte(), 0x01.toByte()))
        lines.line1.take(columns).forEach { out.add(it.code.toByte()) }
        out.add(0x0D.toByte())

        // Move to line 2
        out.addAll(listOf(0x1F.toByte(), 0x01.toByte(), 0x02.toByte()))
        lines.line2.take(columns).forEach { out.add(it.code.toByte()) }
        out.add(0x0D.toByte())

        return out.toByteArray()
    }

    override fun testMessage(columns: Int): ByteArray {
        val l1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, columns)
        val l2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, columns)
        return writeLines(FormattedDisplayLines(l1, l2), columns)
    }
}

class PlainTextVfdDriver : VfdProtocolDriver {
    override val protocol: VfdProtocol = VfdProtocol.PLAIN_TEXT

    override fun initDisplay(): ByteArray = byteArrayOf(0x0D, 0x0A)

    override fun clearDisplay(): ByteArray = byteArrayOf(0x0C)

    override fun writeLines(lines: FormattedDisplayLines, columns: Int): ByteArray {
        val out = mutableListOf<Byte>()
        out.addAll(clearDisplay().toList())

        lines.line1.take(columns).forEach { out.add(it.code.toByte()) }
        out.addAll(listOf(0x0D.toByte(), 0x0A.toByte()))
        lines.line2.take(columns).forEach { out.add(it.code.toByte()) }
        out.addAll(listOf(0x0D.toByte(), 0x0A.toByte()))

        return out.toByteArray()
    }

    override fun testMessage(columns: Int): ByteArray {
        val l1 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_1, columns)
        val l2 = VfdMessageFormatter.centerLine(CustomerDisplayDefaults.TEST_LINE_2, columns)
        return writeLines(FormattedDisplayLines(l1, l2), columns)
    }
}

object VfdDriverRegistry {
    private val drivers = mapOf(
        VfdProtocol.ESC_POS to EscPosVfdDriver(),
        VfdProtocol.CD5220 to Cd5220VfdDriver(),
        VfdProtocol.DSP800 to Dsp800VfdDriver(),
        VfdProtocol.UTC_STANDARD to UtcStandardVfdDriver(),
        VfdProtocol.PLAIN_TEXT to PlainTextVfdDriver()
    )

    fun getDriver(protocol: VfdProtocol): VfdProtocolDriver =
        drivers[protocol] ?: EscPosVfdDriver()
}
