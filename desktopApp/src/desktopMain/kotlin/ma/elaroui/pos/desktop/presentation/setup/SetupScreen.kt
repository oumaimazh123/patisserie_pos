@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import androidx.compose.foundation.BorderStroke
import ma.elaroui.pos.desktop.presentation.components.TouchPinField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.shared.rules.PinValidationError
import ma.elaroui.pos.shared.rules.PinValidationRules

@Composable
fun SetupScreen(
    strings: DesktopStrings,
    onSetupComplete: (establishmentName: String, address: String, phone: String, ownerName: String, pin: String) -> Unit,
    errorMessage: String? = null
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedLanguage by remember { mutableStateOf("fr") }
    var restaurantName by remember { mutableStateOf("") }
    var restaurantPhone by remember { mutableStateOf("") }
    var restaurantAddress by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var ownerPinConfirm by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf("Caisse principale") }

    var restaurantNameError by remember { mutableStateOf<String?>(null) }
    var ownerNameError by remember { mutableStateOf<String?>(null) }
    var ownerPinError by remember { mutableStateOf<String?>(null) }
    var ownerPinConfirmError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.text("Configuration POS — Étape $currentStep / 6", "POS Setup — Step $currentStep / 6", "إعداد نقطة البيع — الخطوة $currentStep / 6"), color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PosColors.Primary)
            )
        }
    ) { padding ->
        val setupScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PosColors.Workspace)
                .touchDragScroll(setupScrollState)
                .verticalScroll(setupScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                when (currentStep) {
                    1 -> StepWelcome(strings)
                    2 -> StepLanguage(strings, selectedLanguage) { selectedLanguage = it }
                    3 -> StepRestaurantInfo(
                        strings = strings,
                        name = restaurantName,
                        phone = restaurantPhone,
                        address = restaurantAddress,
                        nameError = restaurantNameError,
                        onNameChange = { restaurantName = it; restaurantNameError = null },
                        onPhoneChange = { restaurantPhone = it },
                        onAddressChange = { restaurantAddress = it }
                    )
                    4 -> StepOwnerAccount(
                        strings = strings,
                        name = ownerName,
                        pin = ownerPin,
                        pinConfirm = ownerPinConfirm,
                        nameError = ownerNameError,
                        pinError = ownerPinError,
                        pinConfirmError = ownerPinConfirmError,
                        onNameChange = { ownerName = it; ownerNameError = null },
                        onPinChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                ownerPin = it
                                ownerPinError = null
                            }
                        },
                        onPinConfirmChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                ownerPinConfirm = it
                                ownerPinConfirmError = null
                            }
                        }
                    )
                    5 -> StepFirstRegister(
                        strings = strings, name = registerName,
                        onUpdate = { registerName = it }
                    )
                    6 -> StepSummary(
                        strings = strings, restaurantName = restaurantName,
                        language = selectedLanguage,
                        ownerName = ownerName,
                        registerName = registerName
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = {
                            restaurantNameError = null
                            ownerNameError = null
                            ownerPinError = null
                            ownerPinConfirmError = null
                            currentStep -= 1
                        }
                    ) {
                        Text(strings.text("Précédent", "Back", "السابق"))
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (currentStep < 6) {
                    Button(
                        onClick = {
                            when (currentStep) {
                                3 -> {
                                    if (restaurantName.isBlank()) {
                                        restaurantNameError = strings.text("Veuillez saisir le nom de l'établissement.", "Please enter company name.", "يرجى إدخال اسم المؤسسة.")
                                        return@Button
                                    }
                                }
                                4 -> {
                                    var hasError = false
                                    if (ownerName.isBlank()) {
                                        ownerNameError = strings.text("Veuillez saisir le nom du propriétaire.", "Please enter owner name.", "يرجى إدخال اسم المالك.")
                                        hasError = true
                                    }
                                    val pinValidation = PinValidationRules.validatePinConfirmation(ownerPin, ownerPinConfirm)
                                    if (pinValidation != null) {
                                        when (pinValidation) {
                                            PinValidationError.BLANK -> {
                                                ownerPinError = strings.text("Le code PIN est obligatoire.", "The PIN is required.", "رمز PIN مطلوب.")
                                            }
                                            PinValidationError.INVALID_LENGTH -> {
                                                ownerPinError = strings.text("Le code PIN doit comporter 4 à 6 chiffres.", "The PIN must contain 4 to 6 digits.", "يجب أن يتكون رمز PIN من 4 إلى 6 أرقام.")
                                            }
                                            PinValidationError.NOT_DIGITS -> {
                                                ownerPinError = strings.text("Le code PIN ne doit contenir que des chiffres.", "The PIN must contain digits only.", "يجب أن يحتوي رمز PIN على أرقام فقط.")
                                            }
                                            PinValidationError.MISMATCH -> {
                                                ownerPinConfirmError = strings.pinMismatch
                                            }
                                        }
                                        hasError = true
                                    }
                                    if (hasError) return@Button
                                }
                            }
                            restaurantNameError = null
                            ownerNameError = null
                            ownerPinError = null
                            ownerPinConfirmError = null
                            currentStep += 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary)
                    ) {
                        Text(strings.text("Suivant", "Next", "التالي"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            onSetupComplete(
                                restaurantName.trim(),
                                restaurantAddress.trim(),
                                restaurantPhone.trim(),
                                ownerName.trim(),
                                ownerPin
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary)
                    ) {
                        Text(strings.text("Terminer la configuration", "Finish setup", "إنهاء الإعداد"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(strings: DesktopStrings) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.text("Bienvenue sur PATISSERIE_POS", "Welcome to PATISSERIE_POS", "مرحباً بك في PATISSERIE_POS"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            strings.text("Cet assistant vous guidera pour configurer votre magasin et votre compte propriétaire.", "This assistant will guide you through setting up your store and owner account.", "سيرشدك هذا المساعد لإعداد متجرك وحساب المالك."),
            fontSize = 16.sp, color = PosColors.TextMedium
        )
    }
}

@Composable
private fun StepLanguage(strings: DesktopStrings, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(strings.text("Choisir la langue d’affichage", "Choose display language", "اختر لغة العرض"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Spacer(modifier = Modifier.height(16.dp))
        listOf("fr" to "Français", "ar" to "العربية", "en" to "English").forEach { (code, label) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                onClick = { onSelect(code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == code) PosColors.PrimaryLight else PosColors.Surface
                ),
                border = BorderStroke(1.dp, if (selected == code) PosColors.Primary else PosColors.Border)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (selected == code),
                        onClick = { onSelect(code) },
                        colors = RadioButtonDefaults.colors(selectedColor = PosColors.Primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh)
                }
            }
        }
    }
}

@Composable
private fun StepRestaurantInfo(
    strings: DesktopStrings,
    name: String,
    phone: String,
    address: String,
    nameError: String? = null,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(strings.companyInfo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Spacer(modifier = Modifier.height(16.dp))
        TouchTextField(
            value = name,
            onValueChange = onNameChange,
            label = "${strings.companyNameLabel} *",
            errorMessage = nameError,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TouchTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = strings.phoneLabel,
            placeholder = "05XX XX XX XX / 06XX XX XX XX",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        TouchTextField(
            value = address,
            onValueChange = onAddressChange,
            label = strings.addressLabel,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(strings.text("Devise : MAD (dirham marocain)", "Currency: MAD (Moroccan dirham)", "العملة: الدرهم المغربي"), fontWeight = FontWeight.Bold, color = PosColors.Primary)
    }
}

@Composable
private fun StepOwnerAccount(
    strings: DesktopStrings,
    name: String,
    pin: String,
    pinConfirm: String,
    nameError: String? = null,
    pinError: String? = null,
    pinConfirmError: String? = null,
    onNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onPinConfirmChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.text("Compte propriétaire & Sécurité", "Owner account & Security", "حساب المالك والأمان"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Text(
            strings.text(
                "Le code PIN sert à vous connecter à la caisse et autoriser les opérations sensibles (clôtures, suppressions, etc.).",
                "The PIN is used to log into the register and authorize sensitive operations (closures, deletions, etc.).",
                "يستخدم رمز PIN لتسجيل الدخول إلى الصندوق والتفويض بالعمليات الحساسة (الإغلاق، الحذف، إلخ)."
            ),
            fontSize = 13.sp,
            color = PosColors.TextMedium
        )

        TouchTextField(
            value = name,
            onValueChange = onNameChange,
            label = "${strings.ownerName} *",
            errorMessage = nameError,
            modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TouchPinField(
                pin = pin,
                onPinChange = onPinChange,
                label = "${strings.pinCode} (4-6 ch.) *",
                errorMessage = pinError,
                maxDigits = 6,
                modifier = Modifier.weight(1f)
            )
            TouchPinField(
                pin = pinConfirm,
                onPinChange = onPinConfirmChange,
                label = "${strings.confirmPin} *",
                errorMessage = pinConfirmError,
                maxDigits = 6,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StepFirstRegister(strings: DesktopStrings, name: String, onUpdate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(strings.text("Caisse principale", "Main register", "الصندوق الرئيسي"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Spacer(modifier = Modifier.height(16.dp))
        TouchTextField(
            value = name,
            onValueChange = onUpdate,
            label = strings.text("Nom de la caisse", "Register name", "اسم الصندوق"),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepSummary(
    strings: DesktopStrings,
    restaurantName: String,
    language: String,
    ownerName: String,
    registerName: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(strings.text("Résumé de la configuration", "Setup summary", "ملخص الإعداد"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PosColors.Surface),
            border = BorderStroke(1.dp, PosColors.Border)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${strings.companyNameLabel}: $restaurantName", fontWeight = FontWeight.Bold, color = PosColors.TextHigh)
                Text("${strings.languageLabel}: ${language.uppercase()}", color = PosColors.TextMedium)
                Text("${strings.ownerRole}: $ownerName", color = PosColors.TextMedium)
                Text("${strings.text("Caisse principale", "Main register", "الصندوق الرئيسي")}: $registerName", color = PosColors.TextMedium)
                Text(strings.text("Devise : MAD", "Currency: MAD", "العملة: MAD"), color = PosColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
