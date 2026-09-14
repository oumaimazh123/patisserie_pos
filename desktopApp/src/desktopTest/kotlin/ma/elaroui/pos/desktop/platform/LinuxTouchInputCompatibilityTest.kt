package ma.elaroui.pos.desktop.platform

import java.awt.event.MouseEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxTouchInputCompatibilityTest {
    @Test
    fun `buttonless touch press release and click require bridging`() {
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.NOBUTTON))
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_RELEASED, MouseEvent.NOBUTTON))
        assertTrue(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_CLICKED, MouseEvent.NOBUTTON))
    }

    @Test
    fun `real mouse primary clicks remain untouched`() {
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1))
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1))
    }

    @Test
    fun `hover and movement are never converted into clicks`() {
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON))
        assertFalse(LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON))
    }
}
