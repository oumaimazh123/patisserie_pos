package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class KeyboardLayoutType {
    AZERTY,
    QWERTY,
    ARABIC,
    SYMBOLS
}

@Composable
fun VirtualKeyboard(
    text: String,
    onTextChange: (String) -> Unit,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialLayout: KeyboardLayoutType = KeyboardLayoutType.AZERTY,
    keyHeight: Dp = 48.dp
) {
    var currentLayout by remember { mutableStateOf(initialLayout) }
    var isShifted by remember { mutableStateOf(false) }
    var isCapsLocked by remember { mutableStateOf(false) }

    val azertyRows = listOf(
        listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        listOf("w", "x", "c", "v", "b", "n", "'", "-", "@", ".")
    )

    val qwertyRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m", "'", "-", ".")
    )

    val arabicRows = listOf(
        listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"),
        listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
        listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ", "ذ")
    )

    val symbolsRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "€", "$", "%", "&", "-", "+", "(", ")"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "/", "\\", "_")
    )

    val numberTopRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    fun appendChar(char: String) {
        val toInsert = if (isShifted || isCapsLocked) char.uppercase() else char
        onTextChange(text + toInsert)
        if (isShifted && !isCapsLocked) {
            isShifted = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PosColors.Workspace, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Layout selector & helper toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyboardModeChip("AZERTY", currentLayout == KeyboardLayoutType.AZERTY) {
                    currentLayout = KeyboardLayoutType.AZERTY
                }
                KeyboardModeChip("QWERTY", currentLayout == KeyboardLayoutType.QWERTY) {
                    currentLayout = KeyboardLayoutType.QWERTY
                }
                KeyboardModeChip("العربية", currentLayout == KeyboardLayoutType.ARABIC) {
                    currentLayout = KeyboardLayoutType.ARABIC
                }
                KeyboardModeChip("?123", currentLayout == KeyboardLayoutType.SYMBOLS) {
                    currentLayout = KeyboardLayoutType.SYMBOLS
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = { onTextChange("") },
                    shape = RoundedCornerShape(6.dp),
                    color = PosColors.Surface,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text("Tout effacer", color = PosColors.TextHigh, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Numbers row on top of alphabetic layouts
        if (currentLayout != KeyboardLayoutType.SYMBOLS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                numberTopRow.forEach { num ->
                    KeyButton(
                        text = num,
                        modifier = Modifier.weight(1f).height(keyHeight),
                        onClick = { appendChar(num) }
                    )
                }
            }
        }

        // Character Rows
        val activeRows = when (currentLayout) {
            KeyboardLayoutType.AZERTY -> azertyRows
            KeyboardLayoutType.QWERTY -> qwertyRows
            KeyboardLayoutType.ARABIC -> arabicRows
            KeyboardLayoutType.SYMBOLS -> symbolsRows
        }

        activeRows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shift key on the 3rd row for alphabetic layouts
                if (index == activeRows.lastIndex && currentLayout != KeyboardLayoutType.ARABIC && currentLayout != KeyboardLayoutType.SYMBOLS) {
                    Surface(
                        onClick = {
                            if (isShifted) {
                                isCapsLocked = !isCapsLocked
                                if (!isCapsLocked) isShifted = false
                            } else {
                                isShifted = true
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCapsLocked) PosColors.Danger else if (isShifted) PosColors.Primary else PosColors.SurfaceHigh,
                        border = BorderStroke(1.dp, if (isCapsLocked) PosColors.Danger else if (isShifted) PosColors.Primary else PosColors.Border),
                        modifier = Modifier.weight(1.4f).height(keyHeight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (isCapsLocked) "⇪ CAP" else "⇧ SHIFT",
                                color = if (isCapsLocked || isShifted) Color.White else PosColors.TextHigh,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                row.forEach { ch ->
                    val displayChar = if ((isShifted || isCapsLocked) && currentLayout != KeyboardLayoutType.ARABIC && currentLayout != KeyboardLayoutType.SYMBOLS) {
                        ch.uppercase()
                    } else {
                        ch
                    }
                    KeyButton(
                        text = displayChar,
                        modifier = Modifier.weight(1f).height(keyHeight),
                        onClick = { appendChar(ch) }
                    )
                }

                // Backspace key on the last row
                if (index == activeRows.lastIndex) {
                    Surface(
                        onClick = {
                            if (text.isNotEmpty()) {
                                onTextChange(text.dropLast(1))
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = PosColors.SurfaceHigh,
                        border = BorderStroke(1.dp, PosColors.Border),
                        modifier = Modifier.weight(1.4f).height(keyHeight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⌫ Effacer", color = PosColors.TextHigh, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom spacebar and action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                text = ",",
                modifier = Modifier.weight(1f).height(keyHeight),
                onClick = { appendChar(",") }
            )

            KeyButton(
                text = "Espace",
                modifier = Modifier.weight(5f).height(keyHeight),
                backgroundColor = PosColors.Surface,
                onClick = { onTextChange(text + " ") }
            )

            KeyButton(
                text = ".",
                modifier = Modifier.weight(1f).height(keyHeight),
                onClick = { appendChar(".") }
            )

            Surface(
                onClick = onDone,
                shape = RoundedCornerShape(6.dp),
                color = PosColors.Primary,
                modifier = Modifier.weight(2.2f).height(keyHeight)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("OK ✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PosColors.Surface,
    textColor: Color = PosColors.TextHigh,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, PosColors.Border),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KeyboardModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) PosColors.Primary else PosColors.Surface,
        border = BorderStroke(1.dp, if (isSelected) PosColors.Primary else PosColors.Border),
        modifier = Modifier.height(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                label,
                color = if (isSelected) Color.White else PosColors.TextHigh,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun VirtualKeyboardDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isPassword: Boolean = false,
    placeholder: String = ""
) {
    var currentValue by remember { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
            border = BorderStroke(1.dp, PosColors.Border),
            modifier = Modifier
                .widthIn(max = 850.dp)
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = PosColors.TextHigh,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = PosColors.TextMuted, fontSize = 18.sp)
                    }
                }

                // Input preview box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.5.dp, PosColors.Primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (currentValue.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = PosColors.TextMuted, fontSize = 16.sp)
                        } else {
                            val display = if (isPassword) "●".repeat(currentValue.length) else currentValue
                            Text(display, color = PosColors.TextHigh, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // On-screen Virtual Keyboard
                VirtualKeyboard(
                    text = currentValue,
                    onTextChange = { currentValue = it },
                    onDone = { onConfirm(currentValue) },
                    keyHeight = 46.dp
                )

                // Dialog Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = PosColors.TextMedium, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(currentValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Valider", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
