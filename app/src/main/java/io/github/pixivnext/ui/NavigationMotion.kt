package io.github.pixivnext.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
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
import androidx.navigation3.ui.LocalNavAnimatedContentScope

internal val LocalNavigationGestureActive = staticCompositionLocalOf { false }

internal class NavigationMotion(
    private val position: FiniteAnimationSpec<IntOffset>,
    private val scale: FiniteAnimationSpec<Float>,
    private val direction: Int,
) {
    private var predictiveDirection: Int? = null

    fun forward(scope: AnimatedContentTransitionScope<*>): ContentTransform = with(scope) {
        if (predictiveDirection != null) return@with back()
        (
            slideInHorizontally(position) { direction * it } +
                scaleIn(scale, initialScale = 0.96f)
        ) togetherWith (
            slideOutHorizontally(position) { -direction * it / 12 } +
                scaleOut(scale, targetScale = 0.96f) +
                ExitTransition.KeepUntilTransitionsFinished
        )
    }

    fun back(): ContentTransform {
        val swipeDirection = predictiveDirection ?: direction
        return (
            slideInHorizontally(position) { -swipeDirection * it / 12 } +
                scaleIn(scale, initialScale = 0.96f)
        ) togetherWith (
            slideOutHorizontally(position) { swipeDirection * it } +
                scaleOut(scale, targetScale = 0.96f)
        )
    }

    fun predictiveBack(swipeDirection: Int): ContentTransform {
        predictiveDirection = swipeDirection
        return back()
    }

    fun settled() {
        predictiveDirection = null
    }
}

@Composable
internal fun rememberNavigationMotion(): NavigationMotion {
    val position = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    return remember(position, scale, direction) { NavigationMotion(position, scale, direction) }
}

@Composable
internal fun NavigationPage(onSettled: () -> Unit, content: @Composable () -> Unit) {
    val navigation = LocalNavAnimatedContentScope.current.transition
    val transitions = generateSequence<Transition<*>>(navigation) {
        it.parentTransition
    }.toList()
    val seeking = transitions.any { it.isSeeking }
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
            val rounding = corners.coerceIn(0f, 1f)
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
