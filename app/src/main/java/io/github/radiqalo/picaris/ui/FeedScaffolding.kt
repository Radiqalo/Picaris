package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.*
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

val LocalAppBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@Composable
fun ScrollingScreen(
    scrollBehaviorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val behavior = if (scrollBehaviorEnabled) TopAppBarDefaults.enterAlwaysScrollBehavior() else null
    CompositionLocalProvider(LocalAppBarScrollBehavior provides behavior) {
        val modifier =
            if (behavior == null) {
                Modifier
            } else {
                Modifier.nestedScroll(behavior.nestedScrollConnection)
            }
        Box(Modifier.fillMaxSize().then(modifier)) { content() }
    }
}

@Composable
fun ScreenBar(
    title: String,
    back: (() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = LocalAppBarScrollBehavior.current,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleContent: @Composable () -> Unit = {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val navigationContent: @Composable () -> Unit = {
        if (back != null) {
            IconButton(back) {
                AppIcon(
                    materialSymbol(MaterialSymbol.ArrowBack),
                    stringResource(R.string.ui_11d0241540),
                )
            }
        }
    }
    TopAppBar(
        modifier = modifier,
        title = titleContent,
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
        navigationIcon = navigationContent,
        actions = actions,
        windowInsets = windowInsets,
    )
}

@Composable
fun ContentKindAction(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selected = if (settings.contentKind == "novel") "novel" else "illust"
    val feedback = selectionFeedback()
    IconButton(onClick = {
        feedback()
        vm.selectContentKind(if (selected == "novel") "illust" else "novel")
    }) {
        FeedbackIcon(
            materialSymbol(if (selected == "novel") MaterialSymbol.Book else MaterialSymbol.Image),
            if (selected == "novel") "当前小说，切换到图片" else "当前图片，切换到小说",
            selected = selected == "novel",
        )
    }
}

@Composable
fun ChoiceChips(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = false,
    alignment: Alignment.Horizontal = Alignment.Start,
    equalWidth: Boolean = false,
) {
    val sources = remember(options) { List(options.size) { MutableInteractionSource() } }
    val feedback = selectionFeedback()
    val select: (String) -> Unit = { key ->
        if (selected != key) {
            feedback()
            onSelect(key)
        }
    }
    ButtonGroup(
        overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
        modifier = modifier,
        expandedRatio = 1f,
        horizontalArrangement =
            Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, alignment),
    ) {
        options.forEachIndexed { index, (key, label) ->
            customItem(
                buttonGroupContent = {
                    val padding = ButtonDefaults.ContentPadding
                    ToggleButton(
                        checked = selected == key,
                        onCheckedChange = { select(key) },
                        colors =
                            if (equalWidth) {
                                ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                ToggleButtonDefaults.colors()
                            },
                        shapes =
                            when {
                                options.size == 1 ->
                                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                index == options.lastIndex ->
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        interactionSource = sources[index],
                        modifier =
                            (if (equalWidth) Modifier.weight(1f) else Modifier).animateWidth(
                                interactionSource = sources[index],
                                compressionLimit =
                                    padding.calculateEndPadding(LocalLayoutDirection.current),
                            ),
                    ) {
                        if (showCheck && selected == key) {
                            AppIcon(
                                materialSymbol(MaterialSymbol.Check),
                                null,
                                Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        }
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = {
                    DropdownMenuItem(text = { Text(label) }, onClick = { select(key) })
                },
            )
        }
    }
}
