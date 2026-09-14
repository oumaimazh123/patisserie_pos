@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.FieldErrorMessage
import ma.elaroui.pos.desktop.presentation.components.NumericKeypad
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosDimens
import ma.elaroui.pos.shared.domain.User
import ma.elaroui.pos.shared.domain.UserRole
import ma.elaroui.pos.shared.rules.PinValidationRules

@Composable
fun UserSelectionScreen(
    users: List<User>,
    strings: DesktopStrings,
    onLoginSubmitted: (selectedUser: User, pin: String) -> Unit,
    errorMessage: String = "",
    onClearError: () -> Unit = {},
    showWindowModeButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit = {},
    onExitApp: (() -> Unit)? = null
) {
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var enteredPin by remember { mutableStateOf("") }

    if (selectedUser != null) {
        PinLoginScreen(
            user = selectedUser!!,
            enteredPin = enteredPin,
            strings = strings,
            errorMessage = errorMessage,
            showWindowModeButton = showWindowModeButton,
            isFullscreen = isFullscreen,
            onToggleWindowMode = onToggleWindowMode,
            onExitApp = onExitApp,
            onBackClick = {
                selectedUser = null
                enteredPin = ""
                onClearError()
            },
            onPinChange = { candidate ->
                if (errorMessage.isNotBlank()) onClearError()
                val normalized = candidate.filter(Char::isDigit).take(6)
                enteredPin = normalized
                if (normalized.length == 6) {
                    onLoginSubmitted(selectedUser!!, normalized)
                    enteredPin = ""
                }
            },
            onSubmitPin = {
                if (PinValidationRules.isValid(enteredPin)) {
                    onLoginSubmitted(selectedUser!!, enteredPin)
                    enteredPin = ""
                }
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.selectUser, color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PosColors.BakeryBrown)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(PosColors.Canvas)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Veuillez choisir votre compte",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PosColors.TextHigh,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                val usersGridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = usersGridState,
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 800.dp)
                        .touchDragScroll(usersGridState)
                ) {
                    items(users.filter { it.active }) { user ->
                        UserCard(
                            user = user, 
                            strings = strings, 
                            onClick = { 
                                onClearError()
                                selectedUser = user
                                enteredPin = "" 
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    strings: DesktopStrings,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PosDimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PosColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (user.role == UserRole.OWNER) PosColors.Primary else PosColors.BakeryBrown
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = user.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                Text(
                    text = if (user.role == UserRole.OWNER) strings.ownerRole else strings.cashierRole,
                    fontSize = 14.sp,
                    color = PosColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun PinLoginScreen(
    user: User,
    enteredPin: String,
    strings: DesktopStrings,
    errorMessage: String,
    showWindowModeButton: Boolean,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit,
    onExitApp: (() -> Unit)? = null,
    onBackClick: () -> Unit,
    onPinChange: (String) -> Unit,
    onSubmitPin: () -> Unit
) {
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) onPinChange("")
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(user.id) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.enterPin, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← ${strings.selectUser}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PosColors.BakeryBrown)
            )
        }
    ) { padding ->
        val pinScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PosColors.Canvas)
                .touchDragScroll(pinScrollState)
                .verticalScroll(pinScrollState)
                .padding(16.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val typedCharacter = event.utf16CodePoint.takeIf { it > 0 }?.let { Character.toChars(it).concatToString().singleOrNull() }
                    when {
                        event.key == Key.Enter -> {
                            if (PinValidationRules.isValid(enteredPin)) onSubmitPin()
                            true
                        }
                        event.key == Key.Backspace -> {
                            if (enteredPin.isNotEmpty()) onPinChange(enteredPin.dropLast(1))
                            true
                        }
                        typedCharacter in '0'..'9' -> {
                            if (enteredPin.length < 6) onPinChange(enteredPin + typedCharacter)
                            true
                        }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = user.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = PosColors.TextHigh
            )
            Text(
                text = "Entrez votre code PIN (4-6 chiffres)",
                fontSize = 14.sp,
                color = PosColors.TextMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pin Indicator Dots
            val hasPinError = errorMessage.isNotBlank()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                repeat(maxOf(4, enteredPin.length)) { index ->
                    val isFilled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasPinError) PosColors.Danger.copy(alpha = if (isFilled) 1f else 0.35f)
                                else if (isFilled) PosColors.Primary
                                else PosColors.Border
                            )
                    )
                }
            }

            if (hasPinError) {
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    FieldErrorMessage(errorMessage = errorMessage)
                }
            }

            NumericKeypad(
                value = enteredPin,
                onValueChange = onPinChange,
                isDecimalAllowed = false,
                clearLabel = strings.text("Effacer", "Clear", "مسح"),
                modifier = Modifier.widthIn(max = 390.dp),
                keyHeight = 60.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSubmitPin,
                enabled = enteredPin.length in 4..6,
                modifier = Modifier.widthIn(max = 390.dp).fillMaxWidth().height(PosDimens.CashOutButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PosColors.Primary,
                    disabledContainerColor = PosColors.Primary.copy(alpha = 0.38f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(strings.confirm, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
