package io.github.radiqalo.picaris.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

private fun downloadProgressText(
    bytes: Long,
    total: Long,
): String =
    if (total > 0) {
        "${formatByteSize(bytes)} / ${formatByteSize(total)}"
    } else {
        formatByteSize(bytes)
    }

private data class DownloadSpeedSample(
    val bytes: Long,
    val time: Long,
    val smoothedSpeed: Long,
)

private data class DownloadGroup(
    val workId: Long,
    val tasks: List<DownloadEntity>,
) {
    val work: Work? =
        tasks.firstNotNullOfOrNull {
            runCatching { AppJson.decodeFromString<Work>(it.workJson) }.getOrNull()
        }
    val status: String
        get() =
            when {
                tasks.any { it.status == "running" } -> "running"
                tasks.any { it.status == "queued" } -> "queued"
                tasks.any { it.status == "failed" } -> "failed"
                tasks.any { it.status == "paused" } -> "paused"
                tasks.any { it.status == "cancelled" } -> "cancelled"
                else -> "complete"
            }
    val bytes get() = tasks.sumOf { it.bytes }
    val total get() = tasks.sumOf { it.total }
    val completedCount get() = tasks.count { it.status == "complete" }
    val progress: Float
        get() =
            if (tasks.isEmpty()) {
                0f
            } else {
                tasks
                    .sumOf { task ->
                        when {
                            task.status == "complete" -> 1.0
                            task.total > 0 ->
                                (task.bytes.toDouble() / task.total).coerceIn(
                                    0.0,
                                    1.0,
                                )
                            else -> 0.0
                        }
                    }.div(tasks.size)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
    val createdAt get() = tasks.minOfOrNull { it.createdAt } ?: 0L
    val uploadedAt get() =
        runCatching {
            java.time.OffsetDateTime
                .parse(work?.create_date)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    val imageCount get() = tasks.count { it.kind == "illust" || it.kind == "manga" }
    val pageCount get() = work?.page_count?.takeIf { it > 0 } ?: imageCount
    val title get() = work?.title ?: tasks.firstOrNull()?.title.orEmpty()
    val localImageUris get() =
        tasks
            .asSequence()
            .filter {
                it.status == "complete" &&
                    it.kind in listOf("illust", "manga") &&
                    it.uri.isNotBlank()
            }.sortedBy { it.page }
            .map { it.uri }
            .toList()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val tasks by vm.downloadList.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf("all") }
    var selecting by remember(vm.accountId) { mutableStateOf(false) }
    var selected by remember(vm.accountId) { mutableStateOf(emptySet<Long>()) }
    var confirmRemoval by remember(vm.accountId) { mutableStateOf(false) }
    var keepDownloadedFiles by remember(vm.accountId) { mutableStateOf(false) }
    var sortOptionsExpanded by remember(vm.accountId) { mutableStateOf(false) }
    var statusOptionsExpanded by remember(vm.accountId) { mutableStateOf(false) }
    var sortMode by remember(vm.accountId) { mutableStateOf("download_desc") }
    var galleryMode by androidx.compose.runtime.saveable.rememberSaveable(vm.accountId) {
        mutableStateOf(false)
    }
    val novelMode = settings.contentKind == "novel"
    val downloadListState = rememberLazyListState()
    val downloadGridState = rememberLazyStaggeredGridState()
    var speeds by remember(vm.accountId) { mutableStateOf(emptyMap<Long, Long>()) }
    val speedSamples = remember(vm.accountId) { mutableMapOf<Long, DownloadSpeedSample>() }
    LaunchedEffect(tasks) {
        val now = SystemClock.elapsedRealtime()
        val runningGroups =
            tasks
                .groupBy { it.workId }
                .filterValues { groupTasks -> groupTasks.any { it.status == "running" } }
        val nextSpeeds = speeds.filterKeys { it in runningGroups }.toMutableMap()
        runningGroups.forEach { (workId, groupTasks) ->
            val bytes = groupTasks.sumOf { it.bytes }
            val previous = speedSamples[workId]
            if (previous == null) {
                speedSamples[workId] = DownloadSpeedSample(bytes, now, 0L)
            } else if (now - previous.time >= 1_000L) {
                val instantaneous =
                    ((bytes - previous.bytes).coerceAtLeast(0L) * 1_000L) /
                        (now - previous.time)
                val smoothed =
                    if (previous.smoothedSpeed == 0L) {
                        instantaneous
                    } else {
                        (previous.smoothedSpeed * .65 + instantaneous * .35).toLong()
                    }
                nextSpeeds[workId] = smoothed
                speedSamples[workId] = DownloadSpeedSample(bytes, now, smoothed)
            }
        }
        speedSamples.keys.retainAll(runningGroups.keys)
        speeds = nextSpeeds
    }
    val groups =
        remember(tasks, sortMode) {
            val grouped =
                tasks
                    .groupBy { it.workId }
                    .map { (workId, groupTasks) -> DownloadGroup(workId, groupTasks) }
                    .sortedByDescending { it.createdAt }
            val comparator =
                when (sortMode.removeSuffix("_asc").removeSuffix("_desc")) {
                    "upload" -> compareBy<DownloadGroup> { it.uploadedAt }
                    "title" -> compareBy { it.title.lowercase() }
                    "author" ->
                        compareBy {
                            it.work
                                ?.user
                                ?.name
                                .orEmpty()
                                .lowercase()
                        }
                    else -> compareBy { it.createdAt }
                }
            grouped.sortedWith(if (sortMode.endsWith("_asc")) comparator else comparator.reversed())
        }
    val filtered =
        groups.filter { group ->
            val novel = group.tasks.any { it.kind == "novel" } || group.work?.isNovel == true
            val matchesKind = novel == novelMode
            matchesKind &&
                when (filter) {
                    "not_started" ->
                        group.status == "cancelled" ||
                            (group.status == "paused" && group.progress == 0f)
                    "waiting" -> group.status == "queued"
                    "downloading" ->
                        group.status == "running" ||
                            (group.status == "paused" && group.progress > 0f)
                    "failed" -> group.status == "failed"
                    "complete" -> group.status == "complete"
                    else -> true
                }
        }
    LaunchedEffect(sortMode) {
        if (galleryMode) {
            downloadGridState.scrollToItem(0)
        } else {
            downloadListState.scrollToItem(0)
        }
    }
    LaunchedEffect(novelMode) {
        if (galleryMode) {
            downloadGridState.scrollToItem(0)
        } else {
            downloadListState.scrollToItem(0)
        }
    }
    val chosen = groups.filter { it.workId in selected }
    val selectedTaskIds = chosen.flatMap { it.tasks }.map { it.id }.toSet()
    LaunchedEffect(groups) {
        selected = selected.intersect(groups.map { it.workId }.toSet())
        if (selected.isEmpty()) selecting = false
    }
    LaunchedEffect(selecting) {
        if (selecting) sortOptionsExpanded = false
    }

    fun toggleSelection(workId: Long) {
        selected = if (workId in selected) selected - workId else selected + workId
        selecting = selected.isNotEmpty()
    }

    fun removeSelection(deleteFiles: Boolean) {
        vm.removeDownloadRecords(selectedTaskIds, deleteFiles)
        confirmRemoval = false
        keepDownloadedFiles = true
        selected = emptySet()
        selecting = false
    }

    fun exitSelection() {
        selected = emptySet()
        selecting = false
    }
    BackHandler(enabled = selecting || sortOptionsExpanded) {
        if (sortOptionsExpanded) sortOptionsExpanded = false else exitSelection()
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(
            stringResource(R.string.ui_18df1a67a2),
            {
                when {
                    selecting -> exitSelection()
                    sortOptionsExpanded -> sortOptionsExpanded = false
                    else -> back()
                }
            },
            scrollBehavior = null,
        )
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick = { statusOptionsExpanded = true }) {
                    Text(
                        when (filter) {
                            "not_started" -> "未启动"
                            "waiting" -> "等待中"
                            "downloading" -> "下载中"
                            "complete" -> "已完成"
                            "failed" -> "失败"
                            else -> "全部"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppIcon(materialSymbol(MaterialSymbol.ChevronRight), null, Modifier.rotate(90f))
                }
                DropdownMenu(
                    expanded = statusOptionsExpanded,
                    onDismissRequest = { statusOptionsExpanded = false },
                ) {
                    listOf(
                        "all" to "全部",
                        "not_started" to "未启动",
                        "waiting" to "等待中",
                        "downloading" to "下载中",
                        "complete" to "已完成",
                        "failed" to "失败",
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = {
                                RadioButton(
                                    selected = filter == key,
                                    onClick = null,
                                )
                            },
                            onClick = {
                                filter = key
                                statusOptionsExpanded = false
                            },
                        )
                    }
                }
            }
            Text("${filtered.size} 个作品", style = MaterialTheme.typography.bodyMedium)
            Box {
                IconButton(onClick = { sortOptionsExpanded = true }, enabled = !selecting) {
                    AppIcon(materialSymbol(MaterialSymbol.Sort), "排序方式")
                }
                DropdownMenu(
                    expanded = sortOptionsExpanded,
                    onDismissRequest = { sortOptionsExpanded = false },
                ) {
                    listOf(
                        "upload_desc" to "上传时间 · 降序",
                        "upload_asc" to "上传时间 · 升序",
                        "download_desc" to "下载时间 · 降序",
                        "download_asc" to "下载时间 · 升序",
                        "title_asc" to "标题 · 升序",
                        "title_desc" to "标题 · 降序",
                        "author_asc" to "作者 · 升序",
                        "author_desc" to "作者 · 降序",
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                sortMode = mode
                                sortOptionsExpanded = false
                            },
                            trailingIcon = {
                                if (sortMode ==
                                    mode
                                ) {
                                    AppIcon(materialSymbol(MaterialSymbol.Check), null)
                                }
                            },
                        )
                    }
                }
            }
            IconButton(
                onClick = { galleryMode = !galleryMode },
                enabled = !selecting,
            ) {
                AppIcon(
                    materialSymbol(if (galleryMode) MaterialSymbol.Feed else MaterialSymbol.Image),
                    if (galleryMode) "切换到列表" else "切换到图片流",
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                EmptyState(
                    if (tasks.isEmpty()) stringResource(R.string.ui_92024c1013) else "没有匹配的下载",
                    if (tasks.isEmpty()) stringResource(R.string.ui_ed6ecba3e8) else "可以切换其他状态查看",
                    materialSymbol(MaterialSymbol.Download),
                )
            } else {
                AnimatedContent(
                    targetState = galleryMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState) 1 else -1
                        (
                            slideInHorizontally(
                                initialOffsetX = { direction * it / 18 },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            ) + fadeIn(tween(160))
                        ) togetherWith
                            (
                                slideOutHorizontally(
                                    targetOffsetX = { -direction * it / 18 },
                                    animationSpec = tween(110),
                                ) + fadeOut(tween(100))
                            )
                    },
                    label = "downloadsViewMode",
                ) { showGallery ->
                    if (showGallery) {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Adaptive(160.dp),
                            state = downloadGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    top = 8.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                        ) {
                            items(filtered, key = { it.workId }) { group ->
                                val work = group.work
                                val selectedGroup = group.workId in selected
                                val shape = MaterialTheme.shapes.medium
                                Surface(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                            .clip(
                                                shape,
                                            ).combinedClickable(
                                                onClick = {
                                                    if (selecting) {
                                                        toggleSelection(group.workId)
                                                    } else {
                                                        work?.let {
                                                            vm.record(it)
                                                            if (it.isNovel) {
                                                                navigate(Reader(it))
                                                            } else {
                                                                navigate(
                                                                    Reader(
                                                                        it,
                                                                        localUris = group.localImageUris,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (selecting) {
                                                        toggleSelection(group.workId)
                                                    } else {
                                                        work?.let {
                                                            vm.record(it)
                                                            navigate(Detail(it))
                                                        }
                                                    }
                                                },
                                            ),
                                    shape = shape,
                                    color =
                                        if (selectedGroup) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                ) {
                                    Column {
                                        Box {
                                            if (work != null) {
                                                AsyncImage(
                                                    model =
                                                        group.tasks
                                                            .firstOrNull {
                                                                it.coverUri
                                                                    .isNotEmpty()
                                                            }?.coverUri
                                                            ?: group.localImageUris.firstOrNull()
                                                            ?: work.previews.firstOrNull()
                                                            ?: work.cover,
                                                    contentDescription = "${group.title}，单击阅读，长按查看详情",
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(
                                                                if (work.isNovel) .9f else work.aspect,
                                                            ),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(.78f)
                                                        .background(
                                                            MaterialTheme.colorScheme.secondaryContainer,
                                                        ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AppIcon(
                                                        materialSymbol(MaterialSymbol.Book),
                                                        null,
                                                    )
                                                }
                                            }
                                            if (group.pageCount > 1) {
                                                Text(
                                                    "${group.pageCount}P",
                                                    Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(8.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                                                .copy(
                                                                    alpha = .94f,
                                                                ),
                                                        ).padding(
                                                            horizontal = 8.dp,
                                                            vertical = 4.dp,
                                                        ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            if (selecting) {
                                                Checkbox(
                                                    checked = selectedGroup,
                                                    onCheckedChange = {
                                                        toggleSelection(
                                                            group.workId,
                                                        )
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .align(
                                                                Alignment.TopEnd,
                                                            ).padding(4.dp),
                                                )
                                            }
                                        }
                                        if (settings.showHomeMetadata && work != null) {
                                            Text(
                                                group.title,
                                                Modifier.padding(
                                                    top = 2.dp,
                                                    start = 3.dp,
                                                    end = 3.dp,
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                work.user.name,
                                                Modifier.padding(start = 3.dp, end = 3.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = downloadListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    top = 8.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filtered, key = { it.workId }) { group ->
                                val selectedGroup = group.workId in selected
                                val groupSpeed = speeds[group.workId] ?: 0L
                                val shape = MaterialTheme.shapes.large
                                Surface(
                                    modifier =
                                        Modifier.animateItem().clip(shape).combinedClickable(
                                            onClick = {
                                                if (selecting) {
                                                    toggleSelection(group.workId)
                                                } else {
                                                    group.work?.let { navigate(Detail(it)) }
                                                }
                                            },
                                            onLongClick = {
                                                if (selecting) {
                                                    toggleSelection(group.workId)
                                                } else {
                                                    selected = setOf(group.workId)
                                                    selecting = true
                                                }
                                            },
                                        ),
                                    shape = shape,
                                    color =
                                        if (selectedGroup) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                ) {
                                    Row(Modifier.fillMaxWidth().height(132.dp)) {
                                        val work = group.work
                                        val isProgressVisible =
                                            group.status in setOf("running", "queued", "paused")
                                        Box(
                                            Modifier
                                                .width(
                                                    88.dp,
                                                ).fillMaxHeight()
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                ),
                                        ) {
                                            if (work != null) {
                                                AsyncImage(
                                                    model =
                                                        group.tasks
                                                            .firstOrNull {
                                                                it.coverUri.isNotEmpty()
                                                            }?.coverUri
                                                            ?: group.localImageUris.firstOrNull()
                                                            ?: work.previews.firstOrNull()
                                                            ?: work.cover,
                                                    contentDescription = "预览 ${group.title} 全图或图集",
                                                    modifier =
                                                        Modifier.fillMaxSize().then(
                                                            if (!selecting) {
                                                                Modifier.clickable {
                                                                    navigate(
                                                                        Reader(
                                                                            work,
                                                                            localUris = group.localImageUris,
                                                                        ),
                                                                    )
                                                                }
                                                            } else {
                                                                Modifier
                                                            },
                                                        ),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Box(
                                                    Modifier.fillMaxSize().background(
                                                        MaterialTheme.colorScheme.secondaryContainer,
                                                    ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AppIcon(
                                                        materialSymbol(MaterialSymbol.Book),
                                                        null,
                                                    )
                                                }
                                            }
                                        }
                                        Box(Modifier.weight(1f).fillMaxHeight()) {
                                            Column(
                                                Modifier.fillMaxSize().padding(
                                                    start = 12.dp,
                                                    end = 4.dp,
                                                    top = 8.dp,
                                                    bottom = 8.dp,
                                                ),
                                                verticalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                val author = work?.user?.name.orEmpty()
                                                val size =
                                                    if (group.bytes >
                                                        0
                                                    ) {
                                                        formatByteSize(group.bytes)
                                                    } else {
                                                        ""
                                                    }
                                                val time =
                                                    remember(group.createdAt) {
                                                        java.text
                                                            .SimpleDateFormat(
                                                                "yyyy-MM-dd HH:mm",
                                                                java.util.Locale.getDefault(),
                                                            ).format(
                                                                java.util.Date(group.createdAt),
                                                            )
                                                    }
                                                Text(
                                                    group.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                )
                                                if (isProgressVisible) {
                                                    Column(
                                                        verticalArrangement =
                                                            Arrangement.spacedBy(
                                                                3.dp,
                                                            ),
                                                    ) {
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                if (group.tasks.size >
                                                                    1
                                                                ) {
                                                                    "${group.completedCount}/${group.tasks.size}"
                                                                } else if (group.total >
                                                                    0
                                                                ) {
                                                                    "${(group.progress * 100).toInt()}%"
                                                                } else {
                                                                    "等待中"
                                                                },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                            )
                                                            if (group.status == "running") {
                                                                Text(
                                                                    "${formatByteSize(
                                                                        groupSpeed,
                                                                    )}/s",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    maxLines = 1,
                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                                )
                                                            }
                                                        }
                                                        if (group.total > 0 ||
                                                            group.tasks.size > 1
                                                        ) {
                                                            LinearWavyProgressIndicator(
                                                                progress = { group.progress },
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        } else {
                                                            LinearWavyProgressIndicator(
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Text(
                                                        author.ifBlank { "未知作者" },
                                                        modifier = Modifier.padding(end = 44.dp),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Column(
                                                    Modifier.fillMaxWidth().padding(end = 44.dp),
                                                    horizontalAlignment = Alignment.Start,
                                                    verticalArrangement =
                                                        Arrangement.spacedBy(
                                                            1.dp,
                                                        ),
                                                ) {
                                                    Text(
                                                        size.ifBlank { "大小未知" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                    )
                                                    Text(
                                                        time,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                            Column(
                                                Modifier
                                                    .align(
                                                        Alignment.CenterEnd,
                                                    ).width(44.dp)
                                                    .fillMaxHeight()
                                                    .padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Box(
                                                    Modifier.size(30.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (selecting) {
                                                        Checkbox(
                                                            checked = selectedGroup,
                                                            onCheckedChange = {
                                                                toggleSelection(
                                                                    group.workId,
                                                                )
                                                            },
                                                            modifier = Modifier.size(30.dp),
                                                        )
                                                    }
                                                }
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    if (group.pageCount > 0) {
                                                        if (!isProgressVisible) {
                                                            Text(
                                                                "${group.pageCount}P",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                            )
                                                        }
                                                    }
                                                    val statusLabel =
                                                        when (group.status) {
                                                            "complete" -> "重新检查并下载"
                                                            "failed" -> "点击重试下载"
                                                            "running", "queued" -> "暂停下载"
                                                            "paused", "cancelled" -> "继续下载"
                                                            else -> "下载状态"
                                                        }
                                                    val statusIcon =
                                                        when (group.status) {
                                                            "running", "queued" -> MaterialSymbol.Pause
                                                            "paused", "cancelled" -> MaterialSymbol.PlayArrow
                                                            "failed" -> MaterialSymbol.Refresh
                                                            else -> MaterialSymbol.Check
                                                        }
                                                    IconButton(
                                                        onClick = {
                                                            when (group.status) {
                                                                "running", "queued" ->
                                                                    vm.downloadBatchAction(
                                                                        group.tasks
                                                                            .filter {
                                                                                it.status in
                                                                                    setOf(
                                                                                        "running",
                                                                                        "queued",
                                                                                    )
                                                                            }.map { it.id }
                                                                            .toSet(),
                                                                        "paused",
                                                                    )
                                                                "paused", "cancelled" ->
                                                                    vm.downloadBatchAction(
                                                                        group.tasks
                                                                            .filter {
                                                                                it.status in
                                                                                    setOf(
                                                                                        "paused",
                                                                                        "cancelled",
                                                                                    )
                                                                            }.map { it.id }
                                                                            .toSet(),
                                                                        "queued",
                                                                    )
                                                                "failed", "complete" ->
                                                                    vm.retryDownloads(
                                                                        group.tasks
                                                                            .filter {
                                                                                it.status in
                                                                                    setOf(
                                                                                        "failed",
                                                                                        "complete",
                                                                                    )
                                                                            }.map { it.id }
                                                                            .toSet(),
                                                                    )
                                                                else -> Unit
                                                            }
                                                        },
                                                        modifier = Modifier.size(32.dp),
                                                        enabled = !selecting,
                                                    ) {
                                                        AppIcon(
                                                            materialSymbol(statusIcon),
                                                            statusLabel,
                                                            Modifier.size(19.dp),
                                                            tint =
                                                                if (group.status ==
                                                                    "failed"
                                                                ) {
                                                                    MaterialTheme.colorScheme.error
                                                                } else {
                                                                    MaterialTheme.colorScheme.primary
                                                                },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selecting && !confirmRemoval) {
            BottomAppBar(
                modifier =
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                    ),
                actions = {
                    Text(
                        "${selected.size} 项已选",
                        Modifier.weight(1f).padding(start = 12.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            val visible = filtered.map { it.workId }.toSet()
                            selected =
                                if (selected.containsAll(visible)) {
                                    selected - visible
                                } else {
                                    selected +
                                        visible
                                }
                            selecting = selected.isNotEmpty()
                        },
                    ) { AppIcon(materialSymbol(MaterialSymbol.Check), "全选") }
                    IconButton(onClick = { vm.downloadBatchAction(selectedTaskIds, "queued") }) {
                        AppIcon(materialSymbol(MaterialSymbol.PlayArrow), "开始")
                    }
                    IconButton(onClick = { vm.downloadBatchAction(selectedTaskIds, "paused") }) {
                        AppIcon(materialSymbol(MaterialSymbol.Pause), "暂停")
                    }
                    IconButton(onClick = {
                        keepDownloadedFiles = false
                        confirmRemoval = true
                    }) {
                        AppIcon(
                            materialSymbol(MaterialSymbol.Delete),
                            "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    IconButton(onClick = { exitSelection() }) {
                        AppIcon(materialSymbol(MaterialSymbol.Close), "退出选择模式")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
    if (confirmRemoval) {
        ModalBottomSheet(onDismissRequest = { confirmRemoval = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("删除下载项？", style = MaterialTheme.typography.titleLarge)
                Text("未完成任务会取消。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = keepDownloadedFiles,
                        onCheckedChange = { keepDownloadedFiles = it },
                    )
                    Text(
                        "保留已下载文件",
                        Modifier.clickable { keepDownloadedFiles = !keepDownloadedFiles },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            confirmRemoval = false
                            keepDownloadedFiles = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        onClick = { removeSelection(deleteFiles = !keepDownloadedFiles) },
                        modifier = Modifier.weight(1f),
                    ) { Text("确认") }
                }
            }
        }
    }
}
