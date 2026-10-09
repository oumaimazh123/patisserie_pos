@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.elaroui.pos.desktop.presentation.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.skia.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ma.elaroui.pos.desktop.DesktopLanguage
import ma.elaroui.pos.desktop.DesktopStrings
import ma.elaroui.pos.desktop.platform.NativeFileDialogs
import ma.elaroui.pos.desktop.platform.DesktopApplicationPathsProvider
import ma.elaroui.pos.desktop.platform.DesktopPlatform
import ma.elaroui.pos.desktop.backup.DesktopBackupService
import ma.elaroui.pos.desktop.license.*
import ma.elaroui.pos.desktop.importing.CatalogImportAnalysis
import ma.elaroui.pos.desktop.importing.CsvImportSummary
import ma.elaroui.pos.desktop.importing.CsvImports
import ma.elaroui.pos.desktop.persistence.*
import ma.elaroui.pos.desktop.print.*
import ma.elaroui.pos.shared.application.*
import ma.elaroui.pos.shared.domain.*
import ma.elaroui.pos.desktop.presentation.auth.UserSelectionScreen
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.desktop.presentation.components.PosSnackbar
import ma.elaroui.pos.desktop.presentation.cashiers.CashierManagementScreen
import ma.elaroui.pos.desktop.presentation.components.PosServiceHeader
import ma.elaroui.pos.desktop.presentation.model.*
import ma.elaroui.pos.desktop.presentation.dashboard.DashboardScreen
import ma.elaroui.pos.desktop.presentation.license.LicenseManagementScreen
import ma.elaroui.pos.desktop.presentation.license.TrialBanner
import ma.elaroui.pos.desktop.presentation.management.catalog.CatalogImportDialog
import ma.elaroui.pos.desktop.presentation.management.categories.CategoryManagementScreen
import ma.elaroui.pos.desktop.presentation.management.products.CataloguePreviewScreen
import ma.elaroui.pos.desktop.presentation.management.products.ProductManagementScreen
import ma.elaroui.pos.desktop.presentation.management.tables.TableManagementScreen
import ma.elaroui.pos.desktop.presentation.payment.PaymentScreen
import ma.elaroui.pos.desktop.presentation.pos.active.ActiveOrdersScreen
import ma.elaroui.pos.desktop.presentation.pos.main.POSMainScreen
import ma.elaroui.pos.desktop.presentation.register.close.CloseRegisterScreen
import ma.elaroui.pos.desktop.presentation.register.current.CurrentSessionScreen
import ma.elaroui.pos.desktop.presentation.register.history.RegisterSessionsHistoryScreen
import ma.elaroui.pos.desktop.presentation.register.open.OpenRegisterScreen
import ma.elaroui.pos.desktop.presentation.reports.DailySalesReportScreen
import ma.elaroui.pos.desktop.presentation.sales.CompletedSalesScreen
import ma.elaroui.pos.desktop.presentation.sales.ReceiptPreviewScreen
import ma.elaroui.pos.desktop.presentation.sales.SaleDetailScreen
import ma.elaroui.pos.desktop.presentation.settings.BackupRestoreScreen
import ma.elaroui.pos.desktop.presentation.settings.CustomerDisplaySettingsScreen
import ma.elaroui.pos.desktop.presentation.settings.DataManagementScreen
import ma.elaroui.pos.desktop.presentation.settings.PrinterSettingsScreen
import ma.elaroui.pos.desktop.presentation.settings.SettingsScreen
import ma.elaroui.pos.desktop.presentation.setup.SetupScreen
import ma.elaroui.pos.desktop.display.CustomerDisplaySettingsRepository
import ma.elaroui.pos.desktop.display.DesktopSerialVfdTransport
import ma.elaroui.pos.shared.display.CustomerDisplayController
import ma.elaroui.pos.shared.display.VfdTransport
import ma.elaroui.pos.shared.domain.OrderLine
import ma.elaroui.pos.shared.rules.OrderCalculationRules
import ma.elaroui.pos.shared.Clock
import ma.elaroui.pos.shared.EpochMilliseconds
import ma.elaroui.pos.shared.rules.MoneyRules
import ma.elaroui.pos.shared.rules.RegisterClosingInput
import ma.elaroui.pos.shared.rules.CategoryPopularityRules
import ma.elaroui.pos.shared.rules.ProductPopularityRules
import ma.elaroui.pos.shared.rules.SESSION_CLOSING_REPORT_SETTING
import ma.elaroui.pos.shared.rules.SessionClosingReportRules
import ma.elaroui.pos.shared.rules.SessionReportType

enum class DesktopScreenRoute {
    SETUP, LICENSE_GATE, USER_SELECTION, DASHBOARD, POS_MAIN, ACTIVE_ORDERS, PAYMENT, RECEIPT_PREVIEW,
    OPEN_REGISTER, CURRENT_SESSION, CLOSE_REGISTER, REGISTER_HISTORY, PRODUCT_MGMT, CATALOGUE_PREVIEW,
    CATEGORY_MGMT, TABLE_MGMT, CASHIER_MGMT, DAILY_REPORT, COMPLETED_SALES, SALE_DETAIL, SETTINGS, PRINTER_SETTINGS,
    CUSTOMER_DISPLAY_SETTINGS, BACKUP_RESTORE, DATA_MANAGEMENT
}

fun isOwnerRoute(route: DesktopScreenRoute): Boolean = when (route) {
    DesktopScreenRoute.DASHBOARD,
    DesktopScreenRoute.PRODUCT_MGMT,
    DesktopScreenRoute.CATALOGUE_PREVIEW,
    DesktopScreenRoute.CATEGORY_MGMT,
    DesktopScreenRoute.TABLE_MGMT,
    DesktopScreenRoute.CASHIER_MGMT,
    DesktopScreenRoute.DAILY_REPORT,
    DesktopScreenRoute.COMPLETED_SALES,
    DesktopScreenRoute.SALE_DETAIL,
    DesktopScreenRoute.REGISTER_HISTORY,
    DesktopScreenRoute.SETTINGS,
    DesktopScreenRoute.PRINTER_SETTINGS,
    DesktopScreenRoute.CUSTOMER_DISPLAY_SETTINGS,
    DesktopScreenRoute.BACKUP_RESTORE,
    DesktopScreenRoute.DATA_MANAGEMENT,
    DesktopScreenRoute.LICENSE_GATE -> true
    DesktopScreenRoute.SETUP,
    DesktopScreenRoute.USER_SELECTION,
    DesktopScreenRoute.POS_MAIN,
    DesktopScreenRoute.ACTIVE_ORDERS,
    DesktopScreenRoute.PAYMENT,
    DesktopScreenRoute.RECEIPT_PREVIEW,
    DesktopScreenRoute.OPEN_REGISTER,
    DesktopScreenRoute.CURRENT_SESSION,
    DesktopScreenRoute.CLOSE_REGISTER -> false
}

private object SystemClock : Clock { override fun now() = EpochMilliseconds(System.currentTimeMillis()) }

data class CompletedSaleConfirmation(
    val order: Order,
    val payment: Payment,
    val paymentMethod: PaymentMethod,
    val receivedCentimes: Long?,
    val changeCentimes: Long?
)

class DesktopNavState(
    val db: WindowsPosDatabase,
    val dataDir: Path,
    val printerServiceOverride: PrinterService? = null,
    val vfdTransportOverride: VfdTransport? = null
) {
    private val printerLogger = ma.elaroui.pos.desktop.JvmPlatformLogger()
    val printerService = printerServiceOverride ?: DesktopPrinterServiceFactory.create(logger = printerLogger)
    val customerDisplayTransport = vfdTransportOverride ?: DesktopSerialVfdTransport()
    val customerDisplayController = CustomerDisplayController(
        initialConfig = runBlocking { CustomerDisplaySettingsRepository.loadConfig(db.settings) },
        transport = customerDisplayTransport
    )
    private val escPosFormatter = EscPosFormatterFactory.create()
    private val automaticPrintGuard = PrintJobDeduplicator()
    private val closingSessionLock = java.util.concurrent.atomic.AtomicBoolean(false)
    var setupComplete by mutableStateOf(runBlocking { db.settings.get("setup_complete") == "true" })
    var currentUser by mutableStateOf<User?>(null)
    var currentRoute by mutableStateOf(DesktopScreenRoute.POS_MAIN)
    var message by mutableStateOf("")
    var uiMessage by mutableStateOf<UiMessage?>(null)
    var language by mutableStateOf(DesktopLanguage.from(runBlocking { db.settings.get("selected_language") }))
    val strings get() = DesktopStrings(language)

    fun getCartTotalCentimes(): Long {
        if (cart.isEmpty()) return 0L
        val lines = cart.mapNotNull { (productId, qty) ->
            if (qty <= 0) return@mapNotNull null
            val product = products.firstOrNull { it.id == productId } ?: return@mapNotNull null
            OrderLine(
                productId = product.id,
                name = product.name,
                unitPriceCentimes = product.priceCentimes,
                quantity = qty,
                taxRateBasisPoints = product.taxRateBasisPoints
            )
        }
        if (lines.isEmpty()) return 0L
        return OrderCalculationRules.calculate(lines, discountBasisPoints, itemDiscountsBasisPoints.toMap()).totalCentimes
    }

    fun syncCustomerDisplayCart() {
        val total = getCartTotalCentimes()
        if (cart.isEmpty() || total <= 0L) {
            customerDisplayController.showIdle()
        } else {
            customerDisplayController.updateCart(total)
        }
    }

    fun showSuccess(text: String, presentation: MessagePresentation = MessagePresentation.TEMPORARY) {
        uiMessage = UiMessage.success(text, presentation)
        message = text
    }

    fun showError(text: String, presentation: MessagePresentation = MessagePresentation.TEMPORARY) {
        uiMessage = UiMessage.error(text, presentation)
        message = text
    }

    fun showWarning(text: String, presentation: MessagePresentation = MessagePresentation.TEMPORARY) {
        uiMessage = UiMessage.warning(text, presentation)
        message = text
    }

    fun showInfo(text: String, presentation: MessagePresentation = MessagePresentation.TEMPORARY) {
        uiMessage = UiMessage.info(text, presentation)
        message = text
    }

    /** Clears the current message, useful when switching users or navigating away. */
    fun clearMessage() {
        uiMessage = null
        message = ""
    }

    fun getEffectiveCustomerPrinter(): String = runBlocking {
        val isWindows = DesktopPlatform.detect() == DesktopPlatform.WINDOWS
        val saved = db.settings.get("customer_printer")?.ifBlank { null }
        if (!saved.isNullOrBlank() && (!isWindows || (saved != PrinterService.DEFAULT_LINUX_POS_QUEUE && !WindowsThermalPrinter.isIgnored(saved)))) {
            val exists = if (isWindows) {
                printerService.discoverPrinters().printers.any { it.name.equals(saved, ignoreCase = true) }
            } else true
            if (exists) return@runBlocking saved
        }
        val defaultDiscovered = printerService.getDefaultPrinter()?.name.orEmpty()
        if (defaultDiscovered.isNotBlank() && (!isWindows || !WindowsThermalPrinter.isIgnored(defaultDiscovered))) {
            return@runBlocking defaultDiscovered
        }
        val candidate = printerService.discoverPrinters().printers
            .firstOrNull { !isWindows || !WindowsThermalPrinter.isIgnored(it.name) }?.name.orEmpty()
        if (candidate.isNotBlank()) {
            return@runBlocking candidate
        }
        if (isWindows) "" else PrinterService.DEFAULT_LINUX_POS_QUEUE
    }

    suspend fun checkStartupPrinterHealth() = withContext(Dispatchers.IO) {
        runCatching {
            val configuredPrinter = getEffectiveCustomerPrinter()
            if (configuredPrinter.isNotBlank()) {
                val health = printerService.checkPrinterHealth(configuredPrinter)
                if (!health.isConnected) {
                    val warnMsg = strings.text(
                        "Imprimante $configuredPrinter non connectée ou hors ligne",
                        "Printer $configuredPrinter disconnected or offline",
                        "الطابعة $configuredPrinter غير متصلة أو غير متاحة"
                    )
                    withContext(Dispatchers.Main) {
                        showWarning(warnMsg, presentation = MessagePresentation.TEMPORARY)
                    }
                }
            } else {
                val infoMsg = strings.text(
                    "Aucune imprimante connectée détectée",
                    "No connected printer detected",
                    "لم يتم العثور على طابعة متصلة"
                )
                withContext(Dispatchers.Main) {
                    showWarning(infoMsg, presentation = MessagePresentation.TEMPORARY)
                }
            }
        }
    }

    // Data models
    var products by mutableStateOf<List<Product>>(emptyList())
    var categories by mutableStateOf<List<Category>>(emptyList())
    var popularCategoryIds by mutableStateOf<List<Long>>(emptyList())
    var categoryScores by mutableStateOf<Map<Long, Long>>(emptyMap())
    var productScores by mutableStateOf<Map<Long, Long>>(emptyMap())
    var tables by mutableStateOf<List<RestaurantTable>>(emptyList())
    var users by mutableStateOf<List<User>>(emptyList())
    var areas by mutableStateOf<List<DiningArea>>(emptyList())
    var salesHistory by mutableStateOf<List<SalesHistoryRow>>(emptyList())
    var sessionHistory by mutableStateOf<List<SessionHistoryRow>>(emptyList())
    var cashMovements by mutableStateOf<List<CashMovement>>(emptyList())
    var session by mutableStateOf<RegisterSession?>(null)
    var resumedSessionNotice by mutableStateOf<RegisterSession?>(null)
    var pendingClosingReportRetrySessionId by mutableStateOf<Long?>(null)
    var openOrders by mutableStateOf<List<Order>>(emptyList())

    private var cachedCompany: ReceiptCompany? = null

    fun getCompany(): ReceiptCompany {
        return cachedCompany ?: loadCompany(db).also { cachedCompany = it }
    }

    fun invalidateCompany() {
        cachedCompany = null
    }

    fun loadSalesHistory() = runBlocking {
        salesHistory = db.salesHistory()
    }

    fun loadSessionHistory() = runBlocking {
        sessionHistory = db.sessionHistory()
    }

    // Cart & Selection transient state
    val cart = mutableStateMapOf<Long, Int>()
    val itemDiscountsBasisPoints = mutableStateMapOf<Long, Int>()
    var pendingOrder by mutableStateOf<Order?>(null)
    var editingOrderId by mutableStateOf<Long?>(null)
    var selectedSaleRow by mutableStateOf<SalesHistoryRow?>(null)
    var selectedReceiptKind by mutableStateOf(TicketKind.CUSTOMER)
    var receiptReturnRoute by mutableStateOf(DesktopScreenRoute.POS_MAIN)
    var completedSaleConfirmation by mutableStateOf<CompletedSaleConfirmation?>(null)
    var orderType by mutableStateOf(OrderType.COUNTER)
    var tableId by mutableStateOf<Long?>(null)
    var discountBasisPoints by mutableIntStateOf(0)

    init { refresh() }

    fun refresh() = runBlocking {
        products = db.products.observeAll().first()
        categories = db.categories.observeAll().first()
        val rankingNow = System.currentTimeMillis()
        val lookback = CategoryPopularityRules.lookbackStart(rankingNow)
        val categorySamples = db.recentCategorySaleSamples(lookback)
        val productSamples = db.recentProductSaleSamples(lookback)
        val activeCatIds = categories.filter { it.active }.mapTo(mutableSetOf()) { it.id }
        val activeProdIds = products.filter { it.active && it.available }.mapTo(mutableSetOf()) { it.id }

        categoryScores = CategoryPopularityRules.scores(
            samples = categorySamples,
            nowEpochMilliseconds = rankingNow,
            activeCategoryIds = activeCatIds
        )
        popularCategoryIds = CategoryPopularityRules.rank(
            samples = categorySamples,
            nowEpochMilliseconds = rankingNow,
            activeCategoryIds = activeCatIds
        )
        productScores = ProductPopularityRules.scores(
            samples = productSamples,
            nowEpochMilliseconds = rankingNow,
            activeProductIds = activeProdIds
        )
        tables = db.allTables()
        users = db.allUsers()
        areas = db.areas()
        session = currentUser?.let { db.sessions.findOpenByUser(it.id) }
        openOrders = session?.let { db.openOrdersForSession(it.id) }.orEmpty()
        cashMovements = session?.let { db.cashMovements.observeForSession(it.id).first() }.orEmpty()
        if (currentRoute == DesktopScreenRoute.COMPLETED_SALES) {
            salesHistory = db.salesHistory()
        }
        if (currentRoute == DesktopScreenRoute.REGISTER_HISTORY) {
            sessionHistory = db.sessionHistory()
        }
    }

    fun todaySalesSummary(
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): SalesSummary = db.todaySalesSummary(zoneId, nowEpochMs)

    fun onFactoryResetCompleted() {
        setupComplete = false
        currentUser = null
        session = null
        pendingOrder = null
        editingOrderId = null
        selectedSaleRow = null
        tableId = null
        cart.clear()
        itemDiscountsBasisPoints.clear()
        discountBasisPoints = 0
        customerDisplayController.showIdle()
        currentRoute = DesktopScreenRoute.SETUP
        clearMessage()
        refresh()
    }

    fun onDataResetOrDeleted() {
        cart.clear()
        itemDiscountsBasisPoints.clear()
        pendingOrder = null
        editingOrderId = null
        selectedSaleRow = null
        tableId = null
        discountBasisPoints = 0
        customerDisplayController.showIdle()
        val currentUserId = currentUser?.id
        if (currentUserId != null && currentUserId != 1L) {
            val userStillExists = runBlocking { db.allUsers().any { it.id == currentUserId } }
            if (!userStillExists) {
                lock()
                return
            }
        }
        salesHistory = runBlocking { db.salesHistory() }
        sessionHistory = runBlocking { db.sessionHistory() }
        refresh()
    }

    fun setup(
        business: String,
        address: String,
        phone: String,
        ownerName: String,
        pin: String
    ) = runCatching {
        db.configureInitialSetup(business, ownerName, pin)
        runBlocking {
            db.settings.put(AppSetting("restaurant_address", address))
            db.settings.put(AppSetting("restaurant_phone", phone))
        }
        setupComplete = true
        currentUser = null
        currentRoute = DesktopScreenRoute.USER_SELECTION
        cart.clear()
        itemDiscountsBasisPoints.clear()
        pendingOrder = null
        editingOrderId = null
        tableId = null
        discountBasisPoints = 0
        clearMessage()
        refresh()
    }.onFailure { showError(it.message ?: strings.text("Erreur lors de la configuration", "Setup error", "خطأ في الإعداد")) }


    fun navigateTo(route: DesktopScreenRoute) {
        clearMessage()
        if (isOwnerRoute(route) && currentUser?.role != UserRole.OWNER) {
            currentRoute = DesktopScreenRoute.POS_MAIN
            return
        }
        currentRoute = route
    }

    private val failedPinAttempts = mutableMapOf<Long, Int>()
    private val pinLockoutUntil = mutableMapOf<Long, Long>()

    fun login(selected: User, pin: String) = runBlocking {
        val now = System.currentTimeMillis()
        val lockedUntil = pinLockoutUntil[selected.id] ?: 0L
        if (now < lockedUntil) {
            val remainingSecs = ((lockedUntil - now) / 1000).coerceAtLeast(1)
            showError(strings.text(
                "Compte temporairement bloqué. Réessayez dans $remainingSecs s.",
                "Account temporarily locked. Please retry in $remainingSecs s.",
                "الحساب مغلق مؤقتاً. يرجى المحاولة بعد $remainingSecs ثانية."
            ))
            return@runBlocking
        }

        when (val result = AuthenticateUser(db.authentication).execute(pin)) {
            is UseCaseResult.Success -> {
                if (result.value.id == selected.id) {
                    failedPinAttempts.remove(selected.id)
                    pinLockoutUntil.remove(selected.id)
                    currentUser = result.value
                    val existingOpenSession = db.sessions.findOpenByUser(result.value.id)
                    if (existingOpenSession == null) {
                        val newSessionId = db.nextId("register_sessions")
                        OpenRegisterSession(db.sessions, SystemClock).execute(
                            newSessionId, 1, result.value.id, 0L
                        )
                    }
                    refresh()
                    resumedSessionNotice = if (existingOpenSession != null) session else null
                    navigateTo(if (result.value.role == UserRole.OWNER) DesktopScreenRoute.DASHBOARD else DesktopScreenRoute.POS_MAIN)
                } else {
                    handleFailedPin(selected.id)
                }
            }
            is UseCaseResult.Failure -> handleFailedPin(selected.id)
        }
    }

    private fun handleFailedPin(userId: Long) {
        val attempts = (failedPinAttempts[userId] ?: 0) + 1
        if (attempts >= 5) {
            failedPinAttempts.remove(userId)
            pinLockoutUntil[userId] = System.currentTimeMillis() + 30_000L
            showError(strings.text(
                "Trop de tentatives incorrectes. Compte bloqué pendant 30 secondes.",
                "Too many incorrect attempts. Account locked for 30 seconds.",
                "محاولات خاطئة متكررة. تم قفل الحساب لمدة 30 ثانية."
            ))
        } else {
            failedPinAttempts[userId] = attempts
            showError(strings.wrongPin)
        }
    }

    fun lock() {
        currentUser = null
        session = null
        openOrders = emptyList()
        cashMovements = emptyList()
        resumedSessionNotice = null
        cart.clear()
        itemDiscountsBasisPoints.clear()
        pendingOrder = null
        editingOrderId = null
        customerDisplayController.showIdle()
        navigateTo(DesktopScreenRoute.USER_SELECTION)
    }

    private var logoutKeepingSessionInProgress = false

    /**
     * Logs out only the expected authenticated user. The persisted register session is deliberately
     * untouched so it can be resumed by the same user on the next login.
     */
    fun logoutKeepingRegisterSessionOpen(expectedUserId: Long): Boolean {
        if (logoutKeepingSessionInProgress || currentUser?.id != expectedUserId) return false
        logoutKeepingSessionInProgress = true
        return try {
            lock()
            true
        } finally {
            logoutKeepingSessionInProgress = false
        }
    }

    private fun mapFailureToFrench(reason: String): String = when {
        reason.contains("Only dine-in orders can use a table", ignoreCase = true) ->
            strings.text("Seules les commandes sur place peuvent être associées à une table.", "Only dine-in orders can use a table.", "الطلبات المحلية فقط يمكن ربطها بطاولة.")
        reason.contains("Table is not available", ignoreCase = true) ->
            strings.text("La table sélectionnée n'est pas disponible.", "Selected table is not available.", "الطاولة المختارة غير متاحة.")
        reason.contains("Table not found", ignoreCase = true) ->
            strings.text("Table introuvable.", "Table not found.", "الطاولة غير موجودة.")
        reason.contains("Product quantity must be positive", ignoreCase = true) ->
            strings.text("La quantité doit être supérieure à zéro.", "Quantity must be positive.", "الكمية يجب أن تكون أكبر من صفر.")
        reason.contains("is not available", ignoreCase = true) ->
            strings.text("Un des produits sélectionnés n'est plus disponible.", "A selected product is not available.", "أحد المنتجات المختارة غير متوفر.")
        reason.contains("Register session is not open", ignoreCase = true) || reason.contains("Session not found", ignoreCase = true) ->
            strings.text("La session de caisse n'est pas ouverte.", "Register session is not open.", "جلسة الصندوق غير مفتوحة.")
        reason.contains("Failed to save order", ignoreCase = true) ->
            strings.text("Échec de l'enregistrement de la commande.", "Failed to save order.", "فشل في حفظ الطلب.")
        reason.contains("Amount received is insufficient", ignoreCase = true) ->
            strings.text("Le montant reçu est inférieur au total.", "Amount received is insufficient.", "المبلغ المستلم غير كاف.")
        reason.contains("Order cannot be paid", ignoreCase = true) ->
            strings.text("Cette commande ne peut pas être encaissée.", "Order cannot be paid.", "لا يمكن سداد هذا الطلب.")
        reason.contains("Active orders must be completed or cancelled", ignoreCase = true) ->
            strings.activeOrdersBlockClosing
        reason.contains("The sum of amount left in drawer and removed amount must equal counted cash", ignoreCase = true) ->
            strings.leftPlusRemovedMustEqualCounted
        reason.contains("Session is already closed", ignoreCase = true) ->
            strings.text("La session est déjà clôturée.", "Session is already closed.", "الجلسة مغلقة بالفعل.")
        else -> reason
    }

    fun printOperationalTickets(order: Order, sendToKitchen: Boolean = false, sendToPreparation: Boolean = false) = runBlocking {
        runCatching {
            val company = getCompany()
            val customer = getEffectiveCustomerPrinter()
            val width = db.settings.get("printer_width")?.toIntOrNull() ?: 80
            val cashier = currentUser?.name
            val table = order.tableId?.let { id -> tables.firstOrNull { it.id == id } }
            val area = table?.let { selected -> areas.firstOrNull { it.id == selected.areaId } }

            if (sendToPreparation && !customer.isNullOrBlank()) {
                submitAutomaticDocument(
                    key = "preparation:${order.id}:${order.lines.hashCode()}",
                    printerName = customer,
                    role = PrinterRole.CASHIER_RECEIPT,
                    request = EscPosPrintRequest(
                        order, company, TicketKind.PREPARATION, width, cashierName = cashier,
                        tableLabel = table?.name, areaLabel = area?.name
                    )
                )
            }
        }
    }

    private fun submitAutomaticDocument(
        key: String,
        printerName: String,
        role: PrinterRole,
        request: EscPosPrintRequest
    ): PrintResult {
        if (printerName.isBlank()) return PrintResult(false, "No printer configured", "No printer configured", PrintErrorCategory.NOT_CONFIGURED)
        val health = printerService.checkPrinterHealth(printerName)
        if (!health.isReady) {
            printerLogger.warning(
                "Skipping automatic print for role=$role on '$printerName': ${health.message} (${health.detailedReason ?: "unavailable"})",
                category = "Printer"
            )
            return PrintResult(false, health.message, health.detailedReason ?: health.message, PrintErrorCategory.OFFLINE)
        }
        if (!automaticPrintGuard.acquire(key)) return PrintResult(true, "Duplicate automatic print suppressed")
        val formatted = escPosFormatter.format(request)
        val result = when (formatted) {
            is EscPosFormatResult.Success -> printerService.printRaw(printerName, formatted.bytes, role)
            is EscPosFormatResult.Failure -> PrintResult(false, formatted.message, formatted.message, formatted.category)
        }
        if (!result.success) {
            automaticPrintGuard.releaseAfterFailure(key)
            printerLogger.error(
                "Automatic print failed role=$role printer='$printerName' category=${result.errorCategory}: ${result.errorMessage}",
                category = "Printer"
            )
        }
        return result
    }

    fun saveDraft(proceedToPayment: Boolean) = runBlocking {
        val open = session ?: run {
            pendingOrder = null
            showError(strings.text("Ouvrez la caisse avant d'enregistrer une vente", "Open register before recording a sale", "يرجى فتح الصندوق قبل تسجيل البيع"))
            return@runBlocking
        }
        if (cart.isEmpty()) {
            pendingOrder = null
            showWarning(strings.text("Le panier est vide. Ajoutez au moins un article.", "Cart is empty. Add at least one item.", "السلة فارغة. يرجى إضافة منتج واحد على الأقل."))
            return@runBlocking
        }
        val existing = editingOrderId?.let { db.orders.findById(it) }
        val orderId = existing?.id ?: db.nextId("orders")
        val effectiveType = existing?.type ?: OrderType.COUNTER
        val result = CreateOrder(db.sessions, db.categories, db.products, db.tables, db.orders).execute(
            orderId, existing?.number ?: "SALE-${System.currentTimeMillis()}", effectiveType, open.id,
            cart.map { it.key to it.value }, null, currentUser!!.id, discountBasisPoints,
            itemDiscountsBasisPoints = itemDiscountsBasisPoints.toMap()
        )
        if (result is UseCaseResult.Success) {
            val created = result.value
            pendingOrder = created.takeIf { proceedToPayment }
            cart.clear()
            itemDiscountsBasisPoints.clear()
            editingOrderId = null
            discountBasisPoints = 0
            if (proceedToPayment) {
                clearMessage()
                customerDisplayController.paymentStarted(created.totalCentimes)
            } else {
                showSuccess(strings.text("Vente mise en attente", "Sale held", "تم تعليق البيع"))
                customerDisplayController.showIdle()
            }
            currentRoute = if (proceedToPayment) DesktopScreenRoute.PAYMENT else DesktopScreenRoute.POS_MAIN
            refresh()
        } else {
            pendingOrder = null
            val rawReason = (result as UseCaseResult.Failure).reason
            showError(mapFailureToFrench(rawReason))
        }
    }

    fun createOrder() = saveDraft(proceedToPayment = true)

    fun holdOrder() = saveDraft(proceedToPayment = false)

    fun resumeOrder(order: Order) {
        cart.clear()
        itemDiscountsBasisPoints.clear()
        order.lines.forEach { cart[it.productId] = it.quantity }
        editingOrderId = order.id
        discountBasisPoints = if (order.subtotalCentimes > 0) {
            ((order.discountCentimes * 10_000L) / order.subtotalCentimes).toInt()
        } else 0
        pendingOrder = null
        clearMessage()
        currentRoute = DesktopScreenRoute.POS_MAIN
        syncCustomerDisplayCart()
    }

    fun onBarcodeScanned(rawBarcode: String) {
        val clean = rawBarcode.trim()
        if (clean.isBlank()) return

        var product = products.firstOrNull { it.barcode?.trim().equals(clean, ignoreCase = false) }
        if (product == null) {
            product = products.firstOrNull { it.sku?.trim().equals(clean, ignoreCase = true) }
        }
        if (product == null) {
            product = db.findProductByBarcode(clean) ?: db.findProductBySku(clean)
        }

        if (product == null) {
            showWarning(strings.text("Produit introuvable pour le code : $clean", "Product not found for code: $clean", "المنتج غير موجود للرمز: $clean"))
            return
        }

        if (!product.active || !product.available) {
            showWarning(strings.text("Produit désactivé : ${product.name}", "Product deactivated: ${product.name}", "المنتج معطل: ${product.name}"))
            return
        }
        if (categories.none { it.id == product.categoryId && it.active }) {
            showWarning(strings.text("Catégorie désactivée : ${product.name}", "Product category is deactivated: ${product.name}", "فئة المنتج معطلة: ${product.name}"))
            return
        }

        val currentQty = cart[product.id] ?: 0
        val newQty = currentQty + 1
        cart[product.id] = newQty
        syncCustomerDisplayCart()
        showInfo(strings.text("${product.name} ajouté ($newQty)", "${product.name} added ($newQty)", "تمت إضافة ${product.name} ($newQty)"))
    }

    fun pay(method: PaymentMethod, received: Long?) = runBlocking {
        val order = pendingOrder ?: return@runBlocking
        val result = CompletePayment(db.sessions, db.orders, db.payments, db.transactions)
            .execute(order.id, method, received, "${order.id}-${System.nanoTime()}", currentUser!!.id)
        if (result is UseCaseResult.Success) {
            val payment = result.value
            val customer = getEffectiveCustomerPrinter()

            val drawerEnabled = db.settings.get("cash_drawer_enabled") == "true"
            if (method == PaymentMethod.CASH && drawerEnabled && customer.isNotBlank() &&
                automaticPrintGuard.acquire("drawer:${order.id}:${payment.id}")) {
                val drawerResult = printerService.openCashDrawer(customer)
                if (!drawerResult.success) automaticPrintGuard.releaseAfterFailure("drawer:${order.id}:${payment.id}")
            }

            completedSaleConfirmation = CompletedSaleConfirmation(
                order = order,
                payment = payment,
                paymentMethod = method,
                receivedCentimes = received,
                changeCentimes = payment.changeCentimes
            )
            cart.clear()
            customerDisplayController.paymentCompleted()
            showSuccess(strings.saleCompletedSuccess)
            currentRoute = DesktopScreenRoute.POS_MAIN
            refresh()
        } else {
            val rawReason = (result as UseCaseResult.Failure).reason
            showError(mapFailureToFrench(rawReason))
        }
    }

    fun printCompletedSaleReceipt(confirmation: CompletedSaleConfirmation): PrintResult = runBlocking {
        val company = getCompany()
        val customer = getEffectiveCustomerPrinter()
        val width = db.settings.get("printer_width")?.toIntOrNull() ?: 80
        val table = confirmation.order.tableId?.let { id -> tables.firstOrNull { it.id == id } }
        val area = table?.let { selected -> areas.firstOrNull { it.id == selected.areaId } }

        if (customer.isBlank()) {
            val errorMsg = strings.text(
                "Aucune imprimante connectée trouvée.",
                "No connected printer found.",
                "لم يتم العثور على طابعة متصلة."
            )
            showError(errorMsg)
            return@runBlocking PrintResult(
                success = false,
                message = errorMsg,
                errorMessage = errorMsg,
                errorCategory = PrintErrorCategory.NOT_CONFIGURED
            )
        }

        val printKey = "receipt:${confirmation.order.id}:${confirmation.payment.id}"
        val printResult = submitAutomaticDocument(
            key = printKey,
            printerName = customer,
            role = PrinterRole.CASHIER_RECEIPT,
            request = EscPosPrintRequest(
                confirmation.order,
                company,
                TicketKind.CUSTOMER,
                width,
                isReprint = false,
                paymentMethod = confirmation.paymentMethod,
                receivedCentimes = confirmation.receivedCentimes,
                changeCentimes = confirmation.changeCentimes,
                cashierName = currentUser?.name,
                tableLabel = table?.name,
                areaLabel = area?.name
            )
        )
        if (!printResult.success) {
            val err = printResult.errorMessage ?: printResult.message
            showError(strings.text(
                "Erreur d'impression : $err",
                "Print error: $err",
                "خطأ في الطباعة: $err"
            ))
        } else {
            showSuccess(strings.text(
                "Ticket envoyé à l'imprimante avec succès",
                "Receipt sent to printer successfully",
                "تم إرسال الإيصال إلى الطابعة بنجاح"
            ))
        }
        printResult
    }

    fun openRegister(value: Long) = runBlocking {
        val result = OpenRegisterSession(db.sessions, SystemClock).execute(db.nextId("register_sessions"), 1, currentUser!!.id, value)
        if (result is UseCaseResult.Success) {
            showSuccess(strings.text("Caisse ouverte", "Register opened", "تم فتح الصندوق"), presentation = MessagePresentation.TEMPORARY)
            currentRoute = DesktopScreenRoute.POS_MAIN
        } else {
            showError(mapFailureToFrench((result as UseCaseResult.Failure).reason))
        }
        refresh()
    }

    fun closeRegister(input: RegisterClosingInput) = runBlocking {
        if (!closingSessionLock.compareAndSet(false, true)) return@runBlocking
        try {
            val open = session ?: return@runBlocking
            val uniqueRef = input.remittanceReference?.let { ref ->
                db.nextUniqueRemittanceReference(ref)
            }
            val closingWithUser = input.copy(
                closingUserId = currentUser?.id,
                remittanceReference = uniqueRef
            )
            val result = CloseRegisterSession(db.sessions, db.payments, db.cashMovements, db.transactions, SystemClock, db.orders)
                .execute(open.id, closingWithUser)
            if (result is UseCaseResult.Success) {
                val closedSession = result.value
                val isAutoEnabled = SessionClosingReportRules.isAutoPrintEnabled(
                    db.settings.get(SESSION_CLOSING_REPORT_SETTING)
                )
                val printResult = if (isAutoEnabled) {
                    val printer = getEffectiveCustomerPrinter()
                    if (printer.isBlank() && printerServiceOverride == null) {
                        PrintResult(
                            false,
                            strings.text("Aucune imprimante connectée", "No connected printer", "لا توجد طابعة متصلة"),
                            strings.text("Aucune imprimante thermique détectée sous Windows", "No thermal printer detected in Windows", "لم يتم العثور على طابعة حرارية في ويندوز"),
                            PrintErrorCategory.OFFLINE
                        )
                    } else {
                        printSessionClosingReport(closedSession.id, type = SessionReportType.SUMMARY, isReprint = false, automatic = true)
                    }
                } else null
                lock()
                if (printResult != null && !printResult.success) {
                    pendingClosingReportRetrySessionId = closedSession.id
                    showError(
                        strings.text(
                            "Session #${closedSession.id} clôturée. Impression du rapport impossible : ${printResult.errorMessage ?: printResult.message}",
                            "Session #${closedSession.id} closed. The report could not be printed: ${printResult.errorMessage ?: printResult.message}",
                            "تم إغلاق الجلسة رقم ${closedSession.id}، لكن تعذرت طباعة التقرير: ${printResult.errorMessage ?: printResult.message}"
                        ),
                        presentation = MessagePresentation.PERSISTENT
                    )
                } else {
                    showSuccess(strings.text("Caisse fermée avec succès", "Register closed successfully", "تم إغلاق الصندوق بنجاح"))
                }
            } else {
                val rawReason = (result as UseCaseResult.Failure).reason
                showError(mapFailureToFrench(rawReason))
            }
            refresh()
        } finally {
            closingSessionLock.set(false)
        }
    }

    fun setAutomaticSessionClosingReport(enabled: Boolean) = runBlocking {
        check(currentUser?.role == UserRole.OWNER) { "Owner permission required" }
        db.settings.put(AppSetting(SESSION_CLOSING_REPORT_SETTING, enabled.toString()))
    }

    fun printSessionClosingReport(
        sessionId: Long,
        type: SessionReportType = SessionReportType.SUMMARY,
        isReprint: Boolean = false,
        automatic: Boolean = false
    ): PrintResult = runBlocking {
        val automaticPrintKey = "session-closing-report:$sessionId"
        var automaticPrintAcquired = false
        try {
            val printerName = getEffectiveCustomerPrinter()
            val health = printerService.checkPrinterHealth(printerName)
            if (!health.isReady) {
                return@runBlocking PrintResult(
                    false,
                    health.message,
                    health.detailedReason ?: health.message,
                    PrintErrorCategory.OFFLINE
                )
            }
            val report = db.sessionClosingReport(sessionId)
            if (automatic && !automaticPrintGuard.acquire(automaticPrintKey)) {
                return@runBlocking PrintResult(true, "Duplicate automatic closing report suppressed")
            }
            automaticPrintAcquired = automatic
            val formatted = SessionClosingReportEscPosFormatter.format(
                report = report,
                establishmentName = getCompany().name,
                paperWidth = db.settings.get("printer_width")?.toIntOrNull() ?: 80,
                type = type,
                isReprint = isReprint
            )
            val result = when (formatted) {
                is EscPosFormatResult.Success -> printerService.printRaw(
                    printerName,
                    formatted.bytes,
                    PrinterRole.SESSION_CLOSING_REPORT
                )
                is EscPosFormatResult.Failure -> PrintResult(false, formatted.message, formatted.message, formatted.category)
            }
            if (automaticPrintAcquired && !result.success) {
                automaticPrintGuard.releaseAfterFailure(automaticPrintKey)
            }
            result
        } catch (error: Throwable) {
            if (automaticPrintAcquired) {
                automaticPrintGuard.releaseAfterFailure(automaticPrintKey)
            }
            PrintResult(
                false,
                "Impression du rapport impossible",
                error.message ?: "Erreur d’impression inattendue",
                PrintErrorCategory.TRANSPORT_FAILURE
            )
        }
    }

    fun movement(type: CashMovementType, value: Long, reason: String) = runBlocking {
        val open = session ?: return@runBlocking
        val result = RecordCashMovement(db.sessions, db.payments, db.cashMovements, SystemClock)
            .execute(open.id, type, value, reason, null, currentUser!!.id)
        if (result is UseCaseResult.Success) {
            showSuccess(strings.text("Mouvement enregistré", "Cash movement recorded", "تم تسجيل حركة النقدية"))
        } else {
            showError(mapFailureToFrench((result as UseCaseResult.Failure).reason))
        }
        refresh()
    }

    fun importProductImage(source: Path): String {
        validateImage(source)
        val dir = dataDir.resolve("images/products")
        java.nio.file.Files.createDirectories(dir)
        val target = dir.resolve("prod_${System.currentTimeMillis()}_${source.fileName}")
        java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return target.toString()
    }

    fun importAreaImage(source: Path): String {
        validateImage(source)
        val dir = dataDir.resolve("images/areas")
        java.nio.file.Files.createDirectories(dir)
        val target = dir.resolve("area_${System.currentTimeMillis()}_${source.fileName}")
        java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return target.toString()
    }

    fun importCategoryImage(source: Path): String {
        validateImage(source)
        val dir = dataDir.resolve("images/categories")
        java.nio.file.Files.createDirectories(dir)
        val target = dir.resolve("cat_${System.currentTimeMillis()}_${source.fileName}")
        java.nio.file.Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return target.toString()
    }

    fun deleteManagedCategoryImage(path: String?) {
        if (path.isNullOrBlank()) return
        val root = dataDir.resolve("images/categories").toAbsolutePath().normalize()
        val target = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull() ?: return
        if (target.startsWith(root)) runCatching { java.nio.file.Files.deleteIfExists(target) }
    }

    fun deleteManagedAreaImage(path: String?) {
        if (path.isNullOrBlank()) return
        val root = dataDir.resolve("images/areas").toAbsolutePath().normalize()
        val target = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull() ?: return
        if (target.startsWith(root)) runCatching { java.nio.file.Files.deleteIfExists(target) }
    }

    fun deleteManagedProductImage(path: String?) {
        if (path.isNullOrBlank()) return
        val root = dataDir.resolve("images/products").toAbsolutePath().normalize()
        val target = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull() ?: return
        if (target.startsWith(root)) runCatching { java.nio.file.Files.deleteIfExists(target) }
    }

    private fun validateImage(source: Path) {
        require(java.nio.file.Files.isRegularFile(source)) { "Image file not found" }
        require(source.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp")) { "Unsupported image format" }
        runCatching { Image.makeFromEncoded(java.nio.file.Files.readAllBytes(source)).close() }
            .getOrElse { throw IllegalArgumentException("Corrupted or invalid image", it) }
    }

    private fun downloadWebImage(urlStr: String, isCategory: Boolean): String? {
        return runCatching {
            val client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                .connectTimeout(java.time.Duration.ofSeconds(6))
                .build()

            val request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(urlStr))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GeneralPOS/1.0")
                .GET()
                .build()

            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() in 200..299) {
                val subfolder = if (isCategory) "images/categories" else "images/products"
                val prefix = if (isCategory) "cat_web_" else "prod_web_"
                val dir = dataDir.resolve(subfolder)
                Files.createDirectories(dir)

                val contentType = response.headers().firstValue("Content-Type").orElse("").lowercase()
                val ext = when {
                    contentType.contains("png") -> ".png"
                    contentType.contains("webp") -> ".webp"
                    contentType.contains("gif") -> ".gif"
                    else -> ".jpg"
                }
                val target = dir.resolve("${prefix}${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}$ext")
                response.body().use { stream ->
                    Files.copy(stream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                if (Files.isRegularFile(target) && Files.size(target) > 0) {
                    target.toString()
                } else {
                    Files.deleteIfExists(target)
                    null
                }
            } else null
        }.getOrNull()
    }

    private fun resolveAndImportCatalogImage(
        rawPath: String?,
        csvParent: Path,
        isCategory: Boolean,
        webCache: MutableMap<String, String>
    ): String? {
        if (rawPath.isNullOrBlank()) return null
        val trimmed = rawPath.trim()
        return runCatching {
            if (CsvImports.isWebUrl(trimmed)) {
                webCache.getOrPut(trimmed) {
                    downloadWebImage(trimmed, isCategory) ?: ""
                }.ifBlank { null }
            } else {
                val candidate = runCatching {
                    val p = Path.of(trimmed)
                    if (p.isAbsolute) p else csvParent.resolve(p)
                }.getOrNull()

                if (candidate != null && Files.isRegularFile(candidate)) {
                    if (isCategory) importCategoryImage(candidate.normalize())
                    else importProductImage(candidate.normalize())
                } else null
            }
        }.getOrNull()
    }

    fun analyzeCatalogCsv(source: Path): CatalogImportAnalysis {
        return CsvImports.analyzeCatalogCsv(source, categories, products)
    }

    fun executeCatalogImport(analysis: CatalogImportAnalysis, sourceCsvPath: Path): CsvImportSummary = runBlocking {
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val categoryCache = mutableMapOf<Pair<String, Long?>, Category>()
        categories.forEach { cat ->
            categoryCache[cat.name.trim().lowercase() to cat.parentId] = cat
        }

        val webImageCache = mutableMapOf<String, String>()
        val csvParent = sourceCsvPath.parent ?: Path.of(".")

        analysis.validRows.forEach { row ->
            try {
                // 1. Resolve or create categories along the hierarchy
                var parentId: Long? = null
                for ((levelName, levelImg) in row.categoryLevels) {
                    val key = levelName.trim().lowercase() to parentId
                    var cat = categoryCache[key]
                    if (cat == null) {
                        val managedImage = resolveAndImportCatalogImage(levelImg, csvParent, isCategory = true, webCache = webImageCache)
                        val newCat = Category(
                            id = 0L,
                            name = levelName.trim(),
                            active = true,
                            displayOrder = 0,
                            parentId = parentId,
                            imagePath = managedImage
                        )
                        val newId = db.categories.save(newCat)
                        cat = newCat.copy(id = newId)
                        categoryCache[key] = cat
                    } else if (cat.imagePath == null && !levelImg.isNullOrBlank()) {
                        val managedImage = resolveAndImportCatalogImage(levelImg, csvParent, isCategory = true, webCache = webImageCache)
                        if (managedImage != null) {
                            val updated = cat.copy(imagePath = managedImage)
                            db.categories.save(updated)
                            cat = updated
                            categoryCache[key] = cat
                        }
                    }
                    parentId = cat.id
                }

                val deepestCategoryId = parentId ?: error("Impossible de déterminer la catégorie")

                // 2. Resolve Product image
                val managedProdImage = resolveAndImportCatalogImage(row.productImage, csvParent, isCategory = false, webCache = webImageCache)

                // 3. Check for existing product by SKU / Product ID
                val existingProd = products.firstOrNull { it.sku?.trim().equals(row.productId.trim(), ignoreCase = true) }
                if (existingProd != null) {
                    val updated = existingProd.copy(
                        categoryId = deepestCategoryId,
                        name = row.productNameFrench.trim(),
                        priceCentimes = row.priceCentimes,
                        taxRateBasisPoints = row.taxBasisPoints,
                        imagePath = managedProdImage ?: existingProd.imagePath,
                        nameArabic = row.productNameArabic?.trim()?.ifBlank { null } ?: existingProd.nameArabic,
                        unit = row.unit?.trim()?.ifBlank { null } ?: existingProd.unit,
                        description = row.description?.trim()?.ifBlank { null } ?: existingProd.description,
                        sku = row.productId.trim(),
                        active = true,
                        available = true
                    )
                    db.products.save(updated)
                } else {
                    val toSave = Product(
                        id = 0L,
                        categoryId = deepestCategoryId,
                        name = row.productNameFrench.trim(),
                        priceCentimes = row.priceCentimes,
                        taxRateBasisPoints = row.taxBasisPoints,
                        imagePath = managedProdImage,
                        nameArabic = row.productNameArabic?.trim()?.ifBlank { null },
                        unit = row.unit?.trim()?.ifBlank { null },
                        description = row.description?.trim()?.ifBlank { null },
                        sku = row.productId.trim(),
                        active = true,
                        available = true
                    )
                    db.products.save(toSave)
                }
                imported++
            } catch (error: Throwable) {
                errors += "Ligne ${row.line}: ${error.message ?: "Échec de l'importation"}"
            }
        }

        refresh()
        val warningsList = analysis.warnings.map { "Ligne ${it.line}: [${it.column}] ${it.message}" }
        CsvImportSummary(imported, skipped, errors.size, errors, warningsList)
    }

    fun importCatalogCsv(source: Path): CsvImportSummary {
        val analysis = analyzeCatalogCsv(source)
        if (analysis.errors.isNotEmpty()) {
            return CsvImportSummary(
                imported = 0,
                skipped = 0,
                failed = analysis.errors.size,
                errors = analysis.errors.map { "Ligne ${it.line}: [${it.column}] ${it.message}" },
                warnings = analysis.warnings.map { "Ligne ${it.line}: [${it.column}] ${it.message}" }
            )
        }
        return executeCatalogImport(analysis, source)
    }
}

@Composable
fun DesktopNavGraph(
    database: WindowsPosDatabase,
    dataDir: Path,
    licenseManager: WindowsLicenseManager,
    showWindowModeButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleWindowMode: () -> Unit = {},
    onExitApp: (() -> Unit)? = null
) {
    val state = remember { DesktopNavState(database, dataDir) }
    val backupService = remember {
        DesktopBackupService(database, DesktopApplicationPathsProvider(DesktopPlatform.detect()).paths())
    }
    var license by remember { mutableStateOf(licenseManager.state()) }
    val isLicenseBlocking = license.status in listOf(
        WindowsLicenseStatus.TRIAL_EXPIRED,
        WindowsLicenseStatus.EXPIRED,
        WindowsLicenseStatus.INVALID,
        WindowsLicenseStatus.WRONG_DEVICE,
        WindowsLicenseStatus.CLOCK_ROLLBACK
    )

    DisposableEffect(state) {
        onDispose {
            state.customerDisplayController.shutdown()
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (state.language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        LaunchedEffect(Unit) {
            state.checkStartupPrinterHealth()
        }

        when {
            isLicenseBlocking -> {
                LicenseManagementScreen(
                    licenseManager = licenseManager,
                    licenseState = license,
                    strings = state.strings,
                    onLicenseUpdated = { license = it },
                    canNavigateBack = false
                )
            }
            !state.setupComplete -> {
                SetupScreen(
                    strings = state.strings,
                    onSetupComplete = { b, a, p, o, pin -> state.setup(b, a, p, o, pin) },
                    errorMessage = state.message
                )
            }
            state.currentUser == null -> {
                UserSelectionScreen(
                    users = state.users,
                    strings = state.strings,
                    onLoginSubmitted = { u, pin -> state.login(u, pin) },
                    errorMessage = state.message,
                    onClearError = { state.clearMessage() },
                    showWindowModeButton = showWindowModeButton,
                    isFullscreen = isFullscreen,
                    onToggleWindowMode = onToggleWindowMode,
                    onExitApp = onExitApp
                )
            }
            else -> {
                DesktopShell(
                    state = state,
                    dataDir = dataDir,
                    backupService = backupService,
                    licenseManager = licenseManager,
                    license = license,
                    showWindowModeButton = showWindowModeButton,
                    isFullscreen = isFullscreen,
                    onToggleWindowMode = onToggleWindowMode,
                    onLicenseUpdated = { license = it }
                )
            }
        }

        state.pendingClosingReportRetrySessionId?.let { sessionId ->
            AlertDialog(
                onDismissRequest = { state.pendingClosingReportRetrySessionId = null },
                title = { Text(state.strings.text("Rapport non imprimé", "Report not printed", "لم تتم طباعة التقرير")) },
                text = {
                    Text(
                        state.strings.text(
                            "La session #$sessionId est bien clôturée. Vérifiez l’imprimante client puis réessayez.",
                            "Session #$sessionId is closed. Check the receipt printer and try again.",
                            "تم إغلاق الجلسة رقم $sessionId. تحقق من طابعة الإيصالات ثم أعد المحاولة."
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val retry = state.printSessionClosingReport(sessionId)
                        if (retry.success) {
                            state.pendingClosingReportRetrySessionId = null
                            state.showSuccess(state.strings.text("Rapport imprimé", "Report printed", "تمت طباعة التقرير"))
                        } else {
                            state.showError(retry.errorMessage ?: retry.message, MessagePresentation.PERSISTENT)
                        }
                    }) {
                        Text(state.strings.text("Réessayer", "Retry", "إعادة المحاولة"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { state.pendingClosingReportRetrySessionId = null }) {
                        Text(state.strings.text("Plus tard", "Later", "لاحقاً"))
                    }
                }
            )
        }
    }
}

@Composable
private fun DesktopShell(
    state: DesktopNavState,
    dataDir: Path,
    backupService: DesktopBackupService,
    licenseManager: WindowsLicenseManager,
    license: WindowsLicenseState,
    showWindowModeButton: Boolean,
    isFullscreen: Boolean,
    onToggleWindowMode: () -> Unit,
    onLicenseUpdated: (WindowsLicenseState) -> Unit
) {
    val user = state.currentUser!!
    val isOwner = user.role == UserRole.OWNER
    val strings = state.strings
    val showOperationalTopBar = state.currentRoute in setOf(
        DesktopScreenRoute.POS_MAIN,
        DesktopScreenRoute.ACTIVE_ORDERS,
        DesktopScreenRoute.CURRENT_SESSION
    )
    var showLogoutReminder by remember(user.id) { mutableStateOf(false) }
    var logoutActionInProgress by remember(user.id) { mutableStateOf(false) }
    var catalogImportSourcePath by remember { mutableStateOf<Path?>(null) }
    var catalogImportAnalysis by remember { mutableStateOf<CatalogImportAnalysis?>(null) }
    var showCatalogImportDialog by remember { mutableStateOf(false) }
    val requestLogout = {
        if (state.session?.cashierId == user.id && state.session?.status == RegisterSessionStatus.OPEN) {
            showLogoutReminder = true
        } else {
            state.lock()
        }
    }

    val effectiveRoute = if (!isOwner && isOwnerRoute(state.currentRoute)) {
        DesktopScreenRoute.POS_MAIN
    } else {
        state.currentRoute
    }

    Scaffold(
        topBar = {
            Column {
                if (effectiveRoute == DesktopScreenRoute.DASHBOARD) {
                    TrialBanner(
                        licenseState = license,
                        strings = strings,
                        onActivateClick = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) }
                    )
                }
                if (showOperationalTopBar) {
                    PosServiceHeader(
                        cashierName = user.name,
                        strings = strings,
                        onDashboard = if (isOwner) ({ state.navigateTo(DesktopScreenRoute.DASHBOARD) }) else null,
                        onOpenPos = if (effectiveRoute == DesktopScreenRoute.POS_MAIN) null else ({
                            state.navigateTo(DesktopScreenRoute.POS_MAIN)
                        }),
                        onActiveOrders = { state.navigateTo(DesktopScreenRoute.ACTIVE_ORDERS) },
                        onCurrentSession = { state.navigateTo(DesktopScreenRoute.CURRENT_SESSION) },
                        showWindowModeButton = showWindowModeButton,
                        isFullscreen = isFullscreen,
                        onToggleWindowMode = onToggleWindowMode,
                        onLock = requestLogout
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(PosColors.Workspace)) {
            when (effectiveRoute) {
                DesktopScreenRoute.SETUP, DesktopScreenRoute.USER_SELECTION -> {
                    // Managed by top-level DesktopNavGraph container
                }
                DesktopScreenRoute.DASHBOARD -> {
                    var todaySummary by remember(state.currentRoute) { mutableStateOf(state.db.todaySalesSummary()) }
                    LaunchedEffect(state.currentRoute) {
                        while (isActive) {
                            todaySummary = state.db.todaySalesSummary()
                            delay(5_000L)
                        }
                    }
                    DashboardScreen(
                        summary = todaySummary,
                        strings = strings,
                        onNavigateToPos = { state.navigateTo(DesktopScreenRoute.POS_MAIN) },
                        onNavigateToSales = { state.navigateTo(DesktopScreenRoute.COMPLETED_SALES) },
                        onNavigateToDailyReport = { state.navigateTo(DesktopScreenRoute.DAILY_REPORT) },
                        onNavigateToProducts = { state.navigateTo(DesktopScreenRoute.PRODUCT_MGMT) },
                        onNavigateToCategories = { state.navigateTo(DesktopScreenRoute.CATEGORY_MGMT) },
                        onNavigateToTables = { state.navigateTo(DesktopScreenRoute.TABLE_MGMT) },
                        onNavigateToCashiers = { state.navigateTo(DesktopScreenRoute.CASHIER_MGMT) },
                        onNavigateToRegisterHistory = { state.navigateTo(DesktopScreenRoute.REGISTER_HISTORY) },
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToSettings = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onLockPos = requestLogout,
                        showWindowModeButton = showWindowModeButton,
                        isFullscreen = isFullscreen,
                        onToggleWindowMode = onToggleWindowMode
                    )
                }
                DesktopScreenRoute.POS_MAIN -> {
                    POSMainScreen(
                        products = state.products,
                        categories = state.categories,
                        popularCategoryIds = state.popularCategoryIds,
                        categoryScores = state.categoryScores,
                        productScores = state.productScores,
                        cart = state.cart,
                        discountBasisPoints = state.discountBasisPoints,
                        itemDiscountsBasisPoints = state.itemDiscountsBasisPoints,
                        strings = strings,
                        onProductClicked = { p ->
                            state.cart[p.id] = (state.cart[p.id] ?: 0) + 1
                            state.syncCustomerDisplayCart()
                        },
                        onQuantityChanged = { pid, q ->
                            if (q <= 0) {
                                state.cart.remove(pid)
                                state.itemDiscountsBasisPoints.remove(pid)
                            } else {
                                state.cart[pid] = q
                            }
                            state.syncCustomerDisplayCart()
                        },
                        onDiscountChanged = {
                            state.discountBasisPoints = it.coerceIn(0, 10_000)
                            state.syncCustomerDisplayCart()
                        },
                        onItemDiscountChanged = { pid, bps ->
                            if (bps <= 0) {
                                state.itemDiscountsBasisPoints.remove(pid)
                            } else {
                                state.itemDiscountsBasisPoints[pid] = bps.coerceIn(0, 10_000)
                            }
                            state.syncCustomerDisplayCart()
                        },
                        onHoldOrder = { state.holdOrder() },
                        onProceedToPayment = { state.createOrder() },
                        onClearCart = {
                            state.cart.clear()
                            state.itemDiscountsBasisPoints.clear()
                            state.editingOrderId = null
                            state.discountBasisPoints = 0
                            state.syncCustomerDisplayCart()
                        },
                        onBarcodeScanned = { barcode -> state.onBarcodeScanned(barcode) },
                        message = state.message,
                        uiMessage = state.uiMessage,
                        onClearMessage = { state.clearMessage() }
                    )
                }
                DesktopScreenRoute.ACTIVE_ORDERS -> {
                    ActiveOrdersScreen(
                        openOrders = state.openOrders,
                        tables = state.tables,
                        strings = strings,
                        onResumeOrder = { o -> state.resumeOrder(o) },
                        onPaymentOrder = { o -> state.pendingOrder = o; state.navigateTo(DesktopScreenRoute.PAYMENT) },
                        onCancelOrder = { id, reason, pin ->
                            runCatching { state.db.cancelOrder(id, reason, user.id, pin) }
                                .onFailure { state.showError(strings.text("Annulation impossible", "Cancellation failed", "تعذر الإلغاء") + if (!it.message.isNullOrBlank()) ": ${it.message}" else "") }
                            state.refresh()
                        },
                        onMoveOrderTable = { id, tid -> runCatching { state.db.moveOrder(id, tid) }; state.refresh() },
                        isCancellationPinRequired = !isOwner && state.db.isOrderCancellationPinConfigured(),
                        onVerifyCancellationPin = { pin -> state.db.verifyOrderCancellationPin(pin) },
                        onBack = { state.navigateTo(DesktopScreenRoute.POS_MAIN) }
                    )
                }
                DesktopScreenRoute.PAYMENT -> {
                    state.pendingOrder?.let { order ->
                        LaunchedEffect(order.id) {
                            state.customerDisplayController.paymentStarted(order.totalCentimes)
                        }
                        PaymentScreen(
                            order = order,
                            strings = strings,
                            onPaymentSubmitted = { m, r -> state.pay(m, r) },
                            onBack = {
                                state.navigateTo(DesktopScreenRoute.POS_MAIN)
                                state.syncCustomerDisplayCart()
                            },
                            onCashAmountChanged = { received, _ ->
                                if (received != null && received > 0L) {
                                    state.customerDisplayController.cashReceived(received, order.totalCentimes)
                                } else {
                                    state.customerDisplayController.paymentStarted(order.totalCentimes)
                                }
                            },
                            onPaymentMethodChanged = { method ->
                                if (method == PaymentMethod.CARD) {
                                    state.customerDisplayController.nonCashPaymentSelected("CARTE / TPE", order.totalCentimes)
                                } else {
                                    state.customerDisplayController.paymentStarted(order.totalCentimes)
                                }
                            },
                            message = state.message,
                            uiMessage = state.uiMessage,
                            onClearMessage = { state.clearMessage() }
                        )
                    } ?: run { state.navigateTo(DesktopScreenRoute.POS_MAIN) }
                }
                DesktopScreenRoute.RECEIPT_PREVIEW -> {
                    state.pendingOrder?.let { order ->
                        ReceiptPreviewScreen(
                            order = order,
                            company = state.getCompany(),
                            kind = state.selectedReceiptKind,
                            customerPrinterConfig = DesktopPrinterConfig(
                                printerName = runBlocking { state.db.settings.get("customer_printer").orEmpty() },
                                paperWidth = runBlocking { state.db.settings.get("printer_width")?.toIntOrNull() ?: 80 }
                            ),
                            kitchenPrinterConfig = DesktopPrinterConfig(
                                printerName = runBlocking { state.db.settings.get("kitchen_printer").orEmpty() },
                                paperWidth = runBlocking { state.db.settings.get("printer_width")?.toIntOrNull() ?: 80 }
                            ),
                            strings = strings,
                            onNavigateToPrinterSettings = if (isOwner) ({ state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) }) else null,
                            onBack = {
                                val target = state.receiptReturnRoute
                                state.pendingOrder = null
                                state.receiptReturnRoute = DesktopScreenRoute.POS_MAIN
                                state.navigateTo(target)
                            }
                        )
                    } ?: run { state.navigateTo(DesktopScreenRoute.POS_MAIN) }
                }
                DesktopScreenRoute.OPEN_REGISTER -> {
                    state.navigateTo(DesktopScreenRoute.POS_MAIN)
                }
                DesktopScreenRoute.CURRENT_SESSION -> {
                    val openSess = state.session
                    val cashSales = runBlocking { openSess?.let { state.db.payments.totalCashForSession(it.id) } ?: 0L }
                    val cardSales = runBlocking { openSess?.let { state.db.payments.totalNonCashForSession(it.id) } ?: 0L }
                    val sales = runBlocking { openSess?.let { state.db.salesHistory(sessionId = it.id) } ?: emptyList() }
                    CurrentSessionScreen(
                        session = state.session,
                        cashMovements = state.cashMovements,
                        cashSalesCentimes = cashSales,
                        cardSalesCentimes = cardSales,
                        sessionSales = sales,
                        strings = strings,
                        canCloseRegister = state.currentUser != null,
                        onCashMovementSubmitted = { t, a, r -> state.movement(t, a, r) },
                        onNavigateToReceipt = { order ->
                            state.pendingOrder = order
                            state.selectedReceiptKind = TicketKind.CUSTOMER
                            state.receiptReturnRoute = DesktopScreenRoute.CURRENT_SESSION
                            state.navigateTo(DesktopScreenRoute.RECEIPT_PREVIEW)
                        },
                        onNavigateToCloseRegister = { state.navigateTo(DesktopScreenRoute.CLOSE_REGISTER) },
                        onBack = { state.navigateTo(DesktopScreenRoute.POS_MAIN) },
                        message = state.message,
                        uiMessage = state.uiMessage,
                        onClearMessage = { state.clearMessage() }
                    )
                }
                DesktopScreenRoute.CLOSE_REGISTER -> {
                    val openSess = state.session
                    if (openSess == null) {
                        state.navigateTo(DesktopScreenRoute.POS_MAIN)
                    } else {
                        val cashSales = runBlocking { state.db.payments.totalCashForSession(openSess.id) }
                        val cardSales = runBlocking { state.db.payments.totalNonCashForSession(openSess.id) }
                        val totalIn = state.cashMovements.filter { it.type == CashMovementType.CASH_IN }.sumOf { it.amountCentimes }
                        val totalOut = state.cashMovements.filter { it.type == CashMovementType.CASH_OUT }.sumOf { it.amountCentimes }
                        val expected = openSess.openingCashCentimes + cashSales + totalIn - totalOut
                        val activeOrdersCount = runBlocking { state.db.countOpenOrdersForSession(openSess.id) }

                        CloseRegisterScreen(
                            openingCashCentimes = openSess.openingCashCentimes,
                            cashSalesCentimes = cashSales,
                            cardSalesCentimes = cardSales,
                            cashInCentimes = totalIn,
                            cashOutCentimes = totalOut,
                            expectedCashCentimes = expected,
                            activeOrdersCount = activeOrdersCount,
                            cashierName = state.currentUser?.name.orEmpty(),
                            strings = strings,
                            onCloseRegisterSubmitted = { input -> state.closeRegister(input) },
                            onNavigateToActiveOrders = { state.navigateTo(DesktopScreenRoute.ACTIVE_ORDERS) },
                            onBack = { state.navigateTo(DesktopScreenRoute.CURRENT_SESSION) },
                            errorMessage = state.message,
                            uiMessage = state.uiMessage,
                            onClearMessage = { state.clearMessage() }
                        )
                    }
                }
            DesktopScreenRoute.REGISTER_HISTORY -> {
                    LaunchedEffect(Unit) {
                        state.loadSessionHistory()
                    }
                RegisterSessionsHistoryScreen(
                    sessions = state.sessionHistory,
                    strings = strings,
                    onReprintClosingReport = { sessionId, type ->
                        val result = state.printSessionClosingReport(sessionId, type = type, isReprint = true)
                        if (result.success) {
                            state.pendingClosingReportRetrySessionId = null
                            val successMsg = when (type) {
                                SessionReportType.SUMMARY -> strings.text("Rapport résumé envoyé à l’imprimante", "Summary report sent to printer", "تم إرسال التقرير الملخص إلى الطابعة")
                                SessionReportType.DETAILED -> strings.text("Rapport détaillé envoyé à l’imprimante", "Detailed report sent to printer", "تم إرسال التقرير المفصل إلى الطابعة")
                            }
                            state.showSuccess(successMsg)
                        } else {
                            state.pendingClosingReportRetrySessionId = sessionId
                            state.showError(result.errorMessage ?: result.message, MessagePresentation.PERSISTENT)
                        }
                    },
                    onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                )
                }
                DesktopScreenRoute.PRODUCT_MGMT -> {
                    ProductManagementScreen(
                        products = state.products,
                        categories = state.categories,
                        strings = strings,
                        onSaveProduct = { p ->
                            val previousPath = state.products.firstOrNull { it.id == p.id }?.imagePath
                            when (val result = runBlocking { SaveProduct(state.db.categories, state.db.products).execute(p) }) {
                                is UseCaseResult.Success -> {
                                    if (previousPath != p.imagePath) state.deleteManagedProductImage(previousPath)
                                    state.refresh()
                                    null
                                }
                                is UseCaseResult.Failure -> {
                                    val reason = result.reason
                                    when {
                                        reason.contains("existe déjà", ignoreCase = true) || reason.contains("DUPLICATE", ignoreCase = true) ->
                                            strings.duplicateProductError
                                        reason.contains("nom", ignoreCase = true) && reason.contains("obligatoire", ignoreCase = true) ->
                                            strings.required
                                        else -> reason
                                    }
                                }
                            }
                        },
                        onToggleProductActive = { p ->
                            val newActive = !p.active
                            runBlocking { state.db.products.save(p.copy(active = newActive, available = newActive)) }
                            state.refresh()
                        },
                        onSoftDeleteProduct = { p ->
                            runBlocking { state.db.softDeleteProduct(p.id) }
                            state.refresh()
                        },
                        onImportImage = {
                            val selected = NativeFileDialogs.selectImage(strings.text("Choisir une image", "Select image", "اختيار صورة"))
                            if (selected != null) {
                                runCatching { state.importProductImage(selected) }
                            } else Result.success(null)
                        },
                        onImportCsv = {
                            val selected = NativeFileDialogs.selectCsv(strings.text("Importer le catalogue CSV", "Import catalog CSV", "استيراد كتالوج CSV"))
                            if (selected != null) {
                                catalogImportSourcePath = selected
                                catalogImportAnalysis = state.analyzeCatalogCsv(selected)
                                showCatalogImportDialog = true
                            }
                            null
                        },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) },
                        message = state.message
                    )
                }
                DesktopScreenRoute.CATALOGUE_PREVIEW -> {
                    CataloguePreviewScreen(
                        products = state.products,
                        strings = strings,
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.CATEGORY_MGMT -> {
                    CategoryManagementScreen(
                        categories = state.categories,
                        products = state.products,
                        strings = strings,
                        onSaveCategory = { cat ->
                            val result = runCatching {
                                val prevCat = state.categories.firstOrNull { it.id == cat.id }
                                runBlocking { state.db.categories.save(cat) }
                                if (prevCat?.imagePath != null && prevCat.imagePath != cat.imagePath) {
                                    state.deleteManagedCategoryImage(prevCat.imagePath)
                                }
                            }
                            if (result.isSuccess) {
                                state.refresh()
                                null
                            } else {
                                val err = result.exceptionOrNull()
                                if (err is DesktopValidationException) {
                                    err.message ?: strings.duplicateCategoryError
                                } else {
                                    err?.message ?: strings.text("Erreur lors de l'enregistrement de la catégorie", "Error saving category", "خطأ أثناء حفظ الفئة")
                                }
                            }
                        },
                        onImportImage = {
                            val selected = NativeFileDialogs.selectImage(strings.text("Sélectionner une image de catégorie", "Select category image", "اختر صورة الفئة"))
                            if (selected != null) {
                                runCatching { state.importCategoryImage(selected) }
                            } else {
                                Result.success(null)
                            }
                        },
                        onToggleCategoryActive = { cat ->
                            runBlocking {
                                state.db.categories.save(cat.copy(active = !cat.active))
                            }
                            state.refresh()
                        },
                        onSoftDeleteCategory = { cat ->
                            runBlocking { state.db.softDeleteCategory(cat.id) }
                            state.refresh()
                        },
                        onImportCsv = {
                            val selected = NativeFileDialogs.selectCsv(strings.text("Importer le catalogue CSV", "Import catalog CSV", "استيراد كتالوج CSV"))
                            if (selected != null) {
                                catalogImportSourcePath = selected
                                catalogImportAnalysis = state.analyzeCatalogCsv(selected)
                                showCatalogImportDialog = true
                            }
                            null
                        },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) },
                        message = state.message
                    )
                }
                DesktopScreenRoute.TABLE_MGMT -> {
                    TableManagementScreen(
                        areas = state.areas,
                        tables = state.tables,
                        strings = strings,
                        onSaveArea = { a ->
                            val result = runCatching {
                                val previousPath = state.areas.firstOrNull { it.id == a.id }?.imagePath
                                state.db.saveArea(a)
                                if (previousPath != a.imagePath) state.deleteManagedAreaImage(previousPath)
                                state.refresh()
                            }
                            if (result.isSuccess) null
                            else {
                                val err = result.exceptionOrNull()
                                if (err is DesktopValidationException) err.message
                                else strings.text("Erreur lors de l'enregistrement de l'espace", "Error saving area", "خطأ أثناء حفظ المساحة")
                            }
                        },
                        onToggleAreaActive = { a -> state.db.saveArea(a.copy(active = !a.active)); state.refresh() },
                        onSoftDeleteArea = { a ->
                            runBlocking { state.db.softDeleteArea(a.id) }
                            state.refresh()
                        },
                        onSaveTable = { aid, name ->
                            val result = runCatching {
                                state.db.createTable(aid, name)
                                state.refresh()
                            }
                            if (result.isSuccess) null
                            else {
                                val err = result.exceptionOrNull()
                                if (err is DesktopValidationException) err.message
                                else strings.text("Erreur lors de l'enregistrement de la table", "Error saving table", "خطأ أثناء حفظ الطاولة")
                            }
                        },
                        onUpdateTable = { tid, name, aid ->
                            val result = runCatching {
                                state.db.updateTable(tid, name, aid)
                                state.refresh()
                            }
                            if (result.isSuccess) null
                            else {
                                val err = result.exceptionOrNull()
                                if (err is DesktopValidationException) err.message
                                else strings.text("Erreur lors de la modification de la table", "Error updating table", "خطأ أثناء تعديل الطاولة")
                            }
                        },
                        onTableStatusChanged = { tid, s ->
                            runBlocking { ChangeTableStatus(state.db.tables).execute(tid, s) }
                            state.refresh()
                        },
                        onToggleTableActive = { t ->
                            runBlocking { state.db.tables.save(t.copy(active = !t.active)) }
                            state.refresh()
                        },
                        onSoftDeleteTable = { t ->
                            runBlocking { state.db.softDeleteTable(t.id) }
                            state.refresh()
                        },
                        onImportImage = {
                            val selected = NativeFileDialogs.selectImage(strings.text("Choisir une image", "Select image", "اختيار صورة"))
                            if (selected != null) {
                                runCatching { state.importAreaImage(selected) }
                                    .onFailure { state.showError(it.message.orEmpty()) }
                                    .getOrNull()
                            } else null
                        },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) },
                        message = state.message,
                        uiMessage = state.uiMessage,
                        onClearMessage = { state.clearMessage() }
                    )
                }
                DesktopScreenRoute.CASHIER_MGMT -> {
                    CashierManagementScreen(
                        users = state.users,
                        strings = strings,
                        onCreateCashier = { name, pin ->
                            runCatching { state.db.createCashier(name, pin); state.refresh() }
                                .exceptionOrNull()?.let { if (it is DesktopValidationException) strings.duplicatePin else it.message }
                        },
                        onUpdateCashier = { id, name, pin, active ->
                            runCatching { state.db.updateCashier(id, name, pin, active); state.refresh() }
                                .exceptionOrNull()?.let { if (it is DesktopValidationException) strings.duplicatePin else it.message }
                        },
                        onSoftDeleteCashier = { u ->
                            runCatching {
                                state.db.softDeleteCashier(u.id)
                                state.refresh()
                            }.onFailure { state.showError(it.message ?: "") }
                        },
                        onUpdateOwnerPin = { newPin ->
                            runCatching { state.db.updateOwnerPin(newPin); state.refresh() }
                                .exceptionOrNull()?.let { if (it is DesktopValidationException) strings.duplicatePin else it.message }
                        },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) },
                        message = state.message,
                        uiMessage = state.uiMessage,
                        onClearMessage = { state.clearMessage() }
                    )
                }
                DesktopScreenRoute.DAILY_REPORT -> {
                    DailySalesReportScreen(
                        summaryForRange = { from, to, cashierId, orderType, categoryIds ->
                            state.db.salesSummary(from, to, cashierId, orderType, categoryIds)
                        },
                        analyticsForRange = { from, to, cashierId, orderType, categoryIds ->
                            state.db.retailSalesAnalytics(from, to, cashierId, orderType, categoryIds)
                        },
                        evolutionForRange = { from, to, isHourly, cashierId, orderType, categoryIds ->
                            state.db.salesEvolution(from, to, isHourly, cashierId = cashierId, orderType = orderType, matchingCategoryIds = categoryIds)
                        },
                        earliestSaleEpoch = { state.db.earliestCompletedSaleEpochMs() },
                        cashiers = state.users,
                        categories = state.categories,
                        strings = strings,
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.COMPLETED_SALES -> {
                    LaunchedEffect(Unit) {
                        state.loadSalesHistory()
                    }
                    CompletedSalesScreen(
                        sales = state.salesHistory,
                        strings = strings,
                        categories = state.categories,
                        products = state.products,
                        onSelectSale = { row -> state.selectedSaleRow = row; state.navigateTo(DesktopScreenRoute.SALE_DETAIL) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.SALE_DETAIL -> {
                    state.selectedSaleRow?.let { row ->
                        SaleDetailScreen(
                            row = row,
                            strings = strings,
                            onNavigateToReceiptPreview = { kind ->
                                state.pendingOrder = row.order
                                state.selectedReceiptKind = kind
                                state.receiptReturnRoute = DesktopScreenRoute.SALE_DETAIL
                                state.navigateTo(DesktopScreenRoute.RECEIPT_PREVIEW)
                            },
                            onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                        )
                    } ?: run { state.navigateTo(DesktopScreenRoute.COMPLETED_SALES) }
                }
                DesktopScreenRoute.SETTINGS -> {
                SettingsScreen(
                        initialCompany = state.getCompany(),
                        initialLogoPath = runBlocking { state.db.settings.get("restaurant_logo_uri").orEmpty() },
                        currentLanguage = state.language,
                        strings = strings,
                    isCancellationPinConfigured = state.db.isOrderCancellationPinConfigured(),
                    automaticSessionClosingReport = runBlocking {
                        SessionClosingReportRules.isAutoPrintEnabled(state.db.settings.get(SESSION_CLOSING_REPORT_SETTING))
                    },
                    onAutomaticSessionClosingReportChanged = { enabled ->
                        state.setAutomaticSessionClosingReport(enabled)
                    },
                        onSetCancellationPin = { pin ->
                            runCatching { state.db.setOrderCancellationPin(pin) }.exceptionOrNull()?.message
                        },
                        onRemoveCancellationPin = {
                            state.db.removeOrderCancellationPin()
                        },
                        onSaveSettings = { company, lang ->
                            runBlocking {
                                mapOf(
                                    "establishment_name" to company.name,
                                    "establishment_specialty" to company.specialty,
                                    "restaurant_address" to company.address,
                                    "restaurant_phone" to company.phone,
                                    "seller_ice" to company.ice,
                                    "seller_tax_id" to company.taxId,
                                    "seller_commercial_register" to company.commercialRegister,
                                    "seller_patente" to company.patente,
                                    "wifi_name" to company.wifiName,
                                    "wifi_code" to company.wifiCode,
                                    "print_establishment_name" to company.printEstablishmentName.toString(),
                                    "selected_language" to lang.code
                                ).forEach { (k, v) -> state.db.settings.put(AppSetting(k, v)) }
                            }
                            state.invalidateCompany()
                            state.language = lang
                            state.showSuccess(strings.text("Paramètres enregistrés avec succès", "Settings saved successfully", "تم حفظ الإعدادات بنجاح"))
                        },
                        onImportLogo = {
                            val selected = NativeFileDialogs.selectImage(strings.text("Choisir un logo", "Select logo", "اختيار شعار"))
                            if (selected != null) {
                                val path = runCatching { state.importProductImage(selected) }.getOrNull()
                                if (path != null) {
                                    runBlocking { state.db.settings.put(AppSetting("restaurant_logo_uri", path)) }
                                    state.invalidateCompany()
                                }
                                path
                            } else null
                        },
                        onRemoveLogo = {
                            runBlocking { state.db.settings.put(AppSetting("restaurant_logo_uri", "")) }
                            state.invalidateCompany()
                        },
                        onNavigateToPrinterSettings = { state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) },
                        onNavigateToCustomerDisplay = { state.navigateTo(DesktopScreenRoute.CUSTOMER_DISPLAY_SETTINGS) },
                        onNavigateToBackupRestore = { state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE) },
                        onNavigateToDataManagement = { state.navigateTo(DesktopScreenRoute.DATA_MANAGEMENT) },
                        onNavigateToLicenseManagement = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.CUSTOMER_DISPLAY_SETTINGS -> {
                    CustomerDisplaySettingsScreen(
                        controller = state.customerDisplayController,
                        strings = strings,
                        onSaveSettings = { cfg ->
                            runBlocking {
                                CustomerDisplaySettingsRepository.saveConfig(state.db.settings, cfg)
                            }
                        },
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToPrinters = { state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) },
                        onNavigateToBackupRestore = { state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE) },
                        onNavigateToDataManagement = { state.navigateTo(DesktopScreenRoute.DATA_MANAGEMENT) },
                        onNavigateToLicense = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) },
                        onBack = { state.navigateTo(DesktopScreenRoute.SETTINGS) }
                    )
                }
                DesktopScreenRoute.PRINTER_SETTINGS -> {
                    val defaultDiscovered = remember { state.printerService.getDefaultPrinter()?.name.orEmpty() }
                    val defaultFallback = remember { state.getEffectiveCustomerPrinter() }
                    PrinterSettingsScreen(
                        customerPrinterName = runBlocking {
                            val saved = state.db.settings.get("customer_printer").orEmpty()
                            val isWindows = DesktopPlatform.detect() == DesktopPlatform.WINDOWS
                            if (isWindows && (saved == PrinterService.DEFAULT_LINUX_POS_QUEUE || WindowsThermalPrinter.isIgnored(saved))) {
                                defaultFallback
                            } else {
                                saved.ifBlank { defaultFallback }
                            }
                        },
                        kitchenPrinterName = "",
                        paperWidth = runBlocking { state.db.settings.get("printer_width")?.toIntOrNull() ?: 80 },
                        cashDrawerEnabled = runBlocking { state.db.settings.get("cash_drawer_enabled") == "true" },
                        company = state.getCompany(),
                        strings = strings,
                        automaticSessionClosingReport = runBlocking {
                            SessionClosingReportRules.isAutoPrintEnabled(state.db.settings.get(SESSION_CLOSING_REPORT_SETTING))
                        },
                        onAutomaticSessionClosingReportChanged = { enabled ->
                            state.setAutomaticSessionClosingReport(enabled)
                        },
                        onSavePrinterSettings = { cust, _, w, drawerEnabled ->
                            runBlocking {
                                state.db.settings.put(AppSetting("customer_printer", cust.trim().ifBlank { defaultFallback }))
                                state.db.settings.put(AppSetting("kitchen_printer", ""))
                                state.db.settings.put(AppSetting("printer_width", w.toString()))
                                state.db.settings.put(AppSetting("cash_drawer_enabled", drawerEnabled.toString()))
                            }
                        },
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToBackupRestore = { state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE) },
                        onNavigateToDataManagement = { state.navigateTo(DesktopScreenRoute.DATA_MANAGEMENT) },
                        onNavigateToLicense = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.BACKUP_RESTORE -> {
                    BackupRestoreScreen(
                        defaultBackupPath = dataDir.resolve("backups/pos-backup.db"),
                        strings = strings,
                        onBackupRequested = { target -> runCatching { state.db.backupTo(target) } },
                        onRestoreRequested = { source -> runCatching { state.db.stageRestore(source) } },
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToPrinters = { state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) },
                        onNavigateToDataManagement = { state.navigateTo(DesktopScreenRoute.DATA_MANAGEMENT) },
                        onNavigateToLicense = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.DATA_MANAGEMENT -> {
                    DataManagementScreen(
                        db = state.db,
                        strings = strings,
                        onDataResetOrDeleted = { state.onDataResetOrDeleted() },
                        onFactoryResetComplete = { state.onFactoryResetCompleted() },
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToPrinters = { state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) },
                        onNavigateToBackupRestore = { state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE) },
                        onNavigateToLicense = { state.navigateTo(DesktopScreenRoute.LICENSE_GATE) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
                DesktopScreenRoute.LICENSE_GATE -> {
                    LicenseManagementScreen(
                        licenseManager = licenseManager,
                        licenseState = license,
                        strings = strings,
                        onLicenseUpdated = onLicenseUpdated,
                        canNavigateBack = true,
                        onNavigateToEstablishment = { state.navigateTo(DesktopScreenRoute.SETTINGS) },
                        onNavigateToPrinters = { state.navigateTo(DesktopScreenRoute.PRINTER_SETTINGS) },
                        onNavigateToBackupRestore = { state.navigateTo(DesktopScreenRoute.BACKUP_RESTORE) },
                        onNavigateToDataManagement = { state.navigateTo(DesktopScreenRoute.DATA_MANAGEMENT) },
                        onBack = { state.navigateTo(DesktopScreenRoute.DASHBOARD) }
                    )
                }
            }

            PosSnackbar(
                message = state.uiMessage?.takeIf { it.presentation == MessagePresentation.TEMPORARY },
                onDismiss = { state.clearMessage() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    state.resumedSessionNotice?.takeIf { it.cashierId == user.id }?.let { resumed ->
        AlertDialog(
            onDismissRequest = { state.resumedSessionNotice = null },
            title = { Text(strings.text("Reprise de votre session", "Resuming your session", "استئناف جلستك")) },
            text = {
                Text(
                    strings.text(
                        "Votre session de caisse #${resumed.id} est toujours ouverte. Le fond initial, les ventes, paiements et mouvements existants sont conservés.",
                        "Your register session #${resumed.id} is still open. Its opening cash, sales, payments and movements are preserved.",
                        "جلسة الصندوق رقم ${resumed.id} ما زالت مفتوحة. تم الاحتفاظ برصيد الافتتاح والمبيعات والمدفوعات والحركات."
                    )
                )
            },
            confirmButton = {
                Button(onClick = { state.resumedSessionNotice = null }) {
                    Text(strings.text("Continuer", "Continue", "متابعة"))
                }
            }
        )
    }

    state.completedSaleConfirmation?.let { confirmation ->
        SaleCompletedDialog(
            confirmation = confirmation,
            strings = strings,
            onPrintReceipt = {
                state.printCompletedSaleReceipt(confirmation)
                state.completedSaleConfirmation = null
                state.pendingOrder = null
                state.navigateTo(DesktopScreenRoute.POS_MAIN)
            },
            onFinish = {
                state.completedSaleConfirmation = null
                state.pendingOrder = null
                state.navigateTo(DesktopScreenRoute.POS_MAIN)
            }
        )
    }

    if (showLogoutReminder) {
        AlertDialog(
            onDismissRequest = { if (!logoutActionInProgress) showLogoutReminder = false },
            title = {
                Text(
                    strings.text(
                        "Votre session de caisse est toujours ouverte",
                        "Your register session is still open",
                        "جلسة الصندوق ما زالت مفتوحة"
                    ),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Text(
                    strings.text(
                        "Si vous avez terminé votre service, pensez à clôturer votre session de caisse avant de vous déconnecter.",
                        "If you have finished your shift, remember to close your register session before logging out.",
                        "إذا انتهت ورديتك، تذكر إغلاق جلسة الصندوق قبل تسجيل الخروج."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!logoutActionInProgress) {
                            showLogoutReminder = false
                            state.navigateTo(DesktopScreenRoute.CLOSE_REGISTER)
                        }
                    },
                    enabled = !logoutActionInProgress,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text(strings.text("Clôturer ma session", "Close my session", "إغلاق جلستي"))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // Protection double-clic : guard atomique avant toute action.
                            // lock() efface uniquement la mémoire (currentUser, session en RAM).
                            // La session SQLite reste OPEN — elle sera reprise à la prochaine connexion.
                            if (!logoutActionInProgress) {
                                logoutActionInProgress = true
                                showLogoutReminder = false
                                // Appel direct et synchrone — pas de coroutine suspendable.
                                // Vérifie que l'utilisateur n'a pas changé entre temps.
                                if (state.currentUser?.id == user.id) {
                                    state.lock()
                                } else {
                                    logoutActionInProgress = false
                                }
                            }
                        },
                        enabled = !logoutActionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PosColors.Alert
                        ),
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Text(
                            strings.text("Se déconnecter quand même", "Log out anyway", "تسجيل الخروج رغم ذلك"),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogoutReminder = false },
                        enabled = !logoutActionInProgress,
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) {
                        Text(strings.cancel)
                    }
                }
            }
        )
    }

    if (showCatalogImportDialog) {
        CatalogImportDialog(
            strings = strings,
            initialAnalysis = catalogImportAnalysis,
            sourceFile = catalogImportSourcePath,
            onPickNewFile = {
                val selected = NativeFileDialogs.selectCsv(strings.text("Sélectionner un fichier CSV", "Select CSV file", "اختيار ملف CSV"))
                if (selected != null) {
                    catalogImportSourcePath = selected
                    catalogImportAnalysis = state.analyzeCatalogCsv(selected)
                }
            },
            onConfirmImport = { analysis, path ->
                val summary = state.executeCatalogImport(analysis, path)
                showCatalogImportDialog = false
                state.message = summary.display()
            },
            onDismiss = { showCatalogImportDialog = false }
        )
    }
}

private fun loadCompany(db: WindowsPosDatabase): ReceiptCompany = runBlocking {
    ReceiptCompany(
        name = db.settings.get("establishment_name").orEmpty(),
        specialty = db.settings.get("establishment_specialty").orEmpty(),
        address = db.settings.get("restaurant_address").orEmpty(),
        phone = db.settings.get("restaurant_phone").orEmpty(),
        ice = db.settings.get("seller_ice").orEmpty(),
        taxId = db.settings.get("seller_tax_id").orEmpty(),
        commercialRegister = db.settings.get("seller_commercial_register").orEmpty(),
        patente = db.settings.get("seller_patente").orEmpty(),
        wifiName = db.settings.get("wifi_name").orEmpty(),
        wifiCode = db.settings.get("wifi_code").orEmpty(),
        logoPath = db.settings.get("restaurant_logo_uri").orEmpty(),
        printEstablishmentName = db.settings.get("print_establishment_name")?.toBooleanStrictOrNull() ?: true
    )
}

@Composable
fun SaleCompletedDialog(
    confirmation: CompletedSaleConfirmation,
    strings: DesktopStrings,
    onPrintReceipt: () -> Unit,
    onFinish: () -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {
            if (!isProcessing) {
                isProcessing = true
                onFinish()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(PosColors.Success.copy(alpha = 0.12f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PosColors.Success)
            }
        },
        title = {
            Text(
                text = strings.saleCompletedSuccess,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Order details card
                Surface(
                    color = PosColors.Workspace,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.text("Commande :", "Order:", "الطلب:"), color = PosColors.TextMedium, fontSize = 13.sp)
                            Text(confirmation.order.number, fontWeight = FontWeight.Bold, color = PosColors.TextHigh, fontSize = 14.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.text("Mode de paiement :", "Payment method:", "طريقة الدفع:"), color = PosColors.TextMedium, fontSize = 13.sp)
                            val methodText = when (confirmation.paymentMethod) {
                                PaymentMethod.CASH -> strings.cashSales
                                PaymentMethod.CARD -> strings.cardSales
                                PaymentMethod.CARNET_CLIENT -> "CARNET CLIENT"
                                PaymentMethod.MOBILE_QR -> "MOBILE / QR"
                            }
                            Text(methodText, fontWeight = FontWeight.SemiBold, color = PosColors.TextHigh, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = PosColors.Border, thickness = 1.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.text("Total payé :", "Total paid:", "المجموع المدفوع:"), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PosColors.TextHigh)
                            Text(
                                "${MoneyRules.formatFixed(confirmation.order.totalCentimes)} DH",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = PosColors.PrimaryDark
                            )
                        }
                    }
                }

                // If cash payment and change given
                if (confirmation.paymentMethod == PaymentMethod.CASH) {
                    val received = confirmation.receivedCentimes ?: confirmation.order.totalCentimes
                    val change = confirmation.changeCentimes ?: 0L
                    Surface(
                        color = if (change > 0) PosColors.SuccessLight else PosColors.Workspace,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (change > 0) PosColors.Success.copy(alpha = 0.3f) else PosColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.amountReceived, fontSize = 13.sp, color = PosColors.TextMedium)
                                Text("${MoneyRules.formatFixed(received)} DH", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            if (change > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.changeDue, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PosColors.Success)
                                    Text(
                                        "${MoneyRules.formatFixed(change)} DH",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = PosColors.Success
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        onFinish()
                    }
                },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    "✓ " + strings.finishAction,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (!isProcessing) {
                        isProcessing = true
                        onPrintReceipt()
                    }
                },
                enabled = !isProcessing,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PosColors.Primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PosColors.Primary),
                modifier = Modifier
                    .height(48.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    "🖨️ " + strings.printReceipt,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    )
}
