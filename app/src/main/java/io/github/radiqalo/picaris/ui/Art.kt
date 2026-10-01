package io.github.radiqalo.picaris.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.core.Work

val LocalWorkTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalImageTransitionEnabled = staticCompositionLocalOf { true }
internal class ArtworkReturnFeedback(val type: String, val id: Long)
internal val LocalArtworkReturnFeedback = staticCompositionLocalOf<ArtworkReturnFeedback?> { null }

@Composable
internal fun NavigationSceneContent(content: @Composable () -> Unit) {
    val visible = LocalNavigationSharedElementVisible.current
    val preview = LocalNavigationGestureInProgress.current
    val live = visible || preview
    val permitted = navigationPermission()
    NavigationVisualContent(live, { visible && permitted() }, visible && !preview, content)
}

@Composable
internal fun NavigationVisualContent(
    live: Boolean,
    permitted: () -> Boolean,
    accessible: Boolean,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val snapshot = rememberGraphicsLayer()
    val stateHolder = rememberSaveableStateHolder()
    val artwork = LocalNavigationArtwork.current
    val recorded = remember { booleanArrayOf(false) }
    val input = if (live) Modifier.navigationInteractionGate(permitted) else Modifier
    val semantics = if (accessible) Modifier else Modifier.clearAndSetSemantics { }
    Layout(
        content = {
            if (live) stateHolder.SaveableStateProvider("page") {
                Box(Modifier.fillMaxSize()) { content() }
            }
        },
        modifier = Modifier.fillMaxSize().then(input).then(semantics).drawWithContent {
            if (artwork?.recordingSnapshot == true) {
                if (live) snapshot.record { this@drawWithContent.drawContent() }
                drawLayer(snapshot)
                return@drawWithContent
            }
            if (live) {
                layer.record { this@drawWithContent.drawContent() }
                if (artwork?.isActive == true) {
                    artwork.recordingSnapshot = true
                    try {
                        snapshot.record { this@drawWithContent.drawContent() }
                    } finally {
                        artwork.recordingSnapshot = false
                    }
                } else {
                    snapshot.record { drawLayer(layer) }
                }
                recorded[0] = true
            }
            if (recorded[0]) drawLayer(if (live) layer else snapshot)
        },
    ) { measurables, constraints ->
        val child = measurables.firstOrNull()?.measure(constraints)
        layout(child?.width ?: constraints.minWidth, child?.height ?: constraints.minHeight) {
            child?.place(0, 0)
        }
    }
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
    val artwork = LocalNavigationArtwork.current
    val overlay = if (transition != null) with(transition) {
        this@workTransitionControls.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = 2f,
            renderInOverlay = { artwork?.isActive == true && artwork.recordingSnapshot != true && !gestureActive },
        )
    } else this
    return overlay.graphicsLayer { alpha = if (gestureActive) 1f else opacity.coerceIn(0f, 1f) }
}

@Composable
fun Modifier.aboveWorkTransition(zIndexInOverlay: Float = 1f, artworkKey: String? = null): Modifier {
    val gestureActive = LocalNavigationGestureInProgress.current
    val transition = LocalWorkTransition.current ?: return this
    val artwork = LocalNavigationArtwork.current
    val visible = LocalNavigationSharedElementVisible.current
    val owner = LocalNavigationInstance.current
    return with(transition) {
        this@aboveWorkTransition.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = zIndexInOverlay,
            renderInOverlay = {
                artwork?.isActive == true && artwork.recordingSnapshot != true && !gestureActive &&
                    if (artworkKey == null) visible else artwork.hasFlight(artworkKey, owner)
            },
        )
    }
}

@Composable
fun Modifier.authorAvatarTransition(id: Long, enabled: Boolean = true): Modifier {
    if (!enabled || id == 0L || LocalWorkTransition.current == null) return this
    val radius = 1000f
    return navigationArtwork("author:$id:avatar", Rect(radius, radius, radius, radius)).clip(CircleShape)
}

@Composable
fun Modifier.workBookmarkTransition(work: Work): Modifier {
    if (LocalWorkTransition.current == null) return this
    val density = LocalDensity.current
    val radius = MaterialTheme.shapes.extraSmall.topStart.toPx(Size.Zero, density)
    return navigationArtwork(
        "work-like:${work.type}:${work.id}",
        Rect(radius, radius, radius, radius),
    )
}

@Composable
fun WorkImage(
    work: Work,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    url: String = work.cover,
    sharedTransition: Boolean = false,
    rounded: Boolean = true,
    onImageAspectRatio: ((Float) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = MaterialTheme.shapes.small
    val density = LocalDensity.current
    val radius = shape.topStart.toPx(Size.Zero, density)
    val corners = if (rounded) Rect(radius, radius, radius, radius) else Rect.Zero
    var painter by remember(url) { mutableStateOf<Painter?>(null) }
    val capture = if (sharedTransition && LocalImageTransitionEnabled.current && LocalWorkTransition.current != null)
        Modifier.navigationArtwork("work-image:${work.type}:${work.id}", corners, painter)
    else Modifier
    val imageModifier = if (rounded) modifier.clip(shape) else modifier
    Box(imageModifier) {
        AsyncImage(
            url,
            work.title,
            Modifier.matchParentSize().then(capture).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = scale,
            onSuccess = {
                painter = it.painter
                val size = it.painter.intrinsicSize
                if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f)
                    onImageAspectRatio?.invoke(size.width / size.height)
            },
        )
        overlay()
    }
}
