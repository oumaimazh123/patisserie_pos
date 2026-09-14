package ma.elaroui.pos.desktop.persistence

data class DataGroupSelection(
    val products: Boolean = false,
    val categories: Boolean = false,
    val salesHistory: Boolean = false,
    val suspendedSales: Boolean = false,
    val tablesAndAreas: Boolean = false,
    val cashiers: Boolean = false
) {
    val hasSelection: Boolean
        get() = products || categories || salesHistory || suspendedSales || tablesAndAreas || cashiers
}

data class DeletionSummary(
    val productsDeleted: Int = 0,
    val categoriesDeleted: Int = 0,
    val salesHistoryDeleted: Int = 0,
    val suspendedSalesDeleted: Int = 0,
    val tablesDeleted: Int = 0,
    val areasDeleted: Int = 0,
    val cashiersDeleted: Int = 0,
    val message: String = ""
) {
    val totalRecordsDeleted: Int
        get() = productsDeleted + categoriesDeleted + salesHistoryDeleted + suspendedSalesDeleted + tablesDeleted + areasDeleted + cashiersDeleted
}

data class AuditLogEntry(
    val id: Long,
    val actionType: String,
    val actingUserId: Long,
    val entityType: String,
    val entityId: Long?,
    val createdAtEpochMillis: Long,
    val details: String?
)

data class DatabaseMetrics(
    val productsCount: Int = 0,
    val categoriesCount: Int = 0,
    val completedSalesCount: Int = 0,
    val pendingSalesCount: Int = 0,
    val cashiersCount: Int = 0
)


