package ma.elaroui.pos.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopBuildInfoTest {
    @Test
    fun exposesStableProductionIdentityAndRuntimeVersionProperty() {
        val previous = System.getProperty("generalPos.version")
        try {
            System.setProperty("generalPos.version", "1.2.5")
            assertEquals("PATISSERIE_POS", DesktopBuildInfo.APPLICATION_NAME)
            assertEquals("ma.elaroui.generalpos", DesktopBuildInfo.APPLICATION_ID)
            assertEquals("GENERAL_POS_V1", DesktopBuildInfo.PRODUCT_ID)
            assertEquals("patisserie-pos", DesktopBuildInfo.LINUX_PACKAGE_NAME)
            assertEquals("ma.elaroui.patisseriepos.desktop", DesktopBuildInfo.LINUX_DESKTOP_ID)
            assertEquals("46788696-3912-4d20-9f7d-220b62935d31", DesktopBuildInfo.WINDOWS_UPGRADE_UUID)
            assertEquals("1.2.5", DesktopBuildInfo.version)
        } finally {
            if (previous == null) System.clearProperty("generalPos.version")
            else System.setProperty("generalPos.version", previous)
        }
    }
}
