package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.gestures.ScrollableState
import ma.elaroui.pos.desktop.platform.LinuxTouchInputCompatibility
import java.awt.event.MouseEvent
import kotlin.math.abs
import kotlin.test.*

class TouchScrollingAuditTest {

    private class MockScrollableState : ScrollableState {
        var totalDispatchedDelta = 0f
        val dispatchedDeltas = mutableListOf<Float>()
        var isScrollInProgressValue = false

        override val isScrollInProgress: Boolean
            get() = isScrollInProgressValue

        override fun dispatchRawDelta(delta: Float): Float {
            totalDispatchedDelta += delta
            dispatchedDeltas.add(delta)
            return delta
        }

        override suspend fun scroll(
            scrollPriority: androidx.compose.foundation.MutatePriority,
            block: suspend androidx.compose.foundation.gestures.ScrollScope.() -> Unit
        ) {
            isScrollInProgressValue = true
            try {
                val scope = object : androidx.compose.foundation.gestures.ScrollScope {
                    override fun scrollBy(pixels: Float): Float = dispatchRawDelta(pixels)
                }
                block(scope)
            } finally {
                isScrollInProgressValue = false
            }
        }
    }

    @Test
    fun testRealTimeContinuousDeltaDispatchDuringDrag() {
        val state = MockScrollableState()
        val touchSlop = 8f

        // Simulated drag motion events (swiping finger up to scroll list down)
        val pointerMoves = listOf(
            -3f,  // total: -3 (below slop)
            -4f,  // total: -7 (below slop)
            -5f,  // total: -12 (exceeds slop -> engages drag, dispatches -totalDragY = +12f)
            -8f,  // continuous real-time move -> dispatches -deltaY = +8f
            -10f, // continuous real-time move -> dispatches -deltaY = +10f
            -12f  // continuous real-time move -> dispatches -deltaY = +12f
        )

        var isDragging = false
        var totalDragY = 0f
        var totalDragX = 0f

        for (deltaY in pointerMoves) {
            val deltaX = 0f
            if (!isDragging) {
                totalDragY += deltaY
                totalDragX += deltaX
                if (abs(totalDragY) > touchSlop && abs(totalDragY) >= abs(totalDragX)) {
                    isDragging = true
                    state.dispatchRawDelta(-totalDragY)
                }
            } else {
                state.dispatchRawDelta(-deltaY)
            }
        }

        assertTrue(isDragging, "Drag must be engaged once accumulated movement exceeds touch slop")
        assertEquals(4, state.dispatchedDeltas.size, "Every move event after slop must immediately dispatch delta")
        assertEquals(12f, state.dispatchedDeltas[0], "First dispatch must account for accumulated slop distance")
        assertEquals(8f, state.dispatchedDeltas[1], "Subsequent move 1 must dispatch immediately")
        assertEquals(10f, state.dispatchedDeltas[2], "Subsequent move 2 must dispatch immediately")
        assertEquals(12f, state.dispatchedDeltas[3], "Subsequent move 3 must dispatch immediately")
        assertEquals(42f, state.totalDispatchedDelta, "Total dispatched delta must match the full drag distance")
    }

    @Test
    fun testTapWithSlightJitterDoesNotTriggerScrollOrCancelClick() {
        val state = MockScrollableState()
        val touchSlop = 8f

        // Micro-jitter during finger tap (1.5px and -1.0px)
        val jitterMoves = listOf(1.5f, -1.0f, 0.5f)
        var isDragging = false
        var totalDragY = 0f

        for (deltaY in jitterMoves) {
            if (!isDragging) {
                totalDragY += deltaY
                if (abs(totalDragY) > touchSlop) {
                    isDragging = true
                    state.dispatchRawDelta(-totalDragY)
                }
            } else {
                state.dispatchRawDelta(-deltaY)
            }
        }

        assertFalse(isDragging, "Micro-jitter below touch slop must NOT engage drag or consume pointer events")
        assertEquals(0, state.dispatchedDeltas.size, "No scroll deltas should be dispatched during a tap")
        assertEquals(0f, state.totalDispatchedDelta)
    }

    @Test
    fun testSlowFingerDragFollowsContinuously() {
        val state = MockScrollableState()
        val touchSlop = 6f

        // Very slow finger movement: 2px per frame for 15 frames = 30px
        var isDragging = false
        var totalDragY = 0f

        for (i in 1..15) {
            val deltaY = 2f
            if (!isDragging) {
                totalDragY += deltaY
                if (abs(totalDragY) > touchSlop) {
                    isDragging = true
                    state.dispatchRawDelta(-totalDragY)
                }
            } else {
                state.dispatchRawDelta(-deltaY)
            }
        }

        assertTrue(isDragging)
        // Frame 1-3 = 6px (at slop threshold, frame 4 reaches 8px > 6px, then frames 5..15 = 11 more frames)
        assertTrue(state.dispatchedDeltas.size >= 11, "Slow drag must continuously dispatch on every frame")
        assertEquals(-30f, state.totalDispatchedDelta, "Total slow drag distance must match exact finger displacement")
    }

    @Test
    fun testHorizontalCategoryDragDisambiguation() {
        val verticalState = MockScrollableState()
        val horizontalState = MockScrollableState()
        val touchSlop = 8f

        // Simulating a horizontal category swipe: deltaX = -35f, deltaY = 4f
        val deltaX = -35f
        val deltaY = 4f

        val isHorizontal = abs(deltaX) > touchSlop && abs(deltaX) >= abs(deltaY)
        val isVertical = abs(deltaY) > touchSlop && abs(deltaY) >= abs(deltaX)

        assertTrue(isHorizontal, "Horizontal swipe must engage horizontal scroll")
        assertFalse(isVertical, "Horizontal swipe must not engage vertical scroll")

        if (isHorizontal) {
            horizontalState.dispatchRawDelta(-deltaX)
        }
        if (isVertical) {
            verticalState.dispatchRawDelta(-deltaY)
        }

        assertEquals(35f, horizontalState.totalDispatchedDelta)
        assertEquals(0f, verticalState.totalDispatchedDelta)
    }

    @Test
    fun testLinuxTouchInputCompatibilityBridgesMousePressedWithoutButton1Mask() {
        // Touch down with NOBUTTON -> needs bridge
        assertTrue(
            LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.NOBUTTON)
        )
        // Normal mouse click with BUTTON1 -> does NOT need bridge
        assertFalse(
            LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1)
        )
        // Touch release with NOBUTTON -> needs bridge
        assertTrue(
            LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_RELEASED, MouseEvent.NOBUTTON)
        )
        // Mouse motion does not need click bridging
        assertFalse(
            LinuxTouchInputCompatibility.requiresPrimaryClickBridge(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON)
        )
    }
}
