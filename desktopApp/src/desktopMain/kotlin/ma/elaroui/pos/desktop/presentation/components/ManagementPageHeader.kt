package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings

val AppHeaderHeight = PosUi.HeaderHeight

@Composable
fun ManagementPageHeader(
    title: String,
    strings: DesktopStrings,
    onBackToDashboard: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(AppHeaderHeight)) {
        val availableWidth = maxWidth
        val compact = availableWidth < 760.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackToDashboard != null) {
                    BackArrowButton(
                        contentDescription = strings.backToDashboard,
                        onClick = onBackToDashboard
                    )
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    color = PosColors.TextHigh
                )
                Row(
                    modifier = Modifier
                        .widthIn(max = if (compact) availableWidth * 0.48f else availableWidth * 0.62f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
            HorizontalDivider(color = PosColors.Border)
        }
    }
}

@Composable
private fun BackArrowButton(contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val background by animateColorAsState(
        if (hovered || focused) PosColors.Primary.copy(alpha = 0.08f) else Color.Transparent
    )
    Box(
        modifier = Modifier
            .size(PosUi.TouchTarget)
            .background(background, RoundedCornerShape(8.dp))
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Text("←", fontSize = 24.sp, color = PosColors.TextHigh)
    }
}

@Composable
fun PosServiceHeader(
    cashierName: String,
    strings: DesktopStrings,
    onDashboard: (() -> Unit)?,
    onOpenPos: (() -> Unit)?,
    onActiveOrders: () -> Unit,
    onCurrentSession: () -> Unit,
    isRegisterOpen: Boolean = true,
    showWindowModeButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit = {},
    onLock: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(AppHeaderHeight)) {
        val availableWidth = maxWidth
        val isMobile = availableWidth < 600.dp
        val showLabels = availableWidth >= 960.dp

        Surface(color = PosColors.BakeryBrown, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isMobile) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isMobile) "PATISSERIE" else strings.text("PATISSERIE_POS", "PATISSERIE_POS", "نظام نقاط البيع"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isMobile) 17.sp else 20.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.width(if (isMobile) 6.dp else 12.dp))
                Surface(
                    color = PosColors.PrimaryDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        if (isMobile) cashierName else "${strings.cashierRole}: $cashierName",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isRegisterOpen) {
                    Spacer(Modifier.width(if (isMobile) 4.dp else 8.dp))
                    Surface(
                        color = PosColors.Success,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🟢", fontSize = 9.sp)
                            Text(
                                if (isMobile) strings.text("Ouverte", "Open", "مفتوح") else strings.text("Caisse ouverte", "Register open", "الصندوق مفتوح"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    onDashboard?.let { callback ->
                        TextButton(
                            onClick = callback,
                            modifier = Modifier.height(PosUi.TouchTarget).pointerHoverIcon(PointerIcon.Hand),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(if (showLabels) "📊 ${strings.dashboardTitle}" else "📊", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }

                    onOpenPos?.let { callback ->
                        TextButton(
                            onClick = callback,
                            modifier = Modifier.height(PosUi.TouchTarget).pointerHoverIcon(PointerIcon.Hand),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("🛒 POS", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }

                    TextButton(
                        onClick = onActiveOrders,
                        modifier = Modifier.height(PosUi.TouchTarget).pointerHoverIcon(PointerIcon.Hand),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (showLabels) "📋 ${strings.activeOrders}" else "📋", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    TextButton(
                        onClick = onCurrentSession,
                        modifier = Modifier.height(PosUi.TouchTarget).pointerHoverIcon(PointerIcon.Hand),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(if (showLabels) "💰 ${strings.currentSessionTitle}" else "💰", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = onLock,
                        modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand),
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.PrimaryDark, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text(if (showLabels) "🔒 ${strings.lock}" else "🔒", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

