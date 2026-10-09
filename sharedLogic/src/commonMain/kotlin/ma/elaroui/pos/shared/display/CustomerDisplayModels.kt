package ma.elaroui.pos.shared.display

enum class VfdProtocol(val displayName: String) {
    ESC_POS("ESC/POS"),
    CD5220("CD5220 / Partner Tech"),
    DSP800("DSP800 / Posiflex"),
    UTC_STANDARD("UTC Standard / Enhanced"),
    PLAIN_TEXT("Plain Text / ASCII");

    companion object {
        fun fromName(name: String?): VfdProtocol =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ESC_POS
    }
}

enum class VfdConnectionMode(val displayName: String) {
    SERIAL("Série (COM / RS-232)"),
    USB("USB");

    companion object {
        fun fromName(name: String?): VfdConnectionMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SERIAL
    }
}

enum class VfdBaudRate(val rate: Int) {
    B2400(2400),
    B4800(4800),
    B9600(9600),
    B19200(19200),
    B38400(38400),
    B57600(57600),
    B115200(115200);

    companion object {
        fun fromInt(rate: Int?): VfdBaudRate =
            entries.firstOrNull { it.rate == rate } ?: B9600
    }
}

enum class VfdDataBits(val bits: Int) {
    SEVEN(7),
    EIGHT(8);

    companion object {
        fun fromInt(bits: Int?): VfdDataBits =
            entries.firstOrNull { it.bits == bits } ?: EIGHT
    }
}

enum class VfdStopBits(val bits: Int) {
    ONE(1),
    TWO(2);

    companion object {
        fun fromInt(bits: Int?): VfdStopBits =
            entries.firstOrNull { it.bits == bits } ?: ONE
    }
}

enum class VfdParity(val displayName: String) {
    NONE("Aucune (None)"),
    EVEN("Paire (Even)"),
    ODD("Impaire (Odd)");

    companion object {
        fun fromName(name: String?): VfdParity =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
    }
}

enum class CustomerDisplayConnectionStatus(val labelFr: String) {
    DISCONNECTED("Déconnecté"),
    CONNECTING("Connexion..."),
    CONNECTED("Connecté"),
    ERROR("Erreur");
}

data class CustomerDisplayConfig(
    val enabled: Boolean = false,
    val connectionMode: VfdConnectionMode = VfdConnectionMode.SERIAL,
    val portName: String = "",
    val protocol: VfdProtocol = VfdProtocol.ESC_POS,
    val baudRate: VfdBaudRate = VfdBaudRate.B9600,
    val dataBits: VfdDataBits = VfdDataBits.EIGHT,
    val stopBits: VfdStopBits = VfdStopBits.ONE,
    val parity: VfdParity = VfdParity.NONE,
    val columns: Int = 20,
    val rows: Int = 2,
    val welcomeLine1: String = "BIENVENUE",
    val welcomeLine2: String = "HYPER CAISSE",
    val thankYouLine1: String = "MERCI POUR VOTRE",
    val thankYouLine2: String = "VISITE",
    val thankYouDurationSeconds: Int = 5
)

sealed interface CustomerDisplayState {
    data class Idle(
        val line1: String = "BIENVENUE",
        val line2: String = "HYPER CAISSE"
    ) : CustomerDisplayState

    data class DuringSale(
        val totalCentimes: Long,
        val dueCentimes: Long = totalCentimes
    ) : CustomerDisplayState

    data class PaymentStarted(
        val totalCentimes: Long,
        val dueCentimes: Long = totalCentimes
    ) : CustomerDisplayState

    data class CashPayment(
        val receivedCentimes: Long,
        val changeCentimes: Long,
        val remainingCentimes: Long = 0L
    ) : CustomerDisplayState

    data class NonCashPayment(
        val methodLabel: String,
        val amountCentimes: Long
    ) : CustomerDisplayState

    data class PaymentCompleted(
        val line1: String = "MERCI POUR VOTRE",
        val line2: String = "VISITE"
    ) : CustomerDisplayState

    data class CustomMessage(
        val line1: String,
        val line2: String
    ) : CustomerDisplayState
}

object CustomerDisplayDefaults {
    const val TEST_LINE_1 = "AURA CAISSE"
    const val TEST_LINE_2 = "BY ZAKARIA EL EAROUI"
    const val TEST_DURATION_SECONDS = 5
}

data class FormattedDisplayLines(
    val line1: String,
    val line2: String
)
