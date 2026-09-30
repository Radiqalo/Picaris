package io.github.pixivnext.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    val exitSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val enterSpatial = remember(exitSpatial) {
        if (exitSpatial is SpringSpec) spring(
            dampingRatio = exitSpatial.dampingRatio * 0.9f,
            stiffness = exitSpatial.stiffness,
            visibilityThreshold = 0.0005f,
        ) else exitSpatial
    }
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val layoutDirection = LocalLayoutDirection.current
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
        LaunchedEffect(tab, currentTab) {
            if (entering) {
                position.snapTo(direction / 4f)
                position.animateTo(0f, enterSpatial)
            } else {
                position.animateTo(-direction / 6f, exitSpatial)
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationX = size.width * position.value * 0.6f
            },
            color = MaterialTheme.colorScheme.background,
        ) {
            content(currentTab)
        }
    }
}
