package io.github.pixivnext.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okhttp3.Request

@Composable
fun ReaderScreen(work: Work, vm: AppViewModel, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    if (work.isNovel) {
        NovelReader(work, vm, back)
        return
    }
    val s by vm.settings.collectAsStateWithLifecycle()
    var chrome by rememberSaveable { mutableStateOf(true) }
    var vertical by rememberSaveable { mutableStateOf(false) }
    var localPages by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(work.id) {
        localPages =
            vm.dao
                .completed(vm.accountId, work.id)
                .filter { it.kind in listOf("illust", "manga") }
                .associate { it.page to it.uri }
    }
    val count = if (work.demo >= 0) work.page_count else work.originals.size
    val pager = rememberPagerState(pageCount = { count })
    val list = rememberLazyListState()
    val bg = if (s.blackReader) Color.Black else MaterialTheme.colorScheme.background
    val text = if (s.blackReader) Color.White else MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(work.id) {
        vm.dao
            .historyItem(vm.accountId, work.id, work.type)
            ?.progress
            ?.coerceIn(0, (count - 1).coerceAtLeast(0))
            ?.let {
                pager.scrollToPage(it)
                list.scrollToItem(it)
            }
        restored = true
    }
    LaunchedEffect(vertical, restored) {
        if (restored)
            snapshotFlow { if (vertical) list.firstVisibleItemIndex else pager.currentPage }
                .distinctUntilChanged()
                .collect { vm.repo.record(vm.accountId, work, it) }
    }
    Surface(Modifier.fillMaxSize(), color = bg, contentColor = text) {
        Box {
            if (work.type == "ugoira") UgoiraPlayer(work, vm) { chrome = !chrome }
            else if (vertical)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    contentPadding = PaddingValues(vertical = 80.dp),
                ) {
                    items(count) { page ->
                        if (work.demo >= 0)
                            DemoArt(
                                work.demo + page,
                                Modifier.fillMaxWidth().aspectRatio(work.aspect).clickable {
                                    chrome = !chrome
                                },
                            )
                        else
                            ReaderImage(
                                localPages[page] ?: work.originals[page],
                                "${work.title} 第${page+1}页",
                                Modifier.fillMaxWidth().aspectRatio(work.aspect),
                            ) {
                                chrome = !chrome
                            }
                    }
                }
            else
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page
                    ->
                    if (work.demo >= 0) {
                        val zoom = rememberZoomableState()
                        DemoArt(
                            work.demo + page,
                            Modifier.fillMaxWidth()
                                .aspectRatio(work.aspect)
                                .zoomable(zoom)
                                .clickable { chrome = !chrome },
                        )
                    } else
                        ReaderImage(
                            localPages[page] ?: work.originals[page],
                            work.title,
                            Modifier.fillMaxSize(),
                        ) {
                            chrome = !chrome
                        }
                }
            AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter)) {
                TopAppBar(
                    scrollBehavior = LocalAppBarScrollBehavior.current,
                    title = { Text(work.title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(back) {
                            AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = bg.copy(alpha = .8f),
                            titleContentColor = text,
                            navigationIconContentColor = text,
                            actionIconContentColor = text,
                        ),
                    actions = {
                        if (count > 1)
                            IconButton({ vertical = !vertical }) {
                                AppIcon(
                                    Glyph.Book,
                                    if (vertical) strings.getString(R.string.ui_86380149bb)
                                    else strings.getString(R.string.ui_4a58070031),
                                )
                            }
                    },
                )
            }
            if (count > 1)
                AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
                    Surface(color = bg.copy(alpha = .8f), contentColor = text) {
                        Column(Modifier.navigationBarsPadding().padding(20.dp)) {
                            val page by
                                remember(vertical) {
                                    derivedStateOf {
                                        if (vertical) list.firstVisibleItemIndex
                                        else pager.currentPage
                                    }
                                }
                            Text(
                                "${page+1} / $count",
                                Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Slider(
                                rememberSliderState(
                                        page.toFloat(),
                                        steps = (count - 2).coerceAtLeast(0),
                                        trackRange = 0f..(count - 1).toFloat(),
                                    )
                                    .also { it.value = page.toFloat() },
                                onValueChange = {
                                    scope.launch {
                                        if (vertical) list.scrollToItem(it.toInt())
                                        else pager.scrollToPage(it.toInt())
                                    }
                                },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun ReaderImage(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    toggle: () -> Unit,
) {
    val context = LocalContext.current
    val strings = androidx.compose.ui.platform.LocalResources.current
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    val request =
        remember(url, retry) {
            coil3.request.ImageRequest.Builder(context)
                .data(url)
                .listener(
                    onStart = { failed = false },
                    onSuccess = { _, _ -> failed = false },
                    onError = { _, _ -> failed = true },
                )
                .build()
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        ZoomableAsyncImage(request, title, Modifier.matchParentSize(), onClick = { toggle() })
        if (failed)
            EmptyState(
                strings.getString(R.string.reader_image_error),
                strings.getString(R.string.reader_image_error_detail),
                action = strings.getString(R.string.ui_e2d53a6d3a),
            ) {
                retry++
            }
    }
}

@Composable
fun UgoiraPlayer(work: Work, vm: AppViewModel, toggle: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val lifecycleState by
        androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow
            .collectAsStateWithLifecycle()
    val foreground by
        rememberUpdatedState(lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    LaunchedEffect(work.id, retry) {
        error = null
        try {
            val downloads = vm.dao.completed(vm.accountId, work.id)
            val metadata =
                downloads
                    .firstOrNull { it.kind == "frames" }
                    ?.let { task ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver
                                    .openInputStream(task.uri.toUri())
                                    ?.bufferedReader()
                                    ?.use { AppJson.decodeFromString<Ugoira>(it.readText()) }
                            }
                                .getOrNull()
                        }
                    } ?: vm.repo.ugoira(vm.accountId, work.id)
            require(metadata.frames.isNotEmpty() && metadata.frames.size <= 10000) { "动图帧信息无效" }
            val file = File(context.cacheDir, "ugoira_${vm.accountId}_${work.id}.zip")
            if (retry > 0) file.delete()
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    val saved = downloads.firstOrNull { it.kind == "ugoira" }
                    if (saved != null)
                        runCatching {
                            context.contentResolver.openInputStream(saved.uri.toUri())?.use { input
                                ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                        }
                            .onFailure { file.delete() }
                }
                if (!file.exists()) {
                    val partial = File(context.cacheDir, "ugoira_${work.id}.part")
                    val call =
                        vm.downloadsNetwork()
                            .newCall(Request.Builder().url(metadata.zip_urls.medium).build())
                    val cancellation =
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                awaitCancellation()
                            } finally {
                                call.cancel()
                            }
                        }
                    try {
                        call.execute().use { response ->
                            check(response.isSuccessful) { "动图下载失败（${response.code}）" }
                            response.body.byteStream().use { input ->
                                partial.outputStream().use { out ->
                                    val buffer = ByteArray(65536)
                                    var bytes = 0L
                                    while (true) {
                                        ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        bytes += n
                                        check(bytes < 256 * 1024 * 1024) {
                                            strings.getString(R.string.ui_074bfabad3)
                                        }
                                        out.write(buffer, 0, n)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        ensureActive()
                        throw e
                    } finally {
                        cancellation.cancel()
                    }
                    ensureActive()
                    check(partial.renameTo(file)) { strings.getString(R.string.ui_53c753e091) }
                }
            }
            withContext(Dispatchers.IO) {
                ZipFile(file).use { zip ->
                    ready = true
                    while (isActive) {
                        for (frame in metadata.frames) {
                            while (!playing || !foreground) delay(100)
                            val entry =
                                zip.getEntry(frame.file)
                                    ?: error(strings.getString(R.string.ui_b26a82da6c))
                            check(entry.size in 1..32 * 1024 * 1024) {
                                strings.getString(R.string.ui_34a2b4c275)
                            }
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            val options =
                                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            options.inJustDecodeBounds = false
                            options.inSampleSize =
                                (maxOf(options.outWidth, options.outHeight) / 1600).coerceAtLeast(1)
                            val bitmap =
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    ?: error(strings.getString(R.string.ui_e868abf5f4))
                            withContext(Dispatchers.Main) { image = bitmap.asImageBitmap() }
                            delay(frame.delay.toLong().coerceAtLeast(16))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    Box(Modifier.fillMaxSize().clickable(onClick = toggle), contentAlignment = Alignment.Center) {
        when {
            error != null ->
                EmptyState(
                    strings.getString(R.string.ui_138a785284),
                    error!!,
                    action = strings.getString(R.string.ui_e2d53a6d3a),
                ) {
                    retry++
                }
            image != null -> Image(image!!, work.title, Modifier.fillMaxSize())
            else -> LoadingIndicator()
        }
        if (ready)
            FilledTonalIconButton(
                { playing = !playing },
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                AppIcon(
                    if (playing) Glyph.Pause else Glyph.Play,
                    if (playing) strings.getString(R.string.ui_130448bce6)
                    else strings.getString(R.string.ui_21925350de),
                )
            }
    }
}

@Composable
fun NovelReader(work: Work, vm: AppViewModel, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    var body by remember { mutableStateOf<NovelBody?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val list = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(work.id, retry) {
        error = null
        try {
            body =
                if (work.demo >= 0) NovelBody(Demo.novel)
                else {
                    val downloaded =
                        vm.dao.completed(vm.accountId, work.id).firstOrNull {
                            it.kind == "novel" && it.uri.isNotEmpty()
                        }
                    val local = downloaded?.let {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver
                                    .openInputStream(it.uri.toUri())
                                    ?.bufferedReader()
                                    ?.use { r -> r.readText() }
                            }
                                .getOrNull()
                        }
                    }
                    if (local != null) NovelBody(local) else vm.repo.novel(vm.accountId, work.id)
                }
            vm.dao.historyItem(vm.accountId, work.id, work.type)?.progress?.let {
                list.scrollToItem(it.coerceAtMost(body!!.text.split('\n').size))
            }
            restored = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    LaunchedEffect(body, restored) {
        if (body != null && restored)
            snapshotFlow { list.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { vm.repo.record(vm.accountId, work, it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = LocalAppBarScrollBehavior.current,
                title = { Text(work.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(Glyph.Back, strings.getString(R.string.ui_11d0241540))
                    }
                },
                actions = {
                    IconButton({ controls = true }) {
                        AppIcon(Glyph.Settings, strings.getString(R.string.ui_bc0832465e))
                    }
                },
            )
        }
    ) { padding ->
        when {
            error != null ->
                Box(Modifier.padding(padding)) {
                    EmptyState(
                        strings.getString(R.string.ui_5c3b597ab6),
                        error!!,
                        Glyph.Book,
                        strings.getString(R.string.ui_e2d53a6d3a),
                    ) {
                        retry++
                    }
                }
            body == null -> Box(Modifier.padding(padding)) { LoadingState() }
            else ->
                SelectionContainer {
                    LazyColumn(
                        Modifier.padding(padding).fillMaxSize(),
                        state = list,
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Text(work.title, style = MaterialTheme.typography.headlineLarge) }
                        item {
                            Text(
                                work.user.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                        items(body!!.text.split('\n')) { line ->
                            when {
                                line.contains("[uploadedimage:") ||
                                    line.contains("[pixivimage:") -> {
                                    val id =
                                        Regex("\\[(?:uploadedimage|pixivimage):([^]]+)]")
                                            .find(line)
                                            ?.groupValues
                                            ?.get(1)
                                    val url = body!!.images[id]
                                    if (url != null)
                                        AsyncImage(
                                            url,
                                            strings.getString(R.string.ui_b0911e6444),
                                            Modifier.fillMaxWidth()
                                                .heightIn(min = 100.dp, max = 600.dp),
                                        )
                                    else
                                        Text(
                                            strings.getString(R.string.ui_3549b637f3),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                }
                                line.contains("[chapter:") ->
                                    Text(
                                        line.replace(Regex("\\[chapter:([^]]+)]"), "$1"),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(vertical = 20.dp),
                                    )
                                line.contains("[newpage]") ->
                                    HorizontalDivider(Modifier.padding(vertical = 24.dp))
                                else ->
                                    Text(
                                        line.replace(
                                            Regex("\\[\\[rb:([^ >]+) > ([^]]+)]]"),
                                            "$1（$2）",
                                        ),
                                        fontSize = settings.novelFont.sp,
                                        lineHeight = settings.novelSpacing.sp,
                                    )
                            }
                        }
                        item {
                            Text(
                                strings.getString(R.string.ui_4b65d10385),
                                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        }
    }
    if (controls)
        ModalBottomSheet(onDismissRequest = { controls = false }) {
            Column(
                Modifier.padding(24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    strings.getString(R.string.ui_bc0832465e),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("字号 · ${settings.novelFont}")
                Slider(
                    rememberSliderState(
                            settings.novelFont.toFloat(),
                            steps = 17,
                            trackRange = 14f..32f,
                        )
                        .also { it.value = settings.novelFont.toFloat() },
                    onValueChange = { v -> vm.update { it.copy(novelFont = v.toInt()) } },
                )
                Text("行距 · ${settings.novelSpacing}")
                Slider(
                    rememberSliderState(
                            settings.novelSpacing.toFloat(),
                            steps = 27,
                            trackRange = 24f..52f,
                        )
                        .also { it.value = settings.novelSpacing.toFloat() },
                    onValueChange = { v -> vm.update { it.copy(novelSpacing = v.toInt()) } },
                )
                Text(
                    strings.getString(R.string.ui_2334433cb5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
}
