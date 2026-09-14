package ma.elaroui.pos.domain.model

enum class UserRole {
    OWNER,
    CASHIER
}

enum class TableStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED
}

enum class RegisterSessionStatus {
    OPEN,
    CLOSED
}

enum class OrderType {
    DINE_IN,
    TAKEAWAY,
    COUNTER
}

enum class OrderStatus {
    OPEN,
    COMPLETED,
    CANCELLED
}

enum class PaymentMethod {
    CASH,          // Espèces
    CARD,          // Carte Bancaire / TPE
    CARNET_CLIENT, // Deferred Client Account
    MOBILE_QR      // Mobile Pay
}

enum class PaymentStatus {
    COMPLETED,
    VOIDED
}

enum class PrinterType {
    ANDROID_SYSTEM,
    BLUETOOTH_ESCPOS,
    ETHERNET_ESCPOS
}

enum class AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ORDER_CANCELLED,
    PAYMENT_COMPLETED,
    RECEIPT_REPRINTED,
    BACKUP_CREATED,
    RESTORE_COMPLETED,
    RESTORE_FAILED,
    REGISTER_OPENED,
    REGISTER_CLOSED,
    CASH_MOVEMENT,
    USER_CREATED,
    USER_DEACTIVATED,
    PIN_RESET
}
