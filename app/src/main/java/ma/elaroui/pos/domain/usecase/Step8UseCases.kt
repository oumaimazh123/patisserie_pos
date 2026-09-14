package ma.elaroui.pos.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import ma.elaroui.pos.core.backup.BackupManager
import ma.elaroui.pos.core.exception.DomainException
import ma.elaroui.pos.core.print.PrintResult
import ma.elaroui.pos.core.print.ReceiptPrinter
import ma.elaroui.pos.data.local.database.POSDatabase
import ma.elaroui.pos.domain.model.*
import ma.elaroui.pos.domain.repository.*
import ma.elaroui.pos.domain.session.SessionManager
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetOwnerDashboardUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val registerRepository: RegisterRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): DashboardStats {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val endOfDay = System.currentTimeMillis()

        val cashSales = paymentRepository.getCashPaymentsForDateRange(startOfDay, endOfDay)
        val cardSales = paymentRepository.getCardPaymentsForDateRange(startOfDay, endOfDay)
        val totalSales = cashSales + cardSales

        val todayOrders = orderRepository.getCompletedOrdersForDateRange(startOfDay, endOfDay)
        val completedCount = todayOrders.size

        // Simple count of open orders & sessions
        val activeOrders = orderRepository.getActiveOrdersForList()
        val openSessions = registerRepository.getOpenSessions()

        return DashboardStats(
            todayCashSalesCentimes = cashSales,
            todayCardSalesCentimes = cardSales,
            todayTotalSalesCentimes = totalSales,
            completedOrderCount = completedCount,
            cancelledOrderCount = 0,
            openOrderCount = 0,
            openSessionCount = 0
        )
    }
}

@Singleton
class GetDailySalesReportUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(dateMs: Long = System.currentTimeMillis()): DailySalesReport {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis - 1

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date(dateMs))

        val completedOrders = orderRepository.getCompletedOrdersForDateRange(startOfDay, endOfDay)
        val completedCount = completedOrders.size
        val cancelledCount = orderRepository.getCancelledOrderCountForDateRange(startOfDay, endOfDay)

        fun recognizedSales(order: Order): Long {
            val completedPayments = order.payments.filter { it.status == PaymentStatus.COMPLETED }
            return if (completedPayments.isNotEmpty()) {
                completedPayments.sumOf { it.amountCentimes }
            } else {
                // Keep legacy completed orders visible even if they pre-date payment records.
                order.totalCentimes
            }
        }

        val salesByPaymentMethod = PaymentMethod.values()
            .associateWith { method ->
                completedOrders.sumOf { order ->
                    order.payments
                        .filter { it.status == PaymentStatus.COMPLETED && it.method == method }
                        .sumOf { it.amountCentimes }
                }
            }
            .filterValues { it > 0L }

        val cashSales = salesByPaymentMethod[PaymentMethod.CASH] ?: 0L
        val cardSales = salesByPaymentMethod[PaymentMethod.CARD] ?: 0L
        val totalSales = completedOrders.sumOf(::recognizedSales)
        val otherSales = (totalSales - cashSales - cardSales).coerceAtLeast(0L)
        val avgOrderValue = if (completedCount > 0) totalSales / completedCount else 0L

        val salesByOrderType = completedOrders.groupBy { it.type }
            .mapValues { entry -> entry.value.sumOf(::recognizedSales) }

        val cashierNames = completedOrders
            .map { it.cashierId }
            .distinct()
            .associateWith { cashierId ->
                userRepository.getUserById(cashierId)?.name ?: "Serveur #$cashierId"
            }
        val salesByCashier = linkedMapOf<String, Long>()
        for (order in completedOrders.sortedBy { cashierNames[it.cashierId] }) {
            val cashierName = cashierNames.getValue(order.cashierId)
            salesByCashier[cashierName] =
                (salesByCashier[cashierName] ?: 0L) + recognizedSales(order)
        }

        val productMap = mutableMapOf<String, Pair<Int, Long>>()
        val categoryMap = mutableMapOf<String, Long>()
        for (order in completedOrders) {
            for (item in order.items) {
                val recognizedLine = if (order.subtotalCentimes > 0) {
                    item.lineTotalCentimes * order.totalCentimes / order.subtotalCentimes
                } else 0L
                val current = productMap[item.productNameSnapshot] ?: Pair(0, 0L)
                productMap[item.productNameSnapshot] = Pair(
                    current.first + item.quantity,
                    current.second + recognizedLine
                )
                val categoryName = item.categoryNameSnapshot?.takeIf { it.isNotBlank() } ?: "Sans catégorie"
                categoryMap[categoryName] = (categoryMap[categoryName] ?: 0L) + recognizedLine
            }
        }

        val topProducts = productMap.map { (name, pair) ->
            ProductSalesSummary(productName = name, quantitySold = pair.first, totalSalesCentimes = pair.second)
        }.sortedByDescending { it.totalSalesCentimes }
            .take(10)

        return DailySalesReport(
            dateString = dateString,
            completedOrderCount = completedCount,
            cancelledOrderCount = cancelledCount,
            cashSalesCentimes = cashSales,
            cardSalesCentimes = cardSales,
            totalSalesCentimes = totalSales,
            avgOrderValueCentimes = avgOrderValue,
            salesByCashier = salesByCashier,
            salesByOrderType = salesByOrderType,
            topProducts = topProducts,
            otherSalesCentimes = otherSales,
            salesByPaymentMethod = salesByPaymentMethod,
            salesByCategory = categoryMap.toList().sortedByDescending { it.second }.toMap()
        )
    }
}

@Singleton
class ExportSalesCsvUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val registerRepository: RegisterRepository
) {
    suspend operator fun invoke(outputStream: OutputStream, startMs: Long, endMs: Long) {
        val orders = orderRepository.getCompletedOrdersForDateRange(startMs, endMs)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        // UTF-8 BOM keeps accented French text readable when opened directly in Excel.
        writer.write("\uFEFF")
        writer.write(
            csvRow(
                "Commande",
                "Type",
                "Caissier",
                "Caisse",
                "Date",
                "Total (DH)",
                "Mode de paiement"
            )
        )

        for (order in orders) {
            val cashier = userRepository.getUserById(order.cashierId)?.name ?: "N/A"
            val registerSession = registerRepository.getSessionById(order.registerSessionId)
            val register = registerSession
                ?.let { registerRepository.getRegisterById(it.registerId) }
                ?.name
                ?: "N/A"
            val dateStr = dateFormat.format(Date(order.completedAt ?: order.createdAt))
            val paymentMethod = order.payments
                .filter { it.status == PaymentStatus.COMPLETED }
                .map { paymentMethodLabel(it.method) }
                .distinct()
                .joinToString(" + ")
                .ifBlank { "INCONNU" }
            val totalDh = String.format(Locale.US, "%.2f", order.totalCentimes / 100.0)

            writer.write(
                csvRow(
                    order.orderNumber,
                    orderTypeLabel(order.type),
                    cashier,
                    register,
                    dateStr,
                    totalDh,
                    paymentMethod
                )
            )
        }
        writer.flush()
    }

    private fun csvRow(vararg values: String): String =
        values.joinToString(separator = ";", postfix = "\n") { value ->
            val singleLine = value.replace("\r", " ").replace("\n", " ")
            "\"${singleLine.replace("\"", "\"\"")}\""
        }

    private fun orderTypeLabel(type: OrderType): String = when (type) {
        OrderType.DINE_IN -> "Sur place"
        OrderType.TAKEAWAY -> "À emporter"
        OrderType.COUNTER -> "Comptoir"
    }

    private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
        PaymentMethod.CASH -> "Espèces"
        PaymentMethod.CARD -> "Carte / TPE"
        PaymentMethod.CARNET_CLIENT -> "Carnet client"
        PaymentMethod.MOBILE_QR -> "Paiement mobile"
    }
}

@Singleton
class GetPrinterSettingsUseCase @Inject constructor(
    private val repository: PrinterSettingsRepository
) {
    suspend operator fun invoke(): PrinterSettings = repository.getSettings()
    fun observe(): Flow<PrinterSettings> = repository.observeSettings()
}

@Singleton
class SavePrinterSettingsUseCase @Inject constructor(
    private val repository: PrinterSettingsRepository
) {
    suspend operator fun invoke(settings: PrinterSettings) {
        require(settings.paperWidth in setOf(58, 80)) { "Largeur du papier client invalide." }
        require(settings.kitchenPaperWidth in setOf(58, 80)) { "Largeur du papier cuisine invalide." }
        validateEndpoint(
            settings.enabled,
            settings.printerType,
            settings.printerAddress,
            "client"
        )
        validateEndpoint(
            settings.kitchenEnabled,
            settings.kitchenPrinterType,
            settings.kitchenPrinterAddress,
            "cuisine"
        )
        repository.saveSettings(settings)
    }

    private fun validateEndpoint(
        enabled: Boolean,
        type: PrinterType,
        address: String?,
        label: String
    ) {
        if (!enabled || type == PrinterType.ANDROID_SYSTEM) return
        val value = address?.trim().orEmpty()
        require(value.isNotBlank()) { "Adresse de l'imprimante $label obligatoire." }
        if (type == PrinterType.BLUETOOTH_ESCPOS) {
            require(value.matches(Regex("(?i)([0-9A-F]{2}:){5}[0-9A-F]{2}"))) {
                "Adresse Bluetooth $label invalide."
            }
        } else {
            val port = value.substringAfter(':', "9100").toIntOrNull()
            require(value.substringBefore(':').isNotBlank() && port in 1..65535) {
                "Adresse Ethernet $label invalide."
            }
        }
    }
}

@Singleton
class PrintReceiptUseCase @Inject constructor(
    private val settingsRepository: PrinterSettingsRepository,
    private val printer: ReceiptPrinter
) {
    suspend operator fun invoke(data: ReceiptData): PrintResult {
        val settings = settingsRepository.getSettings()
        return printer.print(data, settings)
    }

    suspend fun printTest(restaurantName: String): PrintResult {
        val settings = settingsRepository.getSettings()
        if (!settings.enabled) return PrintResult.Failure("Imprimante client désactivée.")
        return printer.printTest(restaurantName, settings)
    }

    suspend fun printKitchenTest(restaurantName: String): PrintResult {
        val settings = settingsRepository.getSettings()
        if (!settings.kitchenEnabled) {
            return PrintResult.Failure("Imprimante cuisine désactivée.")
        }
        val kitchenSettings = settings.copy(
            enabled = settings.kitchenEnabled,
            printerType = settings.kitchenPrinterType,
            printerName = settings.kitchenPrinterName,
            printerAddress = settings.kitchenPrinterAddress,
            paperWidth = settings.kitchenPaperWidth,
            autoPrint = settings.kitchenAutoPrint
        )
        return printer.printTest(restaurantName, kitchenSettings)
    }

    suspend fun printKitchen(data: ReceiptData): PrintResult {
        val settings = settingsRepository.getSettings()
        if (!settings.kitchenEnabled) {
            return PrintResult.Failure("Imprimante cuisine désactivée.")
        }
        val kitchenSettings = settings.copy(
            enabled = true,
            printerType = settings.kitchenPrinterType,
            printerName = settings.kitchenPrinterName,
            printerAddress = settings.kitchenPrinterAddress,
            paperWidth = settings.kitchenPaperWidth,
            autoPrint = settings.kitchenAutoPrint
        )
        return printer.print(data, kitchenSettings)
    }

    suspend fun printKitchenAutomatically(data: ReceiptData): PrintResult {
        val settings = settingsRepository.getSettings()
        if (!settings.kitchenEnabled || !settings.kitchenAutoPrint) {
            return PrintResult.Success
        }
        return printKitchen(data)
    }
}

@Singleton
class CreateBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: POSDatabase,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(outputStream: OutputStream, password: String? = null): Result<Unit> {
        val result = BackupManager.createBackup(context, database, outputStream, password)
        if (result.isSuccess) {
            sessionManager.currentUser?.let { user ->
                auditRepository.recordLog(
                    AuditLog(
                        actionType = AuditAction.BACKUP_CREATED,
                        actingUserId = user.userId,
                        entityType = "SYSTEM",
                        details = if (!password.isNull_or_blank()) "Sauvegarde chiffrée" else "Sauvegarde standard"
                    )
                )
            }
        }
        return result
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}

@Singleton
class RestoreBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: POSDatabase,
    private val auditRepository: AuditRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(inputStream: InputStream, password: String? = null): Result<Unit> {
        val result = BackupManager.restoreBackup(context, database, inputStream, password)
        if (result.isSuccess) {
            // Force logout after successful restore
            sessionManager.logout()
        }
        return result
    }
}

@Singleton
class RecordAuditLogUseCase @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend operator fun invoke(
        actionType: AuditAction,
        actingUserId: Long,
        entityType: String,
        entityId: Long? = null,
        details: String? = null
    ) {
        auditRepository.recordLog(
            AuditLog(
                actionType = actionType,
                actingUserId = actingUserId,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }
}
