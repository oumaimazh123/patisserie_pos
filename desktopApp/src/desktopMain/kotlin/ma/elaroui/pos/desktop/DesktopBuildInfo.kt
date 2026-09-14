package ma.elaroui.pos.desktop

object DesktopBuildInfo {
    const val APPLICATION_NAME = "PATISSERIE_POS"
    const val APPLICATION_ID = "ma.elaroui.generalpos"
    const val PRODUCT_ID = "GENERAL_POS_V1"
    const val LINUX_PACKAGE_NAME = "patisserie-pos"
    const val LINUX_DESKTOP_ID = "ma.elaroui.patisseriepos.desktop"
    const val WINDOWS_UPGRADE_UUID = "46788696-3912-4d20-9f7d-220b62935d31"
    val version: String get() = System.getProperty("generalPos.version", "development")
    val operatingSystem: String get() = System.getProperty("os.name", "Unknown")
    val architecture: String get() = System.getProperty("os.arch", "Unknown")
}
