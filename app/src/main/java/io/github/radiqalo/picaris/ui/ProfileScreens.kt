package io.github.radiqalo.picaris.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun LoginScreen(
    vm: AppViewModel,
    settings: () -> Unit,
) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    var import by remember { mutableStateOf(false) }
    val tokenState = rememberTextFieldState()
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        IconButton(
            settings,
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
        ) {
            AppIcon(materialSymbol(MaterialSymbol.Settings), stringResource(R.string.ui_7debf9cb03))
        }
        Column(
            Modifier
                .align(Alignment.Center)
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
                        CustomTabsIntent
                            .Builder()
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
                Text(stringResource(R.string.ui_77373439fa))
            }
            OutlinedButton(
                { import = true },
                Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
            ) {
                Text(stringResource(R.string.ui_91e037beb0))
            }
            if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (import) {
        ActionSheet(
            onDismissRequest = { if (!busy) import = false },
            title = { Text(stringResource(R.string.ui_592bb1ace9)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.ui_5c8d1c78fd),
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
                    Text(stringResource(R.string.ui_21f1e88275))
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
                    Text(stringResource(R.string.ui_4d0b4688c7))
                }
            },
        )
    }
}

@Composable
fun ProfileScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    val account by vm.active.collectAsStateWithLifecycle()
    LaunchedEffect(account?.user?.id) { vm.syncAccountProfile() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profileList"),
        contentPadding =
            PaddingValues(
                bottom =
                    PixivSpacing.content + LocalHomeNavigationInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(PixivSpacing.section),
    ) {
        item(key = "identity") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = PixivSpacing.content,
                        end = PixivSpacing.compact,
                        top = PixivSpacing.tight,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PixivSpacing.content),
            ) {
                Avatar(
                    account?.user ?: User(),
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(enabled = account != null, onClickLabel = "我的主页") {
                            account?.user?.let { navigate(Author(it)) }
                        },
                    sharedTransition = true,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                ) {
                    Text(
                        account?.user?.name ?: "Picaris",
                        modifier =
                            Modifier.clickable(enabled = account != null, onClickLabel = "我的主页") {
                                account?.user?.let { navigate(Author(it)) }
                            },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "ID ${account?.user?.id ?: ""}",
                        modifier =
                            Modifier.clickable(enabled = account != null, onClickLabel = "我的主页") {
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
            Column(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                listOf(
                    "我的关注" to { navigate(People("following", "我的关注")) },
                    "我的粉丝" to { navigate(People("follower", "我的粉丝")) },
                ).forEachIndexed { index, (label, action) ->
                    SegmentedListItem(
                        colors = PixivContainerDefaults.listItemColors(),
                        onClick = action,
                        shapes = ListItemDefaults.segmentedShapes(index, 2),
                        content = { Text(label, style = MaterialTheme.typography.titleMedium) },
                        leadingContent = {
                            AppIcon(
                                materialSymbol(MaterialSymbol.Person),
                                null,
                            )
                        },
                        trailingContent = {
                            AppIcon(
                                materialSymbol(MaterialSymbol.ChevronRight),
                                null,
                            )
                        },
                    )
                }
            }
        }
        item(key = "preferences") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                listOf(
                    Triple(
                        "settings",
                        stringResource(R.string.ui_7debf9cb03),
                        materialSymbol(MaterialSymbol.Settings),
                    ),
                    Triple(
                        "about",
                        stringResource(R.string.ui_272209c708),
                        materialSymbol(MaterialSymbol.Explore),
                    ),
                ).forEachIndexed { index, (page, label, icon) ->
                    SegmentedListItem(
                        colors = PixivContainerDefaults.listItemColors(),
                        onClick = { navigate(Utility(page)) },
                        shapes = ListItemDefaults.segmentedShapes(index, 2),
                        content = { Text(label) },
                        leadingContent = { AppIcon(icon, null) },
                        trailingContent = {
                            AppIcon(
                                materialSymbol(MaterialSymbol.ChevronRight),
                                null,
                            )
                        },
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
                    content = { Text(stringResource(R.string.ui_9d4ca7f307)) },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Person), null) },
                    trailingContent = {
                        AppIcon(
                            materialSymbol(MaterialSymbol.ChevronRight),
                            null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileLibraryActions(navigate: (NavKey) -> Unit) {
    val actions =
        listOf(
            Triple(
                "bookmarks",
                stringResource(R.string.profile_bookmarks),
                materialSymbol(MaterialSymbol.Favorite),
            ),
            Triple(
                "history",
                stringResource(R.string.profile_history),
                materialSymbol(MaterialSymbol.History),
            ),
            Triple(
                "downloads",
                stringResource(R.string.ui_18df1a67a2),
                materialSymbol(MaterialSymbol.Download),
            ),
        )
    val sources =
        remember {
            List(3) {
                androidx.compose.foundation.interaction
                    .MutableInteractionSource()
            }
        }
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menuState = menu) },
    ) {
        actions.forEachIndexed { index, (page, label, icon) ->
            customItem(
                buttonGroupContent = {
                    val connectedShapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    FilledTonalButton(
                        onClick = { navigate(Utility(page)) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .animateWidth(sources[index])
                                .height(ButtonDefaults.MediumContainerHeight),
                        interactionSource = sources[index],
                        shapes =
                            ButtonDefaults.shapes(
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
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { navigate(Utility(page)) },
                        leadingIcon = { AppIcon(icon, null) },
                    )
                },
            )
        }
    }
}
