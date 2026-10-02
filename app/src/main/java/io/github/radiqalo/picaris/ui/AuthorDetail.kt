package io.github.radiqalo.picaris.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun AuthorScreen(
    initial: User,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val novelMode = settings.contentKind == "novel"
    var user by remember(initial.id) { mutableStateOf(initial) }
    var profile by remember(initial.id) { mutableStateOf(AuthorProfile()) }
    var profileLoaded by remember(initial.id) { mutableStateOf(false) }
    var details by remember(initial.id) { mutableStateOf(AuthorDetails(initial)) }
    var privatelyFollowed by remember(initial.id) { mutableStateOf(false) }
    var followVisibilityLoaded by remember(initial.id) { mutableStateOf(false) }
    val pageLabels = if (novelMode) listOf("小说", "收藏") else listOf("插画", "漫画", "收藏")
    val pager =
        androidx.compose.foundation.pager.rememberPagerState(
            pageCount = { pageLabels.size },
        )
    val outerScroll = rememberLazyListState()
    val pageGridStates =
        listOf(
            rememberLazyStaggeredGridState(),
            rememberLazyStaggeredGridState(),
            rememberLazyStaggeredGridState(),
        )
    val scope = rememberCoroutineScope()
    val feedback = selectionFeedback()
    val pageMotion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val workTransition = LocalWorkTransition.current
    val coordinatedScroll =
        remember(outerScroll) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (available.y >= 0f) return androidx.compose.ui.geometry.Offset.Zero
                    val consumed = outerScroll.dispatchRawDelta(-available.y)
                    return androidx.compose.ui.geometry
                        .Offset(0f, -consumed)
                }

                override fun onPostScroll(
                    consumed: androidx.compose.ui.geometry.Offset,
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (available.y <= 0f) return androidx.compose.ui.geometry.Offset.Zero
                    val consumedY = outerScroll.dispatchRawDelta(-available.y)
                    return androidx.compose.ui.geometry
                        .Offset(0f, -consumedY)
                }
            }
        }
    var busy by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var authorMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(initial.id, vm.accountId) {
        try {
            vm.authorDetails(initial).let {
                details = it
                user = it.user
                profile = it.profile
                profileLoaded =
                    true
            }
            privatelyFollowed = user.is_followed && vm.isPrivatelyFollowing(user.id)
            followVisibilityLoaded = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            followVisibilityLoaded = true
            // Keep the known author and their reachable works.
        }
    }
    LaunchedEffect(pageLabels.size) {
        if (pager.currentPage >= pageLabels.size) pager.scrollToPage(pageLabels.lastIndex)
    }

    fun shareAuthor() {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(
                        "text/plain",
                    ).putExtra(Intent.EXTRA_TEXT, "https://www.pixiv.net/users/${user.id}"),
                "分享作者",
            ),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportHeight = maxHeight
        LazyColumn(state = outerScroll, modifier = Modifier.fillMaxSize()) {
            item(key = "author_profile") {
                Column(
                    Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(254.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            profile.background_image_url?.takeIf(String::isNotBlank)?.let {
                                coil3.compose.AsyncImage(
                                    it,
                                    null,
                                    Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            }
                        }
                        Avatar(
                            user,
                            Modifier.align(Alignment.BottomCenter).size(88.dp),
                            sharedTransition = true,
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(PixivSpacing.content),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PixivSpacing.content),
                    ) {
                        Text(
                            user.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Button(
                            onClick = {
                                vm.run {
                                    busy = true
                                    try {
                                        user = vm.follow(user)
                                        privatelyFollowed = false
                                        followVisibilityLoaded = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier =
                                Modifier
                                    .fillMaxWidth(
                                        .75f,
                                    ).height(ButtonDefaults.MediumContainerHeight),
                            shapes =
                                ButtonDefaults.shapesFor(
                                    ButtonDefaults.MediumContainerHeight,
                                ),
                        ) {
                            Text(
                                when {
                                    !user.is_followed -> "关注"
                                    privatelyFollowed -> "非公开关注"
                                    else -> "已关注"
                                },
                            )
                        }
                        Text(
                            if (profileLoaded) "${profile.total_follow_users} 关注" else " ",
                            Modifier.heightIn(min = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            profile.region.orEmpty(),
                            Modifier.heightIn(min = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                user.comment,
                                Modifier.weight(1f).heightIn(min = 40.dp),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { showProfile = true }) { Text("查看资料") }
                        }
                    }
                }
            }
            item(key = "author_tabs") {
                PrimaryTabRow(
                    selectedTabIndex = pager.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    pageLabels.forEachIndexed { index, label ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = {
                                if (pager.currentPage != index) {
                                    feedback()
                                    scope.launch {
                                        pager.animateScrollToPage(
                                            index,
                                            animationSpec = pageMotion,
                                        )
                                    }
                                }
                            },
                            text = { Text(label) },
                        )
                    }
                }
            }
            item(key = "author_works") {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxWidth().height(viewportHeight),
                    pageNestedScrollConnection = coordinatedScroll,
                ) { page ->
                    CompositionLocalProvider(
                        LocalWorkTransition provides
                            if (page == pager.currentPage) workTransition else null,
                    ) {
                        FeedGrid(
                            FeedSpec(
                                section = if (page == pageLabels.lastIndex) "bookmarks" else "user",
                                kind =
                                    when {
                                        novelMode -> "novel"
                                        page == 1 -> "manga"
                                        else -> "illust"
                                    },
                                userId = user.id,
                            ),
                            vm,
                            navigate,
                            Modifier.fillMaxSize(),
                            gridState = pageGridStates[page],
                            pullToRefreshEnabled = false,
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = back) { AppIcon(materialSymbol(MaterialSymbol.ArrowBack), "返回") }
            IconButton(onClick = { authorMenu = true }) {
                Box(
                    Modifier
                        .size(
                            40.dp,
                        ).clip(CircleShape)
                        .background(Color.Black.copy(alpha = .38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(materialSymbol(MaterialSymbol.MoreHoriz), "更多操作", tint = Color.White)
                }
            }
        }
    }
    val isBlocked = settings.blockedUsers.split(',', '\n').any { it.trim() == user.id.toString() }
    if (authorMenu) {
        ModalBottomSheet(
            onDismissRequest = { authorMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            val colors =
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(
                    bottom = PixivSpacing.content,
                ),
            ) {
                ListItem(
                    colors = colors,
                    content = { Text(if (isBlocked) "取消屏蔽作者" else "屏蔽作者") },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.BlockedUser), null) },
                    onClick = {
                        authorMenu = false
                        vm.update { current ->
                            val ids =
                                current.blockedUsers
                                    .split(
                                        ',',
                                        '\n',
                                    ).map(String::trim)
                                    .filter(String::isNotEmpty)
                            val updated =
                                if (isBlocked) {
                                    ids.filterNot { it == user.id.toString() }
                                } else {
                                    ids +
                                        user.id.toString()
                                }
                            current.copy(blockedUsers = updated.joinToString("\n"))
                        }
                        vm.message.tryEmit(if (isBlocked) "已取消屏蔽作者" else "已屏蔽作者")
                    },
                )
                ListItem(
                    colors = colors,
                    content = { Text("分享作者") },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Share), null) },
                    onClick = {
                        authorMenu = false
                        shareAuthor()
                    },
                )
                ListItem(
                    colors = colors,
                    content = {
                        Text(
                            when {
                                user.is_followed && privatelyFollowed -> "已非公开关注"
                                user.is_followed -> "设为非公开关注"
                                else -> "非公开关注"
                            },
                        )
                    },
                    leadingContent = { AppIcon(materialSymbol(MaterialSymbol.Person), null) },
                    enabled =
                        !busy && followVisibilityLoaded && !(user.is_followed && privatelyFollowed),
                    onClick = {
                        authorMenu = false
                        vm.run {
                            busy = true
                            try {
                                user = vm.follow(user, public = false)
                                privatelyFollowed = true
                                followVisibilityLoaded = true
                            } finally {
                                busy = false
                            }
                        }
                    },
                )
            }
        }
    }
    if (showProfile &&
        navigationPermission()()
    ) {
        ModalBottomSheet(onDismissRequest = { showProfile = false }) {
            AuthorProfileContent(details.copy(user = user), vm, profileLoaded)
        }
    }
}
