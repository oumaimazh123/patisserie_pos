package ma.elaroui.pos.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationIdentityTest {
    @Test
    fun windowsIdentityUsesGeneralPosProductAndApplicationIds() {
        val identity = ApplicationIdentity(
            applicationId = "ma.elaroui.generalpos",
            productId = "GENERAL_POS_V1",
            platform = PosPlatform.WINDOWS,
            versionName = "1.0"
        )

        assertEquals("ma.elaroui.generalpos", identity.applicationId)
        assertEquals(PosPlatform.WINDOWS, identity.platform)
    }
}
