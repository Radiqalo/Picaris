package io.github.pixivnext.ui

import android.Manifest
import android.content.Intent
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
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

@Composable
fun DetailScreen(initial: Work, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
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
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.download(work)
        }
    fun bookmark(public: Boolean = true) {
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
                onClick = { if (!actionBusy) bookmark() },
                modifier = Modifier.navigationBarsPadding(),
            ) {
                AppIcon(
                    if (current.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                    if (current.is_bookmarked) "取消收藏" else "收藏",
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
                                UserRow(work.user, { navigate(Author(work.user)) }) {
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
                        item(key = "related") { RelatedWorkStrip(work, vm, navigate) }

                }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .statusBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = back) { AppIcon(Glyph.Back, "返回") }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        AppIcon(Glyph.More, "更多操作")
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (work.isNovel) "开始阅读" else "查看原图") },
                            leadingIcon = { AppIcon(if (work.isNovel) Glyph.Book else Glyph.Play, null) },
                            onClick = { moreMenu = false; openReader(current) },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.getString(R.string.ui_7a92434114)) },
                            leadingIcon = { AppIcon(Glyph.Share, null) },
                            onClick = { moreMenu = false; share() },
                        )
                        DropdownMenuItem(
                            text = { Text(strings.getString(R.string.ui_255d6cabdc)) },
                            leadingIcon = { AppIcon(Glyph.Download, null) },
                            onClick = {
                                moreMenu = false
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        )
                        if (!current.is_bookmarked && !actionBusy)
                            DropdownMenuItem(
                                text = { Text("非公开收藏") },
                                leadingIcon = { AppIcon(Glyph.Heart, null) },
                                onClick = { moreMenu = false; privateDialog = true },
                            )
                    }
                }
            }
        }
    }
    if (privateDialog)
        AlertDialog(
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
private fun RelatedWorkStrip(work: Work, vm: AppViewModel, navigate: (NavKey) -> Unit) {
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
                    Spacer(Modifier.width(144.dp).height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
                }
            else items(minOf(5, related.itemCount), key = { index ->
                related.peek(index)?.let { "${it.type}_${it.id}" } ?: "related_$index"
            }) { index ->
                related[index]?.let { artwork ->
                    val identity = artwork.identity(vm.accountId)
                    val current = bookmarks[identity]?.apply(artwork) ?: artwork
                    Box(Modifier.width(144.dp)) {
                        WorkCard(
                            current,
                            likedBusy = identity in busy,
                            showMetadata = false,
                            onLike = { vm.run { vm.bookmark(current) } },
                            onClick = { vm.record(current); navigate(Detail(current)) },
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(initial.id, vm.accountId) {
        try {
            vm.authorDetails(initial).let { user = it.user; profile = it.profile; profileLoaded = true }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Keep the known author and their reachable works. */ }
    }
    fun shareAuthor() {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://www.pixiv.net/users/${user.id}"), "分享作者"))
    }
    Column {
        ScreenBar("", back = back, actions = {
            IconButton(onClick = ::shareAuthor) { AppIcon(Glyph.Share, "分享作者") }
        })
        FeedGrid(
            FeedSpec(section = "user", kind = settings.contentKind, userId = user.id),
            vm, navigate, Modifier.weight(1f),
            header = {
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
                    Box(Modifier.fillMaxWidth().height(if (profile.background_image_url.isNullOrBlank()) 88.dp else 184.dp)) {
                        profile.background_image_url?.takeIf { it.isNotBlank() }?.let {
                            coil3.compose.AsyncImage(it, null, Modifier.fillMaxWidth().height(144.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        }
                        Avatar(user, Modifier.align(Alignment.BottomCenter).size(88.dp))
                    }
                    Text(user.name, style = MaterialTheme.typography.headlineSmall)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.content)) {
                        if (profileLoaded) Text("${profile.total_follow_users} 关注", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        profile.region?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (user.comment.isNotBlank()) Text(user.comment,
                            Modifier.weight(1f), maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        else Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showProfile = true }) { Text("查看资料") }
                    }
                    val count = if (settings.contentKind == "novel") profile.total_novels
                        else profile.total_illusts + profile.total_manga
                    Row(Modifier.fillMaxWidth().padding(top = PixivSpacing.compact),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (settings.contentKind == "novel") "小说" else "插画 · 漫画",
                            Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
    if (showProfile) ModalBottomSheet(onDismissRequest = { showProfile = false }) {
        Column(Modifier.fillMaxWidth().padding(PixivSpacing.section),
            verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
            Text(user.name, style = MaterialTheme.typography.headlineSmall)
            Text("ID ${user.id}", style = MaterialTheme.typography.bodySmall)
            profile.region?.takeIf { it.isNotBlank() }?.let { Text(it) }
            if (user.comment.isNotBlank()) Text(user.comment)
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            profile.webpage?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { link ->
                TextButton(onClick = { uriHandler.openUri(link) }) { Text("个人网站") }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
