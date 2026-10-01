package io.github.pixivnext.ui

import android.Manifest
import android.content.Intent
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.navigation3.runtime.NavKey
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    initial: Work,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    navigateRelatedDetail: (Detail) -> Unit,
    back: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val permitted = navigationPermission()
    val canOpenReader = permitted()
    val tagTranslations = LocalTagTranslationEnabled.current

    fun openReader(work: Work) {
        if (permitted()) navigate(Reader(work))
    }

    var work by remember { mutableStateOf(initial) }
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val identity = work.identity(vm.accountId)
    val current = bookmarks[identity]?.apply(work) ?: work
    val actionBusy = identity in busy
    var privateDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bookmarkFeedback = toggleFeedback()
    val bookmarkInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.download(work)
        }
    fun bookmark(public: Boolean = true) {
        if (!permitted()) return
        bookmarkFeedback(!current.is_bookmarked)
        vm.run {
            work = vm.bookmark(current, public)
        }
    }
    fun share() {
        val link =
            if (work.isNovel) "https://www.pixiv.net/novel/show.php?id=${work.id}"
            else "https://www.pixiv.net/artworks/${work.id}"
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "${work.title}\n$link"),
                strings.getString(R.string.ui_df80b48aa7),
            )
        )
    }
    LaunchedEffect(initial.id) {
        runCatching { vm.detail(initial) }.onSuccess { work = it }
    }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val firstImageHeight = (maxHeight - topInset - bottomInset - 180.dp).coerceAtLeast(160.dp)
        var expandedPages by androidx.compose.runtime.saveable.rememberSaveable(work.id) { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val information: @Composable (Modifier) -> Unit = { modifier ->
            LazyColumn(
                modifier.testTag("detailList"),
                state = listState,
                contentPadding = PaddingValues(
                    top = if (wide) 64.dp else topInset,
                    bottom = bottomInset + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!wide) {
                    item(key = "firstImage") {
                        if (work.isNovel) {
                            WorkImage(
                                work,
                                Modifier.fillMaxWidth().aspectRatio(work.aspect)
                                    .clickable(enabled = canOpenReader) { openReader(current) }.testTag("detailImage"),
                                sharedTransition = true,
                                rounded = false,
                            )
                        } else {
                            DetailArtworkPage(work, 0, Modifier.fillMaxWidth().height(firstImageHeight)) { page ->
                                if (permitted()) navigate(Reader(current, page))
                            }
                        }
                    }
                    if (!work.isNovel && work.previews.size > 1) {
                        if (expandedPages) {
                            items(work.previews.size - 1, key = { index -> "page:${index + 1}" }) { index ->
                                DetailArtworkPage(work, index + 1, Modifier.fillMaxWidth()) { page ->
                                    if (permitted()) navigate(Reader(current, page))
                                }
                            }
                        }
                        item(key = "expandPages") {
                            FilledTonalButton(
                                onClick = {
                                    if (permitted()) {
                                        if (expandedPages) scope.launch {
                                            listState.scrollToItem(0)
                                            expandedPages = false
                                        } else expandedPages = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            ) {
                                Text(if (expandedPages) "收起图片" else "展开全部 ${work.previews.size} 张")
                            }
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(work.title, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "ID ${work.id}  ·  ${work.create_date.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text(
                                    "${compact(work.total_view)} 浏览",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${compact(current.total_bookmarks)} 收藏",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (work.page_count > 1)
                                    Text(
                                        "${work.page_count} 页",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                            }
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            UserRow(work.user, {
                                if (permitted()) navigate(Author(work.user))
                            }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                FilledTonalButton(
                                    onClick = {
                                        if (permitted() && !followBusy) vm.run {
                                            followBusy = true
                                            try {
                                                work = work.copy(user = vm.follow(work.user))
                                            } finally {
                                                followBusy = false
                                            }
                                        }
                                    },
                                    enabled = !followBusy,
                                ) {
                                    Text(if (work.user.is_followed) "已关注" else "关注")
                                }
                            }
                        }
                    }
                }
                if (work.isNovel)
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            FilledTonalButton(
                                { openReader(current) },
                                Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                AppIcon(if (work.isNovel) materialSymbol(MaterialSymbol.Book) else materialSymbol(MaterialSymbol.PlayArrow), null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (work.isNovel) strings.getString(R.string.ui_f3be3e4b09)
                                    else if (work.type == "ugoira")
                                        strings.getString(R.string.ui_d3657fb0a3)
                                    else strings.getString(R.string.ui_a0217cd1e4)
                                )
                            }
                        }
                    }
                if (work.tags.isNotEmpty())
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                work.tags.forEach { tag ->
                                    AssistChip(
                                        {
                                            navigate(
                                                Collection(
                                                    if (tagTranslations)
                                                        tag.translated_name ?: tag.name else tag.name,
                                                    "search",
                                                    if (work.isNovel) "novel" else "illust",
                                                    word = tag.name,
                                                )
                                            )
                                        },
                                        label = { TagLabel(tag) },
                                    )
                                }
                            }
                        }
                    }
                if (work.caption.isNotBlank())
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Text(
                                Html.fromHtml(work.caption, Html.FROM_HTML_MODE_COMPACT).toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                if (work.series != null)
                    item {
                        OutlinedButton(
                            onClick = {
                                navigate(Collection(work.series!!.title, "series", "novel", work.series!!.id))
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        ) {
                            Text("系列 · ${work.series!!.title}")
                        }
                    }
                item(key = "comments") {
                    OutlinedButton(
                        onClick = { navigate(Comments(current)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    ) {
                        AppIcon(materialSymbol(MaterialSymbol.Comment), null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看评论")
                    }
                }
                if (!work.isNovel)
                    item(key = "related") {
                        RelatedWorkStrip(work, vm, navigate, navigateRelatedDetail)
                    }
            }
        }
        val images: @Composable (Modifier, PaddingValues) -> Unit = { modifier, padding ->
            DetailArtworkFlow(
                work = work,
                modifier = modifier,
                contentPadding = padding,
                onOpenPage = { page ->
                    if (permitted()) navigate(Reader(current, page))
                },
            )
        }
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { if (canOpenReader && !actionBusy) bookmark() },
                    modifier = Modifier.navigationBarsPadding().expressivePress(bookmarkInteraction),
                    interactionSource = bookmarkInteraction,
                ) {
                    FeedbackIcon(
                        if (current.is_bookmarked) materialSymbol(MaterialSymbol.FavoriteFilled) else materialSymbol(MaterialSymbol.Favorite),
                        if (current.is_bookmarked) "取消收藏" else "收藏",
                        selected = current.is_bookmarked,
                    )
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize().clipToBounds()) {
                if (wide) {
                    if (work.isNovel) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        WorkImage(work, Modifier.fillMaxWidth().aspectRatio(work.aspect)
                            .clickable(enabled = canOpenReader) { openReader(current) },
                            sharedTransition = true, rounded = false)
                    } else images(Modifier.weight(1f).fillMaxHeight(), PaddingValues(top = topInset, bottom = bottomInset))
                }
                information(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .statusBarsPadding().padding(12.dp).workTransitionControls(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { back() }) { AppIcon(materialSymbol(MaterialSymbol.ArrowBack), "返回") }
            IconButton(onClick = { if (canOpenReader) moreMenu = true }) {
                AppIcon(materialSymbol(MaterialSymbol.MoreHoriz), "更多操作")
            }
        }
    }
    if (moreMenu && permitted()) ModalBottomSheet(onDismissRequest = { moreMenu = false }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = PixivSpacing.content)) {
            ListItem(
                content = { Text(if (work.isNovel) "开始阅读" else "查看原图") },
                leadingContent = { AppIcon(if (work.isNovel) materialSymbol(MaterialSymbol.Book) else materialSymbol(MaterialSymbol.PlayArrow), null) },
                onClick = { moreMenu = false; openReader(current) },
            )
            ListItem(
                content = { Text(strings.getString(R.string.ui_7a92434114)) },
                leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Share), null) },
                onClick = { moreMenu = false; share() },
            )
            ListItem(
                content = { Text(strings.getString(R.string.ui_255d6cabdc)) },
                leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Download), null) },
                onClick = {
                    moreMenu = false
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            if (!current.is_bookmarked && !actionBusy)
                ListItem(
                    content = { Text("非公开收藏") },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Favorite), null) },
                    onClick = { moreMenu = false; privateDialog = true },
                )
        }
    }
    if (privateDialog && permitted())
        ActionSheet(
            onDismissRequest = { privateDialog = false },
            title = { Text(strings.getString(R.string.ui_67c6787737)) },
            text = { Text(strings.getString(R.string.ui_b6fe094725)) },
            confirmButton = {
                TextButton({
                    privateDialog = false
                    bookmark(false)
                }) {
                    Text(strings.getString(R.string.ui_67c6787737))
                }
            },
            dismissButton = {
                TextButton({ privateDialog = false }) {
                    Text(strings.getString(R.string.ui_4d0b4688c7))
                }
            },
        )
}

@Composable
private fun DetailArtworkPage(
    work: Work,
    page: Int,
    modifier: Modifier,
    onOpenPage: (Int) -> Unit,
) {
    val url = work.previews.getOrNull(page) ?: return
    val fallbackAspect = if (work.width > 1 && work.height > 1)
        work.width.toFloat() / work.height else work.aspect
    var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val imageWidth = if (constraints.hasBoundedHeight) minOf(maxWidth, maxHeight * aspect) else maxWidth
        WorkImage(
            work,
            Modifier.width(imageWidth).aspectRatio(aspect)
                .clickable { onOpenPage(page) }
                .testTag(if (page == 0) "detailImage" else "detailImage:$page"),
            url = url,
            scale = ContentScale.Fit,
            sharedTransition = page == 0,
            rounded = false,
            onImageAspectRatio = { aspect = it },
        )
    }
}

@Composable
private fun DetailArtworkFlow(
    work: Work,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onOpenPage: (Int) -> Unit,
) {
    val pages = work.previews
    if (pages.size == 1) {
        val url = pages.first()
        val fallbackAspect = if (work.width > 1 && work.height > 1)
            work.width.toFloat() / work.height else work.aspect
        var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
        BoxWithConstraints(
            modifier = modifier.testTag("detailImages").padding(contentPadding).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            WorkImage(
                work,
                Modifier.width(minOf(maxWidth, maxHeight * aspect)).aspectRatio(aspect)
                    .clickable { onOpenPage(0) }.testTag("detailImage"),
                url = url,
                scale = ContentScale.Fit,
                sharedTransition = true,
                rounded = false,
                onImageAspectRatio = { aspect = it },
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.testTag("detailImages"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(pages.size, key = { page -> "${work.id}:$page" }) { page ->
            val url = pages[page]
            val fallbackAspect = if (work.width > 1 && work.height > 1)
                work.width.toFloat() / work.height else work.aspect
            var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
            WorkImage(
                work,
                Modifier.fillMaxWidth().aspectRatio(aspect)
                    .clickable { onOpenPage(page) }
                    .testTag(if (page == 0) "detailImage" else "detailImage:$page"),
                url = url,
                scale = ContentScale.Fit,
                sharedTransition = page == 0,
                rounded = false,
                onImageAspectRatio = { aspect = it },
            )
        }
    }
}

@Composable
private fun RelatedWorkStrip(
    work: Work,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    navigateRelatedDetail: (Detail) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spec = remember(work.id) { FeedSpec(section = "related", userId = work.id) }
    val flow = remember(spec, vm.accountId, settings.contentFilter()) { vm.feed(spec) }
    val related = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val openAll = {
        navigate(Collection(strings.getString(R.string.ui_29ffbeb614), "related", userId = work.id))
    }
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        Text(strings.getString(R.string.ui_29ffbeb614),
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (related.itemCount == 0 && related.loadState.refresh is LoadState.Loading)
                items(5) {
                    Spacer(Modifier.width(144.dp).height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
                }
            else items(minOf(5, related.itemCount), key = { index ->
                related.peek(index)?.let { "${it.type}_${it.id}" } ?: "related_$index"
            }) { index ->
                related[index]?.let { artwork ->
                    val identity = artwork.identity(vm.accountId)
                    val current = bookmarks[identity]?.apply(artwork) ?: artwork
                    Box(Modifier.width(180.dp * current.aspect)) {
                        WorkCard(
                            current,
                            likedBusy = identity in busy,
                            showMetadata = false,
                            onLike = { vm.run { vm.bookmark(current) } },
                            onClick = { vm.record(current); navigateRelatedDetail(Detail(current, spec, index)) },
                        )
                    }
                }
            }
            item(key = "more") {
                FilledTonalIconButton(onClick = openAll) {
                    AppIcon(materialSymbol(MaterialSymbol.ChevronRight), "查看全部相关作品")
                }
            }
        }
    }
}

@Composable
fun AuthorScreen(initial: User, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    var user by remember(initial.id) { mutableStateOf(initial) }
    var profile by remember(initial.id) { mutableStateOf(AuthorProfile()) }
    var profileLoaded by remember(initial.id) { mutableStateOf(false) }
    var details by remember(initial.id) { mutableStateOf(AuthorDetails(initial)) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val outerScroll = rememberLazyListState()
    val pageGridStates = listOf(
        rememberLazyStaggeredGridState(),
        rememberLazyStaggeredGridState(),
        rememberLazyStaggeredGridState(),
    )
    val scope = rememberCoroutineScope()
    val pageLabels = listOf("插画", "漫画", "收藏")
    val feedback = selectionFeedback()
    val pageMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val workTransition = LocalWorkTransition.current
    val coordinatedScroll = remember(outerScroll) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (available.y >= 0f) return androidx.compose.ui.geometry.Offset.Zero
                val consumed = outerScroll.dispatchRawDelta(-available.y)
                return androidx.compose.ui.geometry.Offset(0f, -consumed)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                val consumedY = outerScroll.dispatchRawDelta(-available.y)
                return androidx.compose.ui.geometry.Offset(0f, -consumedY)
            }
        }
    }
    var busy by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(initial.id, vm.accountId) {
        try {
            vm.authorDetails(initial).let { details = it; user = it.user; profile = it.profile; profileLoaded = true }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Keep the known author and their reachable works. */ }
    }
    fun shareAuthor() {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://www.pixiv.net/users/${user.id}"), "分享作者"))
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportHeight = maxHeight
        LazyColumn(state = outerScroll, modifier = Modifier.fillMaxSize()) {
            item(key = "author_profile") {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(254.dp)) {
                        Box(Modifier.fillMaxWidth().height(210.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            profile.background_image_url?.takeIf(String::isNotBlank)?.let {
                                coil3.compose.AsyncImage(it, null, Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            }
                        }
                        Avatar(user, Modifier.align(Alignment.BottomCenter).size(88.dp), sharedTransition = true)
                    }
                    Column(Modifier.fillMaxWidth().padding(PixivSpacing.content),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PixivSpacing.content)) {
                        Text(user.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Button(
                            onClick = {
                                vm.run {
                                    busy = true
                                    try { user = vm.follow(user) } finally { busy = false }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(.75f).height(ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        ) { Text(if (user.is_followed) "已关注" else "关注") }
                        Text(if (profileLoaded) "${profile.total_follow_users} 关注" else " ",
                            Modifier.heightIn(min = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(profile.region.orEmpty(), Modifier.heightIn(min = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(user.comment, Modifier.weight(1f).heightIn(min = 40.dp),
                                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showProfile = true }) { Text("查看资料") }
                        }
                    }
                }
            }
            item(key = "author_tabs") {
                PrimaryTabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    pageLabels.forEachIndexed { index, label ->
                        Tab(selected = pager.currentPage == index,
                            onClick = {
                                if (pager.currentPage != index) {
                                    feedback()
                                    scope.launch { pager.animateScrollToPage(index, animationSpec = pageMotion) }
                                }
                            }, text = { Text(label) })
                    }
                }
            }
            item(key = "author_works") {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth().height(viewportHeight),
                    pageNestedScrollConnection = coordinatedScroll,
                ) { page ->
                    CompositionLocalProvider(
                        LocalWorkTransition provides if (page == pager.currentPage) workTransition else null,
                    ) {
                        FeedGrid(
                            FeedSpec(section = if (page == 2) "bookmarks" else "user",
                                kind = if (page == 1) "manga" else "illust", userId = user.id),
                            vm, navigate, Modifier.fillMaxSize(),
                            gridState = pageGridStates[page],
                            pullToRefreshEnabled = false,
                        )
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = back) { AppIcon(materialSymbol(MaterialSymbol.ArrowBack), "返回") }
            IconButton(onClick = ::shareAuthor) { AppIcon(materialSymbol(MaterialSymbol.Share), "分享作者") }
        }
    }
    if (showProfile && navigationPermission()()) ModalBottomSheet(onDismissRequest = { showProfile = false }) {
        AuthorProfileContent(details.copy(user = user), vm, profileLoaded)
    }
}
