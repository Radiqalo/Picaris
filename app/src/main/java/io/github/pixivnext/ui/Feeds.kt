package io.github.pixivnext.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*
import kotlinx.coroutines.launch

private val WorkImageBadgeInset = 8.dp

val LocalAppBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@Composable
fun ScrollingScreen(
    scrollableState: ScrollableState? = null,
    content: @Composable () -> Unit,
) {
    val behavior =
        if (scrollableState != null)
            TopAppBarDefaults.enterAlwaysScrollBehavior(scrollableState = scrollableState)
        else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CompositionLocalProvider(LocalAppBarScrollBehavior provides behavior) {
        Box(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) { content() }
    }
}

@Composable
fun ScreenBar(
    title: String,
    back: (() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val titleContent: @Composable () -> Unit = {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val navigationContent: @Composable () -> Unit = {
        if (back != null)
            IconButton(back) { AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540)) }
    }
    TopAppBar(
        title = titleContent,
        scrollBehavior = LocalAppBarScrollBehavior.current,
        navigationIcon = navigationContent,
        actions = actions,
        windowInsets = windowInsets,
    )
}

@Composable
fun ContentKindAction(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selected = if (settings.contentKind == "novel") "novel" else "illust"
    IconButton(onClick = {
        vm.selectContentKind(if (selected == "novel") "illust" else "novel")
    }) {
        AppIcon(
            materialSymbol(if (selected == "novel") MaterialSymbol.Book else MaterialSymbol.Image),
            if (selected == "novel") "当前小说，切换到图片" else "当前图片，切换到小说",
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
) {
    val sources = remember(options) { List(options.size) { MutableInteractionSource() } }
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
                        onCheckedChange = { onSelect(key) },
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
                            Modifier.animateWidth(
                                interactionSource = sources[index],
                                compressionLimit =
                                    padding.calculateEndPadding(LocalLayoutDirection.current),
                            ),
                    ) {
                        if (showCheck && selected == key) {
                            AppIcon(Glyph.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        }
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = {
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(key) })
                },
            )
        }
    }
}

@Composable
fun RecommendedHomeScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    gridState: LazyStaggeredGridState,
    listState: LazyListState,
    search: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    Column {
        // Collapse the status-bar gutter with the home app bar, leaving the feed edge to edge.
        val collapsed = LocalAppBarScrollBehavior.current?.state?.collapsedFraction ?: 0f
        Spacer(Modifier.height(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() * (1f - collapsed)
        ))
        ScreenBar(
            strings.getString(R.string.tab_home) + if (demo) " · 演示" else "",
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal),
            actions = {
                IconButton(search) {
                    AppIcon(Glyph.Search, strings.getString(R.string.ui_f04090805c))
                }
            },
        )
        FeedGrid(
            FeedSpec(kind = settings.contentKind),
            vm,
            navigate,
            Modifier.weight(1f),
            gridState = gridState,
            listState = listState,
        )
    }
}

@Composable
fun DiscoverScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Column {
        ScreenBar(strings.getString(R.string.ui_523e40a074))
        Box(Modifier.weight(1f)) {
            DiscoveryLanding(vm, navigate)
        }
    }
}

@Composable
private fun DiscoveryLanding(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val trendResult by produceState<List<TrendingTag>?>(null, vm.accountId, demo, settings.contentKind) {
        value = null
        if (settings.contentKind == "novel") {
            value = emptyList()
            return@produceState
        }
        value = try {
            vm.trendingTags()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    val trends = trendResult.orEmpty()
    val loadingTrends = trendResult == null && settings.contentKind != "novel"
    FeedGrid(
        FeedSpec(kind = settings.contentKind),
        vm,
        navigate,
        Modifier.fillMaxSize(),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.content)) {
                FilledTonalButton(
                    onClick = {
                        navigate(Collection(strings.getString(R.string.ui_d00981d6ce), "ranking"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    AppIcon(Glyph.Rank, null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(strings.getString(R.string.ui_d00981d6ce))
                }
                if (loadingTrends) DiscoveryPlaceholders()
                else if (trends.isNotEmpty()) {
                    Text(
                        strings.getString(R.string.discover_featured),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(trends.take(6), key = { it.tag.name }) { trend ->
                            ElevatedCard(
                                onClick = {
                                    navigate(Collection(trend.tag.name, "search", word = trend.tag.name))
                                },
                                modifier = Modifier.width(260.dp),
                            ) {
                                Box {
                                    WorkImage(trend.cover, Modifier.fillMaxWidth().height(150.dp))
                                    SuggestionChip(
                                        onClick = {
                                            navigate(Collection(trend.tag.name, "search", word = trend.tag.name))
                                        },
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        ),
                                        label = {
                                            Text(
                                                "#${trend.tag.translated_name ?: trend.tag.name}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        strings.getString(R.string.discover_tags),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trends, key = { it.tag.name }) { trend ->
                            SuggestionChip(
                                onClick = {
                                    navigate(Collection(trend.tag.name, "search", word = trend.tag.name))
                                },
                                label = { Text("#${trend.tag.translated_name ?: trend.tag.name}") },
                            )
                        }
                    }
                    val artists = trends.map { it.cover.user }.distinctBy { it.id }.take(10)
                    if (artists.isNotEmpty()) {
                        Text(
                            strings.getString(R.string.discover_artists),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(artists, key = { it.id }) { artist ->
                                ElevatedCard(onClick = { navigate(Author(artist)) }) {
                                    Row(
                                        Modifier.width(180.dp).padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Avatar(artist)
                                        Text(
                                            artist.name,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    if (settings.contentKind == "novel") strings.getString(R.string.discover_novels)
                    else strings.getString(R.string.discover_works),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    )
}

@Composable
private fun DiscoveryPlaceholders() {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Text(strings.getString(R.string.discover_featured),
        style = MaterialTheme.typography.headlineSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(3) {
            ElevatedCard(Modifier.width(260.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
        }
    }
    Text(strings.getString(R.string.discover_tags),
        style = MaterialTheme.typography.titleLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(4) {
            SuggestionChip(
                onClick = {},
                enabled = false,
                modifier = Modifier.width(112.dp),
                label = { Spacer(Modifier.height(20.dp)) },
            )
        }
    }
    Text(strings.getString(R.string.discover_artists),
        style = MaterialTheme.typography.titleLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(3) {
            ElevatedCard(Modifier.width(180.dp)) {
                Row(Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Box(Modifier.width(88.dp).height(16.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
        }
    }
}

@Composable
fun FollowScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column {
        ScreenBar(strings.getString(R.string.ui_753ccc8e2e))
        FeedGrid(
            FeedSpec(section = "follow", kind = settings.contentKind),
            vm,
            navigate,
            Modifier.weight(1f),
        )
    }
}

@Composable
fun BookmarkScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var private by rememberSaveable { mutableStateOf(false) }
    Column {
        ScreenBar(
            strings.getString(R.string.ui_d07cee786a),
            back = back,
            actions = {
                IconButton(onClick = { private = !private }) {
                    AppIcon(
                        materialSymbol(if (private) MaterialSymbol.Lock else MaterialSymbol.LockOpen),
                        if (private) "当前私人收藏，切换到公开收藏" else "当前公开收藏，切换到私人收藏",
                    )
                }
            },
        )
        FeedGrid(
            FeedSpec(
                section = "bookmarks",
                kind = settings.contentKind,
                restrict = if (private) "private" else "public",
            ),
            vm,
            navigate,
            Modifier.weight(1f),
        )
    }
}

@Composable
fun CollectionScreen(
    route: Collection,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column {
        if (route.section == "ranking" || route.section == "search")
            TopAppBar(
                title = {
                    Text(
                        if (route.section == "search") "#${route.title}" else route.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540))
                    }
                },
                scrollBehavior = LocalAppBarScrollBehavior.current,
            )
        else ScreenBar(route.title, back = back)
        if (route.section == "ranking")
            RankingPages(vm, navigate, Modifier.weight(1f))
        else FeedGrid(
            FeedSpec(
                section = route.section,
                kind =
                    if (route.section == "series" || route.section == "related") route.kind
                    else settings.contentKind,
                userId = route.userId,
                word = route.word,
            ),
            vm,
            navigate,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun RankingPages(vm: AppViewModel, navigate: (NavKey) -> Unit, modifier: Modifier) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val modes =
        listOf(
            "day" to strings.getString(R.string.ui_f8c9b6d5d8),
            "week" to strings.getString(R.string.ui_5e00476f4e),
            "month" to strings.getString(R.string.ui_0b554f5235),
            "day_male" to strings.getString(R.string.ui_fbe010365d),
            "day_female" to strings.getString(R.string.ui_b57634d889),
            "week_rookie" to strings.getString(R.string.ui_8b7adaf587),
        )
    val pager = rememberPagerState(pageCount = { modes.size })
    val scope = rememberCoroutineScope()
    Column(modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 0.dp,
        ) {
            modes.forEachIndexed { index, (_, title) ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 0,
            key = { modes[it].first },
        ) { page ->
            FeedGrid(
                FeedSpec(section = "ranking", kind = settings.contentKind, mode = modes[page].first),
                vm,
                navigate,
                Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun FeedGrid(
    spec: FeedSpec,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    rank: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    gridState: LazyStaggeredGridState? = null,
    listState: LazyListState? = null,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val flow =
        remember(
            spec,
            vm.accountId,
            settings.showAdult,
            settings.hideAi,
            settings.blockedTags,
            settings.blockedUsers,
        ) {
            vm.feed(spec)
        }
    val items = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val grid = gridState ?: key(spec) { rememberLazyStaggeredGridState() }
    val list = listState ?: key(spec) { rememberLazyListState() }
    val refreshing = items.loadState.refresh is LoadState.Loading
    val error = items.loadState.refresh as? LoadState.Error
    val showFeedMetadata = spec.section != "ranking" && settings.showHomeMetadata
    PullToRefreshBox(
        isRefreshing = refreshing && items.itemCount > 0,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxWidth(),
    ) {
        when {
            refreshing && items.itemCount == 0 -> FeedGridStatus(header) { LoadingState() }
            error != null && items.itemCount == 0 ->
                FeedGridStatus(header) {
                    EmptyState(
                        strings.getString(R.string.ui_590a4df471),
                        error.error.message ?: strings.getString(R.string.ui_73a13d2b99),
                        Glyph.Discover,
                        strings.getString(R.string.ui_e2d53a6d3a),
                    ) {
                        items.retry()
                    }
                }
            items.itemCount == 0 ->
                FeedGridStatus(header) {
                    EmptyState(
                        strings.getString(R.string.ui_37ce9e3518),
                        if (spec.section == "bookmarks") strings.getString(R.string.ui_408822a29e)
                        else strings.getString(R.string.ui_a588489241),
                        Glyph.Book,
                    )
                }
            spec.kind == "novel" ->
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(PixivSpacing.content),
                    verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
                    modifier = Modifier.fillMaxSize().testTag("novelList"),
                ) {
                    if (header != null) item(key = "feed_header") { header() }
                    items(items.itemCount, key = items.itemKey { "${it.type}_${it.id}" }) { index ->
                        items[index]?.let { work ->
                            val identity = work.identity(vm.accountId)
                            val current = bookmarks[identity]?.apply(work) ?: work
                            NovelListItem(
                                work = current,
                                rank = index.takeIf { rank },
                                likedBusy = identity in busy,
                                onLike = { vm.run { vm.bookmark(current) } },
                                onClick = {
                                    vm.record(current)
                                    navigate(Detail(current))
                                },
                            )
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
                    contentPadding = PaddingValues(PixivSpacing.content),
                    modifier = Modifier.fillMaxSize().testTag("feedGrid"),
                ) {
                    if (header != null)
                        item(span = StaggeredGridItemSpan.FullLine, key = "feed_header") {
                            header()
                        }
                    items(items.itemCount, key = items.itemKey { "${it.type}_${it.id}" }) { index ->
                        items[index]?.let { work ->
                            val identity = work.identity(vm.accountId)
                            val current = bookmarks[identity]?.apply(work) ?: work
                            WorkCard(
                                current,
                                index.takeIf { rank },
                                likedBusy = identity in busy,
                                showMetadata = showFeedMetadata,
                                onLike = { vm.run { vm.bookmark(current) } },
                            ) {
                                vm.record(current)
                                navigate(Detail(current))
                            }
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        FeedAppendState(items.loadState.append, items.itemCount, items::retry)
                    }
                }
        }
    }
}

@Composable
private fun NovelListItem(
    work: Work,
    rank: Int?,
    likedBusy: Boolean,
    onLike: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
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
                if (rank != null)
                    Text("#${rank + 1}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                Text(work.title, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
            }
        },
        supportingContent = {
            Text(work.user.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            IconButton(onClick = onLike, enabled = !likedBusy,
                modifier = Modifier.testTag("like_${work.type}_${work.id}")) {
                AppIcon(
                    if (work.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                    if (work.is_bookmarked) "取消喜欢 ${work.title}" else "喜欢 ${work.title}",
                    tint = if (work.is_bookmarked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun FeedAppendState(state: LoadState, count: Int, retry: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    when (state) {
        is LoadState.Loading ->
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(Modifier.size(32.dp))
            }
        is LoadState.Error -> TextButton(retry, Modifier.fillMaxWidth()) {
            Text(strings.getString(R.string.ui_0aa214d301))
        }
        else -> if (count > 0)
            Text(strings.getString(R.string.ui_5f3621612f),
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun FeedGridStatus(header: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    if (header == null) content()
    else
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(horizontal = PixivSpacing.content)) { header() }
            Box(Modifier.weight(1f)) { content() }
        }
}

@Composable
fun WorkCard(
    work: Work,
    rank: Int? = null,
    likedBusy: Boolean = false,
    showMetadata: Boolean = true,
    onLike: () -> Unit,
    onClick: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val imageShape = MaterialTheme.shapes.small
    val badgeShape = MaterialTheme.shapes.extraSmall

    Column(
        Modifier.clip(imageShape)
            .clickable(onClick = onClick)
            .semantics {
                if (!showMetadata) contentDescription = "${work.title}，${work.user.name}"
            }
            .padding(bottom = if (showMetadata) 2.dp else 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(imageShape)) {
            WorkImage(
                work,
                Modifier.fillMaxWidth().aspectRatio(if (work.isNovel) .9f else work.aspect),
            )
            val labels = buildList {
                if (rank != null) add("${rank + 1}")
                if (work.illust_ai_type == 2) add("AI")
                if (work.x_restrict > 0) add(if (work.x_restrict == 2) "R18G" else "R18")
            }
            if (labels.isNotEmpty())
                Row(
                    Modifier.align(Alignment.TopEnd).padding(WorkImageBadgeInset),
                    horizontalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                ) {
                    labels.forEach { label ->
                        Surface(
                            shape = badgeShape,
                            color = Color.Black.copy(alpha = .52f),
                            contentColor = Color.White,
                        ) {
                            Text(
                                label,
                                Modifier.heightIn(min = 32.dp).wrapContentHeight()
                                    .padding(horizontal = PixivSpacing.compact),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            if (work.page_count > 1 || work.type == "ugoira" || work.isNovel)
                Surface(
                    Modifier.align(Alignment.TopStart).padding(WorkImageBadgeInset),
                    shape = badgeShape,
                    color = Color.Black.copy(alpha = .52f),
                    contentColor = Color.White,
                ) {
                    Text(
                        if (work.isNovel) strings.getString(R.string.ui_6eb705b4ce)
                        else if (work.type == "ugoira") strings.getString(R.string.ui_de9dcfdf88)
                        else "${work.page_count}P",
                        Modifier.heightIn(min = 32.dp).wrapContentHeight()
                            .padding(horizontal = PixivSpacing.compact),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Surface(
                    onClick = onLike,
                    enabled = !likedBusy,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(WorkImageBadgeInset)
                        .size(32.dp)
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
                        AppIcon(
                            if (work.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                            null,
                            Modifier.size(20.dp),
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
    if (n >= 10000) "%.1fw".format(n / 10000f)
    else if (n >= 1000) "%.1fk".format(n / 1000f) else n.toString()

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
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (action != null) Button(onAction) { Text(action) }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    val word = rememberTextFieldState()
    val searchState = rememberSearchBarState()
    val searchScope = rememberCoroutineScope()
    var submitted by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("work") }
    var sort by rememberSaveable { mutableStateOf("date_desc") }
    var target by rememberSaveable { mutableStateOf("partial_match_for_tags") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var startDate by rememberSaveable { mutableStateOf("") }
    var endDate by rememberSaveable { mutableStateOf("") }
    var dateField by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var userLoading by remember { mutableStateOf(false) }
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    fun submit(value: String) {
        word.setTextAndPlaceCursorAtEnd(value)
        submitted = value.trim()
        vm.search(submitted)
        searchScope.launch { searchState.animateToCollapsed() }
        focus.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(demo, settings.contentKind) {
        if (demo && settings.contentKind == "novel")
            tags = Demo.works.filter { it.isNovel }.flatMap { it.tags }.distinctBy { it.name }
        else if (demo)
            tags =
                listOf(
                    Tag(strings.getString(R.string.ui_3539adaa60)),
                    Tag(strings.getString(R.string.ui_1f68f47fa2)),
                    Tag(strings.getString(R.string.ui_b0de623882)),
                    Tag(strings.getString(R.string.ui_0b5c557e9d)),
                    Tag(strings.getString(R.string.ui_6a0b30d361)),
                )
        else if (settings.contentKind == "illust")
            runCatching { vm.tags() }.onSuccess { tags = it }
        else tags = emptyList()
    }
    LaunchedEffect(submitted, kind) {
        if (kind == "user" && submitted.isNotBlank()) {
            userLoading = true
            error = null
            try {
                users = vm.searchUsers(submitted)
            } catch (e: Exception) {
                error = e.message
            }
            userLoading = false
        }
    }
    val searchField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = word,
            searchBarState = searchState,
            onSearch = ::submit,
            placeholder = { Text(strings.getString(R.string.ui_f15043c361)) },
            leadingIcon = { AppIcon(Glyph.Search, null) },
            trailingIcon = {
                if (word.text.isNotEmpty())
                    IconButton({
                        word.setTextAndPlaceCursorAtEnd("")
                        submitted = ""
                    }) {
                        AppIcon(Glyph.Close, strings.getString(R.string.ui_7b15e5e8e7))
                    }
            },
        )
    }
    Column {
        ScreenBar(
            strings.getString(R.string.ui_f04090805c),
            back = back,
            actions = {
                IconButton({ filter = true }) {
                    AppIcon(Glyph.Settings, strings.getString(R.string.ui_1c31f74a1d))
                }
            },
        )
        SearchBar(
            state = searchState,
            inputField = searchField,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        ChoiceChips(
            kind,
            listOf(
                "work" to strings.getString(R.string.ui_f394cdc91d),
                "user" to strings.getString(R.string.ui_698bea5124),
            ),
            { kind = it },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (submitted.isNotEmpty()) {
            if (kind == "user") {
                if (userLoading) LoadingState()
                else if (error != null)
                    EmptyState(
                        strings.getString(R.string.ui_b9786d51c3),
                        error!!,
                        action = strings.getString(R.string.ui_e2d53a6d3a),
                    ) {
                        submit(submitted)
                    }
                else
                    LazyColumn {
                        items(users, key = { it.id }) { user ->
                            UserRow(user, { navigate(Author(user)) })
                        }
                    }
            } else
                FeedGrid(
                    FeedSpec(
                        section = "search",
                        kind = settings.contentKind,
                        word = submitted,
                        sort = sort,
                        target = target,
                        startDate = startDate,
                        endDate = endDate,
                    ),
                    vm,
                    navigate,
                    Modifier.weight(1f),
                )
        } else
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.getString(R.string.ui_b6f4afaf6d),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton({ vm.clearSearch() }) {
                                Text(strings.getString(R.string.ui_7b15e5e8e7))
                            }
                        }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { h ->
                                SuggestionChip(
                                    { submit(h.word) },
                                    label = { Text(h.word) },
                                    icon = { AppIcon(Glyph.History, null, Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        strings.getString(R.string.ui_2fd71ef4d8),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                { submit(tag.name) },
                                label = { Text("# ${tag.translated_name ?: tag.name}") },
                            )
                        }
                    }
                }
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                strings.getString(R.string.ui_6de6049a1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                strings.getString(R.string.ui_d26f35cd03),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
    }
    ExpandedFullScreenSearchBar(state = searchState, inputField = searchField) {
        history.take(8).forEach { entry ->
            ListItem(
                leadingContent = { AppIcon(Glyph.History, null) },
                modifier = Modifier.clickable { submit(entry.word) },
            ) {
                Text(entry.word)
            }
        }
    }
    if (filter)
        AlertDialog(
            onDismissRequest = { filter = false },
            title = { Text(strings.getString(R.string.ui_1c31f74a1d)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        strings.getString(R.string.ui_dc35af8d69),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    listOf(
                            "date_desc" to strings.getString(R.string.ui_615547dc71),
                            "date_asc" to strings.getString(R.string.ui_5196834bd3),
                        )
                        .let { list ->
                            (if (active?.premium == true)
                                    list +
                                        listOf(
                                            "popular_desc" to
                                                strings.getString(R.string.ui_296adf9512)
                                        )
                                else list)
                                .forEach { (key, title) ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { sort = key },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(sort == key, { sort = key })
                                        Text(title)
                                    }
                                }
                        }
                    Text(
                        strings.getString(R.string.ui_6a05dbdc88),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    listOf(
                            "partial_match_for_tags" to strings.getString(R.string.ui_f9ba0caac3),
                            "exact_match_for_tags" to strings.getString(R.string.ui_74f182cacf),
                            "title_and_caption" to strings.getString(R.string.ui_96a2fad9d9),
                        )
                        .forEach { (key, title) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { target = key },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(target == key, { target = key })
                                Text(title)
                            }
                        }
                    Text(
                        strings.getString(R.string.search_date_range),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ dateField = "start" }, Modifier.weight(1f)) {
                            Text(
                                startDate.ifEmpty { strings.getString(R.string.search_start_date) }
                            )
                        }
                        OutlinedButton({ dateField = "end" }, Modifier.weight(1f)) {
                            Text(endDate.ifEmpty { strings.getString(R.string.search_end_date) })
                        }
                    }
                    if (startDate.isNotEmpty() || endDate.isNotEmpty())
                        TextButton({
                            startDate = ""
                            endDate = ""
                        }) {
                            Text(strings.getString(R.string.search_clear_dates))
                        }
                }
            },
            confirmButton = {
                TextButton({ filter = false }) { Text(strings.getString(R.string.ui_33246f6a5e)) }
            },
        )
    dateField?.let { field ->
        val initial = if (field == "start") startDate else endDate
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    initial
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            java.time.LocalDate.parse(it)
                                .atStartOfDay(java.time.ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                        }
            )
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { millis ->
                        val date =
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        if (field == "start") {
                            startDate = date
                            if (endDate.isNotEmpty() && endDate < date) endDate = ""
                        } else {
                            endDate = date
                            if (startDate.isNotEmpty() && startDate > date) startDate = ""
                        }
                    }
                    dateField = null
                }) {
                    Text(strings.getString(R.string.ui_33246f6a5e))
                }
            },
            dismissButton = {
                TextButton({ dateField = null }) { Text(strings.getString(R.string.ui_11d0241540)) }
            },
        ) {
            DatePicker(state, showModeToggle = false)
        }
    }
}

@Composable
fun UserRow(
    user: User,
    onClick: () -> Unit,
    segment: Pair<Int, Int>? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = { Text(user.name) }
    val supporting: @Composable () -> Unit = {
        Text("@${user.account.ifEmpty {user.id.toString()}}")
    }
    val leading: @Composable () -> Unit = { Avatar(user) }
    if (segment != null)
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(segment.first, segment.second),
            content = content,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = action,
        )
    else
        ListItem(
            onClick = onClick,
            content = content,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = action,
        )
}

@Composable
fun Avatar(user: User, modifier: Modifier = Modifier) {
    if (user.profile_image_urls.medium.isNotEmpty())
        AsyncImage(
            user.profile_image_urls.medium,
            user.name,
            modifier.size(48.dp).clip(CircleShape),
        )
    else
        Surface(
            modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    user.name.take(1).ifEmpty { "P" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
}
