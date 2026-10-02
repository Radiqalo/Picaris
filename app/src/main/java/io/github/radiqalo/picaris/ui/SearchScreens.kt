package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: (() -> Unit)?,
    initialQuery: String? = null,
) {
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
    var tagSuggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var tagSuggestionError by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var userLoading by remember { mutableStateOf(false) }
    var userRetry by remember { mutableIntStateOf(0) }
    val history by vm.searchHistory.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    var jumping by remember { mutableStateOf(false) }

    fun submit(
        value: String,
        selectedJump: SearchJump? = null,
    ) {
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
                        is SearchJump.Artwork ->
                            navigate(
                                Detail(
                                    vm.detail(
                                        Work(
                                            id = jump.id,
                                            type = if (jump.novel) "novel" else "illust",
                                        ),
                                    ),
                                ),
                            )
                        is SearchJump.Artist -> navigate(Author(vm.user(User(id = jump.id))))
                    }
                } finally {
                    jumping = false
                }
            }
        } else {
            vm.search(query)
            if (initialQuery == null) {
                navigate(SearchResults(query))
            } else {
                submitted = query
            }
        }
    }
    LaunchedEffect(vm.accountId, settings.contentKind) {
        if (initialQuery != null) return@LaunchedEffect
        if (settings.contentKind == "illust") {
            tags = vm.cachedTags().orEmpty()
            runCatching { vm.tags() }.onSuccess { tags = it }
        } else {
            tags = emptyList()
        }
    }
    LaunchedEffect(vm.accountId, settings.contentKind, settings.showTagTranslations, submitted) {
        snapshotFlow { word.text.toString().trim() }
            .distinctUntilChanged()
            .collectLatest { query ->
                kotlinx.coroutines.delay(300)
                if (query.isBlank() || query == submitted || settings.contentKind != "illust") {
                    tagSuggestions = emptyList()
                    tagSuggestionError = null
                } else {
                    try {
                        val suggestions = vm.tagSuggestions(query)
                        tagSuggestions =
                            if (settings.showTagTranslations) {
                                coroutineScope {
                                    suggestions
                                        .map { name ->
                                            async {
                                                val translation =
                                                    runCatching {
                                                        vm.tagTranslation(
                                                            name,
                                                        )
                                                    }.getOrNull()
                                                Tag(name, translation)
                                            }
                                        }.awaitAll()
                                }
                            } else {
                                suggestions.map(::Tag)
                            }
                        tagSuggestionError = null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        tagSuggestions = emptyList()
                        tagSuggestionError = e.message ?: "标签联想请求失败"
                    }
                }
            }
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
            leadingIcon = { AppIcon(materialSymbol(MaterialSymbol.Search), null) },
            trailingIcon = {
                if (word.text.isNotEmpty()) {
                    IconButton({
                        word.setTextAndPlaceCursorAtEnd("")
                        if (initialQuery == null) submitted = ""
                    }) {
                        AppIcon(
                            materialSymbol(MaterialSymbol.Close),
                            stringResource(R.string.ui_7b15e5e8e7),
                        )
                    }
                }
            },
        )
    }
    val idSuggestions: @Composable () -> Unit = {
        val input = word.text.toString().trim()
        val id =
            input
                .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        if (id != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            ) {
                SuggestionChip(
                    onClick = { submit(input, SearchJump.Artist(id)) },
                    enabled = !jumping,
                    label = { Text("作者 ID") },
                    icon = { AppIcon(materialSymbol(MaterialSymbol.Person), null) },
                )
                SuggestionChip(
                    onClick = {
                        submit(
                            input,
                            SearchJump.Artwork(id, settings.contentKind == "novel"),
                        )
                    },
                    enabled = !jumping,
                    label = { Text("作品 ID") },
                    icon = { AppIcon(materialSymbol(MaterialSymbol.Image), null) },
                )
            }
        }
    }
    Column(if (back == null) Modifier.statusBarsPadding() else Modifier) {
        if (back != null) {
            ScreenBar(
                if (initialQuery == null) "" else "搜索结果",
                back = back,
                actions = {
                    if (initialQuery != null) {
                        IconButton({ filter = true }) {
                            AppIcon(
                                materialSymbol(MaterialSymbol.Settings),
                                stringResource(R.string.ui_1c31f74a1d),
                            )
                        }
                    }
                },
            )
        }
        SearchBar(
            state = searchState,
            inputField = searchField,
            modifier =
                Modifier.fillMaxWidth().padding(
                    start = PixivSpacing.content,
                    end = PixivSpacing.content,
                    bottom = PixivSpacing.content,
                ),
            colors =
                SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        )
        idSuggestions()
        if (jumping) {
            LoadingState()
        } else if (submitted.isNotEmpty()) {
            PrimaryTabRow(selectedTabIndex = resultPager.currentPage) {
                listOf(
                    stringResource(R.string.ui_f394cdc91d),
                    stringResource(R.string.ui_698bea5124),
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
                    if (userLoading) {
                        LoadingState()
                    } else if (error != null) {
                        EmptyState(
                            stringResource(R.string.ui_b9786d51c3),
                            error!!,
                            action = stringResource(R.string.ui_e2d53a6d3a),
                        ) {
                            userRetry++
                        }
                    } else if (users.isEmpty()) {
                        EmptyState("没有找到作者", "", materialSymbol(MaterialSymbol.Person))
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    bottom = LocalHomeNavigationInset.current,
                                ),
                        ) {
                            items(users, key = { it.id }) { user ->
                                UserRow(user, { navigate(Author(user)) })
                            }
                        }
                    }
                } else {
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
            }
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        top = 20.dp,
                        end = 20.dp,
                        bottom = 20.dp + LocalHomeNavigationInset.current,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (history.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.ui_b6f4afaf6d),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton({ vm.clearSearch() }) {
                                Text(stringResource(R.string.ui_7b15e5e8e7))
                            }
                        }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { h ->
                                SuggestionChip(
                                    { submit(h.word) },
                                    label = { Text(h.word) },
                                    icon = {
                                        AppIcon(
                                            materialSymbol(MaterialSymbol.History),
                                            null,
                                            Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.ui_2fd71ef4d8),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                { submit(tag.name) },
                                label = { TagLabel(tag) },
                            )
                        }
                    }
                }
            }
        }
    }
    ExpandedFullScreenSearchBar(state = searchState, inputField = searchField) {
        idSuggestions()
        tagSuggestionError?.let { message ->
            Text(
                "标签联想请求失败：$message",
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        tagSuggestions.forEach { suggestion ->
            ListItem(
                modifier = Modifier.clickable { submit(suggestion.name) },
            ) { TagLabel(suggestion) }
        }
        history.take(8).forEach { entry ->
            ListItem(
                leadingContent = { AppIcon(materialSymbol(MaterialSymbol.History), null) },
                modifier = Modifier.clickable { submit(entry.word) },
            ) {
                Text(entry.word)
            }
        }
    }
    if (filter) {
        ActionSheet(
            onDismissRequest = { filter = false },
            title = { Text(stringResource(R.string.ui_1c31f74a1d)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.ui_dc35af8d69),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    listOf(
                        "date_desc" to stringResource(R.string.ui_615547dc71),
                        "date_asc" to stringResource(R.string.ui_5196834bd3),
                    ).let { list ->
                        (
                            if (active?.premium == true) {
                                list +
                                    listOf(
                                        "popular_desc" to
                                            stringResource(R.string.ui_296adf9512),
                                    )
                            } else {
                                list
                            }
                        ).forEach { (key, title) ->
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
                        stringResource(R.string.ui_6a05dbdc88),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    listOf(
                        "partial_match_for_tags" to stringResource(R.string.ui_f9ba0caac3),
                        "exact_match_for_tags" to stringResource(R.string.ui_74f182cacf),
                        "title_and_caption" to stringResource(R.string.ui_96a2fad9d9),
                    ).forEach { (key, title) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { target = key },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(target == key, { target = key })
                            Text(title)
                        }
                    }
                    Text(
                        stringResource(R.string.search_date_range),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ dateField = "start" }, Modifier.weight(1f)) {
                            Text(
                                startDate.ifEmpty { stringResource(R.string.search_start_date) },
                            )
                        }
                        OutlinedButton({ dateField = "end" }, Modifier.weight(1f)) {
                            Text(endDate.ifEmpty { stringResource(R.string.search_end_date) })
                        }
                    }
                    if (startDate.isNotEmpty() || endDate.isNotEmpty()) {
                        TextButton({
                            startDate = ""
                            endDate = ""
                        }) {
                            Text(stringResource(R.string.search_clear_dates))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ filter = false }) { Text(stringResource(R.string.ui_33246f6a5e)) }
            },
        )
    }
    dateField?.let { field ->
        val initial = if (field == "start") startDate else endDate
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    initial
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            java.time.LocalDate
                                .parse(it)
                                .atStartOfDay(java.time.ZoneOffset.UTC)
                                .toInstant()
                                .toEpochMilli()
                        },
            )
        DateSelectionSheet(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { millis ->
                        val date =
                            java.time.Instant
                                .ofEpochMilli(millis)
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
                    Text(stringResource(R.string.ui_33246f6a5e))
                }
            },
            dismissButton = {
                TextButton({ dateField = null }) { Text(stringResource(R.string.ui_11d0241540)) }
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
    containerColor: Color = MaterialTheme.colorScheme.surface,
    action: @Composable (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = { Text(user.name) }
    val supporting: @Composable () -> Unit = {
        Text("@${user.account.ifEmpty {user.id.toString()}}")
    }
    val leading: @Composable () -> Unit = { Avatar(user, sharedTransition = true) }
    if (segment != null) {
        SegmentedListItem(
            colors = PixivContainerDefaults.listItemColors(),
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(segment.first, segment.second),
            content = content,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = action,
        )
    } else {
        ListItem(
            onClick = onClick,
            colors = ListItemDefaults.colors(containerColor = containerColor),
            content = content,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = action,
        )
    }
}

@Composable
fun Avatar(
    user: User,
    modifier: Modifier = Modifier,
    sharedTransition: Boolean = false,
) {
    val avatarModifier =
        modifier
            .size(
                48.dp,
            ).authorAvatarTransition(user.id, sharedTransition)
            .clip(CircleShape)
    if (user.profile_image_urls.medium.isNotEmpty()) {
        AsyncImage(
            user.profile_image_urls.medium,
            user.name,
            avatarModifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
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
}
