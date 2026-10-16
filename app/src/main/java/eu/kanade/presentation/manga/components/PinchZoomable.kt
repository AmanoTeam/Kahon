package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Scale range and damping values of the organic feed zoom
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 3.2f
private const val FULL_SCALE = 3.0f
private const val IDLE_SCALE = 1.0f
private const val RUBBER_BAND_DAMPING = 0.3f
private val SNAP_BACK_SPRING = spring<Float>(dampingRatio = 0.55f, stiffness = 350f)

/**
 * State holder and gesture handler for an organic pinch to zoom.
 *
 * While zoomed, the content is lifted into a full screen overlay so that it is
 * drawn above the rest of the screen instead of being obscured by it:
 *
 * - Pinching starts the zoom as soon as two pointers touch the content.
 * - The scale is rubber-banded: freely between 1x and 3x, damped at 30% beyond
 *   that range, and hard-clamped between 0.5x and 3.2x.
 * - The content is zoomed around the gesture centroid and can be panned with
 *   the remaining finger after lifting one of them, clamped to its bounds.
 * - Releasing snaps the content back to its original state with a spring.
 *
 * The gesture stays attached to the original node (like the reparented view
 * keeps its touch stream), while the visual is rendered by [PinchZoomOverlayHost].
 * The node itself only draws a placeholder while zoomed, just like the empty
 * slot left behind by the detached view.
 */
class PinchZoomState internal constructor(
    private val scope: CoroutineScope,
    internal val host: PinchZoomOverlayHostState?,
) {

    val scale = Animatable(1f)
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)

    var isZooming by mutableStateOf(false)
        private set

    internal var size: IntSize = IntSize.Zero
    internal var layoutBounds: Rect = Rect.Zero
    internal var overlayContent: (@Composable () -> Unit)? = null

    private var settleJob: Job? = null

    internal fun onPinchStarted() {
        settleJob?.cancel()
        isZooming = true
        host?.register(this)
    }

    internal fun onTransform(zoom: Float, centroid: Offset, pan: Offset) = scope.launch {
        val current = scale.value
        val target = (current * zoom).let { raw ->
            when {
                raw > FULL_SCALE -> current + (raw - current) * RUBBER_BAND_DAMPING
                raw < IDLE_SCALE -> current + (raw - current) * RUBBER_BAND_DAMPING
                else -> raw
            }.coerceIn(MIN_SCALE, MAX_SCALE)
        }
        val appliedZoom = if (current != 0f) target / current else 1f

        // Zoom about the gesture centroid while keeping the content under the
        // fingers, then track the centroid movement (pan) on top of it.
        val center = Offset(size.width / 2f, size.height / 2f)
        var tx = appliedZoom * offsetX.value + (1 - appliedZoom) * (centroid.x - center.x) + pan.x
        var ty = appliedZoom * offsetY.value + (1 - appliedZoom) * (centroid.y - center.y) + pan.y

        if (target > IDLE_SCALE) {
            val maxX = (target - IDLE_SCALE) * size.width / 2f
            val maxY = (target - IDLE_SCALE) * size.height / 2f
            tx = tx.coerceIn(-maxX, maxX)
            ty = ty.coerceIn(-maxY, maxY)
        } else {
            tx = 0f
            ty = 0f
        }

        scale.snapTo(target)
        offsetX.snapTo(tx)
        offsetY.snapTo(ty)
    }

    internal fun onPinchEnded() {
        settleJob?.cancel()
        settleJob = scope.launch {
            coroutineScope {
                val scaleJob = async { scale.animateTo(IDLE_SCALE, SNAP_BACK_SPRING) }
                val offsetXJob = async { offsetX.animateTo(0f, SNAP_BACK_SPRING) }
                val offsetYJob = async { offsetY.animateTo(0f, SNAP_BACK_SPRING) }
                awaitAll(scaleJob, offsetXJob, offsetYJob)
            }
            isZooming = false
            host?.unregister(this@PinchZoomState)
        }
    }

    internal fun dispose() {
        settleJob?.cancel()
        host?.unregister(this)
        isZooming = false
    }
}

@Composable
fun rememberPinchZoomState(): PinchZoomState {
    val scope = rememberCoroutineScope()
    val host = LocalPinchZoomOverlayHost.current
    val state = remember(scope, host) { PinchZoomState(scope, host) }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    return state
}

/**
 * Enables an organic pinch to zoom on the modified node.
 *
 * Nothing is consumed while only one pointer is down, so parents such as a
 * LazyColumn keep scrolling normally. Once a second pointer touches down the
 * gesture is claimed: all further changes are consumed, which stops the
 * parent from scrolling while the content is zoomed.
 *
 * The node only handles the gestures and reports its on screen bounds; the
 * zoomed visual is drawn by [PinchZoomOverlayHost].
 */
fun Modifier.pinchZoomable(state: PinchZoomState): Modifier = this
    .onSizeChanged { state.size = it }
    .onGloballyPositioned { state.layoutBounds = it.boundsInRoot() }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zooming = false
            var lastCentroid = Offset.Zero

            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount == 0) break

                if (!zooming) {
                    if (pressedCount >= 2) {
                        zooming = true
                        state.onPinchStarted()
                        lastCentroid = event.calculateCentroid()
                    }
                    continue
                }

                val centroid = event.calculateCentroid()
                val pan = centroid - lastCentroid
                state.onTransform(event.calculateZoom(), centroid, pan)
                lastCentroid = centroid
                event.changes.forEach { it.consume() }
            }

            if (zooming) {
                state.onPinchEnded()
            }
        }
    }

/** The zoomed content currently lifted from the screen layout. */
internal class PinchZoomOverlay(
    val state: PinchZoomState,
    val bounds: Rect,
    val content: @Composable () -> Unit,
)

/** Registry of the covers currently being zoomed, rendered above the whole screen. */
internal class PinchZoomOverlayHostState {

    val overlays = mutableStateListOf<PinchZoomOverlay>()

    fun register(state: PinchZoomState) {
        overlays.removeAll { it.state === state }
        val bounds = state.layoutBounds
        val content = state.overlayContent ?: return
        overlays.add(PinchZoomOverlay(state, bounds, content))
    }

    fun unregister(state: PinchZoomState) {
        overlays.removeAll { it.state === state }
    }
}

internal val LocalPinchZoomOverlayHost = compositionLocalOf<PinchZoomOverlayHostState?> { null }

/**
 * Hosts the zoomed covers above the wrapped content, the Compose equivalent of
 * the content into a full screen overlay container.
 *
 * While a cover is zoomed this draws, on top of all other content: a dim scrim
 * whose alpha follows the zoom (like the overlay container background) and the
 * cover's content positioned exactly where the cover sits in the screen, with
 * the same scale and translation driven by the gesture state.
 *
 * The overlay never intercepts touches; the gesture keeps running on the
 * original node, which is what a reparented view does with its touch stream.
 */
@Composable
fun PinchZoomOverlayHost(content: @Composable () -> Unit) {
    val host = remember { PinchZoomOverlayHostState() }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalPinchZoomOverlayHost provides host) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { hostOrigin = it.positionInRoot() },
        ) {
            content()
            host.overlays.forEach { overlay ->
                key(overlay) {
                    PinchZoomOverlayContent(overlay, hostOrigin)
                }
            }
        }
    }
}

@Composable
private fun PinchZoomOverlayContent(overlay: PinchZoomOverlay, hostOrigin: Offset) {
    val state = overlay.state
    val density = LocalDensity.current
    val sizeDp = with(density) { overlay.bounds.size.toDpSize() }

    // Scrim dimming the screen behind the zoomed cover, like the background
    // of the zoom overlay container.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val alpha = ((state.scale.value - IDLE_SCALE) / (MAX_SCALE - IDLE_SCALE)).coerceIn(0f, 1f)
                drawRect(color = Color.Black, alpha = alpha)
            },
    )

    // The zoomed cover itself, positioned where the original sits in the screen.
    Box(
        Modifier
            .offset {
                IntOffset(
                    (overlay.bounds.left - hostOrigin.x).roundToInt(),
                    (overlay.bounds.top - hostOrigin.y).roundToInt(),
                )
            }
            .requiredSize(sizeDp)
            .graphicsLayer {
                scaleX = state.scale.value
                scaleY = state.scale.value
                translationX = state.offsetX.value
                translationY = state.offsetY.value
            },
    ) {
        overlay.content()
    }
}
