package io.github.radiqalo.picaris.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.launch

private fun formatDownloadEstimate(bytes: Long): String = "约 ${formatByteSize(bytes)}"

@Composable
fun DetailScreen(
    initial: Work,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    navigateRelatedDetail: (Detail) -> Unit,
    back: () -> Unit,
) {
    val permitted = navigationPermission()
    val canOpenReader = permitted()
    val tagTranslations = LocalTagTranslationEnabled.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    fun openReader(work: Work) {
        if (permitted()) navigate(Reader(work))
    }

    var work by remember { mutableStateOf(initial) }
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val identity = work.identity(vm.accountId)
    val current = bookmarks[identity]?.apply(work) ?: work
    val actionBusy = identity in busy
    var bookmarkPrivate by remember(identity) { mutableStateOf<Boolean?>(null) }
    var privateDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var downloadPageSelection by remember(work.id) { mutableStateOf(false) }
    var selectedDownloadPages by remember(work.id) { mutableStateOf(emptySet<Int>()) }
    var pendingDownloadPages by remember(work.id) { mutableStateOf<Set<Int>?>(null) }
    var pendingUgoiraGif by remember(work.id) { mutableStateOf(false) }
    var estimatedDownloadBytes by remember(work.id) { mutableStateOf<Long?>(null) }
    var estimatingDownloadBytes by remember(work.id) { mutableStateOf(false) }
    var followBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.ui_df80b48aa7)
    val bookmarkFeedback = toggleFeedback()
    val bookmarkInteraction =
        remember {
            androidx.compose.foundation.interaction
                .MutableInteractionSource()
        }
    val ugoiraSourceFile =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            uri?.let { vm.saveUgoiraSource(work, it) }
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val pages = pendingDownloadPages
            pendingDownloadPages = null
            if (granted) vm.download(work, pages, pendingUgoiraGif)
        }

    fun requestDownload(pages: Set<Int>? = null) {
        pendingDownloadPages = pages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingDownloadPages = null
            vm.download(work, pages, pendingUgoiraGif)
        }
    }

    fun requestUgoiraDownload(asGif: Boolean) {
        pendingUgoiraGif = asGif
        requestDownload()
    }
    LaunchedEffect(moreMenu, work.id, work.page_count) {
        if (moreMenu && !work.isNovel) {
            estimatingDownloadBytes = true
            estimatedDownloadBytes = runCatching { vm.estimateDownloadBytes(work) }.getOrNull()
            estimatingDownloadBytes = false
        }
    }
    LaunchedEffect(moreMenu, current.is_bookmarked, identity) {
        if (moreMenu && current.is_bookmarked) {
            bookmarkPrivate = runCatching { vm.isBookmarkPrivate(current) }.getOrNull()
        } else if (!current.is_bookmarked) {
            bookmarkPrivate = null
        }
    }

    fun bookmark(public: Boolean = !settings.defaultPrivateBookmarks) {
        if (!permitted()) return
        bookmarkFeedback(!current.is_bookmarked)
        vm.run {
            work = vm.bookmark(current, public)
            bookmarkPrivate = !public
        }
    }

    fun share() {
        val link =
            if (work.isNovel) {
                "https://www.pixiv.net/novel/show.php?id=${work.id}"
            } else {
                "https://www.pixiv.net/artworks/${work.id}"
            }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "${work.title}\n$link"),
                shareLabel,
            ),
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
        var expandedPages by androidx.compose.runtime.saveable.rememberSaveable(work.id) {
            mutableStateOf(false)
        }
        val listState = rememberLazyListState()
        val informationGap = if (!wide && !work.isNovel) 16.dp else 0.dp
        val information: @Composable (Modifier) -> Unit = { modifier ->
            LazyColumn(
                modifier.testTag("detailList"),
                state = listState,
                contentPadding =
                    PaddingValues(
                        top = if (wide) 64.dp else topInset,
                        bottom = bottomInset + 96.dp,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (!wide &&
                            !work.isNovel
                        ) {
                            4.dp
                        } else {
                            20.dp
                        },
                    ),
            ) {
                if (!wide) {
                    item(key = "firstImage") {
                        if (work.isNovel) {
                            WorkImage(
                                work,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(work.aspect)
                                    .clickable(
                                        enabled = canOpenReader,
                                    ) { openReader(current) }
                                    .testTag("detailImage"),
                                url = work.imageForQuality(settings.detailImageQuality),
                                sharedTransition = true,
                                rounded = false,
                            )
                        } else {
                            val imageModifier =
                                if (work.detailPageCount > 1) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth().height(firstImageHeight)
                                }
                            DetailArtworkPage(work, 0, imageModifier, settings.detailImageQuality) { page ->
                                if (permitted()) navigate(Reader(current, page))
                            }
                        }
                    }
                    if (!work.isNovel && work.detailPageCount > 1) {
                        if (expandedPages) {
                            items(work.detailPageCount - 1, key = { index ->
                                "page:${index + 1}"
                            }) { index ->
                                DetailArtworkPage(
                                    work,
                                    index + 1,
                                    Modifier.fillMaxWidth(),
                                    settings.detailImageQuality,
                                ) { page ->
                                    if (permitted()) navigate(Reader(current, page))
                                }
                            }
                        }
                        item(key = "expandPages") {
                            FilledTonalButton(
                                onClick = {
                                    if (permitted()) {
                                        if (expandedPages) {
                                            scope.launch {
                                                listState.scrollToItem(0)
                                                expandedPages = false
                                            }
                                        } else {
                                            expandedPages = true
                                        }
                                    }
                                },
                                modifier =
                                    Modifier.fillMaxWidth().padding(
                                        horizontal = 20.dp,
                                        vertical = 8.dp,
                                    ),
                            ) {
                                Text(if (expandedPages) "收起图片" else "展开全部 ${work.detailPageCount} 张")
                            }
                        }
                    }
                }
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = informationGap,
                        ),
                    ) {
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
                                if (work.page_count > 1) {
                                    Text(
                                        "${work.page_count} 页",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = informationGap,
                        ),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            UserRow(work.user, {
                                if (permitted()) navigate(Author(work.user))
                            }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                FilledTonalButton(
                                    onClick = {
                                        if (permitted() && !followBusy) {
                                            vm.run {
                                                followBusy = true
                                                try {
                                                    work = work.copy(user = vm.follow(work.user))
                                                } finally {
                                                    followBusy = false
                                                }
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
                if (work.isNovel) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = informationGap,
                            ),
                        ) {
                            FilledTonalButton(
                                { openReader(current) },
                                Modifier.fillMaxWidth().height(52.dp),
                            ) {
                                AppIcon(
                                    if (work.isNovel) {
                                        materialSymbol(
                                            MaterialSymbol.Book,
                                        )
                                    } else {
                                        materialSymbol(MaterialSymbol.PlayArrow)
                                    },
                                    null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (work.isNovel) {
                                        stringResource(R.string.ui_f3be3e4b09)
                                    } else if (work.type == "ugoira") {
                                        stringResource(R.string.ui_d3657fb0a3)
                                    } else {
                                        stringResource(R.string.ui_a0217cd1e4)
                                    },
                                )
                            }
                        }
                    }
                }
                if (work.tags.isNotEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = informationGap,
                            ),
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                work.tags.forEach { tag ->
                                    AssistChip(
                                        {
                                            navigate(
                                                Collection(
                                                    if (tagTranslations) {
                                                        tag.translated_name ?: tag.name
                                                    } else {
                                                        tag.name
                                                    },
                                                    "search",
                                                    if (work.isNovel) "novel" else "illust",
                                                    word = tag.name,
                                                ),
                                            )
                                        },
                                        label = { TagLabel(tag) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (work.caption.isNotBlank()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = informationGap,
                            ),
                        ) {
                            Text(
                                Html.fromHtml(work.caption, Html.FROM_HTML_MODE_COMPACT).toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (work.series != null) {
                    item {
                        OutlinedButton(
                            onClick = {
                                work.series?.let { series ->
                                    navigate(
                                        Collection(
                                            series.title,
                                            "series",
                                            if (work.isNovel) "novel" else "manga",
                                            series.id,
                                        ),
                                    )
                                }
                            },
                            modifier =
                                Modifier.fillMaxWidth().padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = informationGap,
                                ),
                        ) {
                            Text("系列 · ${work.series!!.title}")
                        }
                    }
                }
                item(key = "comments") {
                    OutlinedButton(
                        onClick = { navigate(Comments(current)) },
                        modifier =
                            Modifier.fillMaxWidth().padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = informationGap,
                            ),
                    ) {
                        AppIcon(materialSymbol(MaterialSymbol.Comment), null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看评论")
                    }
                }
                if (!work.isNovel) {
                    item(key = "related") {
                        Box(Modifier.padding(top = informationGap)) {
                            RelatedWorkStrip(work, vm, navigate, navigateRelatedDetail)
                        }
                    }
                }
            }
        }
        val images: @Composable (Modifier, PaddingValues) -> Unit = { modifier, padding ->
            DetailArtworkFlow(
                work = work,
                modifier = modifier,
                contentPadding = padding,
                imageQuality = settings.detailImageQuality,
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
                    modifier =
                        Modifier.navigationBarsPadding().expressivePress(
                            bookmarkInteraction,
                        ),
                    interactionSource = bookmarkInteraction,
                ) {
                    FeedbackIcon(
                        if (current.is_bookmarked) {
                            materialSymbol(
                                MaterialSymbol.FavoriteFilled,
                            )
                        } else {
                            materialSymbol(MaterialSymbol.Favorite)
                        },
                        if (current.is_bookmarked) "取消收藏" else "收藏",
                        selected = current.is_bookmarked,
                    )
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize().clipToBounds()) {
                if (wide) {
                    if (work.isNovel) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            WorkImage(
                                work,
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(work.aspect)
                                    .clickable(enabled = canOpenReader) { openReader(current) },
                                url = work.imageForQuality(settings.detailImageQuality),
                                sharedTransition = true,
                                rounded = false,
                            )
                        }
                    } else {
                        images(
                            Modifier.weight(1f).fillMaxHeight(),
                            PaddingValues(top = topInset, bottom = bottomInset),
                        )
                    }
                }
                information(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp)
                .workTransitionControls(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { back() }) {
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
            IconButton(onClick = { if (canOpenReader) moreMenu = true }) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = .38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(
                        materialSymbol(MaterialSymbol.MoreHoriz),
                        "更多操作",
                        Modifier.size(24.dp),
                        Color.White,
                    )
                }
            }
        }
    }
    if (moreMenu && permitted()) {
        ModalBottomSheet(
            onDismissRequest = { moreMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            val menuItemColors =
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(
                    bottom = PixivSpacing.content,
                ),
            ) {
                ListItem(
                    colors = menuItemColors,
                    content = { Text(if (work.isNovel) "开始阅读" else "查看原图") },
                    leadingContent = {
                        AppIcon(
                            if (work.isNovel) {
                                materialSymbol(
                                    MaterialSymbol.Book,
                                )
                            } else {
                                materialSymbol(MaterialSymbol.PlayArrow)
                            },
                            null,
                        )
                    },
                    onClick = {
                        moreMenu = false
                        openReader(current)
                    },
                )
                ListItem(
                    colors = menuItemColors,
                    content = { Text(stringResource(R.string.ui_7a92434114)) },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Share), null) },
                    onClick = {
                        moreMenu = false
                        share()
                    },
                )
                if (work.type == "ugoira") {
                    ListItem(
                        colors = menuItemColors,
                        content = { Text("下载源文件") },
                        supportingContent = { Text("选择保存文件") },
                        leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Download), null) },
                        onClick = {
                            moreMenu = false
                            ugoiraSourceFile.launch("${work.id}_ugoira.zip")
                        },
                    )
                    ListItem(
                        colors = menuItemColors,
                        content = { Text("下载 GIF") },
                        supportingContent = { Text("合成为可循环播放的 GIF") },
                        leadingContent = {
                            AppIcon(
                                materialSymbol(MaterialSymbol.PlayArrow),
                                null,
                            )
                        },
                        onClick = {
                            moreMenu = false
                            requestUgoiraDownload(asGif = true)
                        },
                    )
                } else {
                    ListItem(
                        colors = menuItemColors,
                        content = {
                            Text(if (work.page_count > 1 && !work.isNovel) "下载全部图片" else "下载作品")
                        },
                        trailingContent = {
                            Text(
                                when {
                                    estimatingDownloadBytes -> "正在估算大小…"
                                    estimatedDownloadBytes != null ->
                                        formatDownloadEstimate(
                                            estimatedDownloadBytes!!,
                                        )
                                    work.isNovel -> "下载后显示实际大小"
                                    else -> "大小暂不可用"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        },
                        leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Download), null) },
                        onClick = {
                            moreMenu = false
                            requestDownload()
                        },
                    )
                }
                if (work.page_count > 1 && !work.isNovel) {
                    ListItem(
                        colors = menuItemColors,
                        content = { Text("下载选中图片") },
                        leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Image), null) },
                        onClick = {
                            moreMenu = false
                            selectedDownloadPages = emptySet()
                            downloadPageSelection = true
                        },
                    )
                }
                if (!current.is_bookmarked && !actionBusy) {
                    ListItem(
                        colors = menuItemColors,
                        content = { Text("非公开收藏") },
                        leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Favorite), null) },
                        onClick = {
                            moreMenu = false
                            privateDialog = true
                        },
                    )
                }
                if (current.is_bookmarked && !actionBusy) {
                    ListItem(
                        colors = menuItemColors,
                        content = {
                            Text(
                                when (bookmarkPrivate) {
                                    true -> "设为公开收藏"
                                    false -> "设为私人收藏"
                                    null -> "正在读取收藏状态…"
                                },
                            )
                        },
                        leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Favorite), null) },
                        enabled = bookmarkPrivate != null,
                        onClick = {
                            moreMenu = false
                            vm.run {
                                val makePublic = bookmarkPrivate == true
                                vm.setBookmarkVisibility(current, makePublic)
                                bookmarkPrivate = !makePublic
                                work = current
                            }
                        },
                    )
                }
            }
        }
    }
    if (downloadPageSelection && permitted()) {
        val pageCount = maxOf(work.detailPageCount, work.originals.size)
        ModalBottomSheet(onDismissRequest = { downloadPageSelection = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = PixivSpacing.content)
                    .padding(bottom = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("选择要下载的图片", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已选 ${selectedDownloadPages.size} / $pageCount",
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            selectedDownloadPages =
                                if (selectedDownloadPages.size == pageCount) {
                                    emptySet()
                                } else {
                                    (0 until pageCount).toSet()
                                }
                        },
                    ) { Text(if (selectedDownloadPages.size == pageCount) "取消全选" else "全选") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pageCount) { page ->
                        val selectedPage = page in selectedDownloadPages
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable {
                                    selectedDownloadPages =
                                        if (selectedPage) {
                                            selectedDownloadPages - page
                                        } else {
                                            selectedDownloadPages +
                                                page
                                        }
                                },
                        ) {
                            coil3.compose.AsyncImage(
                                model =
                                    work.largePreviews.getOrNull(page) ?: work.originals.getOrNull(page),
                                contentDescription = "第 ${page + 1} 张",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (selectedPage) {
                                Surface(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                ) {
                                    AppIcon(
                                        materialSymbol(MaterialSymbol.Check),
                                        "已选择",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = { downloadPageSelection = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        enabled = selectedDownloadPages.isNotEmpty(),
                        onClick = {
                            val pages = selectedDownloadPages
                            downloadPageSelection = false
                            requestDownload(pages)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("下载选中 (${selectedDownloadPages.size})") }
                }
            }
        }
    }
    if (privateDialog && permitted()) {
        ActionSheet(
            onDismissRequest = { privateDialog = false },
            title = { Text(stringResource(R.string.ui_67c6787737)) },
            text = { Text(stringResource(R.string.ui_b6fe094725)) },
            confirmButton = {
                TextButton({
                    privateDialog = false
                    bookmark(false)
                }) {
                    Text(stringResource(R.string.ui_67c6787737))
                }
            },
            dismissButton = {
                TextButton({ privateDialog = false }) {
                    Text(stringResource(R.string.ui_4d0b4688c7))
                }
            },
        )
    }
}
