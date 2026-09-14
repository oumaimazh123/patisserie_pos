package ma.elaroui.pos.desktop.presentation.license

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.license.WindowsLicenseState
import ma.elaroui.pos.desktop.license.WindowsLicenseStatus
import ma.elaroui.pos.desktop.presentation.components.PosColors

@Composable
fun TrialBanner(
    licenseState: WindowsLicenseState,
    strings: DesktopStrings,
    onActivateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (licenseState.status != WindowsLicenseStatus.TRIAL_ACTIVE &&
        licenseState.status != WindowsLicenseStatus.TRIAL_EXPIRED
    ) {
        return
    }

    val expiringSoon = licenseState.daysRemaining <= 3
    val backgroundColor = if (expiringSoon) PosColors.AlertLight else PosColors.PrimaryLight
    val contentColor = if (expiringSoon) PosColors.Alert else PosColors.Primary
    val message = "MODE DÉMO — ${licenseState.daysRemaining} jour(s) restant(s)."

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, fontWeight = FontWeight.Bold, color = contentColor)
            Button(
                onClick = onActivateClick,
                modifier = Modifier.height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PosColors.Danger,
                    contentColor = Color.White
                )
            ) {
                Text(strings.activateNow, fontWeight = FontWeight.Bold)
            }
        }
    }
}
