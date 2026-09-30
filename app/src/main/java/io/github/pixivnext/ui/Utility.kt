package io.github.pixivnext.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*

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
            AppIcon(Glyph.Settings, strings.getString(R.string.ui_7debf9cb03))
        }
        Column(
            Modifier.align(Alignment.Center)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PixivNext", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                {
                    vm.run {
                        val url = vm.auth.startLogin()
                        CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, url.toUri())
                    }
                },
                Modifier.fillMaxWidth().height(56.dp),
                enabled = !busy,
            ) {
                AppIcon(Glyph.Person, null)
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
            TextButton({ vm.preview() }, Modifier.fillMaxWidth()) {
                Text(strings.getString(R.string.ui_288ccac9b4))
            }
            if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (import)
        AlertDialog(
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

    val isDemo by vm.demo.collectAsStateWithLifecycle()
    val account by vm.active.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val downloads by vm.downloadList.collectAsStateWithLifecycle()
    Column {
        ScreenBar(
            strings.getString(R.string.ui_a82c993d73),
            actions = {
                IconButton({ navigate(Utility("settings")) }) {
                    AppIcon(Glyph.Settings, strings.getString(R.string.ui_7debf9cb03))
                }
            },
        )
        LazyColumn(
            modifier = Modifier.testTag("profileList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Avatar(account?.user ?: User(), Modifier.size(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account?.user?.name ?: "PixivNext",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    if (isDemo) strings.getString(R.string.ui_fbd46976e0)
                                    else "ID ${account?.user?.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton({ navigate(Utility("accounts")) }) {
                                AppIcon(Glyph.Arrow, strings.getString(R.string.ui_9d4ca7f307))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            Column {
                                Text(
                                    history.size.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    strings.getString(R.string.ui_f69150fb1b),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            Column {
                                Text(
                                    downloads.count { it.status == "complete" }.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    strings.getString(R.string.ui_0c955150ac),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup("我的 Pixiv") {
                    SettingRow(
                        "我的关注",
                        "公开与非公开关注",
                        Glyph.Person,
                        position = SettingsRowPosition.First,
                    ) {
                        navigate(People("following", "我的关注"))
                    }
                    SettingRow("我的粉丝", "关注我的创作者与读者", Glyph.Person) {
                        navigate(People("follower", "我的粉丝"))
                    }
                    SettingRow("我的好 P 友", "互相成为好 P 友的用户", Glyph.Person) {
                        navigate(People("mypixiv", "我的好 P 友"))
                    }
                    SettingRow(
                        "我的作品",
                        "插画、漫画与小说",
                        Glyph.Discover,
                        position = SettingsRowPosition.Last,
                    ) {
                        account?.user?.let { navigate(Author(it)) }
                    }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_11d0497857)) {
                    SettingRow(
                        strings.getString(R.string.ui_29f6711704),
                        strings.getString(R.string.ui_00925a8ece),
                        Glyph.History,
                        position = SettingsRowPosition.First,
                    ) {
                        navigate(Utility("history"))
                    }
                    SettingRow(
                        strings.getString(R.string.ui_18df1a67a2),
                        strings.getString(R.string.ui_582c4d5fb8),
                        Glyph.Download,
                    ) {
                        navigate(Utility("downloads"))
                    }
                    SettingRow(
                        strings.getString(R.string.ui_9d4ca7f307),
                        strings.getString(R.string.ui_23fee3cacc),
                        Glyph.Person,
                        position = SettingsRowPosition.Last,
                    ) {
                        navigate(Utility("accounts"))
                    }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_414559692f)) {
                    SettingRow(
                        strings.getString(R.string.ui_7debf9cb03),
                        strings.getString(R.string.ui_40b33b3472),
                        Glyph.Settings,
                        position = SettingsRowPosition.First,
                    ) {
                        navigate(Utility("settings"))
                    }
                    SettingRow(
                        strings.getString(R.string.ui_272209c708),
                        "Android 17 · Material 3 Expressive",
                        Glyph.Discover,
                        position = SettingsRowPosition.Last,
                    ) {
                        navigate(Utility("about"))
                    }
                }
            }
        }
    }
}

@Composable
fun UtilityScreen(page: String, vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    when (page) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Glyph.Settings,
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
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        content = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Box(
                Modifier.size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        trailingContent = action ?: { AppIcon(Glyph.Arrow, null, Modifier.size(18.dp)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val tree =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null)
                vm.run {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    vm.settingsStore.update { it.copy(downloadTree = uri.toString()) }
                }
        }
    Column {
        ScreenBar(
            strings.getString(R.string.ui_7debf9cb03),
            back,
        )
        LazyColumn(
            modifier = Modifier.testTag("settingsList"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(strings.getString(R.string.ui_09b58aa342)) {
                    SettingRow(
                        "显示作者与作品名",
                        "在发现、动态和收藏瀑布流中显示文字",
                        action = {
                            Switch(
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
                        strings.getString(R.string.ui_e848ddd482),
                        when (s.theme) {
                            "light" -> strings.getString(R.string.ui_80ec9e2b1b)
                            "dark" -> strings.getString(R.string.ui_30b2c979ac)
                            else -> strings.getString(R.string.ui_f4bbd91f79)
                        },
                    ) {
                        dialog = "theme"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_9d180a2c78),
                        strings.getString(R.string.ui_d80ba66133),
                        action = {
                            Switch(s.dynamicColor, { v -> vm.update { it.copy(dynamicColor = v) } })
                        },
                    ) {
                        vm.update { it.copy(dynamicColor = !it.dynamicColor) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_6f67371e05),
                        if (s.dynamicColor) strings.getString(R.string.ui_be728419d8)
                        else strings.getString(R.string.ui_f4564b0336),
                        action = {
                            Surface(
                                Modifier.size(24.dp),
                                shape = CircleShape,
                                color = androidx.compose.ui.graphics.Color(s.seed),
                            ) {}
                        },
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "color"
                    }
                }
            }
            item {
                SettingsGroup(strings.getString(R.string.ui_10ea138040)) {
                    SettingRow(
                        strings.getString(R.string.ui_d5edf52f07),
                        strings.getString(R.string.ui_5b34213640),
                        Glyph.Book,
                        action = {
                            Switch(s.blackReader, { v -> vm.update { it.copy(blackReader = v) } })
                        },
                        position = SettingsRowPosition.First,
                    ) {
                        vm.update { it.copy(blackReader = !it.blackReader) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_da33a1a4d5),
                        strings.getString(R.string.ui_9cc2ab8295),
                        action = {
                            Switch(s.showAdult, { v -> vm.update { it.copy(showAdult = v) } })
                        },
                    ) {
                        vm.update { it.copy(showAdult = !it.showAdult) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_b7f564e542),
                        strings.getString(R.string.ui_2b1cc7450b),
                        action = { Switch(s.hideAi, { v -> vm.update { it.copy(hideAi = v) } }) },
                    ) {
                        vm.update { it.copy(hideAi = !it.hideAi) }
                    }
                    SettingRow(
                        strings.getString(R.string.ui_1a62da8063),
                        strings.getString(R.string.ui_df855f5c93),
                        position = SettingsRowPosition.Middle,
                    ) {
                        dialog = "tags"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_0ed0fdd725),
                        strings.getString(R.string.ui_4544c7e538),
                        Glyph.Person,
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "users"
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
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "proxy"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_4cd5da4f1c),
                        if (s.downloadTree.isEmpty()) strings.getString(R.string.ui_ef4c6139ae)
                        else strings.getString(R.string.ui_218f19435d),
                        Glyph.Download,
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
                            Glyph.Download,
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
                        Glyph.History,
                        position = SettingsRowPosition.First,
                    ) {
                        dialog = "history"
                    }
                    SettingRow(
                        strings.getString(R.string.ui_92ec4c46d9),
                        strings.getString(R.string.ui_698dc8c56e),
                        position = SettingsRowPosition.Last,
                    ) {
                        dialog = "cache"
                    }
                }
            }
            item {
                Text(
                    "PixivNext ${io.github.pixivnext.BuildConfig.VERSION_NAME} · Android 17",
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    when (dialog) {
        "theme" ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(strings.getString(R.string.ui_e848ddd482)) },
                text = {
                    Column {
                        listOf(
                                "system" to strings.getString(R.string.ui_f4bbd91f79),
                                "light" to strings.getString(R.string.ui_80ec9e2b1b),
                                "dark" to strings.getString(R.string.ui_30b2c979ac),
                            )
                            .forEach { (k, t) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        vm.update { it.copy(theme = k) }
                                        dialog = null
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        s.theme == k,
                                        {
                                            vm.update { it.copy(theme = k) }
                                            dialog = null
                                        },
                                    )
                                    Text(t)
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
        "color" ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(strings.getString(R.string.ui_6f67371e05)) },
                text = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listOf(0xFF6256CA, 0xFF006C65, 0xFF0061A4, 0xFF984061, 0xFF825500)
                            .forEach { color ->
                                Surface(
                                    onClick = {
                                        vm.update { it.copy(seed = color, dynamicColor = false) }
                                        dialog = null
                                    },
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    color = androidx.compose.ui.graphics.Color(color),
                                ) {
                                    if (s.seed == color)
                                        Box(contentAlignment = Alignment.Center) {
                                            AppIcon(
                                                Glyph.Check,
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
            AlertDialog(
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
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(strings.getString(R.string.ui_21f227944e)) },
                text = {
                    Text(
                        "将清理当前账号的${if(dialog=="history") strings.getString(R.string.ui_29f6711704) else strings.getString(R.string.ui_47c1321036)}。"
                    )
                },
                confirmButton = {
                    TextButton({
                        val history = dialog == "history"
                        vm.run {
                            if (history) vm.dao.clearHistory(vm.accountId)
                            else vm.dao.clearCache(vm.accountId)
                        }
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

@Composable
fun ProxyDialog(s: Settings, dismiss: () -> Unit, save: (String, String, Int) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    var type by remember { mutableStateOf(s.proxyType) }
    var host by remember { mutableStateOf(s.proxyHost) }
    var port by remember { mutableStateOf(s.proxyPort.toString()) }
    val manual = type == "http" || type == "socks"
    val valid = !manual || (host.isNotBlank() && port.toIntOrNull() in 1..65535)
    AlertDialog(
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

    val isDemo by vm.demo.collectAsStateWithLifecycle()
    val data by vm.accounts.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    val tokenState = rememberTextFieldState()
    var remove by remember { mutableStateOf<Account?>(null) }
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
                                Row {
                                    if (data.activeId == a.user.id && !isDemo)
                                        AppIcon(
                                            Glyph.Check,
                                            strings.getString(R.string.ui_922c94aae3),
                                        )
                                    IconButton({ remove = a }) {
                                        AppIcon(
                                            Glyph.Close,
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
                            val url = vm.auth.startLogin()
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
            if (isDemo)
                item {
                    TextButton(
                        {
                            vm.leave()
                            back()
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.getString(R.string.ui_c7e8e58589))
                    }
                }
        }
    }
    if (add)
        AlertDialog(
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
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("移除 ${a.user.name}？") },
            text = { Text(strings.getString(R.string.ui_ca05850133)) },
            confirmButton = {
                TextButton({
                    vm.run { vm.auth.remove(a.user.id) }
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
}

@Composable
fun HistoryScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val history by vm.history.collectAsStateWithLifecycle()
    Column {
        ScreenBar(strings.getString(R.string.ui_29f6711704), back = back)
        if (history.isEmpty())
            EmptyState(
                strings.getString(R.string.ui_f1fd08eeb6),
                strings.getString(R.string.ui_4a5a8a9f75),
                Glyph.History,
            )
        else
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { "${it.kind}:${it.workId}" }) { record ->
                    val work = remember(record.json) { AppJson.decodeFromString<Work>(record.json) }
                    Surface(
                        onClick = { navigate(Detail(work)) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            WorkImage(
                                work,
                                Modifier.size(72.dp, 88.dp).clip(MaterialTheme.shapes.small),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    work.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                )
                                Text(
                                    work.user.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AppIcon(Glyph.Arrow, null)
                        }
                    }
                }
            }
    }
}

@Composable
fun DownloadsScreen(vm: AppViewModel, navigate: (NavKey) -> Unit, back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val tasks by vm.downloadList.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column {
        ScreenBar(
            strings.getString(R.string.ui_18df1a67a2),
            back,
        )
        if (tasks.isEmpty())
            EmptyState(
                strings.getString(R.string.ui_92024c1013),
                strings.getString(R.string.ui_ed6ecba3e8),
                Glyph.Download,
            )
        else
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(
                                    if (task.status == "complete") Glyph.Check else Glyph.Download,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        task.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        task.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when (task.status) {
                                    "running" ->
                                        IconButton({
                                            vm.run { vm.downloads.action(task.id, "paused") }
                                        }) {
                                            AppIcon(
                                                Glyph.Pause,
                                                strings.getString(R.string.ui_130448bce6),
                                            )
                                        }
                                    "queued",
                                    "paused",
                                    "failed",
                                    "cancelled" ->
                                        IconButton({
                                            vm.run { vm.downloads.action(task.id, "queued") }
                                        }) {
                                            AppIcon(
                                                Glyph.Play,
                                                strings.getString(R.string.ui_3f9550508b),
                                            )
                                        }
                                    "complete" ->
                                        IconButton({
                                            vm.run {
                                                val intent =
                                                    Intent(Intent.ACTION_VIEW)
                                                        .setDataAndType(
                                                            task.uri.toUri(),
                                                            if (task.name.endsWith(".txt"))
                                                                "text/plain"
                                                            else if (task.name.endsWith(".zip"))
                                                                "application/zip"
                                                            else if (task.name.endsWith(".json"))
                                                                "application/json"
                                                            else "image/*",
                                                        )
                                                        .addFlags(
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        )
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        intent,
                                                        strings.getString(R.string.ui_3bb3442038),
                                                    )
                                                )
                                            }
                                        }) {
                                            AppIcon(
                                                Glyph.Arrow,
                                                strings.getString(R.string.ui_38820b3dc3),
                                            )
                                        }
                                }
                            }
                            if (task.status == "running" && task.total > 0)
                                LinearWavyProgressIndicator(
                                    progress = {
                                        (task.bytes.toFloat() / task.total).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (task.status) {
                                        "complete" -> strings.getString(R.string.ui_e99b48a29b)
                                        "paused" -> strings.getString(R.string.ui_fcbae46bf8)
                                        "running" -> "下载中 · ${task.bytes/1024} KB"
                                        "failed" -> task.error
                                        "cancelled" -> strings.getString(R.string.ui_a5ffdc95ee)
                                        else -> strings.getString(R.string.ui_e575faa045)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        if (task.status == "failed") MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                if (task.status != "complete" && task.status != "cancelled")
                                    TextButton({
                                        vm.run { vm.downloads.action(task.id, "cancelled") }
                                    }) {
                                        Text(strings.getString(R.string.ui_4d0b4688c7))
                                    }
                                if (task.workJson.isNotEmpty())
                                    TextButton({
                                        navigate(
                                            Detail(AppJson.decodeFromString<Work>(task.workJson))
                                        )
                                    }) {
                                        Text(strings.getString(R.string.ui_f394cdc91d))
                                    }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current

    val context = LocalContext.current
    var notices by remember { mutableStateOf(false) }
    Column {
        ScreenBar(strings.getString(R.string.ui_bed172efc9), back = back)
        Column(
            Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Surface(
                Modifier.size(88.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "p",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Text("PixivNext", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${io.github.pixivnext.BuildConfig.VERSION_NAME} · Android 17\nMaterial 3 Expressive",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                strings.getString(R.string.ui_d70fcfc258),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                strings.getString(R.string.ui_4b03f9aeb2),
                style = MaterialTheme.typography.titleMedium,
            )
            listOf(
                    "Pixiv-Shaft" to "https://github.com/CeuiLiSA/Pixiv-Shaft",
                    "MaterialFiles" to "https://github.com/zhanghai/MaterialFiles",
                    "FooIbar/EhViewer" to "https://github.com/FooIbar/EhViewer",
                )
                .forEach { (title, url) ->
                    OutlinedButton(
                        { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text(title)
                    }
                }
            OutlinedButton({ notices = true }, Modifier.fillMaxWidth()) {
                Text(strings.getString(R.string.license_notices))
            }
            Text(
                strings.getString(R.string.ui_0b6361b284),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (notices)
        AlertDialog(
            onDismissRequest = { notices = false },
            title = { Text(strings.getString(R.string.license_notices)) },
            text = {
                val text = remember {
                    context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use {
                        it.readText()
                    } +
                        "\n\n" +
                        context.assets.open("Apache-2.0.txt").bufferedReader().use { it.readText() }
                }
                Text(
                    text,
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton({ notices = false }) { Text(strings.getString(R.string.ui_33246f6a5e)) }
            },
        )
}
