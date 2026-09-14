package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ma.elaroui.pos.desktop.presentation.model.MessagePresentation
import ma.elaroui.pos.desktop.presentation.model.MessageSeverity
import ma.elaroui.pos.desktop.presentation.model.UiMessage


/**
 * Shared palette and visual definitions for POS message severities.
 */
object PosAlertColors {
    fun background(severity: MessageSeverity): Color = when (severity) {
        MessageSeverity.SUCCESS -> PosColors.SuccessLight
        MessageSeverity.ERROR -> PosColors.DangerLight
        MessageSeverity.WARNING -> PosColors.AlertLight
        MessageSeverity.INFO -> PosColors.PrimaryLight
    }

    fun border(severity: MessageSeverity): Color = when (severity) {
        MessageSeverity.SUCCESS -> PosColors.Success.copy(alpha = 0.3f)
        MessageSeverity.ERROR -> PosColors.Danger.copy(alpha = 0.3f)
        MessageSeverity.WARNING -> PosColors.Alert.copy(alpha = 0.3f)
        MessageSeverity.INFO -> PosColors.Primary.copy(alpha = 0.3f)
    }

    fun text(severity: MessageSeverity): Color = when (severity) {
        MessageSeverity.SUCCESS -> PosColors.Success
        MessageSeverity.ERROR -> PosColors.Danger
        MessageSeverity.WARNING -> PosColors.Alert
        MessageSeverity.INFO -> PosColors.Primary
    }

    fun icon(severity: MessageSeverity): String = when (severity) {
        MessageSeverity.SUCCESS -> "✅"
        MessageSeverity.ERROR -> "❌"
        MessageSeverity.WARNING -> "⚠️"
        MessageSeverity.INFO -> "ℹ️"
    }
}

/**
 * Floating self-dismissing snackbar for non-blocking POS alerts.
 * Automatically clears after [durationMs] (default 3000ms).
 */
@Composable
fun PosSnackbar(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 3000L
) {
    if (message != null) {
        LaunchedEffect(message.id) {
            if (message.presentation == MessagePresentation.TEMPORARY) {
                delay(durationMs)
                onDismiss()
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(12.dp),
                color = PosAlertColors.background(message.severity),
                border = BorderStroke(1.5.dp, PosAlertColors.border(message.severity))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(PosAlertColors.icon(message.severity), fontSize = 16.sp)

                    Text(
                        text = message.text,
                        color = PosAlertColors.text(message.severity),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    if (message.actionLabel != null && message.onAction != null) {
                        TextButton(
                            onClick = {
                                message.onAction.invoke()
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = message.actionLabel,
                                color = PosAlertColors.text(message.severity),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = "✕",
                        color = PosAlertColors.text(message.severity).copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clean inline alert box for embedded forms, validations, and settings panels.
 */
@Composable
fun PosInlineAlert(
    message: UiMessage?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    if (message != null && message.text.isNotBlank()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = PosAlertColors.background(message.severity),
            border = BorderStroke(1.dp, PosAlertColors.border(message.severity))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(PosAlertColors.icon(message.severity), fontSize = 15.sp)

                Text(
                    text = message.text,
                    color = PosAlertColors.text(message.severity),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                if (message.actionLabel != null && message.onAction != null) {
                    TextButton(
                        onClick = message.onAction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = message.actionLabel,
                            color = PosAlertColors.text(message.severity),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (onDismiss != null) {
                    Text(
                        text = "✕",
                        color = PosAlertColors.text(message.severity).copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(2.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                    )
                }
            }
        }
    }
}

/**
 * Standard confirmation dialog for destructive or critical actions (e.g. database restore, closing register, order cancel).
 */
@Composable
fun PosConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
    warningNote: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (isDestructive) "⚠️" else "❓",
                    fontSize = 20.sp
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isDestructive) PosColors.Danger else PosColors.TextHigh
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = PosColors.TextHigh,
                    lineHeight = 20.sp
                )
                if (!warningNote.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.DangerLight,
                        border = BorderStroke(1.dp, PosColors.Danger.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Text(
                                text = warningNote,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PosColors.Danger
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) PosColors.Danger else PosColors.Primary
                ),
                modifier = Modifier
                    .height(44.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(cancelLabel)
            }
        }
    )
}
