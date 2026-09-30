package io.github.pixivnext.ui

import android.Manifest
import android.content.Intent
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
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
    val navigationTransition = LocalNavAnimatedContentScope.current.transition
    val imageTransition = LocalWorkTransition.current
    val canOpenReader = !navigationTransition.isRunning && imageTransition?.isTransitionActive != true

    fun openReader(work: Work) {
        // Recheck at the event boundary so repeated taps cannot skip the entering detail page.
        if (!navigationTransition.isRunning && imageTransition?.isTransitionActive != true)
            navigate(Reader(work))
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
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (canOpenReader && !actionBusy) bookmark() },
                modifier = Modifier.navigationBarsPadding().expressivePress(bookmarkInteraction),
                interactionSource = bookmarkInteraction,
            ) {
                FeedbackIcon(
                    if (current.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                    if (current.is_bookmarked) "取消收藏" else "收藏",
                    selected = current.is_bookmarked,
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            val largeWindow =
                androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width /
                    androidx.compose.ui.platform.LocalDensity.current.density >= 840f
            val previewHeight = (maxHeight * .55f).coerceIn(200.dp, 360.dp)
            Row {
                if (wide)
                    Box(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        WorkImage(
                            work,
                            Modifier.fillMaxWidth()
                                .aspectRatio(work.aspect)
                                .clickable(enabled = canOpenReader) { openReader(current) }
                                .testTag("detailImage"),
                            sharedTransition = true,
                            rounded = false,
                        )
                    }
                LazyColumn(
                    Modifier.weight(1f).testTag("detailList"),
                    contentPadding = PaddingValues(
                        top = if (wide) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp else 0.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (!wide)
                        item {
                            if (largeWindow) {
                                Box(
                                    Modifier.fillMaxWidth().height(previewHeight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    WorkImage(
                                        work,
                                        Modifier.fillMaxHeight()
                                            .aspectRatio(work.aspect)
                                            .clickable(enabled = canOpenReader) { openReader(current) }
                                            .testTag("detailImage"),
                                        sharedTransition = true,
                                        rounded = false,
                                    )
                                }
                            } else
                                WorkImage(
                                    work,
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(work.aspect.coerceAtLeast(.85f))
                                        .clickable(enabled = canOpenReader) { openReader(current) }
                                        .testTag("detailImage"),
                                    sharedTransition = true,
                                    rounded = false,
                                )
                        }
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (work.demo >= 0)
                                    SuggestionChip(
                                        {},
                                        label = { Text(strings.getString(R.string.ui_0ecf6f27ba)) },
                                        icon = { AppIcon(Glyph.Discover, null, Modifier.size(16.dp)) },
                                    )
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
                                    if (!navigationTransition.isRunning &&
                                        navigationTransition.targetState == androidx.compose.animation.EnterExitState.Visible &&
                                        imageTransition?.isTransitionActive != true
                                    ) navigate(Author(work.user))
                                }) {
                                    FilledTonalButton(
                                        onClick = {
                                            vm.run {
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
                                    AppIcon(if (work.isNovel) Glyph.Book else Glyph.Play, null)
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
                                                        tag.translated_name ?: tag.name,
                                                        "search",
                                                        if (work.isNovel) "novel" else "illust",
                                                        word = tag.name,
                                                    )
                                                )
                                            },
                                            label = { Text("#${tag.translated_name ?: tag.name}") },
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
                            AppIcon(Glyph.Comment, null)
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
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .statusBarsPadding().padding(12.dp).workTransitionControls(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = back) { AppIcon(Glyph.Back, "返回") }
                IconButton(onClick = { if (canOpenReader) moreMenu = true }) {
                    AppIcon(Glyph.More, "更多操作")
                }
            }
        }
    }
    if (moreMenu) ModalBottomSheet(onDismissRequest = { moreMenu = false }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = PixivSpacing.content)) {
            ListItem(
                content = { Text(if (work.isNovel) "开始阅读" else "查看原图") },
                leadingContent = { AppIcon(if (work.isNovel) Glyph.Book else Glyph.Play, null) },
                onClick = { moreMenu = false; openReader(current) },
            )
            ListItem(
                content = { Text(strings.getString(R.string.ui_7a92434114)) },
                leadingContent = { AppIcon(Glyph.Share, null) },
                onClick = { moreMenu = false; share() },
            )
            ListItem(
                content = { Text(strings.getString(R.string.ui_255d6cabdc)) },
                leadingContent = { AppIcon(Glyph.Download, null) },
                onClick = {
                    moreMenu = false
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            if (!current.is_bookmarked && !actionBusy)
                ListItem(
                    content = { Text("非公开收藏") },
                    leadingContent = { AppIcon(Glyph.Heart, null) },
                    onClick = { moreMenu = false; privateDialog = true },
                )
        }
    }
    if (privateDialog)
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
                            onClick = { vm.record(current); navigateRelatedDetail(Detail(current)) },
                        )
                    }
                }
            }
            item(key = "more") {
                FilledTonalIconButton(onClick = openAll) {
                    AppIcon(Glyph.Arrow, "查看全部相关作品")
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
    var selectedPage by remember(initial.id) { mutableIntStateOf(0) }
    val pageGridStates = listOf(
        rememberLazyStaggeredGridState(),
        rememberLazyStaggeredGridState(),
        rememberLazyStaggeredGridState(),
    )
    val feedback = selectionFeedback()
    val pageLabels = listOf("插画", "漫画", "收藏")
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    var artworkOffset by remember(initial.id) { mutableFloatStateOf(0f) }
    var viewportWidth by remember(initial.id) { mutableIntStateOf(0) }
    var pageTransitionRunning by remember(initial.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
    fun changePage(target: Int) {
        if (target == selectedPage || pageTransitionRunning) return
        feedback()
        if (viewportWidth <= 0) {
            selectedPage = target
            artworkOffset = 0f
            return
        }
        pageTransitionRunning = true
        val exitTarget = if (target > selectedPage) -viewportWidth.toFloat() else viewportWidth.toFloat()
        scope.launch {
            animateAuthorArtworkOffset(artworkOffset, exitTarget) { artworkOffset = it }
            selectedPage = target
            artworkOffset = -exitTarget
            androidx.compose.runtime.withFrameNanos { }
            animateAuthorArtworkOffset(artworkOffset, 0f) { artworkOffset = it }
            pageTransitionRunning = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Keep one author header and one feed viewport; animate only artwork when switching tabs.
        Box(Modifier.fillMaxSize().onSizeChanged { viewportWidth = it.width }
            .pointerInput(selectedPage, swipeThreshold, viewportWidth) {
            var horizontalDistance = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    if (!pageTransitionRunning) {
                        horizontalDistance += dragAmount
                        val proposed = artworkOffset + dragAmount
                        artworkOffset = when (selectedPage) {
                            0 -> proposed.coerceAtMost(0f)
                            pageLabels.lastIndex -> proposed.coerceAtLeast(0f)
                            else -> proposed.coerceIn(-viewportWidth.toFloat(), viewportWidth.toFloat())
                        }
                        change.consume()
                    }
                },
                onDragEnd = {
                    val target = when {
                        horizontalDistance <= -swipeThreshold && selectedPage < pageLabels.lastIndex -> selectedPage + 1
                        horizontalDistance >= swipeThreshold && selectedPage > 0 -> selectedPage - 1
                        else -> selectedPage
                    }
                    if (target != selectedPage) changePage(target)
                    else scope.launch { animateAuthorArtworkOffset(artworkOffset, 0f) { artworkOffset = it } }
                    horizontalDistance = 0f
                },
                onDragCancel = {
                    horizontalDistance = 0f
                    if (!pageTransitionRunning) {
                        scope.launch { animateAuthorArtworkOffset(artworkOffset, 0f) { artworkOffset = it } }
                    }
                },
            )
        }) {
            FeedGrid(
                FeedSpec(section = if (selectedPage == 2) "bookmarks" else "user",
                    kind = if (selectedPage == 1) "manga" else "illust", userId = user.id),
                vm, navigate, Modifier.fillMaxSize(),
                itemsModifier = Modifier.graphicsLayer { translationX = artworkOffset },
                gridState = pageGridStates[selectedPage],
                topPadding = 0.dp,
                scrollHeaderWhileEmpty = true,
                header = {
                    Column(Modifier.fillMaxWidth()) {
                        BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
                            Box(Modifier.align(Alignment.TopCenter)
                                .requiredWidth(maxWidth + PixivSpacing.content * 2)
                                .height(210.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                profile.background_image_url?.takeIf(String::isNotBlank)?.let {
                                    coil3.compose.AsyncImage(it, null, Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                            }
                            Avatar(user, Modifier.align(Alignment.BottomStart)
                                .padding(start = PixivSpacing.content).offset(y = 28.dp).size(104.dp),
                                sharedTransition = true)
                            Column(Modifier.align(Alignment.BottomEnd)
                                .padding(end = PixivSpacing.content, bottom = 6.dp)
                                .width((maxWidth - 152.dp).coerceAtLeast(168.dp)),
                                verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    AuthorMetric(
                                        if (profileLoaded) profile.total_illusts + profile.total_manga else null,
                                        "作品",
                                    )
                                    AuthorMetric(if (profileLoaded) profile.total_follow_users else null, "关注")
                                    AuthorMetric(if (profileLoaded) profile.total_illust_bookmarks_public else null, "收藏")
                                }
                                Button(
                                    onClick = {
                                        vm.run {
                                            busy = true
                                            try { user = vm.follow(user) } finally { busy = false }
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shapes = ButtonDefaults.shapesFor(44.dp),
                                ) { Text(if (user.is_followed) "已关注" else "关注") }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Column(Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                            verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                            Text(user.name, Modifier.authorTransition(user.id, "name"),
                                style = MaterialTheme.typography.headlineSmall)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(
                                    user.comment.ifBlank { "Pixiv 创作者" },
                                    Modifier.weight(1f).heightIn(min = 40.dp),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { showProfile = true }) { Text("查看资料") }
                            }
                            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("ID ${user.id}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                profile.region?.takeIf(String::isNotBlank)?.let {
                                    Text(" · $it", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                afterHeader = {
                    PrimaryTabRow(selectedTabIndex = selectedPage) {
                        pageLabels.forEachIndexed { index, label ->
                            Tab(selected = selectedPage == index,
                                onClick = { if (selectedPage != index) changePage(index) },
                                text = { Text(label) })
                        }
                    }
                },
            )
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = back) { AppIcon(Glyph.Back, "返回") }
            IconButton(onClick = ::shareAuthor) { AppIcon(Glyph.Share, "分享作者") }
        }
    }
    if (showProfile) ModalBottomSheet(onDismissRequest = { showProfile = false }) {
        AuthorProfileContent(details.copy(user = user), vm, profileLoaded)
    }
}

@Composable
private fun RowScope.AuthorMetric(value: Int?, label: String) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
    ) {
        Text(value?.let(::compact) ?: "—", style = MaterialTheme.typography.titleMedium,
            maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false)
    }
}

private suspend fun animateAuthorArtworkOffset(from: Float, to: Float, onValue: (Float) -> Unit) {
    animate(
        initialValue = from,
        targetValue = to,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    ) { value, _ -> onValue(value) }
}
