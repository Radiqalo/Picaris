package io.github.pixivnext.ui

import android.Manifest
import android.content.Intent
import android.text.Html
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*

@Composable
fun DetailScreen(initial: Work, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var work by remember { mutableStateOf(initial) }
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val identity = work.identity(vm.accountId)
    val current = bookmarks[identity]?.apply(work) ?: work
    val actionBusy = identity in busy
    var privateDialog by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
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
        topBar = {
            TopAppBar(
                scrollBehavior = LocalAppBarScrollBehavior.current,
                title = {
                    Text(
                        if (work.isNovel) strings.getString(R.string.ui_094616a53c)
                        else strings.getString(R.string.ui_4dad196a35)
                    )
                },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenu,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenu,
                        onCheckedChange = { fabMenu = it },
                    ) {
                        AppIcon(if (fabMenu) Glyph.Close else Glyph.More, "更多操作")
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenu = false
                        navigate(Reader(current))
                    },
                    text = { Text(if (work.isNovel) "开始阅读" else "查看原图") },
                    icon = { AppIcon(if (work.isNovel) Glyph.Book else Glyph.Play, null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenu = false
                        share()
                    },
                    text = { Text(strings.getString(R.string.ui_7a92434114)) },
                    icon = { AppIcon(Glyph.Share, null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenu = false
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    text = { Text(strings.getString(R.string.ui_255d6cabdc)) },
                    icon = { AppIcon(Glyph.Download, null) },
                )
                if (!current.is_bookmarked && !actionBusy)
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenu = false
                            privateDialog = true
                        },
                        text = { Text("非公开收藏") },
                        icon = { AppIcon(Glyph.Heart, null) },
                    )
            }
        },
        bottomBar = {
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        { bookmark() },
                        enabled = !actionBusy,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        AppIcon(
                            if (current.is_bookmarked) Glyph.HeartFilled else Glyph.Heart,
                            null,
                            Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (current.is_bookmarked) strings.getString(R.string.ui_2d2cdabf29)
                            else strings.getString(R.string.ui_7355147b10)
                        )
                    }
                    IconButton({ navigate(Comments(current)) }) {
                        AppIcon(Glyph.Comment, "评论区")
                    }
                }
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
                        Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        WorkImage(
                            work,
                            Modifier.fillMaxWidth()
                                .aspectRatio(work.aspect)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { navigate(Reader(current)) }
                                .testTag("detailImage"),
                        )
                    }
                LazyColumn(
                    Modifier.weight(1f).testTag("detailList"),
                    contentPadding = PaddingValues(20.dp),
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
                                            .clip(MaterialTheme.shapes.large)
                                            .clickable { navigate(Reader(current)) }
                                            .testTag("detailImage"),
                                    )
                                }
                            } else
                                WorkImage(
                                    work,
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(work.aspect.coerceAtLeast(.85f))
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable { navigate(Reader(current)) }
                                        .testTag("detailImage"),
                                )
                        }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (work.demo >= 0)
                                SuggestionChip(
                                    {},
                                    label = { Text(strings.getString(R.string.ui_0ecf6f27ba)) },
                                    icon = { AppIcon(Glyph.Discover, null, Modifier.size(16.dp)) },
                                )
                            Text(work.title, style = MaterialTheme.typography.headlineMedium)
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
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            UserRow(work.user, { navigate(Author(work.user)) }) {
                                AppIcon(Glyph.Arrow, strings.getString(R.string.ui_2184c031c9))
                            }
                        }
                    }
                    if (work.isNovel)
                        item {
                            FilledTonalButton(
                                { navigate(Reader(current)) },
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
                    if (work.tags.isNotEmpty())
                        item {
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
                    if (work.caption.isNotBlank())
                        item {
                            Text(
                                Html.fromHtml(work.caption, Html.FROM_HTML_MODE_COMPACT).toString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    if (work.series != null)
                        item {
                            OutlinedButton(
                                {
                                    navigate(
                                        Collection(
                                            work.series!!.title,
                                            "series",
                                            "novel",
                                            work.series!!.id,
                                        )
                                    )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("系列 · ${work.series!!.title}")
                            }
                        }
                    item {
                        Text(
                            if (work.demo >= 0) strings.getString(R.string.ui_950b103464)
                            else "ID ${work.id}  ·  ${work.create_date.take(10)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!work.isNovel)
                        item {
                            OutlinedButton(
                                {
                                    navigate(
                                        Collection(
                                            strings.getString(R.string.ui_29ffbeb614),
                                            "related",
                                            userId = work.id,
                                        )
                                    )
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(strings.getString(R.string.ui_7c1af69922))
                                Spacer(Modifier.width(8.dp))
                                AppIcon(Glyph.Arrow, null)
                            }
                        }
                    item(key = "comments") { CommentPreview(current, vm, navigate) }
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
fun AuthorScreen(initial: User, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var user by remember { mutableStateOf(initial) }
    var kind by rememberSaveable { mutableStateOf("illust") }
    var busy by remember { mutableStateOf(false) }
    val demo by vm.demo.collectAsStateWithLifecycle()
    LaunchedEffect(user.id) {
        if (!demo) runCatching { vm.user(user) }.onSuccess { user = it }
    }
    Column {
        ScreenBar(user.name, back = back)
        Surface(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Avatar(user, Modifier.size(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "@${user.account.ifEmpty {user.id.toString()}}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        {
                            vm.run {
                                busy = true
                                try {
                                    user = vm.follow(user)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            if (user.is_followed) strings.getString(R.string.ui_f1f896b6ff)
                            else strings.getString(R.string.ui_7ac0d5c9c3)
                        )
                    }
                }
                if (user.comment.isNotEmpty())
                    Text(user.comment, style = MaterialTheme.typography.bodyMedium)
            }
        }
        KindTabs(kind, { kind = it })
        FeedGrid(
            FeedSpec(section = "user", kind = kind, userId = user.id),
            vm,
            navigate,
            Modifier.weight(1f),
        )
    }
}
