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
import androidx.compose.ui.unit.Dp
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
    val select: (String) -> Unit = { key -> if (selected != key) { feedback(); onSelect(key) } }
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
                        colors = if (equalWidth) ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) else ToggleButtonDefaults.colors(),
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
                            AppIcon(Glyph.Check, null, Modifier.size(16.dp))
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
    val trendResult by produceState<List<TrendingTag>?>(vm.cachedTrendingTags(), vm.accountId, demo, settings.contentKind, settings.contentFilter()) {
        value = vm.cachedTrendingTags()
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
    val authorResult by produceState<List<UserPreview>?>(vm.cachedRecommendedAuthors(), vm.accountId, demo, settings.contentKind, settings.contentFilter()) {
        value = vm.cachedRecommendedAuthors()
        value = if (settings.contentKind == "novel") emptyList() else try {
            vm.recommendedAuthors()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
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
                                        modifier = Modifier.align(Alignment.BottomStart)
                                            .padding(start = 12.dp, bottom = 4.dp, end = 12.dp),
                                        border = null,
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
                    trends.take(3).forEach { trend -> DiscoveryTagWorks(trend.tag, vm, navigate) }

                }
                if (settings.contentKind != "novel") {
                    Text(strings.getString(R.string.discover_artists), style = MaterialTheme.typography.titleLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                        if (authorResult == null) items(3) {
                            Spacer(Modifier.width(260.dp).height(300.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large))
                        } else items(authorResult.orEmpty(), key = { it.user.id }) { preview ->
                            DiscoveryAuthorCard(preview, vm, navigate)
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
private fun DiscoveryTagWorks(tag: Tag, vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spec = remember(tag.name) { FeedSpec(section = "search", word = tag.name) }
    val flow = remember(spec, vm.accountId, settings.contentFilter()) { vm.feed(spec) }
    val works = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${tag.translated_name ?: tag.name} · 相关作品", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { navigate(Collection(tag.name, "search", word = tag.name)) }) { Text("查看全部") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
            if (works.itemCount == 0 && works.loadState.refresh is LoadState.Loading) items(5) {
                Spacer(Modifier.width(144.dp).height(180.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
            } else items(minOf(5, works.itemCount), key = { index -> works.peek(index)?.id ?: -index.toLong() }) { index ->
                works[index]?.let { work ->
                    val identity = work.identity(vm.accountId)
                    val current = bookmarks[identity]?.apply(work) ?: work
                    Box(Modifier.width(180.dp * current.aspect)) {
                        WorkCard(current, likedBusy = identity in busy,
                            showMetadata = settings.showHomeMetadata, sharedTransition = false,
                            onLike = { vm.run { vm.bookmark(current) } },
                            onClick = { vm.record(current); navigate(Detail(current)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryAuthorCard(preview: UserPreview, vm: AppViewModel, navigate: (NavKey) -> Unit) {
    var user by remember(preview.user) { mutableStateOf(preview.user) }
    var busy by remember { mutableStateOf(false) }
    val background by produceState<String?>(null, preview.user.id, vm.accountId) {
        value = try { vm.authorDetails(preview.user).profile.background_image_url }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    ElevatedCard(onClick = { navigate(Author(user)) }, modifier = Modifier.width(260.dp)) {
        Box(Modifier.fillMaxWidth().height(96.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            background?.takeIf { it.isNotBlank() }?.let { url ->
                coil3.compose.AsyncImage(url, null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        Column(Modifier.fillMaxWidth().padding(PixivSpacing.content),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
            Avatar(user, Modifier.size(64.dp), sharedTransition = true)
            Text(user.name, Modifier.authorTransition(user.id, "name"), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (user.comment.isNotBlank()) Text(user.comment, maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = {
                vm.run {
                    busy = true
                    try { user = vm.follow(user) } finally { busy = false }
                }
            }, enabled = !busy) { Text(if (user.is_followed) "已关注" else "关注") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.tight)) {
            preview.illusts.take(3).forEach { work ->
                WorkImage(work, Modifier.weight(1f).height(84.dp)
                    .clickable { vm.record(work); navigate(Detail(work)) })
            }
        }
    }
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
    repeat(3) {
        Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
            Spacer(Modifier.width(160.dp).height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                items(5) {
                    Spacer(Modifier.width(144.dp).height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
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

    var rankingDate by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column {
        if (route.section == "ranking" || route.section == "search")
            TopAppBar(
                title = {
                    Text(
                        if (route.section == "search") "#${route.title}"
                        else if (rankingDate.isNotEmpty()) "${route.title} · $rankingDate"
                        else route.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540))
                    }
                },
                actions = {
                    if (route.section == "ranking")
                        IconButton(onClick = { showDatePicker = true }) {
                            AppIcon(materialSymbol(MaterialSymbol.Calendar), "选择榜单日期")
                        }
                },
                scrollBehavior = LocalAppBarScrollBehavior.current,
            )
        else ScreenBar(route.title, back = back)
        if (route.section == "ranking")
            RankingPages(vm, navigate, Modifier.weight(1f), rankingDate)
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
    if (showDatePicker) {
        val today = remember { java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")) }
        val lastDate = today.minusDays(1)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.parse(rankingDate.ifEmpty { lastDate.toString() })
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate() <= lastDate
                override fun isSelectableYear(year: Int): Boolean = year <= lastDate.year
            },
        )
        DateSelectionSheet(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            rankingDate = java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showDatePicker = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("查看") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { rankingDate = ""; showDatePicker = false }) { Text("最新榜单") }
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            },
        ) { DatePicker(state, showModeToggle = false) }
    }

}

@Composable
private fun RankingPages(vm: AppViewModel, navigate: (NavKey) -> Unit, modifier: Modifier, date: String) {
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
                FeedSpec(section = "ranking", kind = settings.contentKind, mode = modes[page].first, date = date),
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
    topPadding: Dp = PixivSpacing.content,
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
                    contentPadding = PaddingValues(start = PixivSpacing.content, end = PixivSpacing.content,
                        top = topPadding, bottom = PixivSpacing.content),
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
                    contentPadding = PaddingValues(start = PixivSpacing.content, end = PixivSpacing.content,
                        top = topPadding, bottom = PixivSpacing.content),
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
    sharedTransition: Boolean = true,
    onLike: () -> Unit,
    onClick: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val imageShape = MaterialTheme.shapes.small
    val badgeShape = MaterialTheme.shapes.extraSmall
    val likeInteraction = remember { MutableInteractionSource() }
    val feedback = toggleFeedback()

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
                sharedTransition = sharedTransition,
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
                    onClick = { feedback(!work.is_bookmarked); onLike() },
                    interactionSource = likeInteraction,
                    enabled = !likedBusy,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(WorkImageBadgeInset)
                        .size(32.dp)
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
                            if (work.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
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
fun SearchScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit, initialQuery: String? = null) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    val word = rememberTextFieldState(initialQuery.orEmpty())
    val searchState = rememberSearchBarState()
    val searchScope = rememberCoroutineScope()
    var submitted by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    val resultPager = rememberPagerState(pageCount = { 2 })
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
    var userRetry by remember { mutableIntStateOf(0) }
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var jumping by remember { mutableStateOf(false) }
    fun submit(value: String, selectedJump: SearchJump? = null) {
        if (jumping || value.isBlank()) return
        val query = value.trim()
        word.setTextAndPlaceCursorAtEnd(query)
        searchScope.launch { searchState.animateToCollapsed() }
        focus.clearFocus()
        keyboard?.hide()
        val jump = selectedJump ?: parseSearchJump(query, settings.contentKind == "novel")
        if (jump != null) {
            jumping = true
            vm.run {
                try {
                    when (jump) {
                        is SearchJump.Artwork -> navigate(Detail(vm.detail(Work(
                            id = jump.id, type = if (jump.novel) "novel" else "illust",
                        ))))
                        is SearchJump.Artist -> navigate(Author(vm.user(User(id = jump.id))))
                    }
                } finally {
                    jumping = false
                }
            }
        } else {
            vm.search(query)
            if (initialQuery == null) navigate(SearchResults(query))
            else submitted = query
        }
    }
    LaunchedEffect(demo, settings.contentKind) {
        if (initialQuery != null) return@LaunchedEffect
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
    LaunchedEffect(submitted, vm.accountId, userRetry) {
        if (submitted.isNotBlank()) {
            userLoading = true
            error = null
            try {
                users = vm.searchUsers(submitted)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                userLoading = false
            }
        }
    }
    val searchField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = word,
            searchBarState = searchState,
            onSearch = { submit(it) },
            placeholder = { Text("关键词、ID 或 Pixiv 链接") },
            leadingIcon = { AppIcon(Glyph.Search, null) },
            trailingIcon = {
                if (word.text.isNotEmpty())
                    IconButton({
                        word.setTextAndPlaceCursorAtEnd("")
                        if (initialQuery == null) submitted = ""
                    }) {
                        AppIcon(Glyph.Close, strings.getString(R.string.ui_7b15e5e8e7))
                    }
            },
        )
    }
    val idSuggestions: @Composable () -> Unit = {
        val input = word.text.toString().trim()
        val id = input.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toLongOrNull()?.takeIf { it > 0 }
        if (id != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            ) {
                SuggestionChip(
                    onClick = { submit(input, SearchJump.Artist(id)) },
                    enabled = !jumping,
                    label = { Text("作者 ID") },
                    icon = { AppIcon(Glyph.Person, null) },
                )
                SuggestionChip(
                    onClick = { submit(input, SearchJump.Artwork(id, settings.contentKind == "novel")) },
                    enabled = !jumping,
                    label = { Text("作品 ID") },
                    icon = { AppIcon(materialSymbol(MaterialSymbol.Image), null) },
                )
            }
        }
    }
    Column {
        ScreenBar(
            if (initialQuery == null) strings.getString(R.string.ui_f04090805c) else "搜索结果",
            back = back,
            actions = {
                if (initialQuery != null) IconButton({ filter = true }) {
                    AppIcon(Glyph.Settings, strings.getString(R.string.ui_1c31f74a1d))
                }
            },
        )
        SearchBar(
            state = searchState,
            inputField = searchField,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        idSuggestions()
        if (jumping) LoadingState()
        else if (submitted.isNotEmpty()) {
            PrimaryTabRow(selectedTabIndex = resultPager.currentPage) {
                listOf(
                    strings.getString(R.string.ui_f394cdc91d),
                    strings.getString(R.string.ui_698bea5124),
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = resultPager.currentPage == index,
                        onClick = { searchScope.launch { resultPager.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }
            HorizontalPager(state = resultPager, modifier = Modifier.weight(1f)) { page ->
                if (page == 1) {
                    if (userLoading) LoadingState()
                    else if (error != null)
                        EmptyState(
                            strings.getString(R.string.ui_b9786d51c3),
                            error!!,
                            action = strings.getString(R.string.ui_e2d53a6d3a),
                        ) {
                            userRetry++
                        }
                    else if (users.isEmpty())
                        EmptyState("没有找到作者", "", Glyph.Person)
                    else
                        LazyColumn(Modifier.fillMaxSize()) {
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
                        Modifier.fillMaxSize(),
                    )
            }
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
            }
    }
    ExpandedFullScreenSearchBar(state = searchState, inputField = searchField) {
        idSuggestions()
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
        ActionSheet(
            onDismissRequest = { filter = false },
            title = { Text(strings.getString(R.string.ui_1c31f74a1d)) },
            text = {
                Column {
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
        DateSelectionSheet(
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
    val content: @Composable () -> Unit = { Text(user.name, Modifier.authorTransition(user.id, "name")) }
    val supporting: @Composable () -> Unit = {
        Text("@${user.account.ifEmpty {user.id.toString()}}")
    }
    val leading: @Composable () -> Unit = { Avatar(user, sharedTransition = true) }
    if (segment != null)
        SegmentedListItem(
            colors = PixivContainerDefaults.listItemColors(),
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
fun Avatar(user: User, modifier: Modifier = Modifier, sharedTransition: Boolean = false) {
    val avatarModifier = modifier.size(48.dp).authorTransition(user.id, "avatar", sharedTransition).clip(CircleShape)
    if (user.profile_image_urls.medium.isNotEmpty())
        AsyncImage(
            user.profile_image_urls.medium,
            user.name,
            avatarModifier,
        )
    else
        Surface(
            avatarModifier,
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
