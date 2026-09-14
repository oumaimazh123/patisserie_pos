package ma.elaroui.pos.presentation.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.core.license.LicenseState
import ma.elaroui.pos.core.license.LicenseStatus
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions
import kotlin.math.ceil

@Composable
fun TrialBanner(
    licenseState: LicenseState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expiringSoon = licenseState.status == LicenseStatus.TRIAL_EXPIRING_SOON
    val backgroundColor = if (expiringSoon) Color(0xFFFFF3CD) else Color(0xFFE3F2FD)
    val contentColor = if (expiringSoon) Color(0xFF7A4F00) else Color(0xFF0D47A1)
    val adaptive = LocalAdaptiveDimensions.current
    val message = "MODE DÉMO — ${formatTrialRemaining(licenseState.remainingTrialTimeMs)} restant(s)."

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().testTag("trial_banner")
    ) {
        if (adaptive.isCompact) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(message, fontWeight = FontWeight.Bold)
                ActivationButton(onActivate, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                ActivationButton(onActivate)
            }
        }
    }
}

@Composable
private fun ActivationButton(onActivate: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onActivate,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE63946),
            contentColor = Color.White
        ),
        modifier = modifier.testTag("trial_activate_button")
    ) {
        Text("Activer maintenant", fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

internal fun formatTrialRemaining(remainingMs: Long): String {
    val hourMs = 60 * 60 * 1000L
    val roundedHours = ceil(remainingMs.coerceAtLeast(0L).toDouble() / hourMs)
        .toLong()
        .coerceAtLeast(1L)
    val days = roundedHours / 24
    val hours = roundedHours % 24
    return when {
        days > 0 && hours > 0 -> "$days jour(s) et $hours heure(s)"
        days > 0 -> "$days jour(s)"
        else -> "$hours heure(s)"
    }
}
