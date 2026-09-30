package io.github.pixivnext.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
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
    if (!enabled || gestureActive || transition == null || id == 0L) return this
    val navigation = LocalNavAnimatedContentScope.current
    val motion = artworkBoundsMotion()
    return with(transition) {
        val key = rememberSharedContentState("author:$id:avatar")
        this@authorAvatarTransition.sharedElement(key, navigation,
            boundsTransform = { _, _ -> motion },
            clipInOverlayDuringTransition = OverlayClip(CircleShape))
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
    val imageModifier = if (sharedTransition && !gestureActive && LocalImageTransitionEnabled.current && transition != null) {
        val navigationScope = LocalNavAnimatedContentScope.current
        val boundsAnimation = artworkBoundsMotion()
        val shape = MaterialTheme.shapes.small
        val cornerMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val rounding by navigationScope.transition.animateFloat(
            transitionSpec = { cornerMotion }, label = "artwork corners",
        ) { visibility ->
            if ((visibility == EnterExitState.Visible) == rounded) 1f else 0f
        }
        val animatedShape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val progress = rounding.coerceIn(0f, 1f)
                return shape.copy(
                    topStart = CornerSize(shape.topStart.toPx(size, density) * progress),
                    topEnd = CornerSize(shape.topEnd.toPx(size, density) * progress),
                    bottomStart = CornerSize(shape.bottomStart.toPx(size, density) * progress),
                    bottomEnd = CornerSize(shape.bottomEnd.toPx(size, density) * progress),
                ).createOutline(size, layoutDirection, density)
            }
        }
        with(transition) {
            modifier.sharedElement(
                sharedContentState = rememberSharedContentState("work-image:${work.type}:${work.id}"),
                animatedVisibilityScope = navigationScope,
                boundsTransform = { _, _ -> boundsAnimation },
                clipInOverlayDuringTransition = OverlayClip(animatedShape),
            ).clip(animatedShape)
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
