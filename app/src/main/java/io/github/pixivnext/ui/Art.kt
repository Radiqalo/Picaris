package io.github.pixivnext.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import io.github.pixivnext.core.Work

val LocalWorkTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalImageTransitionEnabled = staticCompositionLocalOf { true }
val LocalTransitionTapRouter = staticCompositionLocalOf<TransitionTapRouter?> { null }
val LocalFeedTapTargetsEnabled = staticCompositionLocalOf { false }
internal class ArtworkReturnFeedback(val type: String, val id: Long)
internal val LocalArtworkReturnFeedback = staticCompositionLocalOf<ArtworkReturnFeedback?> { null }
internal data class ArtworkPreviewCorners(val topStart: Float, val topEnd: Float)
internal class ArtworkPreviewHandoff {
    var root: LayoutCoordinates? = null
    val bounds = mutableMapOf<String, Rect>()
    val animatedBounds = mutableMapOf<String, Rect>()
    val targetBounds = mutableMapOf<String, Rect>()
    val sourceCorners = mutableMapOf<String, ArtworkPreviewCorners>()
    val previewCorners = mutableMapOf<String, ArtworkPreviewCorners>()
    val previewSources = mutableMapOf<String, LayoutCoordinates>()

    fun captureReleasedBounds() {
        val previewRoot = root?.takeIf { it.isAttached } ?: return
        previewSources.forEach { (key, image) ->
            if (image.isAttached) {
                bounds[key] = previewRoot.localBoundingBoxOf(image, clipBounds = false)
                animatedBounds[key] = bounds.getValue(key)
                sourceCorners[key]?.let { corners ->
                    val scale = bounds.getValue(key).width / image.size.width
                    previewCorners[key] = ArtworkPreviewCorners(corners.topStart * scale, corners.topEnd * scale)
                }
            }
        }
    }
}
internal val LocalArtworkPreviewHandoff = staticCompositionLocalOf<ArtworkPreviewHandoff?> { null }

private class PreviewBoundsAnimationSpec(
    private val previewBounds: Rect,
    private val animationSpec: FiniteAnimationSpec<Rect>,
    private val onSample: (Rect) -> Unit,
) : FiniteAnimationSpec<Rect> {
    override fun <Vector : AnimationVector> vectorize(
        converter: TwoWayConverter<Rect, Vector>,
    ): VectorizedFiniteAnimationSpec<Vector> {
        val animation = animationSpec.vectorize(converter)
        val preview = converter.convertToVector(previewBounds)
        return object : VectorizedFiniteAnimationSpec<Vector> by animation {
            override fun getValueFromNanos(
                playTimeNanos: Long, initialValue: Vector, targetValue: Vector, initialVelocity: Vector,
            ): Vector {
                val value = animation.getValueFromNanos(playTimeNanos, preview, targetValue, initialVelocity)
                onSample(converter.convertFromVector(value))
                return value
            }

            override fun getVelocityFromNanos(
                playTimeNanos: Long, initialValue: Vector, targetValue: Vector, initialVelocity: Vector,
            ): Vector = animation.getVelocityFromNanos(playTimeNanos, preview, targetValue, initialVelocity)

            override fun getDurationNanos(
                initialValue: Vector, targetValue: Vector, initialVelocity: Vector,
            ): Long = animation.getDurationNanos(preview, targetValue, initialVelocity)

            override fun getEndVelocity(
                initialValue: Vector, targetValue: Vector, initialVelocity: Vector,
            ): Vector = animation.getEndVelocity(preview, targetValue, initialVelocity)
        }
    }
}

@Composable
private fun Modifier.captureArtworkPreview(key: String, pageTopCorners: Boolean = false): Modifier {
    val handoff = LocalArtworkPreviewHandoff.current ?: return this
    val preview = LocalNavigationGestureInProgress.current
    val visible = LocalNavigationSharedElementVisible.current
    val coordinates = remember(key) { arrayOfNulls<LayoutCoordinates>(1) }
    val shape = MaterialTheme.shapes.extraLarge
    val density = androidx.compose.ui.platform.LocalDensity.current
    return onGloballyPositioned {
        coordinates[0] = it
        if (preview && visible) {
            handoff.previewSources[key] = it
            if (pageTopCorners) {
                val imageSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                handoff.sourceCorners[key] = ArtworkPreviewCorners(
                    shape.topStart.toPx(imageSize, density), shape.topEnd.toPx(imageSize, density),
                )
            }
        }
    }.drawWithContent {
        val root = handoff.root
        val image = coordinates[0]
        if (preview && visible && root?.isAttached == true && image?.isAttached == true) {
            handoff.previewSources[key] = image
            handoff.bounds[key] = root.localBoundingBoxOf(image, clipBounds = false)
            if (pageTopCorners) {
                handoff.sourceCorners[key] = ArtworkPreviewCorners(
                    shape.topStart.toPx(size, density), shape.topEnd.toPx(size, density),
                )
            }
        }
        drawContent()
    }
}

@Composable
private fun artworkHandoffMotion(key: String): (Rect) -> FiniteAnimationSpec<Rect> {
    val motion = artworkBoundsMotion()
    val handoff = LocalArtworkPreviewHandoff.current
    val returningFromPreview = LocalNavigationGestureActive.current && !LocalNavigationGestureInProgress.current
    return { target ->
        val bounds = if (returningFromPreview) handoff?.bounds?.get(key) else null
        if (bounds != null) {
            handoff?.targetBounds?.set(key, target)
            PreviewBoundsAnimationSpec(bounds, motion) { handoff?.animatedBounds?.set(key, it) }
        } else motion
    }
}

@Composable
private fun Modifier.guardArtworkHandoff(key: String): Modifier {
    val handoff = LocalArtworkPreviewHandoff.current ?: return this
    val visible = LocalNavigationSharedElementVisible.current
    val returning = LocalNavigationGestureActive.current && !LocalNavigationGestureInProgress.current
    val coordinates = remember(key) { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }.drawWithContent {
        val expected = handoff.animatedBounds[key] ?: handoff.bounds[key]
        val root = handoff.root
        val image = coordinates[0]
        if (returning && visible && expected != null &&
            root?.isAttached == true && image?.isAttached == true
        ) {
            val current = root.localBoundingBoxOf(image, clipBounds = false)
            if (current.width > 0f && current.height > 0f &&
                (kotlin.math.abs(current.left - expected.left) > 1f ||
                    kotlin.math.abs(current.top - expected.top) > 1f ||
                    kotlin.math.abs(current.right - expected.right) > 1f ||
                    kotlin.math.abs(current.bottom - expected.bottom) > 1f)
            ) {
                withTransform({
                    translate(expected.left - current.left, expected.top - current.top)
                    scale(expected.width / current.width, expected.height / current.height, Offset.Zero)
                }) {
                    this@drawWithContent.drawContent()
                }
            } else {
                drawContent()
            }
        } else drawContent()
    }
}

class TransitionTapRouter {
    private data class Target(val bounds: Rect, val onClick: () -> Unit)
    private val targets = linkedMapOf<Any, Target>()

    fun update(key: Any, bounds: Rect, onClick: () -> Unit) {
        targets[key] = Target(bounds, onClick)
    }

    fun remove(key: Any) {
        targets.remove(key)
    }

    fun dispatch(positionInWindow: Offset) {
        targets.values.lastOrNull { it.bounds.contains(positionInWindow) }?.onClick?.invoke()
    }
}

/** Retain exit visuals without retaining the exited screen's hit-test surface. */
@Composable
fun NavigationExitContent(isInteractive: () -> Boolean, content: @Composable () -> Unit) {
    val interactive = isInteractive()
    val predictiveBack = LocalNavigationGestureActive.current
    val navigation = LocalNavAnimatedContentScope.current.transition
    val layer = rememberGraphicsLayer()
    var captured by remember { mutableStateOf(false) }
    var gestureActive by remember { mutableStateOf(false) }
    val captureReady = navigation.targetState != EnterExitState.Visible
    val input = if (interactive || !captured || gestureActive)
        Modifier.forwardCommittedExitTaps(isInteractive) { gestureActive = it } else Modifier
    val semantics = if (interactive) Modifier else Modifier.clearAndSetSemantics { }

    Layout(
        content = { Box(Modifier.fillMaxSize()) { content() } },
        modifier = Modifier.fillMaxSize().then(input).then(semantics).drawWithContent {
            when {
                interactive || predictiveBack -> {
                    captured = false
                    drawContent()
                }
                !captureReady -> drawContent()
                else -> {
                    if (!captured) {
                        // Shared artwork is already rendered by SharedTransitionLayout's overlay.
                        // Cache the remaining exit visuals, then stop placing their live children.
                        layer.record { this@drawWithContent.drawContent() }
                        captured = true
                    }
                    drawLayer(layer)
                }
            }
        },
    ) { measurables, constraints ->
        val child = measurables.single().measure(constraints)
        layout(child.width, child.height) {
            if (interactive || predictiveBack || !captured) child.place(0, 0)
        }
    }
}

@Composable
private fun Modifier.forwardCommittedExitTaps(
    isInteractive: () -> Boolean,
    onGestureActiveChanged: (Boolean) -> Unit,
): Modifier {
    val interactiveState = rememberUpdatedState(isInteractive)
    val gestureActiveChanged = rememberUpdatedState(onGestureActiveChanged)
    val router = LocalTransitionTapRouter.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topGuard = with(density) { 100.dp.toPx() }
    val bottomGuard = with(density) { 104.dp.toPx() }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val size = remember { mutableStateOf(IntSize.Zero) }
    val currentOrigin = rememberUpdatedState(origin.value)
    val currentSize = rememberUpdatedState(size.value)
    return this
        .onGloballyPositioned { origin.value = it.positionInWindow() }
        .onSizeChanged { size.value = it }
        .pointerInput(router, topGuard, bottomGuard) {
            if (router != null) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    gestureActiveChanged.value(true)
                    try {
                        val downOrigin = currentOrigin.value
                        var moved = false
                        var released = false
                        if (!interactiveState.value()) down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop)
                                moved = true
                            if (!interactiveState.value()) change.consume()
                            if (!change.pressed) released = true
                        } while (change.pressed)
                        val currentHeight = currentSize.value.height.toFloat()
                        if (released && !moved && !interactiveState.value() &&
                            down.position.y in topGuard..(currentHeight - bottomGuard)
                        ) router.dispatch(downOrigin + down.position)
                    } finally {
                        gestureActiveChanged.value(false)
                    }
                }
            }
        }
}

@Composable
private fun artworkBoundsMotion(): FiniteAnimationSpec<Rect> {
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    // Preserve the theme's spring trajectory; stop when the remaining movement is subpixel.
    return if (motion is SpringSpec<Rect>) spring(
        dampingRatio = motion.dampingRatio,
        stiffness = motion.stiffness,
        visibilityThreshold = Rect(1f, 1f, 1f, 1f),
    ) else motion
}

@Composable
fun Modifier.workTransitionControls(): Modifier {
    val gestureActive = LocalNavigationGestureInProgress.current
    val navigation = LocalNavAnimatedContentScope.current
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val opacity by navigation.transition.animateFloat(
        transitionSpec = { effects }, label = "artwork controls opacity",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val transition = LocalWorkTransition.current
    val overlay = if (transition != null) with(transition) {
        this@workTransitionControls.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = 2f,
            renderInOverlay = { isTransitionActive && !gestureActive },
        )
    } else this
    return overlay.graphicsLayer { alpha = if (gestureActive) 1f else opacity.coerceIn(0f, 1f) }
}

@Composable
fun Modifier.aboveWorkTransition(): Modifier {
    val gestureActive = LocalNavigationGestureInProgress.current
    val transition = LocalWorkTransition.current ?: return this
    val navigation = LocalNavAnimatedContentScope.current
    return with(transition) {
        this@aboveWorkTransition.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = 1f,
            renderInOverlay = {
                isTransitionActive && !gestureActive && navigation.transition.targetState == EnterExitState.Visible
            },
        )
    }
}

@Composable
fun Modifier.authorAvatarTransition(id: Long, enabled: Boolean = true): Modifier {
    val gestureActive = LocalNavigationGestureInProgress.current
    val transition = LocalWorkTransition.current
    if (!enabled || transition == null || id == 0L) return this
    val visible = LocalNavigationSharedElementVisible.current
    val preview = rememberUpdatedState(gestureActive)
    val config = remember {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = !preview.value
            override val shouldKeepEnabledForOngoingAnimation: Boolean = false
        }
    }
    val sharedKey = "author:$id:avatar"
    val motion = artworkHandoffMotion(sharedKey)
    return with(transition) {
        val key = rememberSharedContentState(sharedKey, config)
        this@authorAvatarTransition.captureArtworkPreview(sharedKey)
            .sharedElementWithCallerManagedVisibility(key, visible,
            boundsTransform = { _, target -> motion(target) },
            renderInOverlayDuringTransition = !gestureActive,
            clipInOverlayDuringTransition = OverlayClip(CircleShape))
            .guardArtworkHandoff(sharedKey)
    }
}

@Composable
fun WorkImage(
    work: Work,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    url: String = work.cover,
    sharedTransition: Boolean = false,
    rounded: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val transition = LocalWorkTransition.current
    val gestureActive = LocalNavigationGestureInProgress.current
    val visible = LocalNavigationSharedElementVisible.current
    val imageModifier = if (sharedTransition && LocalImageTransitionEnabled.current && transition != null) {
        val preview = rememberUpdatedState(gestureActive)
        val config = remember {
            object : SharedTransitionScope.SharedContentConfig {
                override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                    get() = !preview.value
                override val shouldKeepEnabledForOngoingAnimation: Boolean = false
            }
        }
        val navigationScope = LocalNavAnimatedContentScope.current
        val sharedKey = "work-image:${work.type}:${work.id}"
        val boundsAnimation = artworkHandoffMotion(sharedKey)
        val shape = MaterialTheme.shapes.small
        val handoff = LocalArtworkPreviewHandoff.current
        val returning = LocalNavigationGestureActive.current && !gestureActive
        val cornerMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val rounding by navigationScope.transition.animateFloat(
            transitionSpec = { cornerMotion }, label = "artwork corners",
        ) { visibility ->
            if ((visibility == EnterExitState.Visible) == rounded) 1f else 0f
        }
        val animatedShape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val progress = rounding.coerceIn(0f, 1f)
                val start = if (returning) handoff?.bounds?.get(sharedKey) else null
                val target = handoff?.targetBounds?.get(sharedKey)
                val current = handoff?.animatedBounds?.get(sharedKey) ?: start
                val previewCorners = if (returning) handoff?.previewCorners?.get(sharedKey) else null
                if (start != null && current != null && previewCorners != null) {
                    val destination = target ?: start
                    val initialEdges = listOf(start.left, start.top, start.right, start.bottom)
                    val targetEdges = listOf(destination.left, destination.top, destination.right, destination.bottom)
                    val currentEdges = listOf(current.left, current.top, current.right, current.bottom)
                    val distance = initialEdges.indices.sumOf { index ->
                        val delta = targetEdges[index] - initialEdges[index]
                        (delta * delta).toDouble()
                    }
                    val traveled = initialEdges.indices.sumOf { index ->
                        ((currentEdges[index] - initialEdges[index]) *
                            (targetEdges[index] - initialEdges[index])).toDouble()
                    }
                    val handoffProgress = when {
                        target == null -> 0f
                        distance > 0.0 -> (traveled / distance).toFloat().coerceIn(0f, 1f)
                        else -> 1f
                    }
                    fun targetCorner(corner: CornerSize) = if (rounded) corner.toPx(size, density) else 0f
                    return shape.copy(
                        topStart = CornerSize(previewCorners.topStart +
                            (targetCorner(shape.topStart) - previewCorners.topStart) * handoffProgress),
                        topEnd = CornerSize(previewCorners.topEnd +
                            (targetCorner(shape.topEnd) - previewCorners.topEnd) * handoffProgress),
                        bottomStart = CornerSize(targetCorner(shape.bottomStart) * handoffProgress),
                        bottomEnd = CornerSize(targetCorner(shape.bottomEnd) * handoffProgress),
                    ).createOutline(size, layoutDirection, density)
                }
                return shape.copy(
                    topStart = CornerSize(shape.topStart.toPx(size, density) * progress),
                    topEnd = CornerSize(shape.topEnd.toPx(size, density) * progress),
                    bottomStart = CornerSize(shape.bottomStart.toPx(size, density) * progress),
                    bottomEnd = CornerSize(shape.bottomEnd.toPx(size, density) * progress),
                ).createOutline(size, layoutDirection, density)
            }
        }
        with(transition) {
            modifier.captureArtworkPreview(sharedKey, pageTopCorners = !rounded).sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(sharedKey, config),
                visible = visible,
                boundsTransform = { _, target -> boundsAnimation(target) },
                renderInOverlayDuringTransition = !gestureActive,
                clipInOverlayDuringTransition = OverlayClip(animatedShape),
            ).guardArtworkHandoff(sharedKey).clip(animatedShape)
        }
    } else modifier
    Box(imageModifier) {
        AsyncImage(
            url,
            work.title,
            Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = scale,
        )
        overlay()
    }
}
