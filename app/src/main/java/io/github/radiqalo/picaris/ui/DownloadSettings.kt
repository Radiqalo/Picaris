package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
internal fun DownloadSettingsRows(
    s: Settings,
    vm: AppViewModel,
    chooseTree: (Boolean) -> Unit,
) {
    var editor by remember { mutableStateOf<String?>(null) }
    var organize by remember { mutableStateOf(false) }
    val icon = materialSymbol(MaterialSymbol.Download)
    listOf(false, true).forEach { novel ->
        val tree = if (novel) s.novelDownloadTree else s.downloadTree
        SettingRow(if (novel) "小说下载目录" else "图片下载目录",
            tree.ifEmpty { if (novel) "Documents/Picaris" else "Pictures/Picaris" }, icon,
            position = SettingsRowPosition.Middle) { chooseTree(novel) }
        if (tree.isNotEmpty()) SettingRow(if (novel) "恢复默认小说目录" else "恢复默认图片目录",
            "", icon, position = SettingsRowPosition.Middle) {
            vm.update { if (novel) it.copy(novelDownloadTree = "") else it.copy(downloadTree = "") }
        }
    }
    listOf(Triple("AI 单独文件夹", s.downloadAiFolder, "ai"),
        Triple("R18 单独文件夹", s.downloadAdultFolder, "adult"),
        Triple("作者单独文件夹", s.downloadAuthorFolder, "author")).forEach { (label, enabled, key) ->
        fun change(value: Boolean) = vm.update { when (key) {
            "ai" -> it.copy(downloadAiFolder = value)
            "adult" -> it.copy(downloadAdultFolder = value)
            else -> it.copy(downloadAuthorFolder = value)
        } }
        SettingRow(label, "", icon,
            action = { Switch(checked = enabled, onCheckedChange = { change(it) }) },
            position = SettingsRowPosition.Middle) { change(!enabled) }
    }
    listOf(
        Triple("下载完成后自动收藏", s.autoBookmarkAfterDownload, "autoBookmarkAfterDownload"),
        Triple("收藏后自动下载", s.autoDownloadAfterBookmark, "autoDownloadAfterBookmark"),
        Triple("默认私人收藏", s.defaultPrivateBookmarks, "defaultPrivateBookmarks"),
    ).forEach { (label, enabled, key) ->
        fun change(value: Boolean) = vm.update { when (key) {
            "autoBookmarkAfterDownload" -> it.copy(autoBookmarkAfterDownload = value)
            "autoDownloadAfterBookmark" -> it.copy(autoDownloadAfterBookmark = value)
            else -> it.copy(defaultPrivateBookmarks = value)
        } }
        SettingRow(label, when (key) {
            "autoBookmarkAfterDownload" -> "下载任务完成后收藏作品"
            "autoDownloadAfterBookmark" -> "收藏作品后加入下载队列"
            else -> "新收藏默认仅自己可见"
        }, materialSymbol(MaterialSymbol.Favorite),
            action = { Switch(checked = enabled, onCheckedChange = { change(it) }) },
            position = SettingsRowPosition.Middle) { change(!enabled) }
    }
    SettingRow("作者文件夹命名", DownloadNaming.preview(s.downloadAuthorTokens.filter { it in DownloadNaming.tokens }, separator = s.downloadAuthorSeparator),
        icon, position = SettingsRowPosition.Middle) { editor = "author" }
    SettingRow("作品文件命名", DownloadNaming.preview(s.downloadFileTokens.filter { it in DownloadNaming.tokens }, omitZero = s.downloadOmitPageZero, separator = s.downloadFileSeparator),
        icon, position = SettingsRowPosition.Middle) { editor = "file" }
    SettingRow("整理历史下载", "", icon, position = SettingsRowPosition.Middle) { organize = true }
    if (organize) ModalBottomSheet(onDismissRequest = { organize = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("整理历史下载", style = MaterialTheme.typography.titleLarge)
            Text("整理当前账号的已完成文件和小说封面。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { organize = false }) { Text("取消") }
                Button(onClick = { organize = false; vm.organizeDownloads() }) { Text("开始整理") }
            }
        }
    }
    editor?.let { kind ->
        TokenEditor(if (kind == "author") s.downloadAuthorTokens.filter { it in DownloadNaming.tokens }.distinct() else s.downloadFileTokens.filter { it in DownloadNaming.tokens }.distinct(),
            separator = if (kind == "author") s.downloadAuthorSeparator else s.downloadFileSeparator,
            author = kind == "author", omitZeroPage = s.downloadOmitPageZero,
            dismiss = { editor = null }) { parts, separator, omitZero ->
            vm.update { if (kind == "author") it.copy(downloadAuthorTokens = parts, downloadAuthorSeparator = separator)
                else it.copy(downloadFileTokens = parts, downloadFileSeparator = separator, downloadOmitPageZero = omitZero) }
            editor = null
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TokenEditor(initial: List<String>, separator: String, author: Boolean, omitZeroPage: Boolean, dismiss: () -> Unit, save: (List<String>, String, Boolean) -> Unit) {
    var parts by remember { mutableStateOf(initial) }
    var selectedSeparator by remember { mutableStateOf(separator.takeIf { it in DownloadNaming.separators } ?: "_") }
    var omitZero by remember(omitZeroPage) { mutableStateOf(omitZeroPage) }
    val currentParts = rememberUpdatedState(parts)
    var draggingToken by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (author) "作者文件夹命名" else "作品文件命名", style = MaterialTheme.typography.titleLarge)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                Text(DownloadNaming.preview(parts, omitZero = omitZero, separator = selectedSeparator) + if (author) "" else ".jpg", Modifier.padding(16.dp))
            }
            ButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
            ) {
                DownloadNaming.separators.entries.forEachIndexed { index, (value, label) ->
                    customItem(buttonGroupContent = {
                        ToggleButton(
                            checked = selectedSeparator == value,
                            onCheckedChange = { selectedSeparator = value },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                DownloadNaming.separators.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }, menuContent = { Text(label) })
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 192.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(parts, key = { it }) { token ->
                    val index = currentParts.value.indexOf(token)
                    val required = !author && token in setOf("id", "page")
                    val isDragging = draggingToken == token
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "tokenElevation")
                    val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "tokenScale")
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("⠿", style = MaterialTheme.typography.labelSmall)
                            Text(DownloadNaming.tokens[token] ?: token, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        } },
                        trailingIcon = {
                            Box(Modifier.size(20.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                if (required) AppIcon(materialSymbol(MaterialSymbol.Lock), "必选", Modifier.size(16.dp))
                                else AppIcon(materialSymbol(MaterialSymbol.Close), "删除", Modifier.size(16.dp)
                                    .clickable { parts = parts.filterNot { it == token } })
                            }
                        },
                        modifier = Modifier.animateItem(placementSpec = spring(stiffness = Spring.StiffnessHigh))
                            .fillMaxWidth().height(36.dp).graphicsLayer {
                            scaleX = scale; scaleY = scale; shadowElevation = elevation.toPx()
                        }
                            .pointerInput(token) {
                            val step = 38.dp.toPx()
                            var dragOffsetY = 0f
                            var draggedIndex = index
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragOffsetY = 0f
                                    draggedIndex = currentParts.value.indexOf(token)
                                    draggingToken = token
                                },
                                onDragEnd = { draggingToken = null },
                                onDragCancel = { draggingToken = null },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY += amount.y
                                    while (dragOffsetY >= step && draggedIndex < currentParts.value.lastIndex) {
                                        parts = currentParts.value.toMutableList().apply {
                                            add(draggedIndex + 1, removeAt(draggedIndex))
                                        }
                                        draggedIndex++
                                        dragOffsetY -= step
                                    }
                                    while (dragOffsetY <= -step && draggedIndex > 0) {
                                        parts = currentParts.value.toMutableList().apply {
                                            add(draggedIndex - 1, removeAt(draggedIndex))
                                        }
                                        draggedIndex--
                                        dragOffsetY += step
                                    }
                                },
                            )
                        },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DownloadNaming.tokens.filterKeys { key ->
                    if (author) key in setOf("author", "authorId") else key !in setOf("id", "page")
                }
                    .forEach { (key, label) ->
                        FilterChip(
                            selected = false,
                            enabled = key !in parts,
                            onClick = {
                                if (parts.size < 16 && key !in parts) parts = parts + key
                            },
                            label = { Text(label) },
                        )
                }
            }
            if (!author) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Checkbox(checked = omitZero, onCheckedChange = { omitZero = it }, modifier = Modifier.size(32.dp))
                Text("p0 页面省略", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { omitZero = !omitZero })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    parts = if (author) listOf("authorId") else listOf("id", "page")
                    selectedSeparator = "_"
                    omitZero = false
                }, modifier = Modifier.weight(1f)) { Text("重置", maxLines = 1) }
                Button(
                    enabled = parts.isNotEmpty() && (author || ("id" in parts && "page" in parts)),
                    onClick = { save(parts, selectedSeparator, omitZero) }, modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
