package io.github.pixivnext.designsystem

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Original outline glyphs; all use a consistent 24-unit viewport.
object Glyph {
    private fun make(
        name: String,
        data: List<androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit>,
    ): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                data.forEach { draw ->
                    path(
                        fill = null,
                        stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                        pathFillType = PathFillType.NonZero,
                        pathBuilder = draw,
                    )
                }
            }
            .build()

    val Discover =
        make(
            "Discover",
            listOf({
                moveTo(12f, 3f)
                lineTo(14.8f, 9.2f)
                lineTo(21f, 12f)
                lineTo(14.8f, 14.8f)
                lineTo(12f, 21f)
                lineTo(9.2f, 14.8f)
                lineTo(3f, 12f)
                lineTo(9.2f, 9.2f)
                close()
            }),
        )
    val Search =
        make(
            "Search",
            listOf({
                moveTo(10.5f, 3f)
                curveTo(20.5f, 3f, 20.5f, 18f, 10.5f, 18f)
                curveTo(.5f, 18f, .5f, 3f, 10.5f, 3f)
                close()
                moveTo(16f, 16f)
                lineTo(21f, 21f)
            }),
        )
    val Heart =
        make(
            "Heart",
            listOf({
                moveTo(12f, 20f)
                curveTo(3f, 14f, 1f, 9f, 4f, 5f)
                curveTo(7f, 2f, 10f, 4f, 12f, 6f)
                curveTo(14f, 4f, 17f, 2f, 20f, 5f)
                curveTo(23f, 9f, 21f, 14f, 12f, 20f)
                close()
            }),
        )
    val Feed =
        make(
            "Feed",
            listOf({
                moveTo(5f, 3f)
                lineTo(5f, 8f)
                moveTo(19f, 3f)
                lineTo(19f, 8f)
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                lineTo(21f, 21f)
                lineTo(3f, 21f)
                close()
                moveTo(7f, 12f)
                lineTo(11f, 12f)
                moveTo(7f, 16f)
                lineTo(16f, 16f)
            }),
        )
    val HeartFilled =
        ImageVector.Builder("HeartFilled", 24.dp, 24.dp, 24f, 24f)
            .apply {
                path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                    moveTo(12f, 20f)
                    curveTo(3f, 14f, 1f, 9f, 4f, 5f)
                    curveTo(7f, 2f, 10f, 4f, 12f, 6f)
                    curveTo(14f, 4f, 17f, 2f, 20f, 5f)
                    curveTo(23f, 9f, 21f, 14f, 12f, 20f)
                    close()
                }
            }
            .build()
    val Comment =
        make(
            "Comment",
            listOf({
                moveTo(3f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 17f)
                lineTo(9f, 17f)
                lineTo(3f, 21f)
                close()
                moveTo(7f, 8f)
                lineTo(17f, 8f)
                moveTo(7f, 12f)
                lineTo(14f, 12f)
            }),
        )
    val Person =
        make(
            "Person",
            listOf({
                moveTo(12f, 3f)
                curveTo(18f, 3f, 18f, 12f, 12f, 12f)
                curveTo(6f, 12f, 6f, 3f, 12f, 3f)
                close()
                moveTo(3f, 21f)
                curveTo(3f, 13f, 21f, 13f, 21f, 21f)
            }),
        )
    val Back =
        make(
            "Back",
            listOf({
                moveTo(20f, 12f)
                lineTo(4f, 12f)
                moveTo(10f, 5f)
                lineTo(3f, 12f)
                lineTo(10f, 19f)
            }),
        )
    val Download =
        make(
            "Download",
            listOf({
                moveTo(12f, 3f)
                lineTo(12f, 16f)
                moveTo(6f, 10f)
                lineTo(12f, 16f)
                lineTo(18f, 10f)
                moveTo(4f, 18f)
                lineTo(4f, 21f)
                lineTo(20f, 21f)
                lineTo(20f, 18f)
            }),
        )
    val More =
        make(
            "More",
            listOf({
                moveTo(5f, 12f)
                lineTo(5.1f, 12f)
                moveTo(12f, 12f)
                lineTo(12.1f, 12f)
                moveTo(19f, 12f)
                lineTo(19.1f, 12f)
            }),
        )
    val Settings =
        make(
            "Settings",
            listOf({
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
                moveTo(8f, 4f)
                lineTo(8f, 10f)
                moveTo(16f, 14f)
                lineTo(16f, 20f)
            }),
        )
    val Arrow =
        make(
            "Arrow",
            listOf({
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }),
        )
    val Close =
        make(
            "Close",
            listOf({
                moveTo(5f, 5f)
                lineTo(19f, 19f)
                moveTo(19f, 5f)
                lineTo(5f, 19f)
            }),
        )
    val Rank =
        make(
            "Rank",
            listOf({
                moveTo(4f, 20f)
                lineTo(4f, 13f)
                lineTo(9f, 13f)
                lineTo(9f, 20f)
                moveTo(9f, 20f)
                lineTo(9f, 5f)
                lineTo(15f, 5f)
                lineTo(15f, 20f)
                moveTo(15f, 20f)
                lineTo(15f, 10f)
                lineTo(20f, 10f)
                lineTo(20f, 20f)
            }),
        )
    val History =
        make(
            "History",
            listOf({
                moveTo(4f, 8f)
                curveTo(8f, -1f, 22f, 3f, 21f, 13f)
                curveTo(20f, 23f, 6f, 24f, 3f, 15f)
                moveTo(3f, 3f)
                lineTo(3f, 9f)
                lineTo(9f, 9f)
                moveTo(12f, 7f)
                lineTo(12f, 13f)
                lineTo(16f, 15f)
            }),
        )
    val Share =
        make(
            "Share",
            listOf({
                moveTo(17f, 5f)
                lineTo(7f, 11f)
                lineTo(17f, 18f)
                moveTo(18f, 3f)
                lineTo(21f, 5f)
                lineTo(18f, 7f)
                close()
                moveTo(3f, 10f)
                lineTo(7f, 10f)
                lineTo(7f, 14f)
                lineTo(3f, 14f)
                close()
                moveTo(18f, 16f)
                lineTo(21f, 18f)
                lineTo(18f, 20f)
                close()
            }),
        )
    val Check =
        make(
            "Check",
            listOf({
                moveTo(4f, 12f)
                lineTo(10f, 18f)
                lineTo(20f, 6f)
            }),
        )
    val Pause =
        make(
            "Pause",
            listOf({
                moveTo(8f, 5f)
                lineTo(8f, 19f)
                moveTo(16f, 5f)
                lineTo(16f, 19f)
            }),
        )
    val Play =
        make(
            "Play",
            listOf({
                moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()
            }),
        )
    val Book =
        make(
            "Book",
            listOf({
                moveTo(12f, 5f)
                curveTo(8f, 2f, 4f, 3f, 2f, 4f)
                lineTo(2f, 20f)
                curveTo(5f, 18f, 8f, 18f, 12f, 21f)
                curveTo(16f, 18f, 19f, 18f, 22f, 20f)
                lineTo(22f, 4f)
                curveTo(19f, 3f, 16f, 2f, 12f, 5f)
                lineTo(12f, 21f)
            }),
        )
}

@Composable
fun AppIcon(
    vector: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) = Icon(vector, description, modifier, tint)
