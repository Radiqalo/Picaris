package io.github.pixivnext.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.pixivnext.core.Work
import kotlin.math.*

val LocalWorkTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalImageTransitionEnabled = staticCompositionLocalOf { true }

@Composable
fun Modifier.workTransitionControls(): Modifier {
    val navigation = LocalNavAnimatedContentScope.current
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val opacity by navigation.transition.animateFloat(
        transitionSpec = { effects }, label = "artwork controls opacity",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val transition = LocalWorkTransition.current
    val overlay = if (transition != null) with(transition) {
        this@workTransitionControls.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2f)
    } else this
    return overlay.graphicsLayer { alpha = opacity.coerceIn(0f, 1f) }
}

@Composable
fun Modifier.aboveWorkTransition(): Modifier {
    val transition = LocalWorkTransition.current ?: return this
    val navigation = LocalNavAnimatedContentScope.current
    return with(transition) {
        this@aboveWorkTransition.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = 1f,
            renderInOverlay = {
                isTransitionActive && navigation.transition.targetState == EnterExitState.Visible
            },
        )
    }
}

@Composable
fun Modifier.authorTransition(id: Long, part: String, enabled: Boolean = true): Modifier {
    val transition = LocalWorkTransition.current
    if (!enabled || transition == null || id == 0L) return this
    val navigation = LocalNavAnimatedContentScope.current
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
    return with(transition) {
        val key = rememberSharedContentState("author:$id:$part")
        if (part == "avatar") this@authorTransition.sharedElement(key, navigation,
            boundsTransform = { _, _ -> motion },
            clipInOverlayDuringTransition = OverlayClip(CircleShape))
        else this@authorTransition.sharedBounds(key, navigation,
            boundsTransform = { _, _ -> motion })
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
    val imageModifier = if (sharedTransition && LocalImageTransitionEnabled.current && transition != null) {
        val navigationScope = LocalNavAnimatedContentScope.current
        val boundsAnimation = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
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
        if (work.demo >= 0) DemoArt(work.demo, Modifier.matchParentSize())
        else AsyncImage(
            url,
            work.title,
            Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = scale,
        )
        overlay()
    }
}

@Composable
fun DemoArt(index: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun polygon(points: List<Pair<Float, Float>>, color: Color) {
            val p =
                Path().apply {
                    points.forEachIndexed { i, (x, y) ->
                        if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h)
                    }
                    close()
                }
            drawPath(p, color)
        }
        val palettes =
            listOf(
                listOf(0xFFBEE5E8, 0xFF7FBEC9, 0xFF327B99),
                listOf(0xFFE5C5D6, 0xFFB886AE, 0xFF68518C),
                listOf(0xFFF1C3A6, 0xFFC58389, 0xFF625677),
                listOf(0xFFCED9F2, 0xFF9EA8D9, 0xFF575D98),
                listOf(0xFFB4D8DF, 0xFF669BAC, 0xFF2D596F),
                listOf(0xFFCADCCE, 0xFF7FAE96, 0xFF366C61),
            )
        val c = palettes[index % 6].map { Color(it) }
        drawRect(Brush.verticalGradient(listOf(c[0], c[1]), 0f, h))
        drawCircle(Color(0xFFFFE8C5), w * .13f, Offset(w * .73f, h * .21f))
        if (index % 6 == 1) {
            drawRect(c[2], topLeft = Offset(0f, h * .72f), size = Size(w, h * .3f))
            repeat(7) { i ->
                val x = w * (.12f + i * .13f)
                val y = h * (.38f + (i % 3) * .13f)
                drawLine(Color(0xFF3D5D58), Offset(x, h), Offset(x, y), w * .012f)
                repeat(5) { petal ->
                    val angle = petal * 2 * PI / 5
                    drawCircle(
                        Color(if (i % 2 == 0) 0xFFF5DCE8 else 0xFFE7CEF7),
                        w * .065f,
                        Offset(
                            x + cos(angle).toFloat() * w * .05f,
                            y + sin(angle).toFloat() * w * .05f,
                        ),
                    )
                }
                drawCircle(Color(0xFFF9DBA4), w * .035f, Offset(x, y))
            }
        } else if (index % 6 == 2) {
            repeat(8) { i ->
                val bh = h * (.12f + (i * 13 % 7) * .055f)
                val x = i * w * .15f - w * .04f
                drawRect(
                    c[2].copy(alpha = .6f + i * .04f),
                    Offset(x, h * .8f - bh),
                    Size(w * .14f, bh + h * .2f),
                )
                repeat(3) { col ->
                    repeat(4) { row ->
                        drawRect(
                            Color(0xFFFFDFB7).copy(alpha = .8f),
                            Offset(
                                x + w * .025f + col * w * .036f,
                                h * .8f - bh + h * .025f + row * h * .05f,
                            ),
                            Size(w * .015f, h * .018f),
                        )
                    }
                }
            }
            polygon(listOf(0f to .92f, 1f to .85f, 1f to 1f, 0f to 1f), Color(0xFF34394D))
        } else {
            polygon(
                listOf(
                    0f to .68f,
                    .25f to .48f,
                    .52f to .65f,
                    .82f to .42f,
                    1f to .58f,
                    1f to 1f,
                    0f to 1f,
                ),
                c[1],
            )
            polygon(
                listOf(0f to .81f, .32f to .67f, .58f to .78f, 1f to .59f, 1f to 1f, 0f to 1f),
                c[2].copy(alpha = .6f),
            )
            polygon(
                listOf(0f to .9f, .35f to .77f, .7f to .93f, 1f to .8f, 1f to 1f, 0f to 1f),
                c[2],
            )
            if (index % 6 == 0 || index % 6 == 4) {
                val water =
                    Path().apply {
                        moveTo(0f, h * .7f)
                        cubicTo(w * .3f, h * .61f, w * .55f, h * .84f, w, h * .71f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                drawPath(
                    water,
                    Brush.verticalGradient(
                        listOf(Color(0xFF90D9D9), Color(0xFF1B7894)),
                        h * .65f,
                        h,
                    ),
                )
                repeat(7) { i ->
                    drawLine(
                        Color.White.copy(alpha = .3f),
                        Offset(w * (.1f + (i % 3) * .1f), h * (.77f + i * .031f)),
                        Offset(w * (.48f + (i % 4) * .11f), h * (.77f + i * .031f)),
                        w * .004f,
                    )
                }
                polygon(
                    listOf(.58f to .73f, .83f to .73f, .78f to .76f, .63f to .76f),
                    Color(0xFF354A65),
                )
                drawLine(
                    Color(0xFF354A65),
                    Offset(w * .72f, h * .73f),
                    Offset(w * .72f, h * .51f),
                    w * .008f,
                )
                polygon(listOf(.71f to .52f, .71f to .71f, .58f to .71f), Color(0xFFFFF1D8))
            }
            if (index % 6 == 5)
                repeat(9) { i ->
                    val x = (i * .17f - .13f) * w
                    val y = (.55f + (i % 3) * .1f) * h
                    drawLine(Color(0xFF264E4B), Offset(x, h), Offset(x, y), w * .035f)
                    drawOval(
                        Color(0xFF417564),
                        Offset(x - w * .13f, y - h * .17f),
                        Size(w * .27f, h * .37f),
                    )
                }
        }
        repeat(4) { i ->
            drawOval(
                Color.White.copy(alpha = .18f),
                Offset(w * (i * .32f - .15f), h * (.07f + (i % 2) * .14f)),
                Size(w * .47f, h * .06f),
            )
        }
        // Subtle paper-like stippling, deterministic and entirely local.
        repeat(300) { i ->
            drawCircle(
                Color.White.copy(alpha = .07f),
                (w * .002f).coerceAtLeast(.5f),
                Offset(((i * 137) % 997) / 997f * w, ((i * 277) % 991) / 991f * h),
            )
        }
    }
}
