package io.github.radiqalo.picaris.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun AccountScreen(
    vm: AppViewModel,
    back: () -> Unit,
) {
    val data by vm.accounts.collectAsStateWithLifecycle()
    val credentialError by vm.credentialReadError.collectAsStateWithLifecycle()
    var add by remember { mutableStateOf(false) }
    val tokenState = rememberTextFieldState()
    var remove by remember { mutableStateOf<Account?>(null) }
    var export by remember { mutableStateOf<Account?>(null) }
    val context = LocalContext.current
    Column {
        ScreenBar(
            stringResource(R.string.ui_9d4ca7f307),
            back,
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (credentialError != null) {
                item {
                    Text(
                        stringResource(R.string.credential_read_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { vm.retryCredentials() }) {
                        Text(stringResource(R.string.credential_read_retry))
                    }
                }
            }
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
                                    if (data.activeId == a.user.id) {
                                        Box(
                                            Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AppIcon(
                                                materialSymbol(MaterialSymbol.Check),
                                                stringResource(R.string.ui_922c94aae3),
                                            )
                                        }
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
                                            stringResource(R.string.ui_63fd41f453),
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
                    Text(stringResource(R.string.ui_63931d2cca))
                }
            }
            item {
                OutlinedButton({ add = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_91e037beb0))
                }
            }
        }
    }
    if (add) {
        ActionSheet(
            onDismissRequest = {
                add = false
                tokenState.clearText()
            },
            title = { Text(stringResource(R.string.ui_ef3ea55386)) },
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
                    Text(stringResource(R.string.ui_21f1e88275))
                }
            },
            dismissButton = {
                TextButton({
                    tokenState.clearText()
                    add = false
                }) {
                    Text(stringResource(R.string.ui_4d0b4688c7))
                }
            },
        )
    }
    remove?.let { a ->
        ActionSheet(
            onDismissRequest = { remove = null },
            title = { Text("移除 ${a.user.name}？") },
            text = { Text(stringResource(R.string.ui_ca05850133)) },
            confirmButton = {
                TextButton({
                    vm.removeAccount(a.user.id)
                    remove = null
                }) {
                    Text(stringResource(R.string.ui_2f752c005e))
                }
            },
            dismissButton = {
                TextButton({ remove = null }) { Text(stringResource(R.string.ui_4d0b4688c7)) }
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
                    context
                        .getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(
                            ClipData.newPlainText("Pixiv refresh token", a.refreshToken),
                        )
                    export = null
                }) {
                    Text("复制 Token")
                }
            },
            dismissButton = {
                TextButton({ export = null }) { Text(stringResource(R.string.ui_4d0b4688c7)) }
            },
        )
    }
}

@Composable
fun HistoryScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val visibleHistory =
        remember(history, settings.contentKind) {
            history.filter { record ->
                val work = runCatching { AppJson.decodeFromString<Work>(record.json) }.getOrNull()
                work?.isNovel == (settings.contentKind == "novel")
            }
        }
    Column(Modifier.fillMaxSize()) {
        ScreenBar(stringResource(R.string.ui_29f6711704), back = back, scrollBehavior = null)
        if (visibleHistory.isEmpty()) {
            EmptyState(
                stringResource(R.string.ui_f1fd08eeb6),
                stringResource(R.string.ui_4a5a8a9f75),
                materialSymbol(MaterialSymbol.History),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
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
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    124.dp,
                                ).clickable { navigate(Detail(work)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(
                                        88.dp,
                                    ).fillMaxHeight()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                AsyncImage(
                                    model = work.imageForQuality(settings.feedImageQuality),
                                    contentDescription = "${work.title} 缩略图",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
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
                                        java.text
                                            .SimpleDateFormat(
                                                "yyyy-MM-dd HH:mm",
                                                locale,
                                            ).format(java.util.Date(record.viewedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (work.isNovel) {
                                        Text(
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
    }
}
