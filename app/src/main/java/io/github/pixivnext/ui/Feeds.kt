package io.github.pixivnext.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
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

private val WorkImageBadgeInset = 8.dp
private val WorkImageBadgeShape = RoundedCornerShape(10.dp)

val LocalAppBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@Composable
fun ScrollingScreen(content: @Composable () -> Unit) {
    val behavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    CompositionLocalProvider(LocalAppBarScrollBehavior provides behavior) {
        Box(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) { content() }
    }
}

@Composable
fun ScreenBar(
    title: String,
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        scrollBehavior = LocalAppBarScrollBehavior.current,
        navigationIcon = {
            if (back != null)
                IconButton(back) { AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540)) }
        },
        actions = actions,
    )
}

@Composable
fun KindTabs(selected: String, onSelect: (String) -> Unit, novel: Boolean = true) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val kinds =
        if (novel)
            listOf(
                "illust" to strings.getString(R.string.ui_2a47176e3d),
                "manga" to strings.getString(R.string.ui_6a0b30d361),
                "novel" to strings.getString(R.string.ui_6eb705b4ce),
            )
        else
            listOf(
                "illust" to strings.getString(R.string.ui_2a47176e3d),
                "manga" to strings.getString(R.string.ui_6a0b30d361),
            )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { (key, label) ->
            FilterChip(
                selected == key,
                { onSelect(key) },
                label = { Text(label) },
                leadingIcon =
                    if (selected == key) {
                        { AppIcon(Glyph.Check, null, Modifier.size(16.dp)) }
                    } else null,
            )
        }
    }
}

@Composable
fun DiscoverScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, search: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var kind by rememberSaveable { mutableStateOf("illust") }
    val demo by vm.demo.collectAsStateWithLifecycle()
    Column {
        ScreenBar(
            strings.getString(R.string.ui_523e40a074) + if (demo) " · 演示" else "",
            actions = {
                IconButton(search) {
                    AppIcon(Glyph.Search, strings.getString(R.string.ui_f04090805c))
                }
                IconButton({ navigate(Utility("downloads")) }) {
                    AppIcon(Glyph.Download, strings.getString(R.string.ui_18df1a67a2))
                }
            },
        )
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = {
                    navigate(Collection(strings.getString(R.string.ui_d00981d6ce), "ranking", kind))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(Glyph.Rank, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column {
                        Text(
                            strings.getString(R.string.ui_d00981d6ce),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            Surface(
                onClick = search,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(
                        Glyph.Discover,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Column {
                        Text(
                            strings.getString(R.string.ui_5f16d9ef91),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
        KindTabs(kind, { kind = it })
        FeedGrid(FeedSpec(kind = kind), vm, navigate, Modifier.weight(1f))
    }
}

@Composable
fun FollowScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var kind by rememberSaveable { mutableStateOf("illust") }
    Column {
        ScreenBar(strings.getString(R.string.ui_e0fd2cee4c))
        KindTabs(kind, { kind = it })
        FeedGrid(FeedSpec(section = "follow", kind = kind), vm, navigate, Modifier.weight(1f))
    }
}

@Composable
fun BookmarkScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var kind by rememberSaveable { mutableStateOf("illust") }
    var private by rememberSaveable { mutableStateOf(false) }
    Column {
        ScreenBar(
            strings.getString(R.string.ui_d07cee786a),
            actions = {
                IconButton({ private = !private }) {
                    AppIcon(
                        if (private) Glyph.Person else Glyph.Heart,
                        if (private) strings.getString(R.string.ui_67c6787737)
                        else strings.getString(R.string.ui_373ffa6d97),
                    )
                }
            },
        )
        KindTabs(kind, { kind = it })
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                !private,
                { private = false },
                label = { Text(strings.getString(R.string.ui_dfe5a318ab)) },
            )
            FilterChip(
                private,
                { private = true },
                label = { Text(strings.getString(R.string.ui_82b464fc64)) },
            )
        }
        FeedGrid(
            FeedSpec(
                section = "bookmarks",
                kind = kind,
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

    var mode by rememberSaveable { mutableStateOf("day") }
    Column {
        ScreenBar(route.title, back = back)
        if (route.section == "ranking")
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        "day" to strings.getString(R.string.ui_f8c9b6d5d8),
                        "week" to strings.getString(R.string.ui_5e00476f4e),
                        "month" to strings.getString(R.string.ui_0b554f5235),
                        "day_male" to strings.getString(R.string.ui_fbe010365d),
                        "day_female" to strings.getString(R.string.ui_b57634d889),
                        "week_rookie" to strings.getString(R.string.ui_8b7adaf587),
                    )
                    .forEach { (key, title) ->
                        FilterChip(mode == key, { mode = key }, label = { Text(title) })
                    }
            }
        FeedGrid(
            FeedSpec(
                section = route.section,
                kind = route.kind,
                mode = mode,
                userId = route.userId,
                word = route.word,
            ),
            vm,
            navigate,
            Modifier.weight(1f),
            route.section == "ranking",
        )
    }
}

@Composable
fun FeedGrid(
    spec: FeedSpec,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    rank: Boolean = false,
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
    val grid = rememberLazyStaggeredGridState()
    val refreshing = items.loadState.refresh is LoadState.Loading
    val error = items.loadState.refresh as? LoadState.Error
    PullToRefreshBox(
        isRefreshing = refreshing && items.itemCount > 0,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxWidth(),
    ) {
        when {
            refreshing && items.itemCount == 0 -> LoadingState()
            error != null && items.itemCount == 0 ->
                EmptyState(
                    strings.getString(R.string.ui_590a4df471),
                    error.error.message ?: strings.getString(R.string.ui_73a13d2b99),
                    Glyph.Discover,
                    strings.getString(R.string.ui_e2d53a6d3a),
                ) {
                    items.retry()
                }
            items.itemCount == 0 ->
                EmptyState(
                    strings.getString(R.string.ui_37ce9e3518),
                    if (spec.section == "bookmarks") strings.getString(R.string.ui_408822a29e)
                    else strings.getString(R.string.ui_a588489241),
                    Glyph.Book,
                )
            else ->
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 16.dp,
                    contentPadding =
                        PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    modifier = Modifier.fillMaxSize().testTag("feedGrid"),
                ) {
                    items(items.itemCount, key = items.itemKey { "${it.type}_${it.id}" }) { index ->
                        items[index]?.let { work ->
                            val identity = work.identity(vm.accountId)
                            val current = bookmarks[identity]?.apply(work) ?: work
                            WorkCard(
                                current,
                                index.takeIf { rank },
                                likedBusy = identity in busy,
                                showMetadata =
                                    (spec.section != "recommended" && spec.section != "follow") ||
                                        settings.showHomeMetadata,
                                onLike = { vm.run { vm.bookmark(current) } },
                            ) {
                                vm.record(current)
                                navigate(Detail(current))
                            }
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        when (val append = items.loadState.append) {
                            is LoadState.Loading ->
                                Box(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator(Modifier.size(32.dp))
                                }
                            is LoadState.Error ->
                                TextButton({ items.retry() }, Modifier.fillMaxWidth()) {
                                    Text(strings.getString(R.string.ui_0aa214d301))
                                }
                            else ->
                                if (items.itemCount > 0)
                                    Text(
                                        strings.getString(R.string.ui_5f3621612f),
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                        }
                    }
                }
        }
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

    Column(
        Modifier.clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .semantics {
                if (!showMetadata) contentDescription = "${work.title}，${work.user.name}"
            }
            .padding(bottom = if (showMetadata) 12.dp else 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))) {
            WorkImage(
                work,
                Modifier.fillMaxWidth().aspectRatio(if (work.isNovel) .9f else work.aspect),
            )
            if (rank != null)
                Surface(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "${rank+1}",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            if (work.page_count > 1 || work.type == "ugoira" || work.isNovel)
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(WorkImageBadgeInset),
                    shape = WorkImageBadgeShape,
                    color = Color.Black.copy(alpha = .52f),
                    contentColor = Color.White,
                ) {
                    Text(
                        if (work.isNovel) strings.getString(R.string.ui_6eb705b4ce)
                        else if (work.type == "ugoira") strings.getString(R.string.ui_de9dcfdf88)
                        else "${work.page_count}P",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            Box(
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(WorkImageBadgeInset)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(enabled = !likedBusy, role = Role.Button, onClick = onLike)
                        .testTag("like_${work.type}_${work.id}")
                        .semantics {
                            contentDescription =
                                if (work.is_bookmarked) "取消喜欢 ${work.title}" else "喜欢 ${work.title}"
                            stateDescription = if (work.is_bookmarked) "已喜欢" else "未喜欢"
                        },
                contentAlignment = Alignment.BottomEnd,
            ) {
                Surface(
                    shape = WorkImageBadgeShape,
                    color = Color.Black.copy(alpha = .48f),
                    contentColor = Color.White,
                ) {
                    Row(
                        Modifier.heightIn(min = 28.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppIcon(
                            if (work.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                            null,
                            Modifier.size(16.dp),
                            tint = if (work.is_bookmarked) Color(0xFFFF80A2) else Color.White,
                        )
                        Text(
                            compact(work.total_bookmarks),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        if (showMetadata) {
            Text(
                work.title,
                Modifier.padding(top = 9.dp, start = 3.dp, end = 3.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                work.user.name,
                Modifier.padding(top = 3.dp, start = 3.dp, end = 3.dp),
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
    val strings = androidx.compose.ui.platform.LocalResources.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator()
            Text(
                strings.getString(R.string.ui_19fa805abc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Glyph.Discover,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                AppIcon(icon, null, Modifier.padding(24.dp).size(40.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (action != null) Button(onAction) { Text(action) }
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var word by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("illust") }
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
    fun submit(value: String) {
        word = value
        submitted = value.trim()
        vm.search(submitted)
        focus.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(Unit) {
        if (demo)
            tags =
                listOf(
                    Tag(strings.getString(R.string.ui_3539adaa60)),
                    Tag(strings.getString(R.string.ui_1f68f47fa2)),
                    Tag(strings.getString(R.string.ui_b0de623882)),
                    Tag(strings.getString(R.string.ui_0b5c557e9d)),
                    Tag(strings.getString(R.string.ui_6a0b30d361)),
                )
        else runCatching { vm.repo.tags(vm.accountId) }.onSuccess { tags = it }
    }
    LaunchedEffect(submitted, kind) {
        if (kind == "user" && submitted.isNotBlank()) {
            userLoading = true
            error = null
            try {
                users =
                    if (demo) Demo.works.map { it.user }
                    else vm.repo.searchUsers(vm.accountId, submitted)
            } catch (e: Exception) {
                error = e.message
            }
            userLoading = false
        }
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
        OutlinedTextField(
            word,
            { word = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text(strings.getString(R.string.ui_f15043c361)) },
            leadingIcon = { AppIcon(Glyph.Search, null) },
            trailingIcon = {
                if (word.isNotEmpty())
                    IconButton({
                        word = ""
                        submitted = ""
                    }) {
                        AppIcon(Glyph.Close, strings.getString(R.string.ui_7b15e5e8e7))
                    }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
            keyboardActions =
                androidx.compose.foundation.text.KeyboardActions(onSearch = { submit(word) }),
        )
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                    "illust" to strings.getString(R.string.ui_f394cdc91d),
                    "novel" to strings.getString(R.string.ui_6eb705b4ce),
                    "user" to strings.getString(R.string.ui_698bea5124),
                )
                .forEach { (key, label) ->
                    FilterChip(kind == key, { kind = key }, label = { Text(label) })
                }
        }
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
                        kind = kind,
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
                            TextButton({ vm.run { vm.dao.clearSearch(vm.accountId) } }) {
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
                        shape = RoundedCornerShape(24.dp),
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
                            (if (vm.auth.active?.premium == true)
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
fun UserRow(user: User, onClick: () -> Unit, action: @Composable (() -> Unit)? = null) {
    ListItem(
        onClick = onClick,
        content = { Text(user.name) },
        supportingContent = { Text("@${user.account.ifEmpty {user.id.toString()}}") },
        leadingContent = { Avatar(user) },
        trailingContent = action,
        modifier = Modifier,
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
