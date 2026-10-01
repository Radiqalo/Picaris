package io.github.pixivnext.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColor
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeNavigationBar(
    floating: Boolean,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icon: @Composable (Int, Boolean) -> Unit,
) {
    if (!floating) {
        ShortNavigationBar(
            modifier = Modifier.aboveWorkTransition(),
            arrangement = ShortNavigationBarArrangement.EqualWeight,
        ) {
            labels.forEachIndexed { index, title ->
                ShortNavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { icon(index, selectedIndex == index) },
                    label = { Text(title) },
                )
            }
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().aboveWorkTransition()
                .windowInsetsPadding(WindowInsets.navigationBars.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val selectedWidth = (maxWidth - 16.dp - 48.dp * (labels.size - 1)).coerceAtLeast(48.dp)
            val transition = updateTransition(selectedIndex, label = "floating navigation")
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val labelStyle = MaterialTheme.typography.labelLarge
            val labelWidth = with(density) {
                labels.maxOf { textMeasurer.measure(it, labelStyle).size.width }.toDp()
            }
            val extraWidth = (labelWidth + 8.dp).coerceAtMost(selectedWidth - 48.dp)
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    labels.forEachIndexed { index, title ->
                        val selected = selectedIndex == index
                        val expansion by transition.animateFloat(
                            transitionSpec = { spatial }, label = "item $index expansion",
                        ) { target -> if (target == index) 1f else 0f }
                        val progress = expansion.coerceIn(0f, 1f)
                        val containerColor by transition.animateColor(
                            transitionSpec = { effects }, label = "item $index container",
                        ) { target ->
                            if (target == index) MaterialTheme.colorScheme.primary else Color.Transparent
                        }
                        val contentColor by transition.animateColor(
                            transitionSpec = { effects }, label = "item $index content",
                        ) { target ->
                            if (target == index) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        Surface(
                            modifier = Modifier.height(56.dp)
                                .width(48.dp + extraWidth * progress)
                                .clip(CircleShape)
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                                .semantics { contentDescription = title },
                            shape = CircleShape,
                            color = containerColor,
                            contentColor = contentColor,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                icon(index, selected)
                                Spacer(Modifier.width(8.dp * progress))
                                Box(Modifier.width((extraWidth - 8.dp).coerceAtLeast(0.dp) * progress)
                                    .clipToBounds().clearAndSetSemantics {}) {
                                    Text(title, style = labelStyle,
                                        modifier = Modifier.requiredWidth(labelWidth).graphicsLayer {
                                            alpha = progress
                                            translationX = (1f - progress) * 8.dp.toPx()
                                        },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
