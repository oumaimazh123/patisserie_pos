package ma.elaroui.pos.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import ma.elaroui.pos.core.license.LicenseStatus
import ma.elaroui.pos.data.local.preferences.SetupPreferences
import ma.elaroui.pos.domain.license.LicenseManager
import ma.elaroui.pos.domain.model.UserRole
import ma.elaroui.pos.domain.session.SessionManager
import ma.elaroui.pos.presentation.auth.AuthViewModel
import ma.elaroui.pos.presentation.auth.UserSelectionScreen
import ma.elaroui.pos.presentation.cashiers.CashierManagementScreen
import ma.elaroui.pos.presentation.cashiers.CashierViewModel
import ma.elaroui.pos.presentation.dashboard.DashboardScreen
import ma.elaroui.pos.presentation.dashboard.DashboardViewModel
import ma.elaroui.pos.presentation.license.LicenseManagementScreen
import ma.elaroui.pos.presentation.license.LicenseViewModel
import ma.elaroui.pos.presentation.license.TrialBanner
import ma.elaroui.pos.presentation.management.categories.CategoryManagementScreen
import ma.elaroui.pos.presentation.management.categories.CategoryViewModel
import ma.elaroui.pos.presentation.management.products.ProductManagementScreen
import ma.elaroui.pos.presentation.management.products.ProductViewModel
import ma.elaroui.pos.presentation.management.products.preview.CataloguePreviewScreen
import ma.elaroui.pos.presentation.management.tables.TableManagementScreen
import ma.elaroui.pos.presentation.management.tables.TableManagementViewModel
import ma.elaroui.pos.presentation.payment.PaymentScreen
import ma.elaroui.pos.presentation.payment.PaymentViewModel
import ma.elaroui.pos.presentation.pos.active.ActiveOrdersScreen
import ma.elaroui.pos.presentation.pos.active.ActiveOrdersViewModel
import ma.elaroui.pos.presentation.pos.main.POSMainScreen
import ma.elaroui.pos.presentation.pos.main.POSMainViewModel
import ma.elaroui.pos.presentation.register.close.CloseRegisterScreen
import ma.elaroui.pos.presentation.register.close.CloseRegisterViewModel
import ma.elaroui.pos.presentation.register.current.CurrentSessionScreen
import ma.elaroui.pos.presentation.register.current.CurrentSessionViewModel
import ma.elaroui.pos.presentation.register.history.RegisterHistoryViewModel
import ma.elaroui.pos.presentation.register.history.RegisterSessionsHistoryScreen
import ma.elaroui.pos.presentation.register.open.OpenRegisterScreen
import ma.elaroui.pos.presentation.register.open.OpenRegisterViewModel
import ma.elaroui.pos.presentation.reports.DailySalesScreen
import ma.elaroui.pos.presentation.reports.DailySalesViewModel
import ma.elaroui.pos.presentation.sales.CompletedSalesScreen
import ma.elaroui.pos.presentation.sales.CompletedSalesViewModel
import ma.elaroui.pos.presentation.sales.ReceiptPreviewScreen
import ma.elaroui.pos.presentation.sales.SaleDetailScreen
import ma.elaroui.pos.presentation.sales.SaleDetailViewModel
import ma.elaroui.pos.presentation.settings.BackupRestoreScreen
import ma.elaroui.pos.presentation.settings.PrinterSettingsScreen
import ma.elaroui.pos.presentation.settings.SettingsScreen
import ma.elaroui.pos.presentation.settings.SettingsViewModel
import ma.elaroui.pos.presentation.setup.SetupScreen
import ma.elaroui.pos.presentation.setup.SetupViewModel

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.switchToUserSelection(sessionManager: SessionManager) {
    // Leave the role-protected destination before clearing the session. Clearing the
    // session first makes that destination's guard race this navigation operation.
    resetTo(Screen.UserSelection.route)
    sessionManager.logout()
}

@Composable
fun POSNavGraph(
    setupPreferences: SetupPreferences,
    sessionManager: SessionManager,
    licenseManager: LicenseManager,
    navController: NavHostController = rememberNavController()
) {
    val isSetupComplete = setupPreferences.isSetupComplete
    val sessionState by sessionManager.sessionState.collectAsState()
    val licenseState by licenseManager.licenseState.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isLicenseBlocking = licenseState.status in listOf(
        LicenseStatus.TRIAL_EXPIRED,
        LicenseStatus.LICENCE_EXPIRED,
        LicenseStatus.LICENCE_INVALID,
        LicenseStatus.LICENCE_WRONG_DEVICE,
        LicenseStatus.CLOCK_ROLLBACK_DETECTED
    )

    val startDestination = when {
        isLicenseBlocking -> Screen.LicenseManagement.route
        !isSetupComplete -> Screen.Setup.route
        sessionState == null -> Screen.UserSelection.route
        sessionState?.isLocked == true -> Screen.UserSelection.route
        sessionState?.role == UserRole.OWNER -> Screen.Dashboard.route
        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
        else -> Screen.PosMain.route
    }

    LaunchedEffect(licenseState.status, licenseState.remainingTrialTimeMs) {
        if (licenseState.status in listOf(
                LicenseStatus.TRIAL_ACTIVE,
                LicenseStatus.TRIAL_EXPIRING_SOON
            )
        ) {
            delay(minOf(60_000L, licenseState.remainingTrialTimeMs.coerceAtLeast(1L)))
            licenseManager.refreshLicenseState()
        }
    }

    LaunchedEffect(isLicenseBlocking, currentRoute) {
        if (isLicenseBlocking && currentRoute != Screen.LicenseManagement.route) {
            navController.resetTo(Screen.LicenseManagement.route)
        }
    }

    LaunchedEffect(sessionState?.isLocked, currentRoute, isLicenseBlocking) {
        if (
            sessionState?.isLocked == true &&
            !isLicenseBlocking &&
            currentRoute != Screen.UserSelection.route
        ) {
            // Locking must remove protected destinations from the back stack so the
            // Android Back action cannot reopen the POS without PIN authentication.
            navController.resetTo(Screen.UserSelection.route)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val showTrialBanner = licenseState.status in listOf(
            LicenseStatus.TRIAL_ACTIVE,
            LicenseStatus.TRIAL_EXPIRING_SOON
        ) && currentRoute != Screen.LicenseManagement.route

        if (showTrialBanner) {
            TrialBanner(
                licenseState = licenseState,
                onActivate = {
                    navController.navigate(Screen.LicenseManagement.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            composable(Screen.LicenseManagement.route) {
                val licenseViewModel: LicenseViewModel = hiltViewModel()
                LicenseManagementScreen(
                    viewModel = licenseViewModel,
                    canNavigateBack = !isLicenseBlocking,
                    onBack = {
                        if (!isLicenseBlocking) {
                            val returnedToPreviousScreen = navController.popBackStack()
                            if (!returnedToPreviousScreen) {
                                val nextRoute = when {
                                    !isSetupComplete -> Screen.Setup.route
                                    sessionState == null -> Screen.UserSelection.route
                                    sessionState?.role == UserRole.OWNER -> Screen.Dashboard.route
                                    sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                                    else -> Screen.PosMain.route
                                }
                                navController.navigate(nextRoute) {
                                    popUpTo(Screen.LicenseManagement.route) { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }

        composable(Screen.Setup.route) {
            val setupViewModel: SetupViewModel = hiltViewModel()
            SetupScreen(
                viewModel = setupViewModel,
                onSetupComplete = {
                    navController.navigate(Screen.UserSelection.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.UserSelection.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            UserSelectionScreen(
                viewModel = authViewModel,
                onUserAuthenticated = { user ->
                    if (user.role == UserRole.OWNER) {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.UserSelection.route) { inclusive = true }
                        }
                    } else {
                        val session = sessionManager.currentUser
                        if (session?.currentRegisterSessionId == null) {
                            navController.navigate(Screen.OpenRegister.route) {
                                popUpTo(Screen.UserSelection.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.PosMain.route) {
                                popUpTo(Screen.UserSelection.route) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        // Owner Dashboard Route
        composable(Screen.Dashboard.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToPos = {
                        if (sessionState?.currentRegisterSessionId == null) {
                            navController.navigate(Screen.OpenRegister.route)
                        } else {
                            navController.navigate(Screen.PosMain.route)
                        }
                    },
                    onNavigateToSales = { navController.navigate(Screen.CompletedSales.route) },
                    onNavigateToDailyReport = { navController.navigate(Screen.DailySalesReport.route) },
                    onNavigateToProducts = { navController.navigate(Screen.ProductManagement.route) },
                    onNavigateToCategories = { navController.navigate(Screen.CategoryManagement.route) },
                    onNavigateToTables = { navController.navigate(Screen.TableManagement.route) },
                    onNavigateToCashiers = { navController.navigate(Screen.CashierManagement.route) },
                    onNavigateToRegisterHistory = { navController.navigate(Screen.RegisterHistory.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onLockPos = { sessionManager.lock() },
                    onSwitchUser = {
                        navController.switchToUserSelection(sessionManager)
                    }
                )
            }
        }

        composable(Screen.CashierManagement.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val cashierViewModel: CashierViewModel = hiltViewModel()
                CashierManagementScreen(
                    viewModel = cashierViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.RegisterHistory.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val historyViewModel: RegisterHistoryViewModel = hiltViewModel()
                RegisterSessionsHistoryScreen(
                    viewModel = historyViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.CategoryManagement.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val categoryViewModel: CategoryViewModel = hiltViewModel()
                CategoryManagementScreen(
                    viewModel = categoryViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.ProductManagement.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val productViewModel: ProductViewModel = hiltViewModel()
                ProductManagementScreen(
                    viewModel = productViewModel,
                    onNavigateToPreview = { navController.navigate(Screen.CataloguePreview.route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.CataloguePreview.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val productViewModel: ProductViewModel = hiltViewModel()
                CataloguePreviewScreen(
                    viewModel = productViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.TableManagement.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState) {
                    val target = when {
                        sessionState == null -> Screen.UserSelection.route
                        sessionState?.currentRegisterSessionId == null -> Screen.OpenRegister.route
                        else -> Screen.PosMain.route
                    }
                    navController.resetTo(target)
                }
            } else {
                val tableViewModel: TableManagementViewModel = hiltViewModel()
                TableManagementScreen(
                    viewModel = tableViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Cashier & POS Routes
        composable(Screen.OpenRegister.route) {
            val openViewModel: OpenRegisterViewModel = hiltViewModel()
            val isOwner = sessionState?.role == UserRole.OWNER
            OpenRegisterScreen(
                viewModel = openViewModel,
                isOwner = isOwner,
                onBackToDashboard = {
                    if (isOwner) {
                        navController.resetTo(Screen.Dashboard.route)
                    }
                },
                onLock = {
                    sessionManager.lock()
                    navController.resetTo(Screen.UserSelection.route)
                },
                onSessionOpened = {
                    navController.navigate(Screen.PosMain.route) {
                        popUpTo(Screen.OpenRegister.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PosMain.route) {
            val posMainViewModel: POSMainViewModel = hiltViewModel()
            POSMainScreen(
                viewModel = posMainViewModel,
                onNavigateToActiveOrders = { navController.navigate(Screen.ActiveOrders.route) },
                onNavigateToSessionDetails = { navController.navigate(Screen.CurrentSession.route) },
                onNavigateToPayment = { orderId ->
                    navController.navigate(Screen.Payment.createRoute(orderId))
                },
                onLockPos = { sessionManager.lock() },
                onSwitchUser = {
                    navController.switchToUserSelection(sessionManager)
                }
            )
        }

        composable(Screen.ActiveOrders.route) {
            val activeOrdersViewModel: ActiveOrdersViewModel = hiltViewModel()
            ActiveOrdersScreen(
                viewModel = activeOrdersViewModel,
                onResumeOrder = { orderId ->
                    navController.navigate(Screen.PosMain.route) {
                        popUpTo(Screen.PosMain.route) { inclusive = true }
                    }
                },
                onNavigateToPayment = { orderId ->
                    navController.navigate(Screen.Payment.createRoute(orderId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Step 7: Payment Route
        composable(
            route = Screen.Payment.route,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) {
            val paymentViewModel: PaymentViewModel = hiltViewModel()
            PaymentScreen(
                viewModel = paymentViewModel,
                onPaymentCompletedSuccess = { orderId ->
                    navController.navigate(Screen.ReceiptPreview.createRoute(orderId)) {
                        popUpTo(Screen.PosMain.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Step 7/8: Sales & Receipt Routes
        composable(Screen.CompletedSales.route) {
            val salesViewModel: CompletedSalesViewModel = hiltViewModel()
            CompletedSalesScreen(
                viewModel = salesViewModel,
                onSelectSale = { orderId ->
                    navController.navigate(Screen.SaleDetail.createRoute(orderId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SaleDetail.route,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) {
            val detailViewModel: SaleDetailViewModel = hiltViewModel()
            SaleDetailScreen(
                viewModel = detailViewModel,
                onNavigateToReceiptPreview = { orderId ->
                    navController.navigate(Screen.ReceiptPreview.createRoute(orderId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ReceiptPreview.route,
            arguments = listOf(navArgument("orderId") { type = NavType.LongType })
        ) {
            val detailViewModel: SaleDetailViewModel = hiltViewModel()
            ReceiptPreviewScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Step 8: Reports & Settings Routes
        composable(Screen.DailySalesReport.route) {
            if (sessionState?.role != UserRole.OWNER) {
                navController.popBackStack()
            } else {
                val reportViewModel: DailySalesViewModel = hiltViewModel()
                DailySalesScreen(
                    viewModel = reportViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Settings.route) {
            if (sessionState?.role != UserRole.OWNER) {
                navController.popBackStack()
            } else {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToPrinterSettings = { navController.navigate(Screen.PrinterSettings.route) },
                    onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToLicenseManagement = { navController.navigate(Screen.LicenseManagement.route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.PrinterSettings.route) {
            if (sessionState?.role != UserRole.OWNER) {
                navController.popBackStack()
            } else {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                PrinterSettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.BackupRestore.route) {
            if (sessionState?.role != UserRole.OWNER) {
                navController.popBackStack()
            } else {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                BackupRestoreScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.CurrentSession.route) {
            val currentViewModel: CurrentSessionViewModel = hiltViewModel()
            CurrentSessionScreen(
                viewModel = currentViewModel,
                onNavigateToCloseRegister = { navController.navigate(Screen.CloseRegister.route) }
            )
        }

        composable(Screen.CloseRegister.route) {
            if (sessionState?.role != UserRole.OWNER) {
                LaunchedEffect(sessionState?.role) {
                    navController.popBackStack()
                }
            } else {
                val closeViewModel: CloseRegisterViewModel = hiltViewModel()
                CloseRegisterScreen(
                    viewModel = closeViewModel,
                    onRegisterClosed = {
                        navController.resetTo(Screen.UserSelection.route)
                    }
                )
            }
        }
        }
    }
}
