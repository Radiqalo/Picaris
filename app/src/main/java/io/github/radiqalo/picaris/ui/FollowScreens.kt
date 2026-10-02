package io.github.radiqalo.picaris.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun FollowScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            Tab(
                selected = pager.currentPage == 0,
                onClick = { scope.launch { pager.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.follow_tab_activity)) },
            )
            Tab(
                selected = pager.currentPage == 1,
                onClick = { scope.launch { pager.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.follow_tab_updates)) },
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                FeedGrid(
                    FeedSpec(section = "follow", kind = settings.contentKind),
                    vm,
                    navigate,
                    Modifier.fillMaxSize(),
                    topPadding = PixivSpacing.content,
                )
            } else {
                FollowedSeriesScreen(vm, navigate)
            }
        }
    }
}

@Composable
private fun FollowedSeriesScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    FollowedSeriesList(
        vm,
        navigate,
        if (settings.contentKind == "novel") "novel" else "manga",
    )
}

@Composable
private fun FollowedSeriesList(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    kind: String,
) {
    val entries =
        remember(
            vm.accountId,
            kind,
        ) { vm.followedSeries(kind) }.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val padding =
        PaddingValues(
            horizontal = PixivSpacing.content,
            vertical = PixivSpacing.compact,
        )
    when {
        entries.loadState.refresh is LoadState.Loading && entries.itemCount == 0 ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingState() }
        entries.loadState.refresh is LoadState.Error && entries.itemCount == 0 -> {
            val error = (entries.loadState.refresh as LoadState.Error).error
            EmptyState(
                stringResource(R.string.follow_tab_updates),
                error.message ?: stringResource(R.string.ui_73a13d2b99),
                materialSymbol(MaterialSymbol.Book),
                stringResource(R.string.ui_e2d53a6d3a),
            ) { entries.retry() }
        }
        entries.itemCount == 0 ->
            EmptyState(
                stringResource(R.string.follow_tab_updates),
                stringResource(R.string.follow_updates_empty),
                materialSymbol(MaterialSymbol.Book),
            )
        else ->
            LazyColumn(
                state = listState,
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
                    entries[index]?.let { series ->
                        val resolvedWorkCount by produceState(
                            initialValue = series.workCount,
                            series.kind,
                            series.id,
                            vm.accountId,
                        ) {
                            if (value <= 0) {
                                value =
                                    runCatching {
                                        vm.seriesDetails(series.kind, series.id).workCount
                                    }.getOrDefault(0)
                            }
                        }
                        Surface(
                            onClick = {
                                navigate(
                                    Collection(
                                        series.title,
                                        "series",
                                        series.kind,
                                        series.id,
                                        watched = true,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(124.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(
                                            88.dp,
                                        ).fillMaxHeight()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    AsyncImage(
                                        model = series.coverUrl,
                                        contentDescription = "${series.title} 封面",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                }
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(
                                            start = 12.dp,
                                            end = 4.dp,
                                            top = 8.dp,
                                            bottom = 8.dp,
                                        ),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        series.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            series.user.name.ifBlank { "未知作者" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            stringResource(
                                                R.string.follow_updates_count,
                                                resolvedWorkCount,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                AppIcon(
                                    materialSymbol(MaterialSymbol.ChevronRight),
                                    null,
                                    Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    when (val state = entries.loadState.append) {
                        is LoadState.Loading -> LoadingState()
                        is LoadState.Error ->
                            TextButton(onClick = entries::retry) {
                                Text(state.error.message ?: stringResource(R.string.ui_73a13d2b99))
                            }
                        else -> Spacer(Modifier.height(PixivSpacing.content))
                    }
                }
            }
    }
}

@Composable
fun BookmarkScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var private by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(
            stringResource(R.string.ui_d07cee786a),
            back = back,
            scrollBehavior = null,
            modifier = Modifier.aboveWorkTransition(),
            actions = {
                val visibilityFeedback = toggleFeedback()
                IconToggleButton(
                    checked = private,
                    onCheckedChange = { isPrivate ->
                        visibilityFeedback(isPrivate)
                        private = isPrivate
                    },
                ) {
                    FeedbackIcon(
                        materialSymbol(
                            if (private) MaterialSymbol.Lock else MaterialSymbol.LockOpen,
                        ),
                        if (private) "当前私人收藏，切换到公开收藏" else "当前公开收藏，切换到私人收藏",
                        selected = private,
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
    val settings by vm.settings.collectAsStateWithLifecycle()

    var rankingDate by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column {
        if (route.section == "ranking" || route.section == "search") {
            TopAppBar(
                modifier = Modifier.aboveWorkTransition(),
                title = {
                    Text(
                        if (route.section == "search") {
                            "#${route.title}"
                        } else if (rankingDate.isNotEmpty()) {
                            "${route.title} · $rankingDate"
                        } else {
                            route.title
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(
                            materialSymbol(MaterialSymbol.ArrowBack),
                            stringResource(R.string.ui_11d0241540),
                        )
                    }
                },
                actions = {
                    if (route.section == "ranking") {
                        IconButton(onClick = { showDatePicker = true }) {
                            AppIcon(materialSymbol(MaterialSymbol.Calendar), "选择榜单日期")
                        }
                    }
                },
                scrollBehavior =
                    if (route.section ==
                        "search"
                    ) {
                        null
                    } else {
                        LocalAppBarScrollBehavior.current
                    },
            )
        } else if (route.section != "series") {
            ScreenBar(
                route.title,
                back = back,
                scrollBehavior = null,
                modifier = Modifier.aboveWorkTransition(),
            )
        }
        if (route.section == "series") {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FeedGrid(
                    FeedSpec(section = "series", kind = route.kind, userId = route.userId),
                    vm,
                    navigate,
                    Modifier.fillMaxSize(),
                    header = { SeriesHeader(route, vm, navigate) },
                    scrollHeaderWhileEmpty = true,
                    topPadding = 0.dp,
                )
                SeriesOverlayActions(route, back, Modifier.align(Alignment.TopCenter))
            }
        } else if (route.section == "ranking") {
            RankingPages(vm, navigate, Modifier.weight(1f), rankingDate)
        } else {
            FeedGrid(
                FeedSpec(
                    section = route.section,
                    kind =
                        if (route.section == "series" || route.section == "related") {
                            route.kind
                        } else {
                            settings.contentKind
                        },
                    userId = route.userId,
                    word = route.word,
                ),
                vm,
                navigate,
                Modifier.weight(1f),
                leadingWork = route.tagCover,
            )
        }
    }
    if (showDatePicker) {
        val today = remember { java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")) }
        val lastDate = today.minusDays(1)
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    java.time.LocalDate
                        .parse(rankingDate.ifEmpty { lastDate.toString() })
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            java.time.Instant
                                .ofEpochMilli(utcTimeMillis)
                                .atZone(java.time.ZoneOffset.UTC)
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
                            rankingDate =
                                java.time.Instant
                                    .ofEpochMilli(it)
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toString()
                        }
                        showDatePicker = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("查看") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        rankingDate = ""
                        showDatePicker = false
                    }) { Text("最新榜单") }
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            },
        ) {
            DatePicker(
                state,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    }
}

@Composable
private fun RankingPages(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    modifier: Modifier,
    date: String,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val modes =
        listOf(
            "day" to stringResource(R.string.ui_f8c9b6d5d8),
            "week" to stringResource(R.string.ui_5e00476f4e),
            "month" to stringResource(R.string.ui_0b554f5235),
            "day_male" to stringResource(R.string.ui_fbe010365d),
            "day_female" to stringResource(R.string.ui_b57634d889),
            "week_rookie" to stringResource(R.string.ui_8b7adaf587),
        )
    val pager = rememberPagerState(pageCount = { modes.size })
    val scope = rememberCoroutineScope()
    Column(modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pager.currentPage,
            edgePadding = 0.dp,
            modifier = Modifier.aboveWorkTransition(),
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
                FeedSpec(
                    section = "ranking",
                    kind = settings.contentKind,
                    mode = modes[page].first,
                    date = date,
                ),
                vm,
                navigate,
                Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    route: Collection,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    var details by remember(route.kind, route.userId, vm.accountId) {
        mutableStateOf(
            vm.cachedSeriesDetails(route.kind, route.userId)?.let {
                if (route.watched) it.copy(isWatched = true) else it
            },
        )
    }
    var loading by remember(route.kind, route.userId, vm.accountId) {
        mutableStateOf(vm.cachedSeriesDetails(route.kind, route.userId) == null)
    }
    var watchBusy by remember(route.kind, route.userId, vm.accountId) { mutableStateOf(false) }
    var error by remember(route.kind, route.userId, vm.accountId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(route.kind, route.userId, vm.accountId) {
        if (details == null) {
            loading = true
            error = null
            runCatching { vm.seriesDetails(route.kind, route.userId) }
                .onSuccess { details = if (route.watched) it.copy(isWatched = true) else it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(bottom = PixivSpacing.content),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
    ) {
        if (details?.coverUrl?.isNotEmpty() == true) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val edgeToEdgeWidth = maxWidth + PixivSpacing.content * 2
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 300.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AsyncImage(
                        model = details?.coverUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier =
                            Modifier
                                .requiredWidth(edgeToEdgeWidth)
                                .heightIn(min = 190.dp, max = 300.dp),
                    )
                }
            }
        } else {
            Spacer(
                Modifier.fillMaxWidth().height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                ),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
        ) {
            Text(
                details?.title?.ifBlank { route.title } ?: route.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            details?.user?.takeIf { it.id > 0 }?.let { user ->
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { navigate(Author(user)) },
                )
            }
            details?.let { series ->
                Button(
                    onClick = {
                        if (!watchBusy) {
                            scope.launch {
                                watchBusy = true
                                runCatching {
                                    vm.setSeriesWatched(
                                        series.kind,
                                        series.id,
                                        !series.isWatched,
                                    )
                                }.onSuccess {
                                    details =
                                        series.copy(isWatched = !series.isWatched)
                                }.onFailure { error = it.message }
                                watchBusy = false
                            }
                        }
                    },
                    enabled = !watchBusy,
                ) {
                    Text(if (series.isWatched) "取消追更" else "追更")
                }
                if (series.caption.isNotBlank()) {
                    Text(
                        series.caption,
                        modifier = Modifier.fillMaxWidth().padding(top = PixivSpacing.compact),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SeriesOverlayActions(
    route: Collection,
    back: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PixivSpacing.tight, vertical = PixivSpacing.tight)
            .workTransitionControls(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = back) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .38f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    materialSymbol(MaterialSymbol.ArrowBack),
                    "返回",
                    Modifier.size(24.dp),
                    Color.White,
                )
            }
        }
        IconButton(
            onClick = {
                val url =
                    if (route.kind == "novel") {
                        "https://www.pixiv.net/novel/series/${route.userId}"
                    } else {
                        "https://www.pixiv.net/user/series/${route.userId}"
                    }
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, url),
                        "分享系列",
                    ),
                )
            },
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .38f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    materialSymbol(MaterialSymbol.Share),
                    "分享",
                    Modifier.size(24.dp),
                    Color.White,
                )
            }
        }
    }
}
