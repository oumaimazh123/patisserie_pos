package ma.elaroui.pos.desktop.presentation.cashiers

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.presentation.components.EmptyStateCard
import ma.elaroui.pos.desktop.presentation.components.ManagementPageHeader
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.TouchPinField
import ma.elaroui.pos.desktop.presentation.components.TouchTextField
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.shared.domain.User
import ma.elaroui.pos.shared.domain.UserRole
import ma.elaroui.pos.shared.rules.PinValidationError
import ma.elaroui.pos.shared.rules.PinValidationRules

import ma.elaroui.pos.desktop.presentation.components.PosInlineAlert
import ma.elaroui.pos.desktop.presentation.model.UiMessage

@Composable
fun CashierManagementScreen(
    users: List<User>,
    strings: DesktopStrings,
    onCreateCashier: (name: String, pin: String) -> String?,
    onUpdateCashier: (id: Long, name: String?, pin: String?, active: Boolean?) -> String?,
    onSoftDeleteCashier: (User) -> Unit = {},
    onUpdateOwnerPin: ((newPin: String) -> String?)? = null,
    onBack: (() -> Unit)? = null,
    message: String = "",
    uiMessage: UiMessage? = null,
    onClearMessage: () -> Unit = {}
) {
    var showCashierDialog by remember { mutableStateOf(false) }
    var showOwnerPinDialog by remember { mutableStateOf(false) }
    var editingCashier by remember { mutableStateOf<User?>(null) }
    var cashierToDelete by remember { mutableStateOf<User?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var nameInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var activeInput by remember { mutableStateOf(true) }

    var cashierNameError by remember { mutableStateOf<String?>(null) }
    var cashierPinError by remember { mutableStateOf<String?>(null) }
    var cashierConfirmPinError by remember { mutableStateOf<String?>(null) }

    var ownerPinInput by remember { mutableStateOf("") }
    var ownerConfirmPinInput by remember { mutableStateOf("") }
    var ownerPinError by remember { mutableStateOf<String?>(null) }
    var ownerConfirmPinError by remember { mutableStateOf<String?>(null) }

    var feedbackMessage by remember { mutableStateOf(message) }

    val ownerUser = users.firstOrNull { it.role == UserRole.OWNER }
    val cashiers = users.filter { it.role == UserRole.CASHIER }
    val filteredCashiers = cashiers.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
    }

    fun openAdd() {
        editingCashier = null
        nameInput = ""
        pinInput = ""
        confirmPinInput = ""
        activeInput = true
        cashierNameError = null
        cashierPinError = null
        cashierConfirmPinError = null
        showCashierDialog = true
    }

    fun openEdit(cashier: User) {
        editingCashier = cashier
        nameInput = cashier.name
        pinInput = ""
        confirmPinInput = ""
        activeInput = cashier.active
        cashierNameError = null
        cashierPinError = null
        cashierConfirmPinError = null
        showCashierDialog = true
    }

    fun openOwnerPinDialog() {
        ownerPinInput = ""
        ownerConfirmPinInput = ""
        ownerPinError = null
        ownerConfirmPinError = null
        showOwnerPinDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PosColors.Canvas)
    ) {
        ManagementPageHeader(
            title = strings.cashierMgmt,
            strings = strings,
            onBackToDashboard = onBack
        ) {
            Button(
                onClick = { openAdd() },
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand),
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    "+ " + strings.cashierRole,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 850.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Search Input Field
                TouchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = strings.text("Rechercher un caissier par nom…", "Search cashier by name…", "البحث عن كاشير بالاسم…"),
                    leadingIcon = { Text("🔍", fontSize = 16.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PosColors.Primary,
                        unfocusedBorderColor = PosColors.Border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (feedbackMessage.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PosColors.PrimaryLight,
                        border = BorderStroke(1.dp, PosColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            feedbackMessage,
                            color = PosColors.BakeryBrown,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (ownerUser != null && onUpdateOwnerPin != null && searchQuery.isBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = PosColors.Workspace),
                            border = BorderStroke(1.dp, PosColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(PosColors.BakeryBrown),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🛡️", fontSize = 20.sp)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            ownerUser.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PosColors.BakeryBrown
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = PosColors.PrimaryLight,
                                            border = BorderStroke(1.dp, PosColors.Primary)
                                        ) {
                                            Text(
                                                strings.ownerRole,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PosColors.PrimaryDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = { openOwnerPinDialog() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    modifier = Modifier.height(40.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        strings.text("Modifier mon PIN", "Change my PIN", "تغيير رمز PIN"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                }

                if (filteredCashiers.isEmpty()) {
                        EmptyStateCard(
                            title = if (searchQuery.isNotBlank()) strings.noResults else strings.text("Aucun compte caissier configuré", "No cashier account configured", "لا يوجد حساب كاشير مضاف"),
                            modifier = Modifier.weight(1f)
                        )
                } else {
                        val cashiersListState = remember { androidx.compose.foundation.lazy.LazyListState() }
                        LazyColumn(
                            state = cashiersListState,
                            modifier = Modifier
                                .weight(1f)
                                .touchDragScroll(cashiersListState),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredCashiers) { cashier ->
                                CashierRowCard(
                                    cashier = cashier,
                                    strings = strings,
                                    onEdit = { openEdit(cashier) },
                                    onToggleStatus = {
                                        val err = onUpdateCashier(cashier.id, null, null, !cashier.active)
                                        if (err != null) {
                                            feedbackMessage = err
                                        }
                                    },
                                    onDelete = { cashierToDelete = cashier }
                                )
                            }
                        }
                }
            }
        }
    }

    // Add / Edit Cashier Modal Dialog
    if (showCashierDialog) {
        val isEditing = editingCashier != null

        AlertDialog(
            onDismissRequest = { showCashierDialog = false },
            title = {
                Text(
                    if (isEditing) strings.text("Modifier le caissier", "Edit Cashier", "تعديل الكاشير") else strings.text("Nouveau compte caissier", "New Cashier Account", "حساب كاشير جديد"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .touchDragScroll(dialogScrollState)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TouchTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it; cashierNameError = null },
                        label = strings.cashierRole + " *",
                        placeholder = strings.text("Nom du caissier", "Cashier name", "اسم الكاشير"),
                        errorMessage = cashierNameError,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchPinField(
                        pin = pinInput,
                        onPinChange = { pinInput = it; cashierPinError = null },
                        label = if (isEditing) strings.text("Nouveau PIN (4 à 6 chiffres, Optionnel)", "New PIN (4-6 digits, Optional)", "رمز PIN جديد (اختياري)") else strings.text("Code PIN (4 à 6 chiffres) *", "PIN Code (4-6 digits) *", "رمز PIN (4 إلى 6 أرقام) *"),
                        placeholder = "4 à 6 chiffres",
                        errorMessage = cashierPinError,
                        maxDigits = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchPinField(
                        pin = confirmPinInput,
                        onPinChange = { confirmPinInput = it; cashierConfirmPinError = null },
                        label = strings.confirmPin + if (!isEditing || pinInput.isNotEmpty()) " *" else "",
                        placeholder = "Confirmer le code PIN",
                        errorMessage = cashierConfirmPinError,
                        maxDigits = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isEditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeInput = !activeInput }
                                .padding(vertical = 4.dp)
                        ) {
                            Switch(
                                checked = activeInput,
                                onCheckedChange = { activeInput = it }
                            )
                            Column {
                                Text(
                                    if (activeInput) strings.text("Compte actif", "Active account", "حساب نشط")
                                    else strings.text("Compte désactivé", "Deactivated account", "حساب معطل"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = PosColors.TextHigh
                                )
                                Text(
                                    if (activeInput) strings.text("Autorisé à se connecter", "Allowed to login", "مسموح له بتسجيل الدخول")
                                    else strings.text("Connexion bloquée", "Login blocked", "تسجيل الدخول محظور"),
                                    fontSize = 11.sp,
                                    color = PosColors.TextMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        cashierNameError = null
                        cashierPinError = null
                        cashierConfirmPinError = null

                        if (nameInput.isBlank()) {
                            cashierNameError = strings.required
                            return@Button
                        }

                        if (!isEditing || pinInput.isNotEmpty()) {
                            val pinErr = PinValidationRules.validatePinConfirmation(pinInput, confirmPinInput)
                            if (pinErr != null) {
                                when (pinErr) {
                                    PinValidationError.BLANK -> cashierPinError = strings.required
                                    PinValidationError.INVALID_LENGTH -> cashierPinError = "Le PIN doit comporter 4 à 6 chiffres."
                                    PinValidationError.NOT_DIGITS -> cashierPinError = "Le PIN ne doit contenir que des chiffres."
                                    PinValidationError.MISMATCH -> cashierConfirmPinError = strings.pinMismatch
                                }
                                return@Button
                            }
                            val saveError = if (isEditing) {
                                onUpdateCashier(editingCashier!!.id, nameInput.trim(), pinInput, activeInput)
                            } else {
                                onCreateCashier(nameInput.trim(), pinInput)
                            }
                            if (saveError == null) {
                                showCashierDialog = false
                            } else {
                                cashierNameError = saveError
                            }
                        } else {
                            val saveError = onUpdateCashier(editingCashier!!.id, nameInput.trim(), null, activeInput)
                            if (saveError == null) {
                                showCashierDialog = false
                            } else {
                                cashierNameError = saveError
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCashierDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Edit Owner PIN Modal Dialog
    if (showOwnerPinDialog && onUpdateOwnerPin != null) {
        AlertDialog(
            onDismissRequest = { showOwnerPinDialog = false },
            title = {
                Text(
                    strings.text("Modifier le code PIN Administrateur", "Change Admin PIN", "تغيير رمز PIN للمشرف"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                val dialogScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .touchDragScroll(dialogScrollState)
                        .verticalScroll(dialogScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        strings.text(
                            "Définissez un nouveau code PIN sécurisé (4 à 6 chiffres) pour le compte administrateur.",
                            "Set a new secure PIN (4 to 6 digits) for the administrator account.",
                            "قم بتعيين رمز PIN آمن جديد (من 4 إلى 6 أرقام) لحساب المشرف."
                        ),
                        fontSize = 13.sp,
                        color = PosColors.TextMedium
                    )

                    TouchPinField(
                        pin = ownerPinInput,
                        onPinChange = { ownerPinInput = it; ownerPinError = null },
                        label = strings.text("Nouveau code PIN (4 à 6 chiffres) *", "New PIN Code (4-6 digits) *", "رمز PIN الجديد (4 إلى 6 أرقام) *"),
                        placeholder = "4 à 6 chiffres",
                        errorMessage = ownerPinError,
                        maxDigits = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    TouchPinField(
                        pin = ownerConfirmPinInput,
                        onPinChange = { ownerConfirmPinInput = it; ownerConfirmPinError = null },
                        label = strings.confirmPin + " *",
                        placeholder = "Confirmer le code PIN",
                        errorMessage = ownerConfirmPinError,
                        maxDigits = 6,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ownerPinError = null
                        ownerConfirmPinError = null

                        val pinErr = PinValidationRules.validatePinConfirmation(ownerPinInput, ownerConfirmPinInput)
                        if (pinErr != null) {
                            when (pinErr) {
                                PinValidationError.BLANK -> ownerPinError = strings.required
                                PinValidationError.INVALID_LENGTH -> ownerPinError = "Le PIN doit comporter 4 à 6 chiffres."
                                PinValidationError.NOT_DIGITS -> ownerPinError = "Le PIN ne doit contenir que des chiffres."
                                PinValidationError.MISMATCH -> ownerConfirmPinError = strings.pinMismatch
                            }
                            return@Button
                        }
                        val saveError = onUpdateOwnerPin(ownerPinInput)
                        if (saveError == null) {
                            showOwnerPinDialog = false
                            feedbackMessage = strings.text("Code PIN administrateur modifié avec succès.", "Admin PIN changed successfully.", "تم تغيير رمز PIN للمشرف بنجاح.")
                        } else {
                            ownerPinError = saveError
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showOwnerPinDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete Cashier Confirmation Dialog
    if (cashierToDelete != null) {
        AlertDialog(
            onDismissRequest = { cashierToDelete = null },
            title = {
                Text(
                    strings.text("Supprimer le caissier", "Delete Cashier", "حذف الكاشير"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PosColors.BakeryBrown
                )
            },
            text = {
                Text(
                    strings.text(
                        "Voulez-vous vraiment supprimer le caissier « ${cashierToDelete?.name} » ? Il ne pourra plus se connecter. L'historique des ventes et sessions passées restera intact.",
                        "Are you sure you want to delete cashier \"${cashierToDelete?.name}\"? They will no longer be able to sign in. Sales history and past sessions will remain intact.",
                        "هل أنت متأكد من حذف الكاشير « ${cashierToDelete?.name} »؟ لن يتمكن من تسجيل الدخول، وسيبقى سجل المبيعات والجلسات السابقة سليماً."
                    ),
                    fontSize = 14.sp,
                    color = PosColors.TextMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        cashierToDelete?.let { onSoftDeleteCashier(it) }
                        cashierToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Danger),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.delete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { cashierToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun CashierRowCard(
    cashier: User,
    strings: DesktopStrings,
    onEdit: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBorder by animateColorAsState(
        if (isHovered) PosColors.Primary else PosColors.Border
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHovered) PosColors.Workspace else Color.White),
        border = BorderStroke(1.dp, animatedBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHovered) 3.dp else 1.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
        ) {
            val isCompact = maxWidth < 560.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (cashier.active) PosColors.SuccessLight else PosColors.Workspace),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 18.sp)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                cashier.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (cashier.active) PosColors.SuccessLight else PosColors.DangerLight,
                                border = BorderStroke(1.dp, if (cashier.active) PosColors.Success else PosColors.Danger)
                            ) {
                                Text(
                                    if (cashier.active) "● " + strings.active else "○ " + strings.inactive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cashier.active) PosColors.Success else PosColors.Danger,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onEdit,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.edit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            if (cashier.active) {
                                OutlinedButton(
                                    onClick = onToggleStatus,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.deactivate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Button(
                                    onClick = onToggleStatus,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                    modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(strings.activate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = onDelete,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text("🗑️", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (cashier.active) PosColors.SuccessLight else PosColors.Workspace),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 20.sp)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                cashier.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PosColors.BakeryBrown
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (cashier.active) PosColors.SuccessLight else PosColors.DangerLight,
                                border = BorderStroke(1.dp, if (cashier.active) PosColors.Success else PosColors.Danger)
                            ) {
                                Text(
                                    if (cashier.active) "● " + strings.active else "○ " + strings.inactive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cashier.active) PosColors.Success else PosColors.Danger,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text(strings.edit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        if (cashier.active) {
                            OutlinedButton(
                                onClick = onToggleStatus,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.deactivate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = onToggleStatus,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                                modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(strings.activate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Danger),
                            modifier = Modifier.height(44.dp).pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Text("🗑️ " + strings.delete, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
