package io.github.pixivnext.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun BottomNavigationPages(
    tab: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    // Both directions share the theme's fast spatial tier so tab motion keeps the same
    // rhythm as every other fast spatial animation on screen.
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val layoutDirection = LocalLayoutDirection.current
    val coordinator = LocalNavigationCoordinator.current
    var previousTab by remember { mutableIntStateOf(tab) }
    val direction = remember(tab, layoutDirection) {
        tab.compareTo(previousTab) * if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    }
    SideEffect { previousTab = tab }
    AnimatedContent(
        targetState = tab,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            (fadeIn(effects) togetherWith fadeOut(effects)).using(null)
        },
        label = "bottom navigation page",
    ) { currentTab ->
        val entering = currentTab == tab
        val position = remember { Animatable(if (entering) direction / 4f else 0f) }
        val velocity = remember { floatArrayOf(0f) }
        val animationKey = remember { Any() }
        LaunchedEffect(tab, currentTab) {
            val id = coordinator?.transitionId ?: 0L
            coordinator?.animationStarted(animationKey, id)
            try {
                if (entering) {
                    position.animateTo(0f, spatial, velocity[0]) { velocity[0] = this.velocity }
                } else {
                    position.animateTo(-direction / 6f, spatial, velocity[0]) { velocity[0] = this.velocity }
                }
            } finally {
                coordinator?.animationFinished(animationKey, id)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * position.value * 0.6f
            }.background(MaterialTheme.colorScheme.background),
        ) {
            CompositionLocalProvider(LocalNavigationTab provides currentTab) {
                val permitted = navigationPermission()
                NavigationVisualContent(entering, permitted, entering) {
                    content(currentTab)
                }
            }
        }
    }
}
