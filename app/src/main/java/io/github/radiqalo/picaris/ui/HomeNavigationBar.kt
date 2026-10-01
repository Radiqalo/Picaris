package io.github.radiqalo.picaris.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val LocalHomeNavigationInset = staticCompositionLocalOf { 0.dp }
internal val FloatingNavigationHeight = 68.dp

@Composable
internal fun floatingNavigationBottomSpacing() =
    (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp).coerceAtLeast(36.dp)

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
        Box(
            modifier = Modifier.fillMaxWidth().aboveWorkTransition()
                .windowInsetsPadding(WindowInsets.navigationBars.only(
                    WindowInsetsSides.Horizontal,
                ))
                .padding(start = 12.dp, end = 12.dp, bottom = floatingNavigationBottomSpacing()),
            contentAlignment = Alignment.BottomCenter,
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.animateContentSize(
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                ).height(FloatingNavigationHeight),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                labels.forEachIndexed { index, title ->
                    val selected = selectedIndex == index
                    val iconHeight by animateDpAsState(
                        targetValue = if (selected) 42.dp else 26.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "floating indicator height $index",
                    )
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxHeight().semantics { contentDescription = title },
                        iconPosition = NavigationItemIconPosition.Start,
                        icon = {
                            Box(Modifier.height(iconHeight), contentAlignment = Alignment.Center) {
                                icon(index, selected)
                            }
                        },
                        label = {
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        expandFrom = Alignment.Start,
                                    ) + slideInHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        initialOffsetX = { -it / 2 },
                                    ),
                                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                                    shrinkHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        shrinkTowards = Alignment.Start,
                                    ) + slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        targetOffsetX = { -it / 2 },
                                    ),
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 2.dp, end = 4.dp).clearAndSetSemantics {},
                                )
                            }
                        },
                        colors = ShortNavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColorStartIconPosition = MaterialTheme.colorScheme.onPrimary,
                            selectedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
