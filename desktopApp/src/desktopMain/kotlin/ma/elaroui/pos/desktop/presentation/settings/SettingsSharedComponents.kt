package ma.elaroui.pos.desktop.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.PosColors

enum class SettingsTab {
    ESTABLISHMENT,
    PRINTERS,
    CUSTOMER_DISPLAY,
    BACKUP_RESTORE,
    DATA_MANAGEMENT,
    LICENSE
}

@Composable
fun SettingsTabBar(
    selectedTab: SettingsTab,
    strings: DesktopStrings,
    onNavigateToEstablishment: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    onNavigateToCustomerDisplay: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToLicense: () -> Unit
) {
    Surface(
        color = PosColors.Surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsTabItem(
                title = "🏪 " + strings.text("Établissement & Facture", "Establishment & Invoice", "المؤسسة والفاتورة"),
                isSelected = selectedTab == SettingsTab.ESTABLISHMENT,
                onClick = onNavigateToEstablishment
            )

            SettingsTabItem(
                title = "🖨️ " + strings.printersTitle,
                isSelected = selectedTab == SettingsTab.PRINTERS,
                onClick = onNavigateToPrinters
            )

            SettingsTabItem(
                title = "📟 " + strings.text("Afficheur client", "Customer Display", "شاشة الزبون"),
                isSelected = selectedTab == SettingsTab.CUSTOMER_DISPLAY,
                onClick = onNavigateToCustomerDisplay
            )

            SettingsTabItem(
                title = "💾 " + strings.backupRestoreTitle,
                isSelected = selectedTab == SettingsTab.BACKUP_RESTORE,
                onClick = onNavigateToBackupRestore
            )

            SettingsTabItem(
                title = "🗑️ " + strings.text("Gestion des données", "Data Management", "إدارة البيانات"),
                isSelected = selectedTab == SettingsTab.DATA_MANAGEMENT,
                onClick = onNavigateToDataManagement
            )

            SettingsTabItem(
                title = "🔑 " + strings.licenseTitle,
                isSelected = selectedTab == SettingsTab.LICENSE,
                onClick = onNavigateToLicense
            )
        }
    }
}

@Composable
private fun SettingsTabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) PosColors.Primary else PosColors.Workspace,
        border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .height(48.dp)
            .pointerHoverIcon(if (isSelected) PointerIcon.Default else PointerIcon.Hand)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                title,
                color = if (isSelected) Color.White else PosColors.TextHigh,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}
