package io.github.pixivnext.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

internal val LocalNavigationGestureActive = staticCompositionLocalOf { false }
internal val LocalNavigationGestureInProgress = staticCompositionLocalOf { false }

@Composable
internal fun NavigationPageDisplay(
    backStack: List<NavKey>,
    modifier: Modifier,
    sharedTransitionScope: SharedTransitionScope,
    motion: NavigationMotion,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<NavKey>>,
    onBack: () -> Unit,
    sceneStrategies: List<SceneStrategy<NavKey>>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val sceneState = rememberSceneState(
        entries = entries,
        sceneStrategies = sceneStrategies,
        sceneDecoratorStrategies = sceneDecoratorStrategies,
        sharedTransitionScope = sharedTransitionScope,
        onBack = onBack,
    )
    val scene = sceneState.currentScene
    val navigationEventState = rememberNavigationEventState(
        currentInfo = SceneInfo(scene),
        backInfo = sceneState.previousScenes.map { SceneInfo(it) },
    )
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = scene.previousEntries.isNotEmpty(),
        onBackCompleted = {
            repeat(entries.size - scene.previousEntries.size) { onBack() }
        },
    )
    val gestureInProgress = navigationEventState.transitionState is NavigationEventTransitionState.InProgress
    CompositionLocalProvider(LocalNavigationGestureInProgress provides gestureInProgress) {
        NavDisplay(
            sceneState = sceneState,
            navigationEventState = navigationEventState,
            modifier = modifier,
            transitionSpec = { motion.forward(this) },
            popTransitionSpec = { motion.back() },
            predictivePopTransitionSpec = { swipeEdge ->
                motion.predictiveBack(if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1)
            },
        )
    }
}

internal class NavigationPageSceneDecorator(
    private val onSettled: () -> Unit,
) : SceneDecoratorStrategy<NavKey> {
    override fun SceneDecoratorStrategyScope<NavKey>.decorateScene(scene: Scene<NavKey>): Scene<NavKey> =
        NavigationPageScene(scene, onSettled)
}

private data class NavigationPageScene(
    val scene: Scene<NavKey>,
    val onSettled: () -> Unit,
) : Scene<NavKey> by scene {
    override val key: Any = scene::class to scene.key
    override val content: @Composable () -> Unit = {
        NavigationPage(onSettled) { scene.content() }
    }
}

internal enum class NavigationMotionStyle { Slide, Zoom }

internal class NavigationMotion(
    private val position: FiniteAnimationSpec<IntOffset>,
    private val scale: FiniteAnimationSpec<Float>,
    private val effects: FiniteAnimationSpec<Float>,
    private val direction: Int,
) {
    private var predictiveDirection: Int? = null

    fun forward(
        scope: AnimatedContentTransitionScope<*>,
        style: NavigationMotionStyle = NavigationMotionStyle.Slide,
    ): ContentTransform = with(scope) {
        predictiveDirection?.let { return@with backPreview(it) }
        when (style) {
            NavigationMotionStyle.Slide ->
                slideInHorizontally(position) { direction * it } togetherWith (
                    slideOutHorizontally(position) { -direction * it / 12 } +
                        ExitTransition.KeepUntilTransitionsFinished
                )
            NavigationMotionStyle.Zoom ->
                (scaleIn(scale, initialScale = 0.92f) + fadeIn(effects)) togetherWith (
                    ExitTransition.KeepUntilTransitionsFinished
                )
        }
    }

    fun back(style: NavigationMotionStyle = NavigationMotionStyle.Slide): ContentTransform {
        predictiveDirection?.let { return backPreview(it, committed = true) }
        return when (style) {
            NavigationMotionStyle.Slide ->
                EnterTransition.None togetherWith
                    slideOutHorizontally(position) { direction * it }
            NavigationMotionStyle.Zoom ->
                EnterTransition.None togetherWith (
                    scaleOut(scale, targetScale = 0.92f) + fadeOut(effects)
                )
        }
    }

    fun predictiveBack(swipeDirection: Int): ContentTransform {
        predictiveDirection = swipeDirection
        return backPreview(swipeDirection)
    }

    private fun backPreview(swipeDirection: Int, committed: Boolean = false): ContentTransform {
        val previewExit = scaleOut(scale, targetScale = 0.90f) +
            slideOutHorizontally(position) { swipeDirection * it / 32 }
        return EnterTransition.None togetherWith
            if (committed) previewExit + fadeOut(effects) else previewExit
    }

    fun metadata(style: NavigationMotionStyle): Map<String, Any> =
        NavDisplay.transitionSpec { forward(this, style) } +
            NavDisplay.popTransitionSpec { back(style) } +
            NavDisplay.predictivePopTransitionSpec { swipeEdge ->
                predictiveBack(
                    if (swipeEdge == androidx.navigationevent.NavigationEvent.EDGE_RIGHT) -1 else 1,
                )
            }

    fun settled() {
        predictiveDirection = null
    }
}

@Composable
internal fun rememberNavigationMotion(): NavigationMotion {
    val position = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    return remember(position, scale, effects, direction) { NavigationMotion(position, scale, effects, direction) }
}

@Composable
internal fun NavigationPage(onSettled: () -> Unit, content: @Composable () -> Unit) {
    val navigation = LocalNavAnimatedContentScope.current.transition
    val transitions = generateSequence<Transition<*>>(navigation) {
        it.parentTransition
    }.toList()
    val seeking = LocalNavigationGestureInProgress.current
    var completingGesture by remember { mutableStateOf(false) }
    SideEffect {
        if (seeking) completingGesture = true
        else if (transitions.none { it.isRunning || it.currentState != it.targetState }) {
            completingGesture = false
            onSettled()
        }
    }
    val gestureActive = seeking || completingGesture
    val shapeMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val corners by navigation.animateFloat(
        transitionSpec = { shapeMotion }, label = "navigation page corners",
    ) { if (it == EnterExitState.Visible) 0f else 1f }
    val pageShape = MaterialTheme.shapes.extraLarge
    val density = LocalDensity.current
    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val revealingPage = navigation.currentState == EnterExitState.PreEnter ||
                navigation.targetState == EnterExitState.PreEnter
            val rounding = when {
                gestureActive && revealingPage -> 0f
                gestureActive -> 1f
                else -> corners.coerceIn(0f, 1f)
            }
            shape = pageShape.copy(
                topStart = CornerSize(pageShape.topStart.toPx(size, density) * rounding),
                topEnd = CornerSize(pageShape.topEnd.toPx(size, density) * rounding),
                bottomStart = CornerSize(pageShape.bottomStart.toPx(size, density) * rounding),
                bottomEnd = CornerSize(pageShape.bottomEnd.toPx(size, density) * rounding),
            )
            clip = true
        },
        color = MaterialTheme.colorScheme.background,
    ) {
        CompositionLocalProvider(LocalNavigationGestureActive provides gestureActive, content = content)
    }
}
