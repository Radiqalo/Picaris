package io.github.radiqalo.picaris.ui

import androidx.compose.ui.res.stringResource

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun ReaderScreen(
    work: Work,
    vm: AppViewModel,
    back: () -> Unit,
    initialPage: Int? = null,
    localUris: List<String> = emptyList(),
) {

    if (work.isNovel) {
        NovelReader(work, vm, back)
        return
    }
    val s by vm.settings.collectAsStateWithLifecycle()
    var chrome by rememberSaveable { mutableStateOf(true) }
    var vertical by rememberSaveable { mutableStateOf(false) }
    var viewerQuality by rememberSaveable(work.type, work.id) { mutableStateOf(s.largeImageQuality) }
    val imagePages = when {
        localUris.isNotEmpty() -> localUris
        viewerQuality == "original" -> work.originals
        else -> (0 until maxOf(work.page_count, work.meta_pages.size, 1))
            .map { work.pageImageForQuality(it, viewerQuality) }
    }
    var pageDialog by rememberSaveable { mutableStateOf(false) }
    var pageInput by rememberSaveable { mutableStateOf("") }
    var localPages by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val loadingImageUrls = remember { mutableStateSetOf<String>() }
    fun selectViewerQuality(quality: String) {
        if (viewerQuality != quality) {
            viewerQuality = quality
        }
    }
    fun pageUrl(page: Int): String = when {
        localUris.isNotEmpty() -> localUris[page]
        viewerQuality == "original" -> localPages[page] ?: imagePages[page]
        else -> imagePages[page]
    }
    fun settingsPageUrl(page: Int): String =
        if (s.largeImageQuality == "original") {
            work.originals.getOrNull(page).orEmpty()
        } else {
            work.pageImageForQuality(page, s.largeImageQuality)
        }
    LaunchedEffect(work.id) {
        localPages =
            vm.completed(work)
                .filter { it.kind in listOf("illust", "manga") }
                .associate { it.page to it.uri }
    }
    val count = localUris.size.takeIf { it > 0 } ?: work.originals.size
    val pager = rememberPagerState(pageCount = { count })
    val list = rememberLazyListState()
    fun startDownload() {
        val page = if (vertical) list.firstVisibleItemIndex else pager.currentPage
        vm.download(
            work,
            if (count > 1 && work.type != "ugoira") setOf(page) else null,
            ugoiraAsGif = work.type == "ugoira",
        )
    }
    val downloadPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startDownload()
    }
    val bg = if (s.blackReader) Color.Black else MaterialTheme.colorScheme.background
    val text = if (s.blackReader) Color.White else MaterialTheme.colorScheme.onBackground
    val scope = rememberCoroutineScope()
    var restored by remember(work.id) { mutableStateOf(false) }
    var pendingLayoutPage by remember(work.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(work.id, vertical, pendingLayoutPage) {
        if (work.type != "ugoira" && count > 0) {
            val targetPage = pendingLayoutPage ?: if (!restored) initialPage ?: vm.readingProgress(work) else null
            targetPage?.coerceIn(0, count - 1)?.let { page ->
                if (vertical) list.scrollToItem(page)
                else pager.scrollToPage(page)
            }
        }
        pendingLayoutPage = null
        restored = true
    }
    LaunchedEffect(vertical, restored) {
        if (restored)
            snapshotFlow { if (vertical) list.firstVisibleItemIndex else pager.currentPage }
                .distinctUntilChanged()
                .collect { vm.recordProgress(work, it) }
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
                        ReaderImage(
                            pageUrl(page),
                            "${work.title} 第${page+1}页",
                            Modifier.fillMaxWidth().aspectRatio(work.aspect),
                            flashOnLoad = pageUrl(page) != settingsPageUrl(page),
                            onLoadingChanged = { isLoading ->
                                if (isLoading) loadingImageUrls.add(pageUrl(page))
                                else loadingImageUrls.remove(pageUrl(page))
                            },
                        ) {
                            chrome = !chrome
                        }
                    }
                }
            else
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page
                    ->
                    ReaderImage(
                        pageUrl(page),
                        work.title,
                        Modifier.fillMaxSize(),
                        flashOnLoad = pageUrl(page) != settingsPageUrl(page),
                        onLoadingChanged = { isLoading ->
                            if (isLoading) loadingImageUrls.add(pageUrl(page))
                            else loadingImageUrls.remove(pageUrl(page))
                        },
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
                            AppIcon(materialSymbol(MaterialSymbol.ArrowBack), stringResource(R.string.ui_11d0241540))
                        }
                    },
                    actions = {
                        if (localUris.isEmpty() && work.type != "ugoira") {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                val currentPage = if (vertical) list.firstVisibleItemIndex else pager.currentPage
                                if (viewerQuality == "original" && pageUrl(currentPage) in loadingImageUrls) {
                                    CircularWavyProgressIndicator(Modifier.size(18.dp))
                                }
                                IconToggleButton(
                                    checked = viewerQuality == "original",
                                    onCheckedChange = { isOriginal ->
                                        selectViewerQuality(if (isOriginal) "original" else "large")
                                    },
                                    modifier = Modifier.semantics {
                                        stateDescription = "当前画质：${if (viewerQuality == "original") "原图" else "高"}"
                                    },
                                    colors = IconButtonDefaults.iconToggleButtonColors(
                                        contentColor = text,
                                        checkedContentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    AppIcon(
                                        materialSymbol(MaterialSymbol.Hd),
                                        if (viewerQuality == "original") "切换到高画质" else "切换到原图",
                                        tint = if (viewerQuality == "original") MaterialTheme.colorScheme.primary else text,
                                    )
                                }
                            }
                        }
                        if (localUris.isEmpty()) IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                downloadPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startDownload()
                            }
                        }) {
                            AppIcon(materialSymbol(MaterialSymbol.Download), stringResource(R.string.ui_255d6cabdc))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = bg.copy(alpha = .8f),
                            titleContentColor = text,
                            navigationIconContentColor = text,
                            actionIconContentColor = text,
                        ),
                )
            }
            if (count > 1)
                AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
                    val page by
                        remember(vertical) {
                            derivedStateOf {
                                if (vertical) list.firstVisibleItemIndex else pager.currentPage
                            }
                        }
                    HorizontalFloatingToolbar(
                        expanded = true,
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp),
                    ) {
                        IconButton(onClick = {
                            pendingLayoutPage = pendingLayoutPage ?: page
                            restored = false
                            vertical = !vertical
                        }) {
                            AppIcon(
                                materialSymbol(MaterialSymbol.Book),
                                if (vertical) stringResource(R.string.ui_86380149bb)
                                else stringResource(R.string.ui_4a58070031),
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (vertical) list.animateScrollToItem(page - 1)
                                    else pager.animateScrollToPage(page - 1)
                                }
                            },
                            enabled = page > 0,
                        ) { AppIcon(materialSymbol(MaterialSymbol.ArrowBack), "上一张") }
                        TextButton(onClick = { pageInput = "${page + 1}"; pageDialog = true }) {
                            Text("${page + 1} / $count", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (vertical) list.animateScrollToItem(page + 1)
                                    else pager.animateScrollToPage(page + 1)
                                }
                            },
                            enabled = page < count - 1,
                        ) { AppIcon(materialSymbol(MaterialSymbol.ChevronRight), "下一张") }
                    }
                }
        }
    }
    if (pageDialog) {
        val selectedPage = pageInput.toIntOrNull()?.takeIf { it in 1..count }
        ActionSheet(
            onDismissRequest = { pageDialog = false },
            title = { Text("跳转页码") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { value -> pageInput = value.filter(Char::isDigit) },
                    label = { Text("页码（1–$count）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedPage?.let { selected ->
                            scope.launch {
                                if (vertical) list.scrollToItem(selected - 1)
                                else pager.scrollToPage(selected - 1)
                            }
                        }
                        pageDialog = false
                    },
                    enabled = selectedPage != null,
                ) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { pageDialog = false }) { Text("取消") } },
        )
    }

}

@Composable
private fun ReaderImage(
    url: String,
    title: String,
    modifier: Modifier = Modifier,
    flashOnLoad: Boolean = false,
    onLoadingChanged: (Boolean) -> Unit = {},
    toggle: () -> Unit,
) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    val imageAlpha = remember(url) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val request =
        remember(url, retry) {
            coil3.request.ImageRequest.Builder(context)
                .data(url)
                .listener(
                    onStart = {
                        failed = false
                        currentOnLoadingChanged(true)
                    },
                    onSuccess = { _, _ ->
                        failed = false
                        currentOnLoadingChanged(false)
                        if (flashOnLoad) scope.launch {
                            imageAlpha.snapTo(0.72f)
                            imageAlpha.animateTo(1f, tween(260))
                        }
                    },
                    onError = { _, _ ->
                        failed = true
                        currentOnLoadingChanged(false)
                    },
                )
                .build()
        }
    val zoomableState = rememberZoomableImageState(
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)),
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        ZoomableAsyncImage(
            request,
            title,
            Modifier.matchParentSize().graphicsLayer { alpha = imageAlpha.value },
            state = zoomableState,
            onClick = { toggle() },
        )
        if (failed)
            EmptyState(
                stringResource(R.string.reader_image_error),
                stringResource(R.string.reader_image_error_detail),
                action = stringResource(R.string.ui_e2d53a6d3a),
            ) {
                retry++
            }
    }
}

@Composable
fun UgoiraPlayer(work: Work, vm: AppViewModel, toggle: () -> Unit) {

    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycleState by
        androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow
            .collectAsStateWithLifecycle()
    val foreground by
        rememberUpdatedState(lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    LaunchedEffect(work.id, retry) {
        error = null
        try {
            vm.ugoiraFrames(work, retry) { playing && foreground }
                .collect { frame ->
                    image = frame
                    ready = true
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
                    stringResource(R.string.ui_138a785284),
                    error!!,
                    action = stringResource(R.string.ui_e2d53a6d3a),
                ) {
                    retry++
                }
            image != null -> Image(image!!, work.title, Modifier.fillMaxSize())
            else -> CircularWavyProgressIndicator()
        }
        if (ready)
            FilledTonalIconButton(
                { playing = !playing },
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                AppIcon(
                    if (playing) materialSymbol(MaterialSymbol.Pause) else materialSymbol(MaterialSymbol.PlayArrow),
                    if (playing) stringResource(R.string.ui_130448bce6)
                    else stringResource(R.string.ui_21925350de),
                )
            }
    }
}

@Composable
fun NovelReader(work: Work, vm: AppViewModel, back: () -> Unit) {

    val settings by vm.settings.collectAsStateWithLifecycle()
    var body by remember { mutableStateOf<NovelBody?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(work.id, retry) {
        error = null
        try {
            body = vm.novelBody(work)
            vm.readingProgress(work)?.let {
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
                .collect { vm.recordProgress(work, it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = LocalAppBarScrollBehavior.current,
                title = { Text(work.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(back) {
                        AppIcon(materialSymbol(MaterialSymbol.ArrowBack), stringResource(R.string.ui_11d0241540))
                    }
                },
                actions = {
                    IconButton({ controls = true }) {
                        AppIcon(materialSymbol(MaterialSymbol.Settings), stringResource(R.string.ui_bc0832465e))
                    }
                },
            )
        }
    ) { padding ->
        when {
            error != null ->
                Box(Modifier.padding(padding)) {
                    EmptyState(
                        stringResource(R.string.ui_5c3b597ab6),
                        error!!,
                        materialSymbol(MaterialSymbol.Book),
                        stringResource(R.string.ui_e2d53a6d3a),
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
                                            stringResource(R.string.ui_b0911e6444),
                                            Modifier.fillMaxWidth()
                                                .heightIn(min = 100.dp, max = 600.dp),
                                        )
                                    else
                                        Text(
                                            stringResource(R.string.ui_3549b637f3),
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
                                stringResource(R.string.ui_4b65d10385),
                                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        }
    }
    if (controls && navigationPermission()())
        ModalBottomSheet(onDismissRequest = { controls = false }) {
            Column(
                Modifier.padding(24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.ui_bc0832465e),
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
                    stringResource(R.string.ui_2334433cb5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
}
