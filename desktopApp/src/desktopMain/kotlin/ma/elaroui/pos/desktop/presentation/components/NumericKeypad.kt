package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun NumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDecimalAllowed: Boolean = true,
    clearLabel: String = "C",
    keyHeight: Dp = PosDimens.KeypadKeyHeight,
    keyColor: Color = Color.White
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (isDecimalAllowed) "." else "__CLEAR__", "0", "←")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { digit ->
                    if (digit.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f).height(keyHeight))
                    } else {
                        val isActionKey = digit == "←" || digit == "__CLEAR__"
                        Button(
                            onClick = {
                                when (digit) {
                                    "←" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                    "__CLEAR__" -> onValueChange("")
                                    "." -> if (!value.contains(".")) onValueChange(if (value.isEmpty()) "0." else "$value.")
                                    else -> onValueChange(value + digit)
                                }
                            },
                            modifier = Modifier.weight(1f).height(keyHeight),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PosColors.Border),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActionKey) PosColors.Workspace else keyColor,
                                contentColor = PosColors.TextHigh
                            )
                        ) {
                            Text(
                                if (digit == "__CLEAR__") clearLabel else digit,
                                fontSize = if (digit == "__CLEAR__") 15.sp else 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.TextHigh,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TouchPinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    maxDigits: Int = 6,
    modifier: Modifier = Modifier,
    keyHeight: Dp = PosDimens.KeypadKeyHeight
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    val isAction = key == "C" || key == "⌫"
                    Button(
                        onClick = {
                            when (key) {
                                "⌫" -> if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                                "C" -> onPinChange("")
                                else -> if (pin.length < maxDigits) onPinChange(pin + key)
                            }
                        },
                        modifier = Modifier.weight(1f).height(keyHeight),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PosColors.Border),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAction) PosColors.Workspace else Color.White,
                            contentColor = PosColors.TextHigh
                        )
                    ) {
                        Text(key, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                    }
                }
            }
        }
    }
}

@Composable
fun TouchNumericDialog(
    title: String,
    initialValue: String,
    currencySymbol: String = "DH",
    isDecimalAllowed: Boolean = true,
    quickAmounts: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var currentValue by remember { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(PosDimens.RadiusPanel),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, PosColors.Border),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PosColors.TextHigh)
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 16.sp, color = PosColors.TextMuted)
                    }
                }

                // Display Value Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.5.dp, PosColors.Primary),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentValue.isEmpty()) "0" else currentValue,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentValue.isEmpty()) PosColors.TextMuted else PosColors.TextHigh
                        )
                        if (currencySymbol.isNotEmpty()) {
                            Text(currencySymbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosColors.TextMedium)
                        }
                    }
                }

                // Quick Amount Shortcuts
                if (quickAmounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickAmounts.forEach { amt ->
                            OutlinedButton(
                                onClick = { currentValue = amt },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PosColors.Border)
                            ) {
                                Text(amt, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                            }
                        }
                    }
                }

                // Numeric Keypad
                NumericKeypad(
                    value = currentValue,
                    onValueChange = { currentValue = it },
                    isDecimalAllowed = isDecimalAllowed,
                    clearLabel = "C",
                    keyHeight = 50.dp
                )

                // Dialog Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = PosColors.TextMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(currentValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Valider", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TouchPinDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    maxDigits: Int = 6
) {
    var enteredPin by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(PosDimens.RadiusPanel),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, PosColors.Border),
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PosColors.TextHigh)
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 16.sp, color = PosColors.TextMuted)
                    }
                }

                // Masked PIN Bullets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(maxDigits) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (isFilled) PosColors.Primary else PosColors.Border,
                                    CircleShape
                                )
                        )
                    }
                }

                TouchPinPad(
                    pin = enteredPin,
                    onPinChange = { enteredPin = it },
                    maxDigits = maxDigits,
                    keyHeight = 52.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler", color = PosColors.TextMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(enteredPin) },
                        enabled = enteredPin.length in 4..maxDigits,
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Valider", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PresetCashButtons(
    onPresetSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(20_00L, 50_00L, 100_00L, 200_00L) // Centimes
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { amountCentimes ->
            OutlinedButton(
                onClick = { onPresetSelected(amountCentimes) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PosColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PosColors.Workspace,
                    contentColor = PosColors.BakeryBrown
                )
            ) {
                Text("+${amountCentimes / 100} DH", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
