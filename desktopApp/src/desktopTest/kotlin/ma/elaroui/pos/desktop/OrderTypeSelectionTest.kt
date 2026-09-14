package ma.elaroui.pos.desktop

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ma.elaroui.pos.desktop.persistence.WindowsPosDatabase
import ma.elaroui.pos.desktop.presentation.navigation.DesktopNavState
import ma.elaroui.pos.shared.domain.OrderType

class OrderTypeSelectionTest {
    @Test
    fun counterIsDefaultOrderType() {
        val root = Files.createTempDirectory("pos-order-type-test")
        WindowsPosDatabase.open(root.resolve("pos.db")).use { db ->
            val state = DesktopNavState(db, root)
            assertEquals(OrderType.COUNTER, state.orderType)
        }
    }
}
