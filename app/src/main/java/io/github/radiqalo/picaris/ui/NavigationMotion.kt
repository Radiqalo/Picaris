package io.github.radiqalo.picaris.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest

internal val LocalNavigationGestureInProgress = staticCompositionLocalOf { false }
internal val LocalNavigationGestureVerticalOffset = staticCompositionLocalOf { 0f }
private val LocalNavigationCurrentSceneKey = staticCompositionLocalOf<Any?> { null }
internal val LocalNavigationSharedElementVisible = staticCompositionLocalOf { true }

@Composable
internal fun NavigationPageDisplay(
    modifier: Modifier,
    sharedTransitionScope: SharedTransitionScope,
    motion: NavigationMotion,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<NavKey>>,
    onBack: (Int) -> Unit,
    sceneStrategies: List<SceneStrategy<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val coordinator = checkNotNull(LocalNavigationCoordinator.current)
    val artwork = checkNotNull(LocalNavigationArtwork.current)
    val entries = rememberDecoratedNavEntries<NavKey>(coordinator.instances, entryDecorators) { key ->
        val instance = key as NavigationInstance
        val entry = entryProvider(instance.destination)
        NavEntry(key = key, contentKey = instance.id, metadata = entry.metadata) {
            CompositionLocalProvider(LocalNavigationInstance provides instance.id) {
                Box(Modifier.fillMaxSize().navigationInteractionGate { coordinator.permits(instance.id) }) {
                    entry.Content()
                }
            }
        }
    }
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = sceneStrategies,
        sceneDecoratorStrategies = sceneDecoratorStrategies,
        sharedTransitionScope = sharedTransitionScope,
        onBack = { onBack(1) },
    )
    val scene = sceneState.currentScene
    val navigationEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(scene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) },
    )
    var previousGestureInProgress by remember { mutableStateOf(false) }
    var previewEntries by remember { mutableStateOf<List<Long>?>(null) }
    var gestureStartTouchY by remember { mutableFloatStateOf(Float.NaN) }
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = scene.previousEntries.isNotEmpty() &&
            (previewEntries == null || previewEntries == coordinator.instances.map { it.id }),
        onBackCompleted = {
            if (previewEntries == null || previewEntries == coordinator.instances.map { it.id })
                onBack(entries.size - scene.previousEntries.size)
        },
    )
    val backEvent =
        (navigationEventState.transitionState as? NavigationEventTransitionState.InProgress)
            ?.latestEvent
    val gestureInProgress = backEvent != null
    val maximumGestureOffset =
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height * 0.025f
    val gestureVerticalOffset =
        if (gestureInProgress && gestureStartTouchY.isFinite()) {
            ((backEvent!!.touchY - gestureStartTouchY) * 0.025f)
                .coerceIn(-maximumGestureOffset, maximumGestureOffset)
        } else {
            0f
        }
    SideEffect {
        if (gestureInProgress && !previousGestureInProgress) {
            coordinator.beginPreview()
            previewEntries = coordinator.instances.map { it.id }
            gestureStartTouchY = backEvent!!.touchY
        } else if (!gestureInProgress && previousGestureInProgress) {
            coordinator.cancelPreview()
            if (coordinator.phase == NavigationTransitionPhase.Restoring) artwork.resumePreview()
            previewEntries = null
            gestureStartTouchY = Float.NaN
        }
        coordinator.updateSceneOwners(scene.entries.map { it.contentKey as Long }.toSet())
        artwork.refreshTargets()
        previousGestureInProgress = gestureInProgress
    }
    CompositionLocalProvider(
        LocalNavigationGestureInProgress provides gestureInProgress,
        LocalNavigationGestureVerticalOffset provides gestureVerticalOffset,
        LocalNavigationCurrentSceneKey provides scene.key,
    ) {
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = navigationEventState,
            modifier = modifier.pointerInput(coordinator, artwork) {
                var activePointer: PointerId? = null
                var pointerDownPosition: Offset? = null
                val touchSlop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                        if (down != null) {
                            activePointer = down.id
                            pointerDownPosition = down.position
                        }
                        val pointer = activePointer?.let { id ->
                            event.changes.firstOrNull { it.id == id }
                        }
                        val origin = pointerDownPosition
                        if (pointer != null && origin != null && pointer.pressed &&
                            kotlin.math.abs(pointer.position.y - origin.y) > touchSlop &&
                            kotlin.math.abs(pointer.position.y - origin.y) >
                            kotlin.math.abs(pointer.position.x - origin.x) &&
                            coordinator.phase == NavigationTransitionPhase.Returning && artwork.isActive
                        ) {
                            artwork.finishForInteraction()
                            activePointer = null
                            pointerDownPosition = null
                        } else if (pointer != null && !pointer.pressed) {
                            activePointer = null
                            pointerDownPosition = null
                        }
                    }
                }
            }.navigationInteractionGate {
                coordinator.permits(null)
            }.onGloballyPositioned { artwork.root = it },
            transitionSpec = { motion.forward(this) },
            popTransitionSpec = { motion.back(this) },
            predictivePopTransitionSpec = { swipeEdge ->
                motion.predictiveBack(this, if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1)
            },
        )
    }
}

internal class NavigationPageSceneDecorator(
    private val onSettled: (Long) -> Unit,
) : SceneDecoratorStrategy<NavKey> {
    override fun SceneDecoratorStrategyScope<NavKey>.decorateScene(scene: Scene<NavKey>): Scene<NavKey> =
        NavigationPageScene(scene, onSettled)
}

// Scene strategies also resolve previous backstack prefixes. Keep this strategy
// installed while another page covers Detail so its return scene keeps its identity.
internal class DetailSceneStrategy(
    private val delegate: SceneStrategy<NavKey>,
    private val coordinator: NavigationTransitionCoordinator,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val id = entries.lastOrNull()?.contentKey as? Long ?: return null
        if (coordinator.destination(id) !is Detail) return null
        return with(delegate) { calculateScene(entries) }
    }
}

private data class NavigationPageScene(
    val scene: Scene<NavKey>,
    val onSettled: (Long) -> Unit,
) : Scene<NavKey> by scene {
    override val key: Any = scene::class to scene.key
    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalNavigationSharedElementVisible provides (key == LocalNavigationCurrentSceneKey.current),
        ) {
            NavigationPage(key, scene.entries.map { it.contentKey as Long }.toSet(), onSettled) { scene.content() }
        }
    }
}

internal enum class NavigationMotionStyle { Slide, Zoom }

internal class NavigationMotion(
    private val position: FiniteAnimationSpec<IntOffset>,
    private val scale: FiniteAnimationSpec<Float>,
    private val effects: FiniteAnimationSpec<Float>,
    private val direction: Int,
    private val coordinator: NavigationTransitionCoordinator,
) {
    private var predictiveDirection: Int? = null
    private var predictiveScenes: Pair<Any, Any>? = null
    private var previewId: Long? = null

    private fun matchesPreview(scope: AnimatedContentTransitionScope<*>): Boolean =
        coordinator.phase != NavigationTransitionPhase.Entering &&
            coordinator.phase != NavigationTransitionPhase.Stable &&
            predictiveScenes == ((scope.initialState as Scene<*>).key to (scope.targetState as Scene<*>).key) &&
            (previewId == coordinator.previewTransitionId ||
                coordinator.returningTransitionId == coordinator.transitionId)

    private fun destination(scene: Any?): NavKey? =
        ((scene as? Scene<*>)?.entries?.lastOrNull()?.contentKey as? Long)?.let(coordinator::destination)

    private fun style(scene: Any?): NavigationMotionStyle = when (destination(scene)) {
        is Detail, is Reader, is Author -> NavigationMotionStyle.Zoom
        else -> NavigationMotionStyle.Slide
    }

    private fun returnsToExistingPage(scope: AnimatedContentTransitionScope<*>): Boolean {
        val initialId = (scope.initialState as? Scene<*>)?.entries?.lastOrNull()?.contentKey
        val targetId = (scope.targetState as? Scene<*>)?.entries?.lastOrNull()?.contentKey
        val initialIndex = coordinator.instances.indexOfFirst { it.id == initialId }
        val targetIndex = coordinator.instances.indexOfFirst { it.id == targetId }
        return initialIndex >= 0 && targetIndex >= 0 && targetIndex < initialIndex
    }

    fun forward(scope: AnimatedContentTransitionScope<*>): ContentTransform = with(scope) {
        // A deferred back target is composed before beginPreview's SideEffect.
        // Resolve direction from stable entry IDs instead of caching a forward
        // scale-in while the coordinator still reports Stable/Entering.
        if (returnsToExistingPage(scope) || coordinator.phase == NavigationTransitionPhase.Returning)
            return@with back(scope)
        val frame = coordinator.incomingFrame
        if (matchesPreview(scope)) predictiveDirection?.let { return@with backPreview(it) }
        when (style(targetState)) {
            NavigationMotionStyle.Slide -> {
                val slideFrame = frame?.takeIf { kotlin.math.abs(it.scale - 1f) < 0.001f }
                val slide = slideInHorizontally(handoffSpec(position,
                    IntOffset(slideFrame?.offsetVelocity?.toInt() ?: 0, 0), IntOffset.Zero,
                )) { slideFrame?.offset ?: (direction * it) }
                slide togetherWith ExitTransition.KeepUntilTransitionsFinished
            }
            NavigationMotionStyle.Zoom ->
                (scaleIn(handoffSpec(scale, frame?.scaleVelocity ?: 0f, 0f),
                    initialScale = frame?.scale?.coerceIn(0.01f, 1.5f) ?: 0.92f) +
                    slideInHorizontally(handoffSpec(position,
                        IntOffset(frame?.offsetVelocity?.toInt() ?: 0, 0), IntOffset.Zero,
                    )) { frame?.offset ?: 0 } +
                    fadeIn(handoffSpec(effects, frame?.opacityVelocity ?: 0f, 0f),
                        initialAlpha = frame?.opacity?.coerceIn(0f, 1f) ?: 0f)) togetherWith (
                    ExitTransition.KeepUntilTransitionsFinished
                )
        }
    }

    fun back(scope: AnimatedContentTransitionScope<*>): ContentTransform {
        if (coordinator.phase == NavigationTransitionPhase.Entering &&
            !returnsToExistingPage(scope) && destination(scope.targetState) != Home)
            return forward(scope)
        if (matchesPreview(scope)) predictiveDirection?.let { return backPreview(it, committed = true) }
        return when (style(scope.initialState)) {
            NavigationMotionStyle.Slide ->
                stationaryBackTarget() togetherWith
                    slideOutHorizontally(position) { direction * it }
            NavigationMotionStyle.Zoom ->
                stationaryBackTarget() togetherWith (
                    scaleOut(scale, targetScale = 0.92f) + fadeOut(effects)
                )
        }
    }

    fun predictiveBack(scope: AnimatedContentTransitionScope<*>, swipeDirection: Int): ContentTransform {
        predictiveDirection = swipeDirection
        previewId = coordinator.previewTransitionId
        predictiveScenes = (scope.initialState as Scene<*>).key to (scope.targetState as Scene<*>).key
        return backPreview(swipeDirection)
    }

    // DeferredAnimatedContent falls back to the target's original enter animation for
    // properties not manually set during preview. Explicit identity transforms prevent
    // a returning page from replaying its original slide/scale-in underneath the gesture.
    private fun stationaryBackTarget() =
        fadeIn(effects, initialAlpha = 1f) +
            scaleIn(scale, initialScale = 1f) +
            slideInHorizontally(position) { 0 }

    private fun backPreview(swipeDirection: Int, committed: Boolean = false): ContentTransform {
        val previewExit = scaleOut(scale, targetScale = 0.90f) +
            slideOutHorizontally(position) { swipeDirection * it / 32 }
        return stationaryBackTarget() togetherWith
            if (committed) previewExit + fadeOut(effects) else previewExit
    }

    fun metadata(): Map<String, Any> =
        NavDisplay.transitionSpec { forward(this) } +
            NavDisplay.popTransitionSpec { back(this) } +
            NavDisplay.predictivePopTransitionSpec { swipeEdge ->
                predictiveBack(
                    this,
                    if (swipeEdge == androidx.navigationevent.NavigationEvent.EDGE_RIGHT) -1 else 1,
                )
            }

    fun settled(id: Long) {
        if (id != coordinator.transitionId) return
        coordinator.settled(id)
        predictiveDirection = null
        predictiveScenes = null
        previewId = null
    }
}

@Composable
internal fun rememberNavigationMotion(): NavigationMotion {
    val position = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val coordinator = checkNotNull(LocalNavigationCoordinator.current)
    return remember(position, scale, effects, direction, coordinator) {
        NavigationMotion(position, scale, effects, direction, coordinator)
    }
}

@Composable
internal fun NavigationPage(
    sceneKey: Any,
    entryIds: Set<Long>,
    onSettled: (Long) -> Unit,
    content: @Composable () -> Unit,
) {
    val navigation = LocalNavAnimatedContentScope.current.transition
    val transitions = generateSequence<Transition<*>>(navigation) {
        it.parentTransition
    }.toList()
    val seeking = LocalNavigationGestureInProgress.current
    val gestureVerticalOffset = LocalNavigationGestureVerticalOffset.current
    val coordinator = checkNotNull(LocalNavigationCoordinator.current)
    val artwork = checkNotNull(LocalNavigationArtwork.current)
    val visible = LocalNavigationSharedElementVisible.current
    val entryFrame = remember(sceneKey) { if (visible) coordinator.incomingFrame else null }
    val id = coordinator.transitionId
    val shapeMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectMotion = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val corners by navigation.animateFloat(
        transitionSpec = { handoffSpec(shapeMotion, entryFrame?.roundingVelocity ?: 0f, 0f) },
        label = "navigation page corners",
    ) { if (it == EnterExitState.Visible) 0f else entryFrame?.rounding ?: 1f }
    val opacity by navigation.animateFloat(
        transitionSpec = { effectMotion },
        label = "navigation scene sampled opacity",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val pageShape = MaterialTheme.shapes.extraLarge
    val density = LocalDensity.current
    val roundingTarget = when {
        seeking -> if (visible) 1f else 0f
        coordinator.returningTransitionId == id -> if (visible) 0f else 1f
        else -> corners.coerceIn(0f, 1f)
    }
    val rounding by animateFloatAsState(
        targetValue = roundingTarget,
        animationSpec = shapeMotion,
        label = "navigation page rounding handoff",
    )
    val renderedRounding = rounding.coerceIn(0f, 1f)
    SideEffect {
        if (visible && !seeking && !artwork.isActive && !coordinator.hasAnimations &&
            kotlin.math.abs(rounding - roundingTarget) < 0.001f &&
            transitions.none { it.isRunning || it.currentState != it.targetState }
        ) {
            artwork.cancelPending()
            onSettled(id)
        }
    }
    val pageCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val pageRadius = remember { floatArrayOf(0f) }
    val sampledRounding = rememberUpdatedState(renderedRounding)
    val previewing = rememberUpdatedState(seeking)
    val sampledVisibility = rememberUpdatedState(
        if (seeking || (!coordinator.usesZoom(entryIds) && coordinator.returningTransitionId != id))
            1f else opacity,
    )
    DisposableEffect(sceneKey, coordinator, artwork) {
        coordinator.registerScene(sceneKey, entryIds, moving = {
            previewing.value || transitions.any { it.isRunning || it.currentState != it.targetState }
        }) {
            val root = artwork.root?.takeIf { it.isAttached }
            val page = pageCoordinates[0]?.takeIf { it.isAttached }
            if (root == null || page == null || root.size.width == 0) null else {
                val bounds = root.localBoundingBoxOf(page, clipBounds = false)
                val scale = bounds.width / root.size.width
                NavigationSceneFrame(
                    scale, (bounds.left - (1f - scale) * root.size.width / 2f).toInt(),
                    sampledVisibility.value, sampledRounding.value,
                )
            }
        }
        onDispose { coordinator.unregisterScene(sceneKey) }
    }
    LaunchedEffect(sceneKey, coordinator) {
        snapshotFlow { previewing.value || transitions.any { it.isRunning || it.currentState != it.targetState } }
            .collectLatest { running ->
                if (running) {
                    while (previewing.value || transitions.any { it.isRunning || it.currentState != it.targetState }) {
                        withFrameNanos {
                            coordinator.sampleScene(sceneKey, it)
                            if (visible) artwork.invalidatePreview()
                        }
                    }
                } else coordinator.sampleScene(sceneKey, System.nanoTime())
            }
    }
    Box(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (seeking && visible) translationY = gestureVerticalOffset
            pageRadius[0] = pageShape.topStart.toPx(size, density) * renderedRounding
            shape = pageShape.copy(
                topStart = CornerSize(pageShape.topStart.toPx(size, density) * renderedRounding),
                topEnd = CornerSize(pageShape.topEnd.toPx(size, density) * renderedRounding),
                bottomStart = CornerSize(pageShape.bottomStart.toPx(size, density) * renderedRounding),
                bottomEnd = CornerSize(pageShape.bottomEnd.toPx(size, density) * renderedRounding),
            )
            clip = true
        }.onGloballyPositioned { pageCoordinates[0] = it }.background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(
            LocalNavigationPageCoordinates provides { pageCoordinates[0] },
            LocalNavigationPageRadius provides { pageRadius[0] },
        ) {
            NavigationSceneContent(content)
        }
    }
}

private fun <Value> handoffSpec(
    motion: FiniteAnimationSpec<Value>,
    velocity: Value,
    zero: Value,
): FiniteAnimationSpec<Value> = if (velocity == zero) motion else VelocityHandoffSpec(motion, velocity, zero)

private data class VelocityHandoffSpec<Value>(
    val motion: FiniteAnimationSpec<Value>,
    val velocity: Value,
    val zero: Value,
) : FiniteAnimationSpec<Value> {
    override fun <Vector : AnimationVector> vectorize(converter: TwoWayConverter<Value, Vector>):
        VectorizedFiniteAnimationSpec<Vector> {
        val animation = motion.vectorize(converter)
        val handoffVelocity = converter.convertToVector(velocity)
        return object : VectorizedFiniteAnimationSpec<Vector> by animation {
            private fun resolved(initialVelocity: Vector) =
                if (converter.convertFromVector(initialVelocity) == zero) handoffVelocity else initialVelocity
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: Vector, targetValue: Vector,
                initialVelocity: Vector): Vector =
                animation.getValueFromNanos(playTimeNanos, initialValue, targetValue, resolved(initialVelocity))
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Vector, targetValue: Vector,
                initialVelocity: Vector): Vector =
                animation.getVelocityFromNanos(playTimeNanos, initialValue, targetValue, resolved(initialVelocity))
            override fun getDurationNanos(initialValue: Vector, targetValue: Vector, initialVelocity: Vector): Long =
                animation.getDurationNanos(initialValue, targetValue, resolved(initialVelocity))
            override fun getEndVelocity(initialValue: Vector, targetValue: Vector, initialVelocity: Vector): Vector =
                animation.getEndVelocity(initialValue, targetValue, resolved(initialVelocity))
        }
    }
}
