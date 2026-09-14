package ma.elaroui.pos.presentation.navigation

sealed class Screen(val route: String, val title: String) {
    object Setup : Screen("setup", "First Run Setup")
    object UserSelection : Screen("user_selection", "Select User")
    object PinLogin : Screen("pin_login", "PIN Login")
    object OpenRegister : Screen("open_register", "Open Register")
    object PosMain : Screen("pos_main", "POS Sales")
    object ActiveOrders : Screen("active_orders", "Active Orders")
    object CurrentSession : Screen("current_session", "Current Session")
    object CloseRegister : Screen("close_register", "Close Register")
    object Dashboard : Screen("dashboard", "Owner Dashboard")
    object CashierManagement : Screen("cashiers_management", "Cashiers")
    object RegisterHistory : Screen("register_history", "Register Sessions History")
    object RegisterManagement : Screen("register_management", "Registers Management")
    object CategoryManagement : Screen("category_management", "Category Management")
    object ProductManagement : Screen("product_management", "Product Management")
    object CataloguePreview : Screen("catalogue_preview", "Catalogue Preview")
    object TableManagement : Screen("table_management", "Dining Areas & Tables")
    object Payment : Screen("payment/{orderId}", "Règlement") {
        fun createRoute(orderId: Long) = "payment/$orderId"
    }
    object PaymentSuccess : Screen("payment_success", "Paiement Effectué")
    object CompletedSales : Screen("completed_sales", "Ventes Clôturées")
    object SaleDetail : Screen("sale_detail/{orderId}", "Détail de Vente") {
        fun createRoute(orderId: Long) = "sale_detail/$orderId"
    }
    object ReceiptPreview : Screen("receipt_preview/{orderId}", "Aperçu du Reçu") {
        fun createRoute(orderId: Long) = "receipt_preview/$orderId"
    }
    object DailySalesReport : Screen("daily_sales_report", "Rapport des Ventes")
    object Settings : Screen("settings", "Paramètres Général")
    object PrinterSettings : Screen("printer_settings", "Configuration Imprimante")
    object BackupRestore : Screen("backup_restore", "Sauvegarde & Restauration")
    object LicenseManagement : Screen("license_management", "Gestion de Licence")
}
