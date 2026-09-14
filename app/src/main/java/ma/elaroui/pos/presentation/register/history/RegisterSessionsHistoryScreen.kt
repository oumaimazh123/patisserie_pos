package ma.elaroui.pos.presentation.register.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.core.util.MonetaryUtils
import ma.elaroui.pos.domain.model.RegisterSession
import ma.elaroui.pos.domain.model.RegisterSessionStatus
import ma.elaroui.pos.domain.model.RegisterOperationType
import ma.elaroui.pos.domain.model.RegisterSessionOperation
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun RegisterSessionsHistoryScreen(
    viewModel: RegisterHistoryViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    RegisterSessionsHistoryContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::loadHistory,
        onSearch = viewModel::updateSearchQuery,
        onSelectCashier = viewModel::selectCashier,
        onSelectSession = viewModel::selectSession,
        onDismissDetail = viewModel::clearSelectedSession,
        onClearError = viewModel::clearError
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterSessionsHistoryContent(
    uiState: RegisterHistoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectCashier: (Long?) -> Unit,
    onSelectSession: (RegisterSession) -> Unit,
    onDismissDetail: () -> Unit,
    onClearError: () -> Unit
) {
    Scaffold(
        modifier = Modifier.testTag("register_history_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Historique des sessions de caisse",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Retour", color = RegisterNavy, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Actualiser") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .testTag("register_history_refresh")
                                .semantics { contentDescription = "Actualiser" }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("register_history_content"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SearchAndCashierFilters(
                    searchQuery = uiState.searchQuery,
                    users = uiState.usersMap.values
                        .sortedBy { it.name }
                        .map { it.id to it.name },
                    selectedCashierId = uiState.cashierFilterId,
                    onSearch = onSearch,
                    onSelectCashier = onSelectCashier
                )
            }
            item {
                Text(
                    "${uiState.sessions.size} résultat(s)",
                    color = RegisterSlate,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("register_history_result_count")
                )
            }

            uiState.errorMessage?.let { message ->
                item {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFC62828),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(message, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearError) { Text("Fermer") }
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.sessions.isEmpty() -> item {
                    Surface(
                        color = RegisterLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_history_empty")
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Aucune session trouvée",
                                color = RegisterNavy,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Aucune session ne correspond à votre recherche.", color = RegisterSlate)
                        }
                    }
                }

                else -> {
                    items(uiState.sessions, key = { it.id }) { session ->
                        SessionHistoryCard(
                            session = session,
                            cashierName = uiState.usersMap[session.cashierId]?.name
                                ?: "Utilisateur #${session.cashierId}",
                            onClick = { onSelectSession(session) }
                        )
                    }
                }
            }
        }
    }

    uiState.selectedSession?.let { session ->
        SessionDetailDialog(
            session = session,
            cashierName = uiState.usersMap[session.cashierId]?.name
                ?: "Utilisateur #${session.cashierId}",
            operations = uiState.selectedSessionOperations,
            isLoading = uiState.isDetailLoading,
            onDismiss = onDismissDetail
        )
    }
}

@Composable
private fun SearchAndCashierFilters(
    searchQuery: String,
    users: List<Pair<Long, String>>,
    selectedCashierId: Long?,
    onSearch: (String) -> Unit,
    onSelectCashier: (Long?) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabletLandscape = maxWidth >= 700.dp
        if (tabletLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactSessionSearch(
                    query = searchQuery,
                    onSearch = onSearch,
                    modifier = Modifier.weight(1f)
                )
                CashierFilter(
                    options = users,
                    selectedId = selectedCashierId,
                    onSelect = onSelectCashier,
                    modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactSessionSearch(
                    query = searchQuery,
                    onSearch = onSearch,
                    modifier = Modifier.fillMaxWidth()
                )
                CashierFilter(
                    options = users,
                    selectedId = selectedCashierId,
                    onSelect = onSelectCashier,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun CompactSessionSearch(
    query: String,
    onSearch: (String) -> Unit,
    modifier: Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onSearch,
        placeholder = {
            Text(
                "Rechercher par numéro de session ou caissier",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onSearch("") },
                    modifier = Modifier
                        .testTag("register_history_search_clear")
                        .semantics { contentDescription = "Effacer la recherche" }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        ),
        modifier = modifier
            .heightIn(min = 52.dp)
            .testTag("register_history_search")
    )
}

@Composable
private fun CashierFilter(
    selectedId: Long?,
    options: List<Pair<Long, String>>,
    onSelect: (Long?) -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.first == selectedId }?.second
    Box(modifier) {
        FilterChip(
            selected = selectedId != null,
            onClick = { expanded = !expanded },
            label = {
                Text(
                    "Caissier : ${selected ?: "Tous"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) {
                        "Fermer la liste des caissiers"
                    } else {
                        "Ouvrir la liste des caissiers"
                    },
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.testTag("register_history_cashier_filter")
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 180.dp, max = 280.dp)
                .testTag("register_history_cashier_menu")
        ) {
            DropdownMenuItem(
                text = { Text("Tous") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: RegisterSession,
    cashierName: String,
    onClick: () -> Unit
) {
    val locale = java.util.Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("register_session_${session.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SessionIdentity(
                        session,
                        cashierName,
                        locale,
                        Modifier.weight(1.4f)
                    )
                    SessionAmounts(session, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionIdentity(
                        session,
                        cashierName,
                        locale,
                        Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                    SessionAmounts(session, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SessionIdentity(
    session: RegisterSession,
    cashierName: String,
    locale: java.util.Locale,
    modifier: Modifier
) {
    Column(modifier) {
        Text(
            "Session #${session.id}",
            fontWeight = FontWeight.Bold,
            color = RegisterNavy
        )
        Text("Ouverte par : $cashierName", color = RegisterSlate, fontSize = 12.sp)
        Text(
            "Ouverture : ${formatSessionDate(session.openedAt, locale)}",
            color = RegisterSlate,
            fontSize = 12.sp
        )
        Text(
            "Clôture : ${
                session.closedAt?.let { formatSessionDate(it, locale) } ?: "—"
            }",
            color = RegisterSlate,
            fontSize = 12.sp
        )
        Text(
            if (session.status == RegisterSessionStatus.OPEN) "OUVERTE" else "FERMÉE",
            color = if (session.status == RegisterSessionStatus.OPEN) {
                RegisterGreen
            } else {
                RegisterSlate
            },
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SessionAmounts(session: RegisterSession, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SessionAmountLine("Fond initial", session.openingCashCentimes)
        SessionAmountLine("Attendu", session.expectedCashCentimes)
        SessionAmountLine("Compté", session.countedCashCentimes)
        SessionAmountLine(
            "Écart",
            session.differenceCentimes,
            session.differenceCentimes?.let { if (it == 0L) RegisterGreen else RegisterRed }
                ?: RegisterSlate
        )
    }
}

@Composable
private fun SessionAmountLine(label: String, amount: Long?, color: Color = RegisterNavy) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = RegisterSlate, fontSize = 11.sp)
        Text(
            amount?.let(MonetaryUtils::formatDh) ?: "—",
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun SessionDetailDialog(
    session: RegisterSession,
    cashierName: String,
    operations: List<RegisterSessionOperation>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val locale = java.util.Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    AlertDialog(
        modifier = Modifier.testTag("register_session_detail"),
        onDismissRequest = onDismiss,
        title = { Text("Session #${session.id}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Ouverte par : $cashierName", color = RegisterSlate)
                    Text(
                        "Ouverture : ${formatSessionDate(session.openedAt, locale)}",
                        color = RegisterSlate
                    )
                    session.closedAt?.let {
                        Text(
                            "Clôture : ${formatSessionDate(it, locale)}",
                            color = RegisterSlate
                        )
                    }
                }
                item { HorizontalDivider() }
                item { DetailAmountRow("Fond initial", session.openingCashCentimes) }
                if (session.status == RegisterSessionStatus.CLOSED) {
                    item {
                        DetailAmountRow(
                            "Attendu",
                            session.expectedCashCentimes ?: 0L
                        )
                    }
                    item {
                        DetailAmountRow(
                            "Compté",
                            session.countedCashCentimes ?: 0L
                        )
                    }
                    item {
                        DetailAmountRow(
                            "Écart",
                            session.differenceCentimes ?: 0L,
                            if ((session.differenceCentimes ?: 0L) == 0L) {
                                RegisterGreen
                            } else {
                                RegisterRed
                            }
                        )
                    }
                    session.closingNote?.let { note ->
                        item {
                            Surface(
                                color = RegisterLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Note : $note", modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                    if (session.ownerApprovedDifference) {
                        item {
                            Text(
                                "Écart approuvé par le propriétaire",
                                color = RegisterGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text(
                        "Historique des opérations (${operations.size})",
                        fontWeight = FontWeight.Bold,
                        color = RegisterNavy,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                when {
                    isLoading -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    operations.isEmpty() -> item {
                        Text("Aucune opération.", color = RegisterSlate)
                    }
                    else -> items(operations, key = { it.stableId }) { operation ->
                        OperationDetailRow(operation, locale)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RegisterNavy)
            ) {
                Text("Fermer")
            }
        }
    )
}

@Composable
private fun DetailAmountRow(label: String, amount: Long, color: Color = RegisterNavy) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = RegisterSlate)
        Text(MonetaryUtils.formatDh(amount), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OperationDetailRow(
    operation: RegisterSessionOperation,
    locale: java.util.Locale
) {
    val positive = operation.type in listOf(
        RegisterOperationType.SALE_COMPLETED,
        RegisterOperationType.CASH_IN
    )
    val negative = operation.type in listOf(
        RegisterOperationType.ORDER_CANCELLED,
        RegisterOperationType.CASH_OUT
    )
    val color = when {
        positive -> RegisterGreen
        negative -> RegisterRed
        else -> RegisterNavy
    }
    Surface(
        color = when {
            positive -> Color(0xFFE8F5E9)
            negative -> Color(0xFFFFEBEE)
            else -> RegisterLight
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(operation.description, fontWeight = FontWeight.SemiBold)
                Text(
                    "${operation.userName} • ${formatSessionDate(operation.occurredAt, locale)}",
                    fontSize = 11.sp,
                    color = RegisterSlate
                )
                operation.reference?.let {
                    Text(it, fontSize = 11.sp, color = RegisterSlate)
                }
            }
            operation.amountCentimes?.let { amount ->
                Text(
                    "${if (positive) "+" else if (negative) "−" else ""} ${
                        MonetaryUtils.formatDh(amount)
                    }",
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatSessionDate(timeMs: Long, locale: java.util.Locale): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(timeMs))

private val RegisterNavy = Color(0xFF1D3557)
private val RegisterGreen = Color(0xFF1E8E3E)
private val RegisterRed = Color(0xFFE63946)
private val RegisterSlate = Color(0xFF64748B)
private val RegisterLight = Color(0xFFF8F9FA)
