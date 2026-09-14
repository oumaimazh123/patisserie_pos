package ma.elaroui.pos.desktop.presentation.register.close

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RegisterClosingHelper {

    /**
     * Generates a normalized envelope reference in the format:
     * ENV-YYYYMMDD-HHmm-NOMCAISSIER
     * e.g., ENV-20260908-0445-ZAKARIA
     */
    fun generateEnvelopeReference(
        cashierName: String,
        epochMilliseconds: Long = System.currentTimeMillis(),
        sequence: Int = 1
    ): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        val dateTimeStr = sdf.format(Date(epochMilliseconds))

        val normalized = Normalizer.normalize(cashierName.trim(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase(Locale.US)
            .replace("[^A-Z0-9]".toRegex(), "")

        val safeName = if (normalized.isNotBlank()) normalized else "CAISSIER"

        val baseRef = "ENV-$dateTimeStr-$safeName"
        return if (sequence <= 1) baseRef else "$baseRef-$sequence"
    }

    /**
     * Calculates the automatic withdrawal amount:
     * Montant retiré = Espèces comptées en caisse - Montant laissé en fond de caisse
     * Guaranteed >= 0.
     */
    fun calculateAutoRemovedCentimes(
        countedCentimes: Long?,
        leftCentimes: Long?
    ): Long? {
        if (countedCentimes == null || leftCentimes == null) return null
        return (countedCentimes - leftCentimes).coerceAtLeast(0L)
    }
}