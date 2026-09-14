package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.shared.domain.TableStatus
import ma.elaroui.pos.shared.rules.MoneyRules

@Composable
fun TableStatusBadge(status: TableStatus) {
    val (backgroundColor, textColor, label) = when (status) {
        TableStatus.AVAILABLE -> Triple(PosColors.SuccessLight, PosColors.Success, "Libre")
        TableStatus.OCCUPIED -> Triple(PosColors.DangerLight, PosColors.Danger, "Occupée")
        TableStatus.RESERVED -> Triple(PosColors.AlertLight, PosColors.Alert, "Réservée")
    }

    Text(
        text = label,
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(backgroundColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun DiscrepancyBadge(differenceCentimes: Long?) {
    if (differenceCentimes == null) return
    val (bgColor, textColor, text) = when {
        differenceCentimes == 0L -> Triple(PosColors.SuccessLight, PosColors.Success, "Conforme (0.00 DH)")
        differenceCentimes > 0L -> Triple(PosColors.PrimaryLight, PosColors.PrimaryDark, "Surplus: +${MoneyRules.formatFixed(differenceCentimes)} DH")
        else -> Triple(PosColors.DangerLight, PosColors.Danger, "Manquant: ${MoneyRules.formatFixed(differenceCentimes)} DH")
    }

    Text(
        text = text,
        color = textColor,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
