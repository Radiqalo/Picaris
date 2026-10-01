package io.github.radiqalo.picaris.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import coil3.compose.AsyncImage
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

@Composable
fun LoginScreen(vm: AppViewModel, settings: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var import by remember { mutableStateOf(false) }
    val tokenState = rememberTextFieldState()
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        IconButton(
            settings,
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
        ) {
            AppIcon(materialSymbol(MaterialSymbol.Settings), strings.getString(R.string.ui_7debf9cb03))
        }
        Column(
            Modifier.align(Alignment.Center)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Picaris", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                {
                    vm.run {
                        val url = vm.beginLogin()
                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, url.toUri())
                    }
                },
                Modifier.fillMaxWidth().height(56.dp),
                enabled = !busy,
            ) {
                AppIcon(materialSymbol(MaterialSymbol.Person), null)
                Spacer(Modifier.width(10.dp))
                Text(strings.getString(R.string.ui_77373439fa))
            }
            OutlinedButton(
                { import = true },
                Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
            ) {
                Text(strings.getString(R.string.ui_91e037beb0))
            }
            if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (import)
        ActionSheet(
            onDismissRequest = { if (!busy) import = false },
            title = { Text(strings.getString(R.string.ui_592bb1ace9)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.getString(R.string.ui_5c8d1c78fd),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedSecureTextField(
                        state = tokenState,
                        label = { Text("Refresh token") },
                        textObfuscationMode = TextObfuscationMode.Hidden,
                        enabled = !busy,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    {
                        vm.loginToken(tokenState.text.toString())
                        tokenState.clearText()
                        import = false
                    },
                    enabled = tokenState.text.isNotBlank() && !busy,
                ) {
                    Text(strings.getString(R.string.ui_21f1e88275))
                }
            },
            dismissButton = {
                TextButton(
                    {
                        tokenState.clearText()
                        import = false
                    },
                    enabled = !busy,
                ) {
                    Text(strings.getString(R.string.ui_4d0b4688c7))
                }
            },
        )
}

@Composable
fun ProfileScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val account by vm.active.collectAsStateWithLifecycle()
    LaunchedEffect(account?.user?.id) { vm.syncAccountProfile() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profileList"),
        contentPadding = PaddingValues(bottom = PixivSpacing.content + LocalHomeNavigationInset.current),
        verticalArrangement = Arrangement.spacedBy(PixivSpacing.section),
    ) {
        item(key = "identity") {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = PixivSpacing.content, end = PixivSpacing.compact, top = PixivSpacing.tight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PixivSpacing.content),
            ) {
                Avatar(account?.user ?: User(), Modifier.size(64.dp).clip(CircleShape)
                    .clickable(enabled = account != null, onClickLabel = "我的主页") {
                        account?.user?.let { navigate(Author(it)) }
                    }, sharedTransition = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight)) {
                    Text(account?.user?.name ?: "Picaris",
                        modifier = Modifier.clickable(enabled = account != null, onClickLabel = "我的主页") {
                            account?.user?.let { navigate(Author(it)) }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "ID ${account?.user?.id ?: ""}",
                        modifier = Modifier.clickable(enabled = account != null, onClickLabel = "我的主页") {
                            account?.user?.let { navigate(Author(it)) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ContentKindAction(vm)
            }
        }
        item(key = "libraryActions") {
            Box(Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content)) {
                ProfileLibraryActions(navigate)
            }
        }
        item(key = "pixiv") {
            Column(Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    listOf(
                        "我的关注" to { navigate(People("following", "我的关注")) },
                        "我的粉丝" to { navigate(People("follower", "我的粉丝")) },
                    ).forEachIndexed { index, (label, action) ->
                        SegmentedListItem(
                            colors = PixivContainerDefaults.listItemColors(),
                            onClick = action,
                            shapes = ListItemDefaults.segmentedShapes(index, 2),
                            content = { Text(label, style = MaterialTheme.typography.titleMedium) },
                            leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Person), null) },
                            trailingContent = { AppIcon(materialSymbol(MaterialSymbol.ChevronRight), null) },
                        )
                    }
                }
            }
        item(key = "preferences") {
            Column(Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    listOf(
                        Triple("settings", strings.getString(R.string.ui_7debf9cb03), materialSymbol(MaterialSymbol.Settings)),
                        Triple("about", strings.getString(R.string.ui_272209c708), materialSymbol(MaterialSymbol.Explore)),
                    ).forEachIndexed { index, (page, label, icon) ->
                        SegmentedListItem(
                            colors = PixivContainerDefaults.listItemColors(),
                            onClick = { navigate(Utility(page)) },
                            shapes = ListItemDefaults.segmentedShapes(index, 2),
                            content = { Text(label) },
                            leadingContent = { AppIcon(icon, null) },
                            trailingContent = { AppIcon(materialSymbol(MaterialSymbol.ChevronRight), null) },
                        )
                    }
                }
            }
        item(key = "accounts") {
            Column(Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content)) {
                SegmentedListItem(
                    colors = PixivContainerDefaults.listItemColors(),
                    onClick = { navigate(Utility("accounts")) },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    content = { Text(strings.getString(R.string.ui_9d4ca7f307)) },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Person), null) },
                    trailingContent = { AppIcon(materialSymbol(MaterialSymbol.ChevronRight), null) },
                )
            }
        }
    }
}

@Composable
private fun ProfileLibraryActions(navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val actions = listOf(
        Triple("bookmarks", strings.getString(R.string.profile_bookmarks), materialSymbol(MaterialSymbol.Favorite)),
        Triple("history", strings.getString(R.string.profile_history), materialSymbol(MaterialSymbol.History)),
        Triple("downloads", strings.getString(R.string.ui_18df1a67a2), materialSymbol(MaterialSymbol.Download)),
    )
    val sources = remember { List(3) { androidx.compose.foundation.interaction.MutableInteractionSource() } }
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
    ) {
        actions.forEachIndexed { index, (page, label, icon) ->
            customItem(
                buttonGroupContent = {
                    val connectedShapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    FilledTonalButton(
                        onClick = { navigate(Utility(page)) },
                        modifier = Modifier.weight(1f).animateWidth(sources[index])
                            .height(ButtonDefaults.MediumContainerHeight),
                        interactionSource = sources[index],
                        shapes = ButtonDefaults.shapes(
                            shape = connectedShapes.shape,
                            pressedShape = connectedShapes.pressedShape,
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                        ) {
                            AppIcon(icon, null, Modifier.size(ButtonDefaults.SmallIconSize))
                            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                menuContent = {
                    DropdownMenuItem(text = { Text(label) }, onClick = { navigate(Utility(page)) },
                        leadingIcon = { AppIcon(icon, null) })
                },
            )
        }
    }
}

@Composable
fun UtilityScreen(page: String, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    when (page) {
        "bookmarks" -> BookmarkScreen(vm, navigate, back)
        "settings" -> SettingsScreen(vm, back)
        "accounts" -> AccountScreen(vm, back)
        "downloads" -> DownloadsScreen(vm, navigate, back)
        "history" -> HistoryScreen(vm, navigate, back)
        else -> AboutScreen(back)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
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
        onClick = { feedback(); onClick() },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        verticalAlignment = Alignment.CenterVertically,
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Box(
                Modifier.size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        trailingContent = action ?: { AppIcon(materialSymbol(MaterialSymbol.ChevronRight), null, Modifier.size(18.dp)) },
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
    val index = when (position) {
        SettingsRowPosition.First, SettingsRowPosition.Only -> 0
        SettingsRowPosition.Middle -> 1
        SettingsRowPosition.Last -> 2
    }
    SegmentedListItem(
        colors = PixivContainerDefaults.listItemColors(),
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, if (position == SettingsRowPosition.Only) 1 else 3),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.related)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PixivSpacing.content)) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(materialSymbol(MaterialSymbol.Theme), null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                ChoiceChips(selected, options, onSelect, Modifier.fillMaxWidth(),
                    alignment = Alignment.CenterHorizontally, equalWidth = true)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val s by vm.settings.collectAsStateWithLifecycle()
    val cachedFeedBytes by vm.cachedFeedBytes.collectAsStateWithLifecycle()
    val imageCacheBytes by vm.imageCacheBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(vm) { vm.refreshImageCacheSize() }
    val tree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null)
                vm.run {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    vm.setDownloadTree(uri.toString())
                }
        }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(
            strings.getString(R.string.ui_7debf9cb03),
            back,
            scrollBehavior = null,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("settingsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(strings.getString(R.string.ui_09b58aa342)) {
                    SettingChoiceBlock(
                        title = strings.getString(R.string.ui_e848ddd482),
                        selected = s.theme,
                        options = listOf("system" to "系统", "light" to "浅色", "dark" to "深色"),
                        onSelect = { theme -> vm.update { it.copy(theme = theme) } },
                        position = SettingsRowPosition.First,
                    )
                    SettingRow(
                        "悬浮底栏",
                        "启用悬浮底栏",
                        icon = materialSymbol(MaterialSymbol.Home),
                        action = {
                            FeedbackSwitch(s.bottomBarStyle == "floating", { enabled ->
                                vm.update { it.copy(bottomBarStyle = if (enabled) "floating" else "standard") }
                            })
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update {
                            it.copy(bottomBarStyle = if (it.bottomBarStyle == "floating") "standard" else "floating")
                        }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_9d180a2c78),
                        strings.getString(R.string.ui_d80ba66133),
                        icon = materialSymbol(MaterialSymbol.Wallpaper),
                        action = {
                            FeedbackSwitch(s.dynamicColor, { v -> vm.update { it.copy(dynamicColor = v) } })
                        },
                    ) {
                        vm.update { it.copy(dynamicColor = !it.dynamicColor) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_6f67371e05),
                        if (s.dynamicColor) strings.getString(R.string.ui_be728419d8)
                        else strings.getString(R.string.ui_f4564b0336),
                        icon = materialSymbol(MaterialSymbol.Palette),
                        action = {
                            Surface(
                                Modifier.size(24.dp),
                                shape = CircleShape,
                                color = androidx.compose.ui.graphics.Color(s.seed),
                            ) {}
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        dialog = "color"
                    }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_10ea138040)) {
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
                        strings.getString(R.string.ui_d5edf52f07),
                        strings.getString(R.string.ui_5b34213640),
                        materialSymbol(MaterialSymbol.Contrast),
                        action = {
                            FeedbackSwitch(s.blackReader, { v -> vm.update { it.copy(blackReader = v) } })
                        },
                        position = SettingsRowPosition.Middle,
                    ) {
                        vm.update { it.copy(blackReader = !it.blackReader) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_da33a1a4d5),
                        strings.getString(R.string.ui_9cc2ab8295),
                        icon = materialSymbol(MaterialSymbol.Adult),
                        action = {
                            FeedbackSwitch(s.showAdult, { v -> vm.update { it.copy(showAdult = v) } })
                        },
                    ) {
                        vm.update { it.copy(showAdult = !it.showAdult) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_b7f564e542),
                        strings.getString(R.string.ui_2b1cc7450b),
                        icon = materialSymbol(MaterialSymbol.Ai),
                        action = { FeedbackSwitch(s.hideAi, { v -> vm.update { it.copy(hideAi = v) } }) },
                    ) {
                        vm.update { it.copy(hideAi = !it.hideAi) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_1a62da8063),
                        strings.getString(R.string.ui_df855f5c93),
                        icon = materialSymbol(MaterialSymbol.BlockedTag),
                        position = SettingsRowPosition.Middle,
                    ) {
                        dialog = "tags"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_0ed0fdd725),
                        strings.getString(R.string.ui_4544c7e538),
                        materialSymbol(MaterialSymbol.BlockedUser),
                        position = SettingsRowPosition.Middle,
                    ) {
                        dialog = "users"
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
                        position = SettingsRowPosition.Last,
                    ) {
                        vm.update { it.copy(showTagTranslations = !it.showTagTranslations) }
                    }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_5d3fbdf11e)) {
                    SettingRow(
                        strings.getString(R.string.ui_999365fe74),
                        when (s.proxyType) {
                            "http" -> "HTTP 代理 ${s.proxyHost}:${s.proxyPort}"
                            "socks" -> "SOCKS 代理 ${s.proxyHost}:${s.proxyPort}"
                            "direct" -> strings.getString(R.string.ui_7d7358e103)
                            else -> strings.getString(R.string.ui_8c99b2221d)
                        },
                        icon = materialSymbol(MaterialSymbol.Network),
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "proxy"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_4cd5da4f1c),
                        if (s.downloadTree.isEmpty()) strings.getString(R.string.ui_ef4c6139ae)
                        else strings.getString(R.string.ui_218f19435d),
                        materialSymbol(MaterialSymbol.Download),
                        position =
                            if (s.downloadTree.isEmpty()) SettingsRowPosition.Last
                            else SettingsRowPosition.Middle,
                    ) {
                        tree.launch(null)
                    }
                    if (s.downloadTree.isNotEmpty())
                        SettingRow(
                            strings.getString(R.string.ui_7e9cc10823),
                            strings.getString(R.string.ui_50c005951d),
                            materialSymbol(MaterialSymbol.Download),
                            position = SettingsRowPosition.Last,
                        ) {
                            vm.update { it.copy(downloadTree = "") }
                        }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_6d4dcd7cb8)) {
                    SettingRow(
                        strings.getString(R.string.ui_0a7bef0788),
                        strings.getString(R.string.ui_be6850c1b8),
                        materialSymbol(MaterialSymbol.History),
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "history"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_92ec4c46d9),
                        "作品列表 ${formatCacheSize(cachedFeedBytes)} · 图片 ${formatCacheSize(imageCacheBytes)}",
                        icon = materialSymbol(MaterialSymbol.ClearCache),
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "cache"
                    }
                }
            }
            item {
                Text(
                    "Picaris ${io.github.radiqalo.picaris.BuildConfig.VERSION_NAME} · Android 17",
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    when (dialog) {
        "color" ->
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = { Text(strings.getString(R.string.ui_6f67371e05)) },
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
                                    modifier = Modifier.size(52.dp).semantics {
                                        contentDescription = "$name 色主题"
                                    },
                                    shape = CircleShape,
                                    color = androidx.compose.ui.graphics.Color(color),
                                ) {
                                    if (s.seed == color)
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
                },
                confirmButton = {
                    TextButton({ dialog = null }) {
                        Text(strings.getString(R.string.ui_33246f6a5e))
                    }
                },
            )
        "tags",
        "users" -> {
            var value by remember {
                mutableStateOf(if (dialog == "tags") s.blockedTags else s.blockedUsers)
            }
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = {
                    Text(
                        if (dialog == "tags") strings.getString(R.string.ui_1a62da8063)
                        else strings.getString(R.string.ui_0ed0fdd725)
                    )
                },
                text = {
                    OutlinedTextField(
                        value,
                        { value = it },
                        minLines = 3,
                        maxLines = 6,
                        label = { Text(strings.getString(R.string.ui_a91a2209d7)) },
                    )
                },
                confirmButton = {
                    TextButton({
                        val tags = dialog == "tags"
                        vm.update {
                            if (tags) it.copy(blockedTags = value)
                            else it.copy(blockedUsers = value)
                        }
                        dialog = null
                    }) {
                        Text(strings.getString(R.string.ui_fadf24dbc5))
                    }
                },
                dismissButton = {
                    TextButton({ dialog = null }) {
                        Text(strings.getString(R.string.ui_4d0b4688c7))
                    }
                },
            )
        }
        "proxy" ->
            ProxyDialog(s, { dialog = null }) { type, host, port ->
                vm.update { it.copy(proxyType = type, proxyHost = host, proxyPort = port) }
                dialog = null
            }
        "history",
        "cache" ->
            ActionSheet(
                onDismissRequest = { dialog = null },
                title = { Text(strings.getString(R.string.ui_21f227944e)) },
                text = {
                    Text(
                        if (dialog == "history") {
                            "将清理当前账号的${strings.getString(R.string.ui_29f6711704)}。"
                        } else {
                            "将清理当前账号的作品列表缓存和图片缓存。"
                        }
                    )
                },
                confirmButton = {
                    TextButton({
                        val history = dialog == "history"
                        vm.clearLibrary(history)
                        dialog = null
                    }) {
                        Text(strings.getString(R.string.ui_907c3945d8))
                    }
                },
                dismissButton = {
                    TextButton({ dialog = null }) {
                        Text(strings.getString(R.string.ui_4d0b4688c7))
                    }
                },
            )
    }
}

private fun formatCacheSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "${"%.1f".format(java.util.Locale.ROOT, kib)} KB"
    val mib = kib / 1024.0
    return "${"%.1f".format(java.util.Locale.ROOT, mib)} MB"
}

@Composable
fun ProxyDialog(s: Settings, dismiss: () -> Unit, save: (String, String, Int) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var type by remember { mutableStateOf(s.proxyType) }
    var host by remember { mutableStateOf(s.proxyHost) }
    var port by remember { mutableStateOf(s.proxyPort.toString()) }
    val manual = type == "http" || type == "socks"
    val valid = !manual || (host.isNotBlank() && port.toIntOrNull() in 1..65535)
    ActionSheet(
        onDismissRequest = dismiss,
        title = { Text(strings.getString(R.string.ui_999365fe74)) },
        text = {
            Column {
                listOf(
                        "system" to strings.getString(R.string.ui_37e46b67a6),
                        "direct" to strings.getString(R.string.ui_7d7358e103),
                        "http" to strings.getString(R.string.ui_d95ef5ba39),
                        "socks" to strings.getString(R.string.ui_f331d21383),
                    )
                    .forEach { (k, t) ->
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
                        label = { Text(strings.getString(R.string.ui_ea1a03edfe)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        port,
                        { port = it },
                        label = { Text(strings.getString(R.string.ui_6cbb7335b5)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        strings.getString(R.string.ui_6a002621b8),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ save(type, host.trim(), port.toIntOrNull() ?: 7890) }, enabled = valid) {
                Text(strings.getString(R.string.ui_fadf24dbc5))
            }
        },
        dismissButton = { TextButton(dismiss) { Text(strings.getString(R.string.ui_4d0b4688c7)) } },
    )
}

@Composable
fun AccountScreen(vm: AppViewModel, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val data by vm.accounts.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    val tokenState = rememberTextFieldState()
    var remove by remember { mutableStateOf<Account?>(null) }
    var export by remember { mutableStateOf<Account?>(null) }
    val context = LocalContext.current
    Column {
        ScreenBar(
            strings.getString(R.string.ui_9d4ca7f307),
            back,
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    data.accounts.forEachIndexed { index, a ->
                        UserRow(
                            a.user,
                            {
                                vm.select(a.user.id)
                                back()
                            },
                            segment = index to data.accounts.size,
                            action = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (data.activeId == a.user.id)
                                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                            AppIcon(
                                                materialSymbol(MaterialSymbol.Check),
                                                strings.getString(R.string.ui_922c94aae3),
                                            )
                                        }
                                    IconButton({ export = a }) {
                                        AppIcon(
                                            materialSymbol(MaterialSymbol.ContentCopy),
                                            "导出 refresh token",
                                        )
                                    }
                                    IconButton({ remove = a }) {
                                        AppIcon(
                                            materialSymbol(MaterialSymbol.Close),
                                            strings.getString(R.string.ui_63fd41f453),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            item {
                Button(
                    {
                        vm.run {
                            val url = vm.beginLogin()
                            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                        }
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(strings.getString(R.string.ui_63931d2cca))
                }
            }
            item {
                OutlinedButton({ add = true }, Modifier.fillMaxWidth()) {
                    Text(strings.getString(R.string.ui_91e037beb0))
                }
            }
        }
    }
    if (add)
        ActionSheet(
            onDismissRequest = {
                add = false
                tokenState.clearText()
            },
            title = { Text(strings.getString(R.string.ui_ef3ea55386)) },
            text = {
                OutlinedSecureTextField(
                    state = tokenState,
                    label = { Text("Refresh token") },
                    textObfuscationMode = TextObfuscationMode.Hidden,
                )
            },
            confirmButton = {
                TextButton(
                    {
                        vm.loginToken(tokenState.text.toString())
                        tokenState.clearText()
                        add = false
                    },
                    enabled = tokenState.text.isNotBlank(),
                ) {
                    Text(strings.getString(R.string.ui_21f1e88275))
                }
            },
            dismissButton = {
                TextButton({
                    tokenState.clearText()
                    add = false
                }) {
                    Text(strings.getString(R.string.ui_4d0b4688c7))
                }
            },
        )
    remove?.let { a ->
        ActionSheet(
            onDismissRequest = { remove = null },
            title = { Text("移除 ${a.user.name}？") },
            text = { Text(strings.getString(R.string.ui_ca05850133)) },
            confirmButton = {
                TextButton({
                    vm.removeAccount(a.user.id)
                    remove = null
                }) {
                    Text(strings.getString(R.string.ui_2f752c005e))
                }
            },
            dismissButton = {
                TextButton({ remove = null }) { Text(strings.getString(R.string.ui_4d0b4688c7)) }
            },
        )
    }
    export?.let { a ->
        ActionSheet(
            onDismissRequest = { export = null },
            title = { Text("导出 ${a.user.name} 的 Token？") },
            text = {
                Text("Refresh token 可用于登录此账号。复制后请妥善保管，不要分享给他人或不可信应用。")
            },
            confirmButton = {
                TextButton({
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Pixiv refresh token", a.refreshToken))
                    export = null
                }) {
                    Text("复制 Token")
                }
            },
            dismissButton = {
                TextButton({ export = null }) { Text(strings.getString(R.string.ui_4d0b4688c7)) }
            },
        )
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val visibleHistory = remember(history, settings.contentKind) {
        history.filter { record ->
            val work = runCatching { AppJson.decodeFromString<Work>(record.json) }.getOrNull()
            work?.isNovel == (settings.contentKind == "novel")
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(strings.getString(R.string.ui_29f6711704), back = back, scrollBehavior = null)
        if (visibleHistory.isEmpty())
            EmptyState(
                strings.getString(R.string.ui_f1fd08eeb6),
                strings.getString(R.string.ui_4a5a8a9f75),
                materialSymbol(MaterialSymbol.History),
            )
        else
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleHistory, key = { "${it.kind}:${it.workId}" }) { record ->
                    val work = remember(record.json) { AppJson.decodeFromString<Work>(record.json) }
                    Surface(
                        modifier = Modifier.clip(MaterialTheme.shapes.large),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(124.dp).clickable { navigate(Detail(work)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(88.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                AsyncImage(
                                    model = work.previews.firstOrNull() ?: work.cover,
                                    contentDescription = "${work.title} 缩略图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    work.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        work.user.name.ifBlank { "未知作者" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(record.viewedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (work.isNovel) Text(
                                        "阅读进度 ${record.progress.coerceIn(0, 100)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

private fun downloadProgressText(bytes: Long, total: Long): String {
    fun format(size: Long): String = when {
        size >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0)
        else -> "$size B"
    }
    return if (total > 0) "${format(bytes)} / ${format(total)}" else format(bytes)
}

private fun downloadSizeText(size: Long): String = when {
    size >= 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", size / (1024.0 * 1024))
    size >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", size / 1024.0)
    else -> "$size B"
}

private data class DownloadSpeedSample(val bytes: Long, val time: Long)

private data class DownloadGroup(val workId: Long, val tasks: List<DownloadEntity>) {
    val work: Work? = tasks.firstNotNullOfOrNull {
        runCatching { AppJson.decodeFromString<Work>(it.workJson) }.getOrNull()
    }
    val status: String
        get() = when {
            tasks.any { it.status == "running" } -> "running"
            tasks.any { it.status == "queued" } -> "queued"
            tasks.any { it.status == "failed" } -> "failed"
            tasks.any { it.status == "paused" } -> "paused"
            tasks.any { it.status == "cancelled" } -> "cancelled"
            else -> "complete"
        }
    val bytes get() = tasks.sumOf { it.bytes }
    val total get() = tasks.sumOf { it.total }
    val createdAt get() = tasks.minOfOrNull { it.createdAt } ?: 0L
    val uploadedAt get() = runCatching {
        java.time.OffsetDateTime.parse(work?.create_date).toInstant().toEpochMilli()
    }.getOrDefault(0L)
    val imageCount get() = tasks.count { it.kind == "illust" || it.kind == "manga" }
    val pageCount get() = work?.page_count?.takeIf { it > 0 } ?: imageCount
    val title get() = work?.title ?: tasks.firstOrNull()?.title.orEmpty()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val tasks by vm.downloadList.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("all") }
    var selecting by remember(vm.accountId) { mutableStateOf(false) }
    var selected by remember(vm.accountId) { mutableStateOf(emptySet<Long>()) }
    var confirmRemoval by remember(vm.accountId) { mutableStateOf(false) }
    var keepDownloadedFiles by remember(vm.accountId) { mutableStateOf(true) }
    var sortOptionsExpanded by remember(vm.accountId) { mutableStateOf(false) }
    var sortMode by remember(vm.accountId) { mutableStateOf("download_desc") }
    var galleryMode by androidx.compose.runtime.saveable.rememberSaveable(vm.accountId) {
        mutableStateOf(false)
    }
    val downloadListState = rememberLazyListState()
    val downloadGridState = rememberLazyStaggeredGridState()
    var pendingSortAnchor by remember(vm.accountId) { mutableStateOf<Pair<Long, Int>?>(null) }
    var speeds by remember(vm.accountId) { mutableStateOf(emptyMap<Long, Long>()) }
    val speedSamples = remember(vm.accountId) { mutableMapOf<Long, DownloadSpeedSample>() }
    LaunchedEffect(tasks) {
        val now = SystemClock.elapsedRealtime()
        val nextSpeeds = mutableMapOf<Long, Long>()
        tasks.filter { it.status == "running" }.forEach { task ->
            val previous = speedSamples[task.id]
            if (previous != null && now > previous.time) {
                nextSpeeds[task.id] = ((task.bytes - previous.bytes).coerceAtLeast(0L) * 1000L) /
                    (now - previous.time)
            }
            speedSamples[task.id] = DownloadSpeedSample(task.bytes, now)
        }
        speedSamples.keys.retainAll(tasks.filter { it.status == "running" }.map { it.id }.toSet())
        speeds = nextSpeeds
    }
    val groups = remember(tasks, sortMode) {
        val grouped = tasks.groupBy { it.workId }
            .map { (workId, groupTasks) -> DownloadGroup(workId, groupTasks) }
            .sortedByDescending { it.createdAt }
        val comparator = when (sortMode.removeSuffix("_asc").removeSuffix("_desc")) {
            "upload" -> compareBy<DownloadGroup> { it.uploadedAt }
            "title" -> compareBy { it.title.lowercase() }
            "author" -> compareBy { it.work?.user?.name.orEmpty().lowercase() }
            else -> compareBy { it.createdAt }
        }
        grouped.sortedWith(if (sortMode.endsWith("_asc")) comparator else comparator.reversed())
    }
    val filtered = groups.filter { group ->
        when (filter) {
            "active" -> group.status in setOf("queued", "running")
            "paused" -> group.status == "paused"
            "failed" -> group.status == "failed"
            "complete" -> group.status == "complete"
            "cancelled" -> group.status == "cancelled"
            else -> true
        }
    }
    LaunchedEffect(sortMode) {
        val anchor = pendingSortAnchor ?: return@LaunchedEffect
        val index = filtered.indexOfFirst { it.workId == anchor.first }
        if (index >= 0) {
            if (galleryMode) downloadGridState.scrollToItem(index, anchor.second)
            else downloadListState.scrollToItem(index, anchor.second)
        }
        pendingSortAnchor = null
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
            strings.getString(R.string.ui_18df1a67a2),
            {
                when {
                    selecting -> exitSelection()
                    sortOptionsExpanded -> sortOptionsExpanded = false
                    else -> back()
                }
            },
            scrollBehavior = null,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("all" to "全部", "active" to "下载中", "paused" to "已暂停",
                "failed" to "失败", "complete" to "已完成", "cancelled" to "已取消")) { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${filtered.size} 个作品" + if (selecting) " · 已选 ${selected.size}" else "",
                Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
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
                                if (mode != sortMode) {
                                    val visibleIndex = if (galleryMode) downloadGridState.firstVisibleItemIndex
                                        else downloadListState.firstVisibleItemIndex
                                    val anchorOffset = if (galleryMode) downloadGridState.firstVisibleItemScrollOffset
                                        else downloadListState.firstVisibleItemScrollOffset
                                    pendingSortAnchor = filtered.getOrNull(visibleIndex)?.let {
                                        it.workId to anchorOffset
                                    }
                                }
                                sortMode = mode
                                sortOptionsExpanded = false
                            },
                            trailingIcon = {
                                if (sortMode == mode) AppIcon(materialSymbol(MaterialSymbol.Check), null)
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
                    if (tasks.isEmpty()) strings.getString(R.string.ui_92024c1013) else "没有匹配的下载",
                    if (tasks.isEmpty()) strings.getString(R.string.ui_ed6ecba3e8) else "可以切换其他状态查看",
                    materialSymbol(MaterialSymbol.Download),
                )
            } else {
                AnimatedContent(
                    targetState = galleryMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState) 1 else -1
                        (slideInHorizontally(
                            initialOffsetX = { direction * it / 18 },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) + fadeIn(tween(160))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -direction * it / 18 },
                                animationSpec = tween(110),
                            ) + fadeOut(tween(100)))
                    },
                    label = "downloadsViewMode",
                ) { showGallery ->
                if (showGallery) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(160.dp),
                        state = downloadGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp,
                    ) {
                        items(filtered, key = { it.workId }) { group ->
                            val work = group.work
                            val selectedGroup = group.workId in selected
                            val shape = MaterialTheme.shapes.medium
                            Surface(
                                modifier = Modifier.fillMaxWidth().animateItem().clip(shape).combinedClickable(
                                    onClick = {
                                        if (selecting) toggleSelection(group.workId)
                                        else work?.let {
                                            vm.record(it)
                                            if (it.isNovel) navigate(Detail(it)) else navigate(Reader(it))
                                        }
                                    },
                                    onLongClick = {
                                        if (selecting) toggleSelection(group.workId)
                                        else work?.let {
                                            vm.record(it)
                                            navigate(Detail(it))
                                        }
                                    },
                                ),
                                shape = shape,
                                color = if (selectedGroup) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column {
                                    Box {
                                        if (work != null && !work.isNovel) {
                                            AsyncImage(
                                                model = work.previews.firstOrNull() ?: work.cover,
                                                contentDescription = "${group.title}，单击预览，长按查看作品",
                                                modifier = Modifier.fillMaxWidth()
                                                    .aspectRatio(if (work.isNovel) .9f else work.aspect),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(.78f)
                                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AppIcon(materialSymbol(MaterialSymbol.Book), null)
                                            }
                                        }
                                        if (group.pageCount > 1) {
                                            Text(
                                                "${group.pageCount}P",
                                                Modifier.align(Alignment.TopStart).padding(8.dp)
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .62f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                            )
                                        }
                                        if (selecting) {
                                            Checkbox(
                                                checked = selectedGroup,
                                                onCheckedChange = { toggleSelection(group.workId) },
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                            )
                                        }
                                    }
                                    if (settings.showHomeMetadata && work != null) {
                                        Text(
                                            group.title,
                                            Modifier.padding(top = 2.dp, start = 3.dp, end = 3.dp),
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
                } else LazyColumn(
                    state = downloadListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.workId }) { group ->
                        val selectedGroup = group.workId in selected
                        val groupSpeed = group.tasks.sumOf { speeds[it.id] ?: 0L }
                        val shape = MaterialTheme.shapes.large
                        Surface(
                            modifier = Modifier.animateItem().clip(shape).combinedClickable(
                                onClick = {
                                    if (selecting) toggleSelection(group.workId)
                                    else group.work?.let { navigate(Detail(it)) }
                                },
                                onLongClick = {
                                    if (selecting) toggleSelection(group.workId)
                                    else {
                                        selected = setOf(group.workId)
                                        selecting = true
                                    }
                                },
                            ),
                            shape = shape,
                            color = if (selectedGroup) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(Modifier.fillMaxWidth().height(124.dp)) {
                                val work = group.work
                                Box(
                                    Modifier.width(88.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    if (work != null && !work.isNovel) {
                                        AsyncImage(
                                            model = work.previews.firstOrNull() ?: work.cover,
                                            contentDescription = "预览 ${group.title} 全图或图集",
                                            modifier = Modifier.fillMaxSize().then(
                                                if (!selecting) Modifier.clickable {
                                                    navigate(Reader(work))
                                                } else Modifier
                                            ),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppIcon(materialSymbol(MaterialSymbol.Book), null)
                                        }
                                    }
                                }
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    val author = work?.user?.name.orEmpty()
                                    val size = if (group.bytes > 0) downloadSizeText(group.bytes) else ""
                                    val time = remember(group.createdAt) {
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(group.createdAt))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(author.ifBlank { "未知作者" }, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        Text(
                                            listOf(size.ifBlank { "大小未知" }, time).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        if (group.status == "running") Text(
                                            "${downloadSizeText(groupSpeed)}/s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    if (group.status in setOf("running", "queued", "paused")) {
                                        if (group.total > 0) LinearWavyProgressIndicator(
                                            progress = { (group.bytes.toFloat() / group.total).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) else if (group.status == "running")
                                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Column(
                                    Modifier.width(44.dp).fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                        if (selecting)
                                            Checkbox(
                                                checked = selectedGroup,
                                                onCheckedChange = { toggleSelection(group.workId) },
                                                modifier = Modifier.size(30.dp),
                                            )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (group.pageCount > 0)
                                            Text("${group.pageCount}P", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val statusLabel = when (group.status) {
                                            "complete" -> "已完成"
                                            "failed" -> "点击重试下载"
                                            "running", "queued" -> "暂停下载"
                                            "paused", "cancelled" -> "继续下载"
                                            else -> "下载状态"
                                        }
                                        val statusIcon = when (group.status) {
                                            "running", "queued" -> MaterialSymbol.Pause
                                            "paused", "cancelled" -> MaterialSymbol.PlayArrow
                                            "failed" -> MaterialSymbol.Refresh
                                            else -> MaterialSymbol.Check
                                        }
                                        IconButton(
                                            onClick = {
                                                when (group.status) {
                                                    "running", "queued" -> vm.downloadBatchAction(
                                                        group.tasks.filter { it.status in setOf("running", "queued") }
                                                            .map { it.id }.toSet(), "paused",
                                                    )
                                                    "paused", "cancelled" -> vm.downloadBatchAction(
                                                        group.tasks.filter { it.status in setOf("paused", "cancelled") }
                                                            .map { it.id }.toSet(), "queued",
                                                    )
                                                    "failed" -> vm.retryDownloads(
                                                        group.tasks.filter { it.status == "failed" }
                                                            .map { it.id }.toSet(),
                                                    )
                                                    else -> Unit
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                            enabled = !selecting && group.status != "complete",
                                        ) {
                                            AppIcon(materialSymbol(statusIcon), statusLabel, Modifier.size(19.dp),
                                                tint = if (group.status == "failed") MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.primary)
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
                actions = {
                    Text(
                        "${selected.size} 项已选",
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            val visible = filtered.map { it.workId }.toSet()
                            selected = if (selected.containsAll(visible)) selected - visible else selected + visible
                            selecting = selected.isNotEmpty()
                        },
                    ) { AppIcon(materialSymbol(MaterialSymbol.Check), "全选") }
                    IconButton(onClick = { vm.downloadBatchAction(selectedTaskIds, "queued") }) {
                        AppIcon(materialSymbol(MaterialSymbol.PlayArrow), "开始")
                    }
                    IconButton(onClick = { vm.downloadBatchAction(selectedTaskIds, "paused") }) {
                        AppIcon(materialSymbol(MaterialSymbol.Pause), "暂停")
                    }
                    IconButton(onClick = { confirmRemoval = true }) {
                        AppIcon(materialSymbol(MaterialSymbol.Delete), "删除", tint = MaterialTheme.colorScheme.error)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    }
    if (confirmRemoval) ModalBottomSheet(onDismissRequest = { confirmRemoval = false }) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("删除下载项？", style = MaterialTheme.typography.titleLarge)
            Text("未完成任务会取消。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keepDownloadedFiles, onCheckedChange = { keepDownloadedFiles = it })
                Text("保留已下载文件", Modifier.clickable { keepDownloadedFiles = !keepDownloadedFiles })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { confirmRemoval = false; keepDownloadedFiles = true },
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

@Composable
fun AboutScreen(back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    Column {
        ScreenBar(strings.getString(R.string.ui_bed172efc9), back = back, scrollBehavior = null)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = PixivSpacing.section),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(
                    top = PixivSpacing.section,
                    start = PixivSpacing.content,
                    end = PixivSpacing.content,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.related),
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.large),
                )
                Text("Picaris", style = MaterialTheme.typography.headlineMedium)
                Text(
                    strings.getString(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(vertical = PixivSpacing.compact)) {
                    AboutInfoRow(
                        strings.getString(R.string.about_version),
                        io.github.radiqalo.picaris.BuildConfig.VERSION_NAME,
                    )
                    AboutInfoRow(
                        strings.getString(R.string.about_appearance),
                        strings.getString(R.string.about_appearance_value),
                    )
                }
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Text(
                strings.getString(R.string.ui_d70fcfc258),
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PixivSpacing.section))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            ) {
                OutlinedButton(
                    {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://github.com/Radiqalo/Picaris".toUri()),
                        )
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(strings.getString(R.string.about_source_code))
                }
            }
            Spacer(Modifier.height(PixivSpacing.section))
            Text(
                strings.getString(R.string.about_license_note),
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { licenses = true }
                    .padding(horizontal = PixivSpacing.content, vertical = PixivSpacing.compact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    if (licenses)
        ActionSheet(
            onDismissRequest = { licenses = false },
            title = { Text(strings.getString(R.string.about_license)) },
            text = {
                val text = remember {
                    listOf("THIRD_PARTY_NOTICES.md", "GPL-3.0.txt", "Apache-2.0.txt", "MIT.txt")
                        .joinToString("\n\n") { name ->
                            runCatching {
                                context.assets.open(name).bufferedReader().use { it.readText() }
                            }.getOrDefault("")
                        }
                }
                Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton({ licenses = false }) { Text(strings.getString(R.string.ui_33246f6a5e)) }
            },
        )
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content, vertical = PixivSpacing.compact),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
