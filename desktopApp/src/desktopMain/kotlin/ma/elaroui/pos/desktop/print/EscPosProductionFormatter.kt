package ma.elaroui.pos.desktop.print

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.shared.domain.Order
import ma.elaroui.pos.shared.domain.PaymentMethod
import ma.elaroui.pos.shared.domain.ReceiptCompany

data class EscPosPrintRequest(
    val order: Order,
    val company: ReceiptCompany,
    val kind: TicketKind,
    val paperWidth: Int,
    val isReprint: Boolean = false,
    val paymentMethod: PaymentMethod? = null,
    val receivedCentimes: Long? = null,
    val changeCentimes: Long? = null,
    val cashierName: String? = null,
    val timestampMs: Long? = null,
    val tableLabel: String? = null,
    val areaLabel: String? = null,
    val specialInstructions: String? = null
)

sealed interface EscPosFormatResult {
    data class Success(val bytes: ByteArray) : EscPosFormatResult
    data class Failure(val message: String, val category: PrintErrorCategory) : EscPosFormatResult
}

fun interface EscPosFormatter {
    fun format(request: EscPosPrintRequest): EscPosFormatResult
}

object EscPosFormatterFactory {
    fun create(platform: DesktopPlatform = DesktopPlatform.detect()): EscPosFormatter = when (platform) {
        DesktopPlatform.WINDOWS -> LegacyWindowsEscPosFormatter
        DesktopPlatform.LINUX -> LinuxEscPosFormatter
        DesktopPlatform.UNSUPPORTED -> EscPosFormatter {
            EscPosFormatResult.Failure("ESC/POS formatting is unavailable on this platform", PrintErrorCategory.TRANSPORT_FAILURE)
        }
    }
}

private object LegacyWindowsEscPosFormatter : EscPosFormatter {
    override fun format(request: EscPosPrintRequest): EscPosFormatResult = runCatching {
        ThermalTicketRenderer.render(
            request.order, request.company, request.kind, request.paperWidth, request.isReprint,
            request.paymentMethod, request.receivedCentimes, request.changeCentimes, request.cashierName,
            request.timestampMs, request.tableLabel, request.areaLabel, request.specialInstructions
        )
    }.fold(
        onSuccess = { EscPosFormatResult.Success(it) },
        onFailure = { EscPosFormatResult.Failure(it.message ?: "ESC/POS formatting failed", PrintErrorCategory.TRANSPORT_FAILURE) }
    )
}

object LinuxEscPosFormatter : EscPosFormatter {
    private val arabicRange = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]")

    override fun format(request: EscPosPrintRequest): EscPosFormatResult {
        val preview = runCatching {
            ThermalTicketRenderer.previewText(
                request.order, request.company, request.kind, request.paperWidth, request.isReprint,
                request.paymentMethod, request.receivedCentimes, request.changeCentimes, request.cashierName,
                request.timestampMs, request.tableLabel, request.areaLabel, request.specialInstructions
            )
        }.getOrElse {
            return EscPosFormatResult.Failure(it.message ?: "ESC/POS formatting failed", PrintErrorCategory.TRANSPORT_FAILURE)
        }

        if (arabicRange.containsMatchIn(preview)) {
            return EscPosFormatResult.Failure(
                "Arabic text cannot be printed safely by the configured raw ESC/POS code page. Use Latin text or a printer-specific raster driver.",
                PrintErrorCategory.UNSUPPORTED_CHARACTERS
            )
        }

        if (!FrenchEscPosEncoder.canEncode(preview)) {
            return EscPosFormatResult.Failure(
                "Ticket contains characters unsupported by ESC/POS CP858",
                PrintErrorCategory.UNSUPPORTED_CHARACTERS
            )
        }

        val logo = if (request.kind == TicketKind.CUSTOMER) {
            ThermalTicketRenderer.renderLogoEscPos(request.company.logoPath, request.paperWidth)
        } else ByteArray(0)
        val styledText = ThermalTicketRenderer.renderStyledText(preview, request.kind, FrenchEscPosEncoder.CHARSET, request.company.printEstablishmentName)
        return EscPosFormatResult.Success(
            EscPosCommands.INIT_CP858 + logo + styledText + EscPosCommands.FEED_AND_CUT
        )
    }
}
