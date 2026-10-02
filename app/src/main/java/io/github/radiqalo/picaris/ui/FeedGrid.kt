package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.*
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val WorkImageBadgeInset = 8.dp

@Composable
fun FeedGrid(
    spec: FeedSpec,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    rank: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    afterHeader: (@Composable () -> Unit)? = null,
    itemsModifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState? = null,
    listState: LazyListState? = null,
    topPadding: Dp = PixivSpacing.content,
    pullToRefreshEnabled: Boolean = true,
    scrollHeaderWhileEmpty: Boolean = false,
    leadingWork: Work? = null,
    onRefresh: (() -> Unit)? = null,
    feedRefreshVersion: Int = 0,
    refreshingOverride: Boolean? = null,
    onRefreshFinished: (() -> Unit)? = null,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val flow =
        remember(
            spec,
            vm.accountId,
            settings.showAdult,
            settings.hideAi,
            settings.blockedTags,
            settings.blockedUsers,
            feedRefreshVersion,
        ) {
            vm.feed(spec, feedRefreshVersion)
        }
    val items = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val grid = gridState ?: key(spec) { rememberLazyStaggeredGridState() }
    val list = listState ?: key(spec) { rememberLazyListState() }
    val refreshing = items.loadState.refresh is LoadState.Loading
    LaunchedEffect(feedRefreshVersion) {
        if (feedRefreshVersion > 0 && onRefreshFinished != null) {
            var observedLoading = false
            withTimeoutOrNull(1_500) {
                snapshotFlow { items.loadState.refresh }.first { loadState ->
                    if (loadState is LoadState.Loading) observedLoading = true
                    observedLoading && loadState !is LoadState.Loading
                }
            }
            onRefreshFinished()
        }
    }
    val error = items.loadState.refresh as? LoadState.Error
    val showFeedMetadata = spec.section != "ranking" && settings.showHomeMetadata
    val feedContent: @Composable () -> Unit = {
        when {
            refreshing && items.itemCount == 0 && leadingWork == null ->
                FeedGridStatus(
                    header,
                    afterHeader,
                    scrollHeaderWhileEmpty,
                    grid,
                    list,
                    topPadding,
                    gridLayout = spec.kind != "novel",
                ) { LoadingState() }
            error != null && items.itemCount == 0 && leadingWork == null ->
                FeedGridStatus(
                    header,
                    afterHeader,
                    scrollHeaderWhileEmpty,
                    grid,
                    list,
                    topPadding,
                    gridLayout = spec.kind != "novel",
                ) {
                    EmptyState(
                        stringResource(R.string.ui_590a4df471),
                        error.error.message ?: stringResource(R.string.ui_73a13d2b99),
                        materialSymbol(MaterialSymbol.Explore),
                        stringResource(R.string.ui_e2d53a6d3a),
                    ) {
                        items.retry()
                    }
                }
            items.itemCount == 0 && leadingWork == null ->
                FeedGridStatus(
                    header,
                    afterHeader,
                    scrollHeaderWhileEmpty,
                    grid,
                    list,
                    topPadding,
                    gridLayout = spec.kind != "novel",
                ) {
                    EmptyState(
                        stringResource(R.string.ui_37ce9e3518),
                        if (spec.section == "bookmarks") {
                            stringResource(R.string.ui_408822a29e)
                        } else {
                            stringResource(R.string.ui_a588489241)
                        },
                        materialSymbol(MaterialSymbol.Book),
                    )
                }
            spec.kind == "novel" ->
                LazyColumn(
                    state = list,
                    contentPadding =
                        PaddingValues(
                            start = PixivSpacing.content,
                            end = PixivSpacing.content,
                            top = topPadding,
                            bottom =
                                PixivSpacing.content + LocalHomeNavigationInset.current,
                        ),
                    verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
                    modifier = Modifier.fillMaxSize().testTag("novelList"),
                ) {
                    if (header != null) item(key = "feed_header") { header() }
                    if (afterHeader != null) item(key = "feed_after_header") { afterHeader() }
                    if (leadingWork != null) {
                        item(key = "leading_work_${leadingWork.id}") {
                            val identity = leadingWork.identity(vm.accountId)
                            val current = bookmarks[identity]?.apply(leadingWork) ?: leadingWork
                            NovelListItem(
                                work = current,
                                rank = null,
                                likedBusy = identity in busy,
                                modifier = itemsModifier,
                                onLike = { vm.run { vm.bookmark(current) } },
                                onClick = {
                                    vm.record(current)
                                    navigate(Detail(current, spec, 0))
                                },
                            )
                        }
                    }
                    items(items.itemCount, key = items.itemKey { "${it.type}_${it.id}" }) { index ->
                        items[index]?.let { work ->
                            if (work.id != leadingWork?.id) {
                                val identity = work.identity(vm.accountId)
                                val current = bookmarks[identity]?.apply(work) ?: work
                                NovelListItem(
                                    work = current,
                                    rank = index.takeIf { rank },
                                    likedBusy = identity in busy,
                                    modifier = itemsModifier,
                                    onLike = { vm.run { vm.bookmark(current) } },
                                    onClick = {
                                        vm.record(current)
                                        navigate(Detail(current))
                                    },
                                )
                            }
                        }
                    }
                    item { FeedAppendState(items.loadState.append, items.itemCount, items::retry) }
                }
            else ->
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
                    verticalItemSpacing = PixivSpacing.compact,
                    contentPadding =
                        PaddingValues(
                            start = PixivSpacing.content,
                            end = PixivSpacing.content,
                            top = topPadding,
                            bottom =
                                PixivSpacing.content + LocalHomeNavigationInset.current,
                        ),
                    modifier = Modifier.fillMaxSize().testTag("feedGrid"),
                ) {
                    if (header != null) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "feed_header") {
                            header()
                        }
                    }
                    if (afterHeader != null) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "feed_after_header") {
                            afterHeader()
                        }
                    }
                    if (leadingWork != null) {
                        item(key = "leading_work_${leadingWork.id}") {
                            val identity = leadingWork.identity(vm.accountId)
                            val current = bookmarks[identity]?.apply(leadingWork) ?: leadingWork
                            WorkCard(
                                current,
                                imageQuality = settings.feedImageQuality,
                                likedBusy = identity in busy,
                                showMetadata = showFeedMetadata,
                                modifier = itemsModifier,
                                onLike = { vm.run { vm.bookmark(current) } },
                            ) {
                                vm.record(current)
                                navigate(Detail(current, spec, 0))
                            }
                        }
                    }
                    items(items.itemCount, key = items.itemKey { "${it.type}_${it.id}" }) { index ->
                        items[index]?.let { work ->
                            if (work.id != leadingWork?.id) {
                                val identity = work.identity(vm.accountId)
                                val current = bookmarks[identity]?.apply(work) ?: work
                                WorkCard(
                                    current,
                                    index.takeIf { rank },
                                    imageQuality = settings.feedImageQuality,
                                    likedBusy = identity in busy,
                                    showMetadata = showFeedMetadata,
                                    modifier = itemsModifier,
                                    onLike = { vm.run { vm.bookmark(current) } },
                                ) {
                                    vm.record(current)
                                    navigate(Detail(current, spec, index))
                                }
                            }
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        FeedAppendState(items.loadState.append, items.itemCount, items::retry)
                    }
                }
        }
    }
    if (pullToRefreshEnabled) {
        val refreshState = rememberPullToRefreshState()
        val isRefreshing = refreshingOverride ?: (refreshing && items.itemCount > 0)
        val isLoadingAfterRefresh = onRefresh != null && feedRefreshVersion > 0 && refreshing
        PullToRefreshBox(
            isRefreshing = isRefreshing || isLoadingAfterRefresh,
            onRefresh = {
                if (onRefresh != null) {
                    onRefresh()
                } else {
                    items.refresh()
                }
            },
            modifier = modifier.fillMaxWidth(),
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) { feedContent() }
    } else {
        Box(modifier.fillMaxWidth()) { feedContent() }
    }
}

@Composable
private fun NovelListItem(
    work: Work,
    rank: Int?,
    likedBusy: Boolean,
    modifier: Modifier = Modifier,
    onLike: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            WorkImage(
                work,
                Modifier.size(width = 72.dp, height = 100.dp).clip(MaterialTheme.shapes.small),
                scale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight)) {
                if (rank != null) {
                    Text(
                        "#${rank + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    work.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        supportingContent = {
            Text(work.user.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(
                onClick = onLike,
                enabled = !likedBusy,
                modifier = Modifier.testTag("like_${work.type}_${work.id}"),
            ) {
                AppIcon(
                    if (work.is_bookmarked) {
                        materialSymbol(
                            MaterialSymbol.FavoriteFilled,
                        )
                    } else {
                        materialSymbol(MaterialSymbol.Favorite)
                    },
                    if (work.is_bookmarked) "取消喜欢 ${work.title}" else "喜欢 ${work.title}",
                    tint =
                        if (work.is_bookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    )
}

@Composable
private fun FeedAppendState(
    state: LoadState,
    count: Int,
    retry: () -> Unit,
) {
    when (state) {
        is LoadState.Loading ->
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(Modifier.size(32.dp))
            }
        is LoadState.Error ->
            TextButton(retry, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_0aa214d301))
            }
        else ->
            if (count > 0) {
                Text(
                    stringResource(R.string.ui_5f3621612f),
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
    }
}

@Composable
private fun FeedGridStatus(
    header: (@Composable () -> Unit)?,
    afterHeader: (@Composable () -> Unit)?,
    scrollHeader: Boolean,
    gridState: LazyStaggeredGridState,
    listState: LazyListState,
    topPadding: Dp,
    gridLayout: Boolean,
    content: @Composable () -> Unit,
) {
    if (scrollHeader && gridLayout) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            verticalItemSpacing = PixivSpacing.compact,
            contentPadding =
                PaddingValues(
                    start = PixivSpacing.content,
                    end = PixivSpacing.content,
                    top = topPadding,
                    bottom = PixivSpacing.content + LocalHomeNavigationInset.current,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header !=
                null
            ) {
                item(span = StaggeredGridItemSpan.FullLine, key = "feed_header") { header() }
            }
            if (afterHeader !=
                null
            ) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "feed_after_header",
                ) { afterHeader() }
            }
            item(span = StaggeredGridItemSpan.FullLine, key = "feed_status") {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    } else if (scrollHeader) {
        LazyColumn(
            state = listState,
            contentPadding =
                PaddingValues(
                    start = PixivSpacing.content,
                    end = PixivSpacing.content,
                    top = topPadding,
                    bottom = PixivSpacing.content + LocalHomeNavigationInset.current,
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) item(key = "feed_header") { header() }
            if (afterHeader != null) item(key = "feed_after_header") { afterHeader() }
            item(key = "feed_status") {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            if (header !=
                null
            ) {
                Box(Modifier.padding(horizontal = PixivSpacing.content)) { header() }
            }
            if (afterHeader !=
                null
            ) {
                Box(Modifier.padding(horizontal = PixivSpacing.content)) { afterHeader() }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = PixivSpacing.content),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

@Composable
fun WorkCard(
    work: Work,
    rank: Int? = null,
    imageQuality: String = "medium",
    likedBusy: Boolean = false,
    showMetadata: Boolean = true,
    sharedTransition: Boolean = true,
    modifier: Modifier = Modifier,
    onLike: () -> Unit,
    onClick: () -> Unit,
) {
    val imageShape = MaterialTheme.shapes.small
    val badgeShape = MaterialTheme.shapes.small
    val permitted = navigationPermission()
    val likeInteraction = remember { MutableInteractionSource() }
    val artworkReturn = LocalArtworkReturnFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(artworkReturn, work.type, work.id) {
        if (artworkReturn?.type == work.type && artworkReturn.id == work.id) {
            val center = with(density) { 16.dp.toPx() }
            val press =
                PressInteraction.Press(
                    androidx.compose.ui.geometry
                        .Offset(center, center),
                )
            likeInteraction.emit(press)
            try {
                kotlinx.coroutines.delay(120)
            } finally {
                likeInteraction.tryEmit(PressInteraction.Release(press))
            }
        }
    }
    val feedback = toggleFeedback()

    Column(
        modifier
            .clip(imageShape)
            .clickable { if (permitted()) onClick() }
            .semantics {
                if (!showMetadata) contentDescription = "${work.title}，${work.user.name}"
            }.padding(bottom = if (showMetadata) 2.dp else 0.dp),
    ) {
        WorkImage(
            work,
            Modifier
                .fillMaxWidth()
                .aspectRatio(
                    if (work.isNovel) .9f else work.aspect,
                ).clip(imageShape),
            url = work.imageForQuality(imageQuality),
            sharedTransition = sharedTransition,
        ) {
            val labels =
                buildList {
                    if (rank != null) add("${rank + 1}")
                    if (work.isAiGenerated) add("AI")
                    if (work.x_restrict > 0) add(if (work.x_restrict == 2) "R18G" else "R18")
                }
            val imageKey = "work-image:${work.type}:${work.id}"
            if (labels.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(WorkImageBadgeInset)
                        .aboveWorkTransition(.5f, imageKey),
                    horizontalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                ) {
                    labels.forEach { label ->
                        Surface(
                            shape = badgeShape,
                            color = Color.Black.copy(alpha = .48f),
                            contentColor = Color.White,
                        ) {
                            Text(
                                label,
                                Modifier
                                    .height(32.dp)
                                    .wrapContentHeight()
                                    .padding(horizontal = PixivSpacing.compact),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            if (work.page_count > 1 || work.type == "ugoira" || work.isNovel) {
                Surface(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(WorkImageBadgeInset)
                        .aboveWorkTransition(.5f, imageKey),
                    shape = badgeShape,
                    color = Color.Black.copy(alpha = .48f),
                    contentColor = Color.White,
                ) {
                    Text(
                        if (work.isNovel) {
                            stringResource(R.string.content_novel)
                        } else if (work.type == "ugoira") {
                            stringResource(R.string.ui_de9dcfdf88)
                        } else {
                            "${work.page_count}P"
                        },
                        Modifier
                            .height(32.dp)
                            .wrapContentHeight()
                            .padding(horizontal = PixivSpacing.compact),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Surface(
                    onClick = {
                        if (permitted()) {
                            feedback(!work.is_bookmarked)
                            onLike()
                        }
                    },
                    interactionSource = likeInteraction,
                    enabled = !likedBusy && permitted(),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(WorkImageBadgeInset)
                            .size(32.dp)
                            .aboveWorkTransition(.75f, imageKey)
                            .expressivePress(likeInteraction)
                            .testTag("like_${work.type}_${work.id}")
                            .semantics {
                                contentDescription =
                                    if (work.is_bookmarked) "取消喜欢 ${work.title}" else "喜欢 ${work.title}"
                                stateDescription = if (work.is_bookmarked) "已喜欢" else "未喜欢"
                            },
                    shape = badgeShape,
                    color = Color.Black.copy(alpha = .48f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FeedbackIcon(
                            if (work.is_bookmarked) {
                                materialSymbol(
                                    MaterialSymbol.FavoriteFilled,
                                )
                            } else {
                                materialSymbol(MaterialSymbol.Favorite)
                            },
                            null,
                            selected = work.is_bookmarked,
                            modifier = Modifier.size(20.dp),
                            tint = if (work.is_bookmarked) Color(0xFFFF80A2) else Color.White,
                        )
                    }
                }
            }
        }
        if (showMetadata) {
            Text(
                work.title,
                Modifier.padding(top = 2.dp, start = 3.dp, end = 3.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                work.user.name,
                Modifier.padding(start = 3.dp, end = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun compact(n: Int) =
    if (n >= 10000) {
        "%.1fw".format(n / 10000f)
    } else if (n >= 1000) {
        "%.1fk".format(n / 1000f)
    } else {
        n.toString()
    }

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(Modifier.size(48.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(
                if (action != null && icon != null) icon else materialSymbol(MaterialSymbol.Inbox),
                null,
                Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            if (action != null) Button(onAction) { Text(action) }
        }
    }
}
