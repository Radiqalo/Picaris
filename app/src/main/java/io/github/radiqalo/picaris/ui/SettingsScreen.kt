package io.github.radiqalo.picaris.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        Text(
            title,
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content,
        )
    }
}

enum class SettingsRowPosition {
    First,
    Middle,
    Last,
    Only,
}

@Composable
fun SettingRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = materialSymbol(MaterialSymbol.Settings),
    action: @Composable (() -> Unit)? = null,
    position: SettingsRowPosition = SettingsRowPosition.Middle,
    onClick: () -> Unit = {},
) {
    val (index, count) =
        when (position) {
            SettingsRowPosition.First -> 0 to 3
            SettingsRowPosition.Middle -> 1 to 3
            SettingsRowPosition.Last -> 2 to 3
            SettingsRowPosition.Only -> 0 to 1
        }
    val feedback = selectionFeedback()
    SegmentedListItem(
        colors = PixivContainerDefaults.listItemColors(),
        onClick = {
            feedback()
            onClick()
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        verticalAlignment = Alignment.CenterVertically,
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                summary.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    icon,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        trailingContent =
            action
                ?: {
                    AppIcon(
                        materialSymbol(MaterialSymbol.ChevronRight),
                        null,
                        Modifier.size(18.dp),
                    )
                },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingChoiceBlock(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    position: SettingsRowPosition,
) {
    val index =
        when (position) {
            SettingsRowPosition.First, SettingsRowPosition.Only -> 0
            SettingsRowPosition.Middle -> 1
            SettingsRowPosition.Last -> 2
        }
    SegmentedListItem(
        colors = PixivContainerDefaults.listItemColors(),
        onClick = {},
        shapes =
            ListItemDefaults.segmentedShapes(
                index,
                if (position ==
                    SettingsRowPosition.Only
                ) {
                    1
                } else {
                    3
                },
            ),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PixivSpacing.content),
                ) {
                    Box(
                        Modifier
                            .size(
                                40.dp,
                            ).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            materialSymbol(MaterialSymbol.Theme),
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                ChoiceChips(
                    selected,
                    options,
                    onSelect,
                    Modifier.fillMaxWidth(),
                    alignment = Alignment.CenterHorizontally,
                    equalWidth = true,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    back: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cachedFeedBytes by vm.cachedFeedBytes.collectAsStateWithLifecycle()
    val imageCacheBytes by vm.imageCacheBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.refreshImageCacheSize() }
    var choosingNovelTree by remember { mutableStateOf(false) }
    val exportAppData =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            uri?.let(vm::exportAppData)
        }
    val importAppData =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importAppData)
        }
    val tree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                vm.run {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    vm.setDownloadTree(uri.toString(), choosingNovelTree)
                }
            }
        }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(
            stringResource(R.string.ui_7debf9cb03),
            back,
            scrollBehavior = null,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("settingsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.ui_09b58aa342)) {
                    SettingChoiceBlock(
                        title = stringResource(R.string.ui_e848ddd482),
                        selected = s.theme,
                        options = listOf("system" to "系统", "light" to "浅色", "dark" to "深色"),
                        onSelect = { theme -> vm.update { it.copy(theme = theme) } },
                        position = SettingsRowPosition.First,
                    )
                    SettingRow(
                        "纯黑深色主题",
                        "深色模式下使用纯黑背景",
                        icon = materialSymbol(MaterialSymbol.Contrast),
                        action = {
                            FeedbackSwitch(s.pureBlackDarkTheme, { enabled ->
                                vm.update { it.copy(pureBlackDarkTheme = enabled) }
                            })
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update { it.copy(pureBlackDarkTheme = !it.pureBlackDarkTheme) }
                    }
                    SettingRow(
                        "悬浮底栏",
                        "启用悬浮底栏",
                        icon = materialSymbol(MaterialSymbol.Home),
                        action = {
                            FeedbackSwitch(s.bottomBarStyle == "floating", { enabled ->
                                vm.update {
                                    it.copy(
                                        bottomBarStyle = if (enabled) "floating" else "standard",
                                    )
                                }
                            })
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update {
                            it.copy(
                                bottomBarStyle =
                                    if (it.bottomBarStyle ==
                                        "floating"
                                    ) {
                                        "standard"
                                    } else {
                                        "floating"
                                    },
                            )
                        }
                    }
                    SettingRow(
                        stringResource(R.string.ui_9d180a2c78),
                        stringResource(R.string.ui_d80ba66133),
                        icon = materialSymbol(MaterialSymbol.Wallpaper),
                        action = {
                            FeedbackSwitch(
                                s.dynamicColor,
                                { v -> vm.update { it.copy(dynamicColor = v) } },
                            )
                        },
                    ) {
                        vm.update { it.copy(dynamicColor = !it.dynamicColor) }
                    }
                    SettingRow(
                        stringResource(R.string.ui_6f67371e05),
                        if (s.dynamicColor) {
                            stringResource(R.string.ui_be728419d8)
                        } else {
                            stringResource(R.string.ui_f4564b0336)
                        },
                        icon = materialSymbol(MaterialSymbol.Palette),
                        action = {
                            Surface(
                                Modifier.size(24.dp),
                                shape = CircleShape,
                                color =
                                    androidx.compose.ui.graphics
                                        .Color(s.seed),
                            ) {}
                        },
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "color"
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.ui_10ea138040)) {
                    SettingRow(
                        "显示作者与作品名",
                        "在图片流中显示作者与作品名",
                        icon = materialSymbol(MaterialSymbol.Image),
                        action = {
                            FeedbackSwitch(
                                s.showHomeMetadata,
                                { v ->
                                    vm.update { it.copy(showHomeMetadata = v) }
                                },
                            )
                        },
                        position = SettingsRowPosition.First,
                    ) {
                        vm.update { it.copy(showHomeMetadata = !it.showHomeMetadata) }
                    }
                    SettingRow(
                        "显示标签翻译",
                        "在标签旁显示译名",
                        icon = materialSymbol(MaterialSymbol.Search),
                        action = {
                            FeedbackSwitch(s.showTagTranslations, { value ->
                                vm.update { it.copy(showTagTranslations = value) }
                            })
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update { it.copy(showTagTranslations = !it.showTagTranslations) }
                    }
                    SettingChoiceBlock(
                        title = "图流预览画质",
                        selected = s.feedImageQuality,
                        options = listOf("medium" to "中", "large" to "高", "original" to "原图"),
                        onSelect = { quality -> vm.update { it.copy(feedImageQuality = quality) } },
                        position = SettingsRowPosition.Middle,
                    )
                    SettingChoiceBlock(
                        title = "插画详情页画质",
                        selected = s.detailImageQuality,
                        options = listOf("medium" to "中", "large" to "高", "original" to "原图"),
                        onSelect = { quality -> vm.update { it.copy(detailImageQuality = quality) } },
                        position = SettingsRowPosition.Middle,
                    )
                    SettingChoiceBlock(
                        title = "查看器画质",
                        selected = s.largeImageQuality,
                        options = listOf("large" to "高", "original" to "原图"),
                        onSelect = { quality -> vm.update { it.copy(largeImageQuality = quality) } },
                        position = SettingsRowPosition.Middle,
                    )
                    SettingRow(
                        stringResource(R.string.ui_d5edf52f07),
                        stringResource(R.string.ui_5b34213640),
                        materialSymbol(MaterialSymbol.Contrast),
                        action = {
                            FeedbackSwitch(
                                s.blackReader,
                                { v -> vm.update { it.copy(blackReader = v) } },
                            )
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update { it.copy(blackReader = !it.blackReader) }
                    }
                    SettingRow(
                        stringResource(R.string.ui_da33a1a4d5),
                        stringResource(R.string.ui_9cc2ab8295),
                        icon = materialSymbol(MaterialSymbol.Adult),
                        action = {
                            FeedbackSwitch(
                                s.showAdult,
                                { v -> vm.update { it.copy(showAdult = v) } },
                            )
                        },
                    ) {
                        vm.update { it.copy(showAdult = !it.showAdult) }
                    }
                    SettingRow(
                        stringResource(R.string.ui_b7f564e542),
                        stringResource(R.string.ui_2b1cc7450b),
                        icon = materialSymbol(MaterialSymbol.Ai),
                        action = {
                            FeedbackSwitch(
                                s.hideAi,
                                { v -> vm.update { it.copy(hideAi = v) } },
                            )
                        },
                    ) {
                        vm.update { it.copy(hideAi = !it.hideAi) }
                    }
                    SettingRow(
                        stringResource(R.string.ui_1a62da8063),
                        stringResource(R.string.ui_df855f5c93),
                        icon = materialSymbol(MaterialSymbol.BlockedTag),
                        position = SettingsRowPosition.Middle,
                    ) {
                        dialog = "tags"
                    }
                    SettingRow(
                        stringResource(R.string.ui_0ed0fdd725),
                        stringResource(R.string.ui_4544c7e538),
                        materialSymbol(MaterialSymbol.BlockedUser),
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "users"
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.ui_5d3fbdf11e)) {
                    SettingRow(
                        stringResource(R.string.ui_999365fe74),
                        when (s.proxyType) {
                            "http" -> "HTTP 代理 ${s.proxyHost}:${s.proxyPort}"
                            "socks" -> "SOCKS 代理 ${s.proxyHost}:${s.proxyPort}"
                            "direct" -> stringResource(R.string.ui_7d7358e103)
                            else -> stringResource(R.string.ui_8c99b2221d)
                        },
                        icon = materialSymbol(MaterialSymbol.Network),
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "proxy"
                    }
                    DownloadSettingsRows(
                        s,
                        vm,
                        chooseTree = { novel ->
                            choosingNovelTree = novel
                            tree.launch(null)
                        },
                    )
                    SettingRow(
                        "同时下载任务数",
                        "当前 ${s.downloadConcurrency.coerceIn(1, 10)} 个并发任务",
                        materialSymbol(MaterialSymbol.Download),
                        action = { Text("${s.downloadConcurrency.coerceIn(1, 10)}/10") },
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "downloadConcurrency"
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.ui_6d4dcd7cb8)) {
                    SettingRow(
                        stringResource(R.string.ui_0a7bef0788),
                        stringResource(R.string.ui_be6850c1b8),
                        materialSymbol(MaterialSymbol.History),
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "history"
                    }
                    SettingRow(
                        "导出应用数据 ZIP",
                        "设置、历史和下载记录；不含图片缓存及登录凭据",
                        materialSymbol(MaterialSymbol.Download),
                        position = SettingsRowPosition.Middle,
                    ) {
                        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                        exportAppData.launch("picaris-app-data-$timestamp.zip")
                    }
                    SettingRow(
                        "导入应用数据 ZIP",
                        "导入备份中的设置和本地记录",
                        materialSymbol(MaterialSymbol.Download),
                        position = SettingsRowPosition.Middle,
                    ) {
                        importAppData.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*"),
                        )
                    }
                    SettingRow(
                        stringResource(R.string.ui_92ec4c46d9),
                        "作品列表 ${formatByteSize(
                            cachedFeedBytes,
                        )} · 图片 ${formatByteSize(imageCacheBytes)}",
                        icon = materialSymbol(MaterialSymbol.ClearCache),
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "cache"
                    }
                }
            }
        }
    }
    when (dialog) {
        "color" ->
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.ui_6f67371e05)) },
                text = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf(
                            "紫罗兰" to 0xFF6256CA,
                            "深紫" to 0xFF4F378B,
                            "薰衣草" to 0xFF6750A4,
                            "蓝紫" to 0xFF565D7E,
                            "宝蓝" to 0xFF0061A4,
                            "晴蓝" to 0xFF006D8F,
                            "青色" to 0xFF006874,
                            "蓝绿" to 0xFF006C65,
                            "薄荷绿" to 0xFF386A5B,
                            "森林绿" to 0xFF386A20,
                            "橄榄绿" to 0xFF596400,
                            "金黄" to 0xFF825500,
                            "琥珀" to 0xFF7A5900,
                            "焦橙" to 0xFF9B4428,
                            "朱红" to 0xFFBA1A1A,
                            "玫瑰红" to 0xFF9C4146,
                            "莓果粉" to 0xFF984061,
                            "灰粉" to 0xFF7D5260,
                            "石板灰" to 0xFF565D6E,
                            "暖棕" to 0xFF705746,
                        ).forEach { (name, color) ->
                            Surface(
                                onClick = {
                                    vm.update { it.copy(seed = color, dynamicColor = false) }
                                    dialog = null
                                },
                                modifier =
                                    Modifier.size(52.dp).semantics {
                                        contentDescription = "$name 色主题"
                                    },
                                shape = CircleShape,
                                color =
                                    androidx.compose.ui.graphics
                                        .Color(color),
                            ) {
                                if (s.seed == color) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AppIcon(
                                            materialSymbol(MaterialSymbol.Check),
                                            null,
                                            tint = androidx.compose.ui.graphics.Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({ dialog = null }) {
                        Text(stringResource(R.string.ui_33246f6a5e))
                    }
                },
            )
        "tags",
        "users",
        -> {
            var value by remember {
                mutableStateOf(if (dialog == "tags") s.blockedTags else s.blockedUsers)
            }
            var entry by remember { mutableStateOf("") }
            val tags = dialog == "tags"
            val entries =
                value
                    .split(
                        ',',
                        '\n',
                    ).map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = {
                    Text(
                        if (tags) {
                            stringResource(R.string.ui_1a62da8063)
                        } else {
                            stringResource(R.string.ui_0ed0fdd725)
                        },
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                entry,
                                { entry = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(if (tags) "输入标签" else "输入用户 ID") },
                            )
                            IconButton(onClick = {
                                val normalized = entry.trim().removePrefix("#")
                                if (normalized.isNotEmpty() &&
                                    entries.none { it.equals(normalized, true) }
                                ) {
                                    value = (entries + normalized).joinToString("\n")
                                }
                                entry = ""
                            }) { AppIcon(materialSymbol(MaterialSymbol.Add), "添加") }
                        }
                        if (entries.isEmpty()) {
                            Text(
                                "暂无屏蔽项",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                entries.forEach { blocked ->
                                    InputChip(
                                        selected = false,
                                        onClick = {
                                            value =
                                                entries.filterNot { it == blocked }.joinToString(
                                                    "\n",
                                                )
                                        },
                                        label = { Text(blocked) },
                                        trailingIcon = {
                                            AppIcon(
                                                materialSymbol(MaterialSymbol.Close),
                                                "移除",
                                                Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton({
                        val tags = dialog == "tags"
                        vm.update {
                            if (tags) {
                                it.copy(blockedTags = value)
                            } else {
                                it.copy(blockedUsers = value)
                            }
                        }
                        dialog = null
                    }) {
                        Text(stringResource(R.string.ui_fadf24dbc5))
                    }
                },
                dismissButton = {
                    TextButton({ dialog = null }) {
                        Text(stringResource(R.string.ui_4d0b4688c7))
                    }
                },
            )
        }
        "downloadConcurrency" -> {
            var value by remember(s.downloadConcurrency) {
                mutableFloatStateOf(s.downloadConcurrency.coerceIn(1, 10).toFloat())
            }
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = { Text("同时下载任务数") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
                        Text("${value.toInt()} 个任务同时下载")
                        Slider(
                            state = rememberSliderState(
                                value = value,
                                steps = 8,
                                trackRange = 1f..10f,
                            ).also { it.value = value },
                            onValueChange = { value = it },
                        )
                    }
                },
                confirmButton = {
                    TextButton({
                        vm.update { it.copy(downloadConcurrency = value.toInt().coerceIn(1, 10)) }
                        dialog = null
                    }) { Text(stringResource(R.string.ui_fadf24dbc5)) }
                },
                dismissButton = {
                    TextButton({ dialog = null }) { Text(stringResource(R.string.ui_4d0b4688c7)) }
                },
            )
        }
        "proxy" ->
            ProxyDialog(s, { dialog = null }) { type, host, port ->
                vm.update { it.copy(proxyType = type, proxyHost = host, proxyPort = port) }
                dialog = null
            }
        "history",
        "cache",
        ->
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.ui_21f227944e)) },
                text = {
                    Text(
                        if (dialog == "history") {
                            "将清理当前账号的${stringResource(R.string.ui_29f6711704)}。"
                        } else {
                            "将清理当前账号的作品列表缓存和图片缓存。"
                        },
                    )
                },
                confirmButton = {
                    TextButton({
                        val history = dialog == "history"
                        vm.clearLibrary(history)
                        dialog = null
                    }) {
                        Text(stringResource(R.string.ui_907c3945d8))
                    }
                },
                dismissButton = {
                    TextButton({ dialog = null }) {
                        Text(stringResource(R.string.ui_4d0b4688c7))
                    }
                },
            )
    }
}

@Composable
fun ProxyDialog(
    s: Settings,
    dismiss: () -> Unit,
    save: (String, String, Int) -> Unit,
) {
    var type by remember { mutableStateOf(s.proxyType) }
    var host by remember { mutableStateOf(s.proxyHost) }
    var port by remember { mutableStateOf(s.proxyPort.toString()) }
    val manual = type == "http" || type == "socks"
    val valid = !manual || (host.isNotBlank() && port.toIntOrNull() in 1..65535)
    ActionSheet(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.ui_999365fe74)) },
        text = {
            Column {
                listOf(
                    "system" to stringResource(R.string.ui_37e46b67a6),
                    "direct" to stringResource(R.string.ui_7d7358e103),
                    "http" to stringResource(R.string.ui_d95ef5ba39),
                    "socks" to stringResource(R.string.ui_f331d21383),
                ).forEach { (k, t) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { type = k },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(type == k, { type = k })
                        Text(t)
                    }
                }
                if (manual) {
                    OutlinedTextField(
                        host,
                        { host = it },
                        label = { Text(stringResource(R.string.ui_ea1a03edfe)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        port,
                        { port = it },
                        label = { Text(stringResource(R.string.ui_6cbb7335b5)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        stringResource(R.string.ui_6a002621b8),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ save(type, host.trim(), port.toIntOrNull() ?: 7890) }, enabled = valid) {
                Text(stringResource(R.string.ui_fadf24dbc5))
            }
        },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.ui_4d0b4688c7)) } },
    )
}
