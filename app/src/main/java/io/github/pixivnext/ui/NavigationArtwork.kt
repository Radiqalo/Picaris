package io.github.pixivnext.ui

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ArtworkFrame(val bounds: Rect, val corners: Rect, val clip: Rect)

internal class ArtworkLayer(
    val key: String,
    val owner: Long?,
    val tab: Int?,
    val context: GraphicsContext,
) {
    val content = context.createGraphicsLayer()
    val placement = context.createGraphicsLayer()
    var coordinates: LayoutCoordinates? = null
    var corners = Rect.Zero
    var pageRadius: () -> Float = { 0f }
    var pageCoordinates: LayoutCoordinates? = null
    var visible = true
    var recorded = false
    var recordedPainter: Painter? = null
    var recordedSize = IntSize.Zero
    var detached = false
    var references = 0
    var released = false

    fun retain() { references++ }
    fun release() {
        references--
        disposeIfUnused()
    }
    fun disposeIfUnused() {
        if (detached && references == 0 && !released) {
            released = true
            context.releaseGraphicsLayer(placement)
            context.releaseGraphicsLayer(content)
        }
    }
}

internal class ArtworkValue<Value>(initial: Value) {
    var value by mutableStateOf(initial)
}

private class ArtworkAnimation<Value, Vector : AnimationVector>(
    private val state: ArtworkValue<Value>,
    private val converter: TwoWayConverter<Value, Vector>,
    private val motion: FiniteAnimationSpec<Value>,
    var velocity: Value,
    private val durationScale: Float,
) {
    private var animation: TargetBasedAnimation<Value, Vector>? = null
    private var start = 0L

    fun pause() { animation = null }

    /** Play time in animation time, honouring the platform's animation duration scale. */
    private fun playTime(now: Long): Long =
        ((now - start).coerceAtLeast(0L) / durationScale).toLong()

    fun advance(now: Long, target: Value): Boolean {
        // A zero scale means the system asks for no animation: land directly on the target
        // instead of driving a frame loop Compose's own animation APIs would have skipped.
        if (durationScale <= 0f) {
            state.value = target
            animation = null
            return true
        }
        var current = animation
        if (current != null) {
            val elapsed = playTime(now)
            state.value = current.getValueFromNanos(elapsed)
            velocity = converter.convertFromVector(current.getVelocityVectorFromNanos(elapsed))
        }
        if (current == null || current.targetValue != target) {
            current = TargetBasedAnimation(motion, converter, state.value, target,
                converter.convertToVector(velocity))
            animation = current
            start = now
        }
        return playTime(now) >= current.durationNanos
    }
}

internal class ArtworkFlight(val key: String, val source: ArtworkLayer, frame: ArtworkFrame) {
    val bounds = ArtworkValue(frame.bounds)
    val corners = ArtworkValue(frame.corners)
    val clip = ArtworkValue(frame.clip)
    val alpha = ArtworkValue(1f)
    var boundsVelocity = Rect.Zero
    var cornersVelocity = Rect.Zero
    var clipVelocity = Rect.Zero
    var alphaVelocity = 0f
    var target: ArtworkFrame? = null
    var targetLayer: ArtworkLayer? = null
    var job: Job? = null
    var generation = 0L
    var handoffFrame: ArtworkFrame? = null
}

internal class NavigationArtwork(
    private val coordinator: NavigationTransitionCoordinator,
    private val scope: CoroutineScope,
    private val spatial: FiniteAnimationSpec<Rect>,
    private val effects: FiniteAnimationSpec<Float>,
) {
    var root: LayoutCoordinates? = null
    var recordingSnapshot = false
    private val layers = linkedSetOf<ArtworkLayer>()
    private val pending = linkedMapOf<String, Pair<ArtworkLayer, ArtworkFrame>>()
    val flights = mutableStateMapOf<String, ArtworkFlight>()
    private var disposed = false
    private class PreviewAnchor(val frame: ArtworkFrame, val page: Rect, val layer: ArtworkLayer) {
        var lastFrame = frame
        var lastTime = System.nanoTime()
    }
    private val previews = mutableMapOf<String, PreviewAnchor>()
    var previewTick by mutableLongStateOf(0L)
        private set
    val isActive: Boolean get() = flights.isNotEmpty()

    fun hasFlight(key: String, owner: Long?): Boolean {
        val flight = flights[key] ?: return false
        return owner != null && (flight.source.owner == owner || flight.targetLayer?.owner == owner)
    }

    fun beginPreview() {
        flights.values.filter { it.targetLayer?.owner in coordinator.activeEntries }.forEach { flight ->
            val layer = flight.targetLayer ?: return@forEach
            val page = layer.pageCoordinates?.takeIf { it.isAttached } ?: return@forEach
            val root = root?.takeIf { it.isAttached } ?: return@forEach
            val bounds = root.localBoundingBoxOf(page, clipBounds = false)
            if (bounds.width > 0f && bounds.height > 0f) {
                previews[flight.key] = PreviewAnchor(currentFrame(flight), bounds, layer)
                flight.job?.cancel()
                flight.generation++
            }
        }
    }

    fun invalidatePreview() { if (previews.isNotEmpty()) previewTick++ }

    fun currentFrame(flight: ArtworkFlight): ArtworkFrame {
        flight.handoffFrame?.let { return it }
        val anchor = previews[flight.key]
        val root = root?.takeIf { it.isAttached }
        val page = anchor?.layer?.pageCoordinates?.takeIf { it.isAttached }
        if (anchor != null && root != null && page != null) {
            val current = root.localBoundingBoxOf(page, clipBounds = false)
            val scaleX = current.width / anchor.page.width
            val scaleY = current.height / anchor.page.height
            fun transform(bounds: Rect) = Rect(
                current.left + (bounds.left - anchor.page.left) * scaleX,
                current.top + (bounds.top - anchor.page.top) * scaleY,
                current.left + (bounds.right - anchor.page.left) * scaleX,
                current.top + (bounds.bottom - anchor.page.top) * scaleY,
            )
            val corners = anchor.frame.corners
            val clip = transform(anchor.frame.clip).intersect(current)
            val radius = if (kotlin.math.abs(clip.top - current.top) < 2f)
                anchor.layer.pageRadius() * scaleX else 0f
            val frame = ArtworkFrame(transform(anchor.frame.bounds), Rect(
                maxOf(corners.left * scaleX, radius), maxOf(corners.top * scaleX, radius),
                corners.right * scaleX, corners.bottom * scaleX,
            ), clip)
            val now = System.nanoTime()
            val seconds = (now - anchor.lastTime) / 1_000_000_000f
            if (seconds in 0.001f..0.1f) {
                fun velocity(current: Rect, previous: Rect) = Rect(
                    (current.left - previous.left) / seconds, (current.top - previous.top) / seconds,
                    (current.right - previous.right) / seconds, (current.bottom - previous.bottom) / seconds,
                )
                flight.boundsVelocity = velocity(frame.bounds, anchor.lastFrame.bounds)
                flight.cornersVelocity = velocity(frame.corners, anchor.lastFrame.corners)
                flight.clipVelocity = velocity(frame.clip, anchor.lastFrame.clip)
                anchor.lastFrame = frame
                anchor.lastTime = now
            }
            return frame
        }
        return ArtworkFrame(flight.bounds.value, flight.corners.value, flight.clip.value)
    }

    fun resumePreview() {
        previews.keys.toList().forEach { key ->
            val flight = flights[key] ?: return@forEach
            flight.handoffFrame = currentFrame(flight)
        }
        previews.clear()
        flights.values.filter { it.handoffFrame != null }.forEach { flight ->
            val layer = flight.targetLayer
            animate(flight, layer, layer?.let { frame(it) })
        }
    }

    fun register(layer: ArtworkLayer) { layers.add(layer) }
    fun unregister(layer: ArtworkLayer) {
        layers.remove(layer)
        layer.detached = true
        layer.disposeIfUnused()
        refreshTargets()
    }

    fun frame(layer: ArtworkLayer): ArtworkFrame? {
        val rootCoordinates = root?.takeIf { it.isAttached } ?: return null
        val coordinates = layer.coordinates?.takeIf { it.isAttached } ?: return null
        val bounds = rootCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val clipped = rootCoordinates.localBoundingBoxOf(coordinates, clipBounds = true)
        if (clipped.width <= 0f || clipped.height <= 0f) return null
        val scale = bounds.width / coordinates.size.width.coerceAtLeast(1)
        val topVisible = kotlin.math.abs(bounds.top - clipped.top) < 2f
        val bottomVisible = kotlin.math.abs(bounds.bottom - clipped.bottom) < 2f
        var corners = Rect(
            if (topVisible) layer.corners.left * scale else 0f,
            if (topVisible) layer.corners.top * scale else 0f,
            if (bottomVisible) layer.corners.right * scale else 0f,
            if (bottomVisible) layer.corners.bottom * scale else 0f,
        )
        val page = layer.pageCoordinates?.takeIf { it.isAttached }
        if (page != null && layer.pageRadius() > 0f) {
            val pageBounds = rootCoordinates.localBoundingBoxOf(page, clipBounds = false)
            if (kotlin.math.abs(clipped.top - pageBounds.top) < 2f) {
                val radius = layer.pageRadius() * scale
                corners = Rect(maxOf(corners.left, radius), maxOf(corners.top, radius), corners.right, corners.bottom)
            }
        }
        return ArtworkFrame(bounds, corners, clipped)
    }

    private fun isTarget(layer: ArtworkLayer): Boolean =
        !layer.detached && layer.visible && layer.owner in coordinator.activeEntries &&
            (layer.tab == null || layer.tab == coordinator.activeTab)

    fun prepare(owners: Set<Long>, keys: Set<String>) {
        if (disposed) return
        resumePreview()
        pending.values.forEach { it.first.release() }
        pending.clear()
        val eligible = layers.filter {
            it.owner in owners && it.visible && it.recorded && !it.detached &&
                (it.tab == null || it.tab == coordinator.activeTab)
        }
        eligible.filter {
            it.key in keys || (keys.any { key -> key.startsWith("work-image:") } &&
                it.key.startsWith("work-image:") && it.corners == Rect.Zero)
        }.forEach { layer ->
            val sourceFrame = frame(layer) ?: return@forEach
            pending.remove(layer.key)?.first?.release()
            layer.retain()
            pending[layer.key] = layer to sourceFrame
        }
    }

    fun refreshTargets() {
        if (disposed || coordinator.phase == NavigationTransitionPhase.Previewing ||
            (pending.isEmpty() && flights.isEmpty())) return
        val targets = layers.filter { isTarget(it) && it.recorded }
            .mapNotNull { layer -> frame(layer)?.let { layer.key to (layer to it) } }.toMap()
        pending.toMap().forEach { (key, source) ->
            val target = targets[key] ?: return@forEach
            pending.remove(key)
            val old = flights[key]
            if (old == null) {
                val flight = ArtworkFlight(key, source.first, source.second)
                flights[key] = flight
                animate(flight, target.first, target.second)
            } else {
                source.first.release()
                animate(old, target.first, target.second)
            }
        }
        flights.values.toList().forEach { flight ->
            val target = targets[flight.key]
            if (target == null) {
                if (flight.targetLayer != null) animate(flight, null, null)
            } else if (flight.target != target.second || flight.targetLayer != target.first) {
                animate(flight, target.first, target.second)
            }
        }
        layers.forEach { layer -> layer.placement.alpha = if (layer.key in flights) 0f else 1f }
        flights.values.forEach { it.source.placement.alpha = 0f }
    }

    private fun animate(flight: ArtworkFlight, targetLayer: ArtworkLayer?, target: ArtworkFrame?) {
        flight.target = target
        flight.targetLayer = targetLayer
        if (flight.job?.isActive == true) return
        flight.generation++
        val generation = flight.generation
        flight.job = scope.launch {
            val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            flight.handoffFrame?.let { frame ->
                flight.bounds.value = frame.bounds
                flight.corners.value = frame.corners
                flight.clip.value = frame.clip
                flight.handoffFrame = null
            }
            val bounds = ArtworkAnimation(flight.bounds, Rect.VectorConverter, spatial, flight.boundsVelocity, durationScale)
            val corners = ArtworkAnimation(flight.corners, Rect.VectorConverter, spatial, flight.cornersVelocity, durationScale)
            val clip = ArtworkAnimation(flight.clip, Rect.VectorConverter, spatial, flight.clipVelocity, durationScale)
            val alpha = ArtworkAnimation(flight.alpha, Float.VectorConverter, effects, flight.alphaVelocity, durationScale)
            var finished = false
            while (!finished && flight.generation == generation) {
                withFrameNanos { now ->
                    refreshTargets()
                    val destination = flight.target
                    if (destination == null) {
                        bounds.pause()
                        corners.pause()
                        clip.pause()
                    }
                    val boundsDone = destination == null || bounds.advance(now, destination.bounds)
                    val cornersDone = destination == null || corners.advance(now, destination.corners)
                    val clipDone = destination == null || clip.advance(now, destination.clip)
                    val alphaDone = alpha.advance(now, if (destination == null) 0f else 1f)
                    flight.boundsVelocity = bounds.velocity
                    flight.cornersVelocity = corners.velocity
                    flight.clipVelocity = clip.velocity
                    flight.alphaVelocity = alpha.velocity
                    finished = boundsDone && cornersDone && clipDone && alphaDone &&
                        (destination == null || (!coordinator.hasSceneMotion && !coordinator.hasAnimations))
                }
            }
            if (flight.generation == generation && flights[flight.key] === flight) finish(flight)
        }
    }

    private fun finish(flight: ArtworkFlight) {
        flights.remove(flight.key)
        previews.remove(flight.key)
        flight.source.content.alpha = 1f
        flight.source.release()
        layers.filter { it.key == flight.key }.forEach { it.placement.alpha = 1f }
    }

    fun cancelPending() {
        pending.values.forEach { it.first.release() }
        pending.clear()
    }

    fun dispose() {
        disposed = true
        cancelPending()
        flights.values.toList().forEach { it.job?.cancel(); finish(it) }
    }
}

internal fun artworkKeys(route: androidx.navigation3.runtime.NavKey?): Set<String> =
    when (route) {
        is Detail -> setOf("work-image:${route.work.type}:${route.work.id}")
        is Reader -> setOf("work-image:${route.work.type}:${route.work.id}")
        is Author -> setOf("author:${route.user.id}:avatar")
        else -> emptySet()
    }

internal val LocalNavigationArtwork = staticCompositionLocalOf<NavigationArtwork?> { null }
internal val LocalNavigationPageCoordinates = staticCompositionLocalOf<() -> LayoutCoordinates?> { { null } }
internal val LocalNavigationPageRadius = staticCompositionLocalOf<() -> Float> { { 0f } }

@Composable
internal fun rememberNavigationArtwork(coordinator: NavigationTransitionCoordinator): NavigationArtwork {
    val scope = rememberCoroutineScope()
    val spatial = androidx.compose.material3.MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    val effects = androidx.compose.material3.MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val artwork = remember(coordinator, scope, spatial, effects) { NavigationArtwork(coordinator, scope, spatial, effects) }
    DisposableEffect(artwork) { onDispose { artwork.dispose() } }
    return artwork
}

@Composable
internal fun Modifier.navigationArtwork(key: String, corners: Rect, painter: Painter? = null): Modifier {
    val artwork = LocalNavigationArtwork.current ?: return this
    val owner = LocalNavigationInstance.current
    val tab = LocalNavigationTab.current
    val context = LocalGraphicsContext.current
    val pageCoordinates = LocalNavigationPageCoordinates.current
    val pageRadius = LocalNavigationPageRadius.current
    val visible = LocalNavigationSharedElementVisible.current
    val layer = remember(artwork, key, owner, tab, context) { ArtworkLayer(key, owner, tab, context) }
    SideEffect {
        layer.corners = corners
        layer.visible = visible
        layer.pageCoordinates = pageCoordinates()
        layer.pageRadius = pageRadius
        artwork.refreshTargets()
    }
    DisposableEffect(artwork, layer) {
        artwork.register(layer)
        onDispose { artwork.unregister(layer) }
    }
    return onGloballyPositioned {
        layer.coordinates = it
        layer.pageCoordinates = pageCoordinates()
        artwork.refreshTargets()
    }.drawWithContent {
        val intrinsic = painter?.intrinsicSize
        if (painter != null && intrinsic != null && intrinsic.width.isFinite() && intrinsic.height.isFinite() &&
            intrinsic.width > 0f && intrinsic.height > 0f
        ) {
            val imageSize = IntSize(intrinsic.width.toInt().coerceAtLeast(1), intrinsic.height.toInt().coerceAtLeast(1))
            if (layer.recordedPainter !== painter || layer.recordedSize != imageSize) {
                layer.content.record(size = imageSize) { with(painter) { draw(imageSize.toSize()) } }
                layer.recordedPainter = painter
                layer.recordedSize = imageSize
            }
        } else {
            layer.content.record { this@drawWithContent.drawContent() }
            layer.recordedPainter = null
        }
        val firstRecording = !layer.recorded
        layer.recorded = true
        layer.placement.record { this@drawWithContent.drawContent() }
        if (firstRecording) artwork.refreshTargets()
        drawLayer(layer.placement)
    }
}

@Composable
internal fun NavigationArtworkOverlay(modifier: Modifier = Modifier) {
    val artwork = LocalNavigationArtwork.current ?: return
    Box(modifier.fillMaxSize().drawWithContent {
        artwork.refreshTargets()
        artwork.previewTick
        artwork.flights.values.toList().forEach { flight ->
            val frame = artwork.currentFrame(flight)
            val bounds = frame.bounds
            val clip = frame.clip
            val corners = frame.corners
            val source = flight.source.content
            if (source.size.width > 0 && source.size.height > 0 && bounds.width > 0f && bounds.height > 0f) {
                val outline = Path().apply {
                    addRoundRect(RoundRect(
                        clip.left, clip.top, clip.right, clip.bottom,
                        CornerRadius(corners.left.coerceAtLeast(0f)), CornerRadius(corners.top.coerceAtLeast(0f)),
                        CornerRadius(corners.bottom.coerceAtLeast(0f)), CornerRadius(corners.right.coerceAtLeast(0f)),
                    ))
                }
                source.alpha = flight.alpha.value
                clipPath(outline) {
                    clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                        withTransform({
                            val scale = maxOf(bounds.width / source.size.width, bounds.height / source.size.height)
                            translate(bounds.center.x - source.size.width * scale / 2f,
                                bounds.center.y - source.size.height * scale / 2f)
                            scale(scale, scale, Offset.Zero)
                        }) { drawLayer(source) }
                    }
                }
            }
        }
    })
}
