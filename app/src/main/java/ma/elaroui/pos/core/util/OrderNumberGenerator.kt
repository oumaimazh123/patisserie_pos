package ma.elaroui.pos.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OrderNumberGenerator {

    fun generateDatePrefix(timestamp: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))
    }

    fun formatOrderNumber(datePrefix: String, sequenceNumber: Int): String {
        val formattedSeq = sequenceNumber.toString().padStart(4, '0')
        return "ORD-$datePrefix-$formattedSeq"
    }
}
