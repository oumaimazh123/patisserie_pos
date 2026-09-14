package ma.elaroui.pos.domain.usecase

import ma.elaroui.pos.domain.model.OrderStatus
import ma.elaroui.pos.domain.model.OrderType
import ma.elaroui.pos.domain.model.PaymentMethod
import ma.elaroui.pos.domain.model.PaymentStatus
import ma.elaroui.pos.domain.model.SalesHistoryEntry
import ma.elaroui.pos.domain.repository.OrderRepository
import ma.elaroui.pos.domain.repository.RegisterRepository
import ma.elaroui.pos.domain.repository.UserRepository
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSalesHistoryUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val registerRepository: RegisterRepository
) {
    suspend operator fun invoke(
        status: OrderStatus,
        startMs: Long,
        endMs: Long,
        sessionId: Long?
    ): List<SalesHistoryEntry> {
        require(status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            "L'historique accepte uniquement les ventes clôturées ou annulées."
        }
        require(startMs <= endMs) { "La date de début doit précéder la date de fin." }

        val orders = when (status) {
            OrderStatus.COMPLETED ->
                orderRepository.getCompletedOrdersForDateRange(startMs, endMs)
            OrderStatus.CANCELLED ->
                orderRepository.getCancelledOrdersForDateRange(startMs, endMs)
            OrderStatus.OPEN -> emptyList()
        }.asSequence()
            .filter { sessionId == null || it.registerSessionId == sessionId }
            .sortedByDescending {
                if (status == OrderStatus.COMPLETED) it.completedAt else it.cancelledAt
            }
            .toList()

        val cashierNames = mutableMapOf<Long, String>()
        val sessions = mutableMapOf<Long, ma.elaroui.pos.domain.model.RegisterSession?>()
        val registerNames = mutableMapOf<Long, String>()

        return orders.map { order ->
            val cashierName = cashierNames[order.cashierId] ?: run {
                (userRepository.getUserById(order.cashierId)?.name ?: "Utilisateur inconnu")
                    .also { cashierNames[order.cashierId] = it }
            }
            val session = if (sessions.containsKey(order.registerSessionId)) {
                sessions[order.registerSessionId]
            } else {
                registerRepository.getSessionById(order.registerSessionId).also {
                    sessions[order.registerSessionId] = it
                }
            }
            val registerId = session?.registerId
            val registerName = if (registerId == null) {
                "Caisse inconnue"
            } else {
                registerNames[registerId] ?: run {
                    (registerRepository.getRegisterById(registerId)?.name ?: "Caisse inconnue")
                        .also { registerNames[registerId] = it }
                }
            }

            SalesHistoryEntry(
                order = order,
                cashierName = cashierName,
                registerId = registerId,
                registerName = registerName
            )
        }
    }
}

@Singleton
class ExportSalesHistoryCsvUseCase @Inject constructor() {
    operator fun invoke(outputStream: OutputStream, entries: List<SalesHistoryEntry>) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH)
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        writer.write("\uFEFF")
        writer.write(
            csvRow(
                "Statut",
                "Commande",
                "Date",
                "Caissier",
                "Caisse",
                "Type",
                "Mode de paiement",
                "Total (DH)",
                "Motif d'annulation"
            )
        )

        entries.forEach { entry ->
            val order = entry.order
            val eventAt = if (order.status == OrderStatus.CANCELLED) {
                order.cancelledAt ?: order.updatedAt
            } else {
                order.completedAt ?: order.updatedAt
            }
            val paymentMethods = order.payments
                .filter { it.status == PaymentStatus.COMPLETED }
                .map { paymentMethodLabel(it.method) }
                .distinct()
                .joinToString(" + ")
                .ifBlank { "—" }

            writer.write(
                csvRow(
                    if (order.status == OrderStatus.CANCELLED) "Annulée" else "Clôturée",
                    order.orderNumber,
                    dateFormat.format(Date(eventAt)),
                    entry.cashierName,
                    entry.registerName,
                    orderTypeLabel(order.type),
                    paymentMethods,
                    String.format(Locale.US, "%.2f", order.totalCentimes / 100.0),
                    order.cancellationReason.orEmpty()
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
