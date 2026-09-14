package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DesktopTitleBar(
    title: String = "PATISSERIE POS",
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFF202020)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Application Title
        Text(
            text = title,
            color = Color(0xFFC0C0C0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )

        // Window Control Buttons (Minimize, Maximize / Restore, Close)
        // 1. Minimize: —
        WindowControlButton(
            onClick = onMinimize,
            hoverColor = Color.White.copy(alpha = 0.12f)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // 2. Maximize / Restore: □
        WindowControlButton(
            onClick = onMaximize,
            hoverColor = Color.White.copy(alpha = 0.12f)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawRect(
                    color = Color.White,
                    topLeft = Offset.Zero,
                    size = size,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // 3. Close: ✕
        WindowControlButton(
            onClick = onClose,
            hoverColor = Color(0xFFE81123)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val stroke = 1.2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                    strokeWidth = stroke
                )
                drawLine(
                    color = Color.White,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = stroke
                )
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    onClick: () -> Unit,
    hoverColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .width(46.dp)
            .height(32.dp)
            .background(if (isHovered) hoverColor else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
