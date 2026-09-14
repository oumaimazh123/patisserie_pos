package ma.elaroui.pos.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.elaroui.pos.domain.model.User
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.presentation.components.NumericKeypad
import ma.elaroui.pos.presentation.components.PinIndicator
import ma.elaroui.pos.presentation.adaptive.LocalAdaptiveDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSelectionScreen(
    viewModel: AuthViewModel,
    onUserAuthenticated: (User) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val adaptive = LocalAdaptiveDimensions.current

    if (uiState.selectedUser != null) {
        PinLoginScreen(
            uiState = uiState,
            onBackClick = { viewModel.deselectUser() },
            onDigitClick = { viewModel.appendDigit(it) },
            onDeleteClick = { viewModel.deleteDigit() },
            onClearClick = { viewModel.clearPin() },
            onSubmitPin = { viewModel.submitPin(onUserAuthenticated) }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sélection de l'utilisateur", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA))
                    .padding(adaptive.screenContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Veuillez choisir votre compte",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557),
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = adaptive.productCardMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(adaptive.gridSpacing),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.activeUsers) { user ->
                        UserCard(user = user, onClick = { viewModel.selectUser(user) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (user.role == UserRole.OWNER) Color(0xFFE63946) else Color(0xFF1D3557)
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
                Text(text = user.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(
                    text = if (user.role == UserRole.OWNER) "Propriétaire" else "Caissier",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinLoginScreen(
    uiState: AuthUiState,
    onBackClick: () -> Unit,
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmitPin: () -> Unit
) {
    val selectedUser = uiState.selectedUser ?: return
    val adaptive = LocalAdaptiveDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connexion PIN", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← Changer", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1D3557))
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onSubmitPin,
                    enabled = uiState.enteredPin.length >= 4 && !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = adaptive.screenPadding, vertical = 12.dp)
                        .height(52.dp)
                        .testTag("pin_confirm_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE63946)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(
                            "Confirmer le PIN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
                .padding(adaptive.screenContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = selectedUser.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D3557)
            )
            Text(
                text = "Entrez votre code PIN (4-6 chiffres)",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PinIndicator(length = uiState.enteredPin.length)

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFE63946),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            NumericKeypad(
                onDigitClick = onDigitClick,
                onDeleteClick = onDeleteClick,
                onClearClick = onClearClick
            )
        }
    }
}
