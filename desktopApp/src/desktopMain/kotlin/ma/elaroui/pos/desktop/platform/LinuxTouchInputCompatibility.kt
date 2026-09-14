package ma.elaroui.pos.desktop.platform

import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import ma.elaroui.pos.shared.PlatformLogger

/**
 * Compatibility bridge for Linux/XWayland touch controllers which expose a tap as an AWT
 * mouse event without a primary-button identity. Compose Desktop intentionally ignores those
 * events because they do not look like a click. Real mouse events already use BUTTON1 and pass
 * through untouched.
 */
class LinuxTouchInputCompatibility(
    private val logger: PlatformLogger,
    private val diagnosticsEnabled: Boolean = false
) {
    private var listener: AWTEventListener? = null

    fun install(window: Window) {
        if (listener != null) return
        val eventListener = AWTEventListener { event ->
            val mouse = event as? MouseEvent ?: return@AWTEventListener
            val component = mouse.component ?: return@AWTEventListener
            if (!belongsToWindow(component, window)) return@AWTEventListener

            if (diagnosticsEnabled) {
                logger.info(
                    "AWT pointer id=${eventName(mouse.id)} button=${mouse.button} " +
                        "modifiers=${mouse.modifiersEx} x=${mouse.x} y=${mouse.y} " +
                        "source=${component.javaClass.name}",
                    "TouchInput"
                )
            }

            if (!requiresPrimaryClickBridge(mouse.id, mouse.button)) return@AWTEventListener
            val modifiers = if (mouse.id == MouseEvent.MOUSE_PRESSED) {
                mouse.modifiersEx or MouseEvent.BUTTON1_DOWN_MASK
            } else {
                mouse.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK.inv()
            }
            val bridged = MouseEvent(
                component,
                mouse.id,
                mouse.`when`,
                modifiers,
                mouse.x,
                mouse.y,
                mouse.xOnScreen,
                mouse.yOnScreen,
                mouse.clickCount.coerceAtLeast(1),
                mouse.isPopupTrigger,
                MouseEvent.BUTTON1
            )
            SwingUtilities.invokeLater { component.dispatchEvent(bridged) }
            logger.info("Bridged Linux touch event to primary click", "TouchInput")
        }
        listener = eventListener
        Toolkit.getDefaultToolkit().addAWTEventListener(eventListener, AWTEvent.MOUSE_EVENT_MASK)
        logger.info("Linux touchscreen compatibility listener installed", "TouchInput")
    }

    fun uninstall() {
        listener?.let(Toolkit.getDefaultToolkit()::removeAWTEventListener)
        listener = null
    }

    companion object {
        internal fun requiresPrimaryClickBridge(eventId: Int, button: Int): Boolean =
            button == MouseEvent.NOBUTTON && eventId in setOf(
                MouseEvent.MOUSE_PRESSED,
                MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED
            )

        private fun belongsToWindow(component: Component, window: Window): Boolean =
            component === window || SwingUtilities.getWindowAncestor(component) === window

        private fun eventName(id: Int): String = when (id) {
            MouseEvent.MOUSE_PRESSED -> "PRESSED"
            MouseEvent.MOUSE_RELEASED -> "RELEASED"
            MouseEvent.MOUSE_CLICKED -> "CLICKED"
            MouseEvent.MOUSE_MOVED -> "MOVED"
            MouseEvent.MOUSE_DRAGGED -> "DRAGGED"
            MouseEvent.MOUSE_ENTERED -> "ENTERED"
            MouseEvent.MOUSE_EXITED -> "EXITED"
            else -> id.toString()
        }
    }
}

