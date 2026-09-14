package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

fun getClipboardText(): String? {
    return runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    }.getOrNull()
}

internal data class TextLineLimits(
    val singleLine: Boolean,
    val minLines: Int,
    val maxLines: Int
)

internal fun resolveTextLineLimits(
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int
): TextLineLimits {
    val safeMinLines = minLines.coerceAtLeast(1)
    val safeMaxLines = maxLines.coerceAtLeast(1)
    val effectiveSingleLine = singleLine && safeMinLines == 1 && safeMaxLines == 1

    return if (effectiveSingleLine) {
        TextLineLimits(singleLine = true, minLines = 1, maxLines = 1)
    } else {
        TextLineLimits(
            singleLine = false,
            minLines = safeMinLines,
            maxLines = maxOf(safeMinLines, safeMaxLines)
        )
    }
}

@Composable
fun posTextFieldColors(
    containerColor: Color = Color.White,
    borderColor: Color = PosColors.Border,
    focusedBorderColor: Color = PosColors.Primary,
    errorBorderColor: Color = PosColors.Danger,
    errorLabelColor: Color = PosColors.Danger,
    errorCursorColor: Color = PosColors.Danger,
    errorSupportingTextColor: Color = PosColors.Danger
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor,
    errorContainerColor = containerColor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = borderColor,
    errorBorderColor = errorBorderColor,
    cursorColor = focusedBorderColor,
    errorCursorColor = errorCursorColor,
    focusedLabelColor = focusedBorderColor,
    errorLabelColor = errorLabelColor,
    errorSupportingTextColor = errorSupportingTextColor
)

@Composable
fun FieldErrorMessage(
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    if (!errorMessage.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.padding(top = 4.dp, start = 4.dp)
        ) {
            Text("⚠️", fontSize = 11.sp)
            Text(
                text = errorMessage,
                color = PosColors.Danger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TouchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    isError: Boolean = !errorMessage.isNullOrBlank(),
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = posTextFieldColors(),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    dialogTitle: String = label ?: placeholder ?: "Saisie"
) {
    var showKeyboardDialog by remember { mutableStateOf(false) }
    val lineLimits = resolveTextLineLimits(singleLine, minLines, maxLines)
    val effectiveIsError = isError || !errorMessage.isNullOrBlank()
    val effectiveSupportingText: @Composable (() -> Unit)? = when {
        !errorMessage.isNullOrBlank() -> {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("⚠️", fontSize = 11.sp)
                    Text(
                        text = errorMessage,
                        color = PosColors.Danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        supportingText != null -> supportingText
        else -> null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = lineLimits.singleLine,
        maxLines = lineLimits.maxLines,
        minLines = lineLimits.minLines,
        readOnly = readOnly,
        enabled = enabled,
        isError = effectiveIsError,
        supportingText = effectiveSupportingText,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        leadingIcon = leadingIcon,
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (value.isNotEmpty() && !readOnly && enabled) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("✕", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                if (!readOnly && enabled) {
                    IconButton(
                        onClick = { showKeyboardDialog = true },
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("⌨️", fontSize = 16.sp)
                    }
                }
                trailingIcon?.invoke()
            }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = colors,
        shape = shape,
        modifier = modifier
    )

    if (showKeyboardDialog) {
        VirtualKeyboardDialog(
            title = dialogTitle,
            initialValue = value,
            placeholder = placeholder ?: "",
            isPassword = isPassword,
            onDismiss = { showKeyboardDialog = false },
            onConfirm = { result ->
                onValueChange(result)
                showKeyboardDialog = false
            }
        )
    }
}

@Composable
fun TouchNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    currencySymbol: String = "",
    isDecimalAllowed: Boolean = true,
    quickAmounts: List<String> = emptyList(),
    errorMessage: String? = null,
    isError: Boolean = !errorMessage.isNullOrBlank(),
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    colors: TextFieldColors = posTextFieldColors(),
    supportingText: @Composable (() -> Unit)? = null
) {
    var showNumericDialog by remember { mutableStateOf(false) }
    val effectiveIsError = isError || !errorMessage.isNullOrBlank()
    val effectiveSupportingText: @Composable (() -> Unit)? = when {
        !errorMessage.isNullOrBlank() -> {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("⚠️", fontSize = 12.sp)
                    Text(
                        text = errorMessage,
                        color = PosColors.Danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        else -> supportingText
    }

    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.isEmpty() || (isDecimalAllowed && candidate.matches(Regex("\\d+([.,]\\d{0,2})?"))) || (!isDecimalAllowed && candidate.all(Char::isDigit))) {
                onValueChange(candidate)
            }
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = effectiveIsError,
        supportingText = effectiveSupportingText,
        singleLine = true,
        colors = colors,
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("✕", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { showNumericDialog = true },
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("🔢", fontSize = 16.sp)
                }
                if (currencySymbol.isNotEmpty()) {
                    Text(currencySymbol, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                }
            }
        },
        shape = shape,
        modifier = modifier
    )

    if (showNumericDialog) {
        TouchNumericDialog(
            title = label ?: placeholder ?: "Montant",
            initialValue = value,
            currencySymbol = currencySymbol,
            isDecimalAllowed = isDecimalAllowed,
            quickAmounts = quickAmounts,
            onDismiss = { showNumericDialog = false },
            onConfirm = { result ->
                onValueChange(result)
                showNumericDialog = false
            }
        )
    }
}

@Composable
fun TouchPinField(
    pin: String,
    onPinChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    maxDigits: Int = 6,
    errorMessage: String? = null,
    isError: Boolean = !errorMessage.isNullOrBlank(),
    supportingText: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    colors: TextFieldColors = posTextFieldColors()
) {
    var showPinDialog by remember { mutableStateOf(false) }
    val effectiveIsError = isError || !errorMessage.isNullOrBlank()
    val effectiveSupportingText: @Composable (() -> Unit)? = when {
        !errorMessage.isNullOrBlank() -> {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("⚠️", fontSize = 11.sp)
                    Text(
                        text = errorMessage,
                        color = PosColors.Danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        supportingText != null -> supportingText
        else -> null
    }

    OutlinedTextField(
        value = pin,
        onValueChange = { candidate ->
            if (candidate.length <= maxDigits && (candidate.isEmpty() || candidate.all(Char::isDigit))) {
                onPinChange(candidate)
            }
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = effectiveIsError,
        supportingText = effectiveSupportingText,
        colors = colors,
        visualTransformation = PasswordVisualTransformation(),
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (pin.isNotEmpty()) {
                    IconButton(
                        onClick = { onPinChange("") },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text("✕", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text("🔒", fontSize = 16.sp)
                }
            }
        },
        shape = shape,
        modifier = modifier
    )

    if (showPinDialog) {
        TouchPinDialog(
            title = label ?: "Code PIN",
            maxDigits = maxDigits,
            onDismiss = { showPinDialog = false },
            onConfirm = { result ->
                onPinChange(result)
                showPinDialog = false
            }
        )
    }
}
