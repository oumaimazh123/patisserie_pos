package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Universal Real-Time Touch Drag Scrolling modifier for Compose Multiplatform Desktop (Skiko/X11/Linux POS).
 *
 * Guarantees:
 * 1. Continuous real-time tracking: every pointer movement updates scroll state immediately while dragging.
 * 2. Does NOT wait for touch release / pointer up to update scroll position.
 * 3. Uses PointerEventPass.Initial to cleanly intercept drag gestures before child clickables consume them.
 * 4. Tap vs Drag disambiguation: movement below touch slop preserves tap activation for buttons/cards.
 * 5. Movement above touch slop immediately consumes pointer events to cancel child clicks and initiate real-time scrolling.
 * 6. Smooth exponential momentum fling on release when velocity is detected.
 * 7. Touching the screen during an active fling instantly cancels the animation (touch-to-stop).
 */
fun Modifier.touchDragScroll(
    state: ScrollableState,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    var activeFlingJob by remember { mutableStateOf<Job?>(null) }

    this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            // Cancel any ongoing momentum fling immediately when the user touches the screen
            activeFlingJob?.cancel()
            activeFlingJob = null

            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var isDragging = false
            val touchSlop = viewConfiguration.touchSlop.coerceIn(4f, 14f)
            var totalDragY = 0f
            var totalDragX = 0f

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    if (isDragging) {
                        change.consume()
                        val velocityY = velocityTracker.calculateVelocity().y
                        if (abs(velocityY) > 80f) {
                            activeFlingJob = coroutineScope.launch {
                                animateFling(state, velocityY)
                            }
                        }
                    }
                    break
                }

                val delta = change.positionChange()
                val deltaY = delta.y
                val deltaX = delta.x

                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!isDragging) {
                    totalDragY += deltaY
                    totalDragX += deltaX
                    // Engage vertical scrolling when accumulated vertical movement exceeds touch slop
                    // and vertical movement dominates horizontal movement
                    if (abs(totalDragY) > touchSlop && abs(totalDragY) >= abs(totalDragX)) {
                        isDragging = true
                        change.consume()
                        // Immediately dispatch the accumulated delta so the list moves with the finger without lag
                        state.dispatchRawDelta(-totalDragY)
                    }
                } else {
                    change.consume()
                    if (deltaY != 0f) {
                        state.dispatchRawDelta(-deltaY)
                    }
                }
            }
        }
    }
}

/**
 * Horizontal touch drag scrolling modifier for LazyRow or horizontal containers.
 */
fun Modifier.touchHorizontalDragScroll(
    state: ScrollableState,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val coroutineScope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    var activeFlingJob by remember { mutableStateOf<Job?>(null) }

    this.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            // Cancel any ongoing momentum fling immediately when the user touches the screen
            activeFlingJob?.cancel()
            activeFlingJob = null

            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var isDragging = false
            val touchSlop = viewConfiguration.touchSlop.coerceIn(4f, 14f)
            var totalDragX = 0f
            var totalDragY = 0f

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    if (isDragging) {
                        change.consume()
                        val velocityX = velocityTracker.calculateVelocity().x
                        if (abs(velocityX) > 80f) {
                            activeFlingJob = coroutineScope.launch {
                                animateFling(state, velocityX)
                            }
                        }
                    }
                    break
                }

                val delta = change.positionChange()
                val deltaX = delta.x
                val deltaY = delta.y

                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!isDragging) {
                    totalDragX += deltaX
                    totalDragY += deltaY
                    if (abs(totalDragX) > touchSlop && abs(totalDragX) >= abs(totalDragY)) {
                        isDragging = true
                        change.consume()
                        state.dispatchRawDelta(-totalDragX)
                    }
                } else {
                    change.consume()
                    if (deltaX != 0f) {
                        state.dispatchRawDelta(-deltaX)
                    }
                }
            }
        }
    }
}

/**
 * Smooth exponential fling momentum animation for touch releases.
 */
private suspend fun animateFling(state: ScrollableState, initialVelocity: Float) {
    var lastValue = 0f
    val decaySpec = exponentialDecay<Float>(frictionMultiplier = 1.2f)
    val animatable = Animatable(0f)
    val ctx = currentCoroutineContext()

    runCatching {
        // Negate velocity to match scroll offset direction (drag up -> positive scroll offset)
        animatable.animateDecay(-initialVelocity, decaySpec) {
            val delta = value - lastValue
            lastValue = value
            val consumed = state.dispatchRawDelta(delta)
            // Stop fling if end/start of scrollable area reached
            if (abs(consumed) < 0.01f && abs(delta) > 0.5f) {
                ctx.cancel()
            }
        }
    }
}

