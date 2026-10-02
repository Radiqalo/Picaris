package io.github.radiqalo.picaris.ui

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.SharedTransitionLayout
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

@Serializable data object Home : NavKey

@Serializable data object Search : NavKey

@Serializable data class SearchResults(val query: String) : NavKey

@Serializable data class People(val section: String, val title: String) : NavKey

@Serializable data class Replies(val work: Work, val comment: Comment) : NavKey

@Serializable data class Comments(val work: Work) : NavKey

@Serializable data class Detail(val work: Work, val source: FeedSpec? = null, val position: Int = 0) : NavKey

@Serializable data class Reader(
    val work: Work,
    val initialPage: Int? = null,
    val localUris: List<String> = emptyList(),
) : NavKey

@Serializable data class Author(val user: User) : NavKey

@Serializable
data class Collection(
    val title: String,
    val section: String,
    val kind: String = "illust",
    val userId: Long = 0,
    val word: String = "",
    val watched: Boolean = false,
    val tagCover: Work? = null,
) : NavKey

@Serializable data class Utility(val page: String) : NavKey

@Composable
fun PixivApp(vm: AppViewModel, incoming: Intent?, handled: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val account by vm.active.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(account?.user?.id) {
        vm.syncAccountProfile()
        onPauseOrDispose { }
    }
    val revision by vm.revision.collectAsStateWithLifecycle()
    var artworkReturn by remember(account?.user?.id, revision) {
        mutableStateOf<ArtworkReturnFeedback?>(null)
    }
    val backStack = rememberNavBackStack(Home)
    val coordinator = rememberSaveable(account?.user?.id, revision, saver = listSaver(
        save = { state: NavigationTransitionCoordinator ->
            listOf(state.activeTab.toLong()) + state.instances.map { it.id }
        },
        restore = { saved -> NavigationTransitionCoordinator(backStack, saved.drop(1), saved.first().toInt()) },
    )) { NavigationTransitionCoordinator(backStack) }
    LaunchedEffect(coordinator.phase, artworkReturn) {
        if (coordinator.phase == NavigationTransitionPhase.Stable) artworkReturn = null
    }
    DisposableEffect(coordinator) { onDispose { coordinator.dispose() } }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    SideEffect { coordinator.clearFocus = { focusManager.clearFocus(force = true) } }
    val snackbar = remember { SnackbarHostState() }
    var tagAction by remember { mutableStateOf<Tag?>(null) }
    val pop: (Int) -> Unit = { count ->
        if (backStack.size > 1) {
            coordinator.commit(backStack, returning = true) {
                (backStack.lastOrNull() as? Detail)?.work?.let {
                    artworkReturn = ArtworkReturnFeedback(it.type, it.id)
                }
                repeat(count.coerceAtMost(backStack.size - 1)) { backStack.removeAt(backStack.lastIndex) }
            }
        }
    }
    val navigate: (NavKey) -> Unit = {
        val route = it
        val previousAuthor = backStack.getOrNull(backStack.lastIndex - 1) as? Author
        if (route is Author && backStack.lastOrNull() is Detail && previousAuthor?.user?.id == route.user.id) {
            // Reuse the source author instance and the same transition as Back.
            pop(1)
        } else if (route != backStack.lastOrNull()) coordinator.commit(backStack, destination = route) {
            if (route == Home) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            } else if (route is Detail && backStack.lastOrNull() is Detail)
                backStack[backStack.lastIndex] = route
            else if (route is SearchResults && backStack.lastOrNull() is SearchResults)
                backStack[backStack.lastIndex] = route
            else backStack.add(route)
        }
    }
    // Related works are a genuine drill-down: retain the current detail so Back returns to it.
    val navigateRelatedDetail: (Detail) -> Unit = {
        val route = it
        coordinator.commit(backStack, destination = route) {
            backStack.add(route)
        }
    }
    val back: () -> Unit = { pop(1) }
    LaunchedEffect(Unit) {
        if (vm.takeLegacyNavigationReset())
            navigate(Home)
        vm.message.collect { snackbar.showSnackbar(it) }
    }
    var routedAccountId by rememberSaveable { mutableStateOf(account?.user?.id) }
    LaunchedEffect(account?.user?.id) {
        if (routedAccountId != account?.user?.id)
            navigate(Home)
        routedAccountId = account?.user?.id
    }
    LaunchedEffect(incoming, account?.user?.id) {
        val uri = incoming?.data
        when {
            uri?.scheme == "pixiv" && uri.host == "account" -> {
                vm.callback(uri)
                handled()
            }
            incoming?.getBooleanExtra("downloads", false) == true -> {
                navigate(Utility("downloads"))
                handled()
            }
            uri?.host == "www.pixiv.net" &&
                uri.pathSegments.firstOrNull() == "artworks" &&
                account != null -> {
                val id = uri.lastPathSegment?.toLongOrNull()
                if (id != null) vm.run { navigate(Detail(vm.detail(Work(id = id)))) }
                handled()
            }
            uri?.host == "www.pixiv.net" &&
                uri.pathSegments.firstOrNull() == "users" &&
                account != null -> {
                val id = uri.pathSegments.getOrNull(1)?.toLongOrNull()
                if (id != null) vm.run { navigate(Author(vm.user(User(id = id)))) }
                handled()
            }
        }
    }
    PixivTheme(
        settings.theme,
        settings.dynamicColor,
        settings.seed,
        pureBlackDarkTheme = settings.pureBlackDarkTheme,
        darkSystemBarIcons =
            when (val current = backStack.lastOrNull()) {
                is Reader -> if (!current.work.isNovel && settings.blackReader) false else null
                else -> null
            },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                if (account == null && backStack.lastOrNull() !is Utility)
                    LoginScreen(vm) { navigate(Utility("settings")) }
                else
                    key(account?.user?.id, revision) {
                        val strategy = rememberListDetailSceneStrategy<NavKey>()
                        val detailStrategy = remember(strategy, coordinator) { DetailSceneStrategy(strategy, coordinator) }
                        SharedTransitionLayout {
                            val artwork = rememberNavigationArtwork(coordinator)
                            SideEffect {
                                coordinator.beforeCommit = { _, owners, destination ->
                                    artwork.prepare(owners, artworkKeys(destination))
                                }
                                coordinator.beforePreview = artwork::beginPreview
                            }
                            CompositionLocalProvider(
                                LocalWorkTransition provides this,
                                LocalNavigationCoordinator provides coordinator,
                                LocalNavigationArtwork provides artwork,
                                LocalArtworkReturnFeedback provides artworkReturn,
                                LocalTagTranslationEnabled provides settings.showTagTranslations,
                                LocalTagLongPress provides { tagAction = it },
                            ) {
                                val navigationMotion = rememberNavigationMotion()
                                val imageNavigation = remember(navigationMotion) {
                                    navigationMotion.metadata()
                                }
                                val pageDecorator = remember(navigationMotion) {
                                    NavigationPageSceneDecorator(navigationMotion::settled)
                                }
                                NavigationPageDisplay(
                                    modifier = Modifier.fillMaxSize(),
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    motion = navigationMotion,
                                    entryDecorators =
                                        listOf(rememberSaveableStateHolderNavEntryDecorator()),
                                    sceneDecoratorStrategies = listOf(pageDecorator),
                                    onBack = pop,
                                    sceneStrategies = listOf(detailStrategy),
                                    entryProvider =
                                        entryProvider {
                                            entry<Home>(metadata = ListDetailSceneStrategy.listPane()) {
                                                HomeScreen(vm, guardedNavigation(navigate))
                                            }
                                            entry<Search>(metadata = ListDetailSceneStrategy.listPane()) {
                                                ScrollingScreen { SearchScreen(vm, guardedNavigation(navigate), guardedBack(back)) }
                                            }
                                            entry<SearchResults>(metadata = ListDetailSceneStrategy.listPane()) {
                                                ScrollingScreen { SearchScreen(vm, guardedNavigation(navigate), guardedBack(back), it.query) }
                                            }
                                            entry<People> {
                                                ScrollingScreen(scrollBehaviorEnabled = false) { PeopleScreen(it, vm, guardedNavigation(navigate), guardedBack(back)) }
                                            }
                                            entry<Replies> {
                                                ScrollingScreen { RepliesScreen(it, vm, guardedNavigation(navigate), guardedBack(back)) }
                                            }
                                            entry<Comments> {
                                                ScrollingScreen {
                                                    CommentsScreen(it.work, vm, guardedNavigation(navigate), guardedBack(back))
                                                }
                                            }
                                            entry<Detail>(metadata = ListDetailSceneStrategy.detailPane() + imageNavigation) {
                                                val permitted = navigationPermission()
                                                DetailPagerScreen(it, vm,
                                                    navigate = guardedNavigation(navigate),
                                                    navigateRelatedDetail = { route ->
                                                        if (permitted()) navigateRelatedDetail(route)
                                                    },
                                                    back = guardedBack(back),
                                                )
                                            }
                                            entry<Reader>(metadata = imageNavigation) {
                                                ScrollingScreen { ReaderScreen(it.work, vm, guardedBack(back), it.initialPage, it.localUris) }
                                            }
                                            entry<Author>(metadata = ListDetailSceneStrategy.listPane()) {
                                                AuthorScreen(it.user, vm, guardedNavigation(navigate), guardedBack(back))
                                            }
                                            entry<Collection>(
                                                metadata = ListDetailSceneStrategy.listPane()
                                            ) {
                                                ScrollingScreen(scrollBehaviorEnabled = it.section == "ranking") { CollectionScreen(it, vm, guardedNavigation(navigate), guardedBack(back)) }
                                            }
                                            entry<Utility> {
                                                ScrollingScreen(scrollBehaviorEnabled = false) {
                                                    UtilityScreen(it.page, vm, guardedNavigation(navigate), guardedBack(back))
                                                }
                                            }
                                        },
                                )
                                NavigationArtworkOverlay(
                                    Modifier.renderInSharedTransitionScopeOverlay(
                                        renderInOverlay = { artwork.isActive },
                                        zIndexInOverlay = 0f,
                                    ),
                                )
                            }
                        }
                    }
                SnackbarHost(
                    snackbar,
                    Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 82.dp, start = 16.dp, end = 16.dp),
                )
                tagAction?.let { tag ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    ModalBottomSheet(onDismissRequest = { tagAction = null }) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = PixivSpacing.content),
                            verticalArrangement = Arrangement.spacedBy(PixivSpacing.related),
                        ) {
                            Text("#${tag.name}", style = MaterialTheme.typography.titleLarge)
                            tag.translated_name?.takeIf {
                                settings.showTagTranslations && it.isNotBlank() && !it.equals(tag.name, true)
                            }?.let { Text(it, style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("tag", tag.name))
                                    vm.message.tryEmit("已复制标签")
                                    tagAction = null
                                }) { Text("复制") }
                                TextButton(onClick = {
                                    vm.update { current ->
                                        val values = current.blockedTags.split(',', '\n').map(String::trim)
                                            .filter(String::isNotEmpty)
                                        if (values.any { it.equals(tag.name, true) }) current
                                        else current.copy(blockedTags = (values + tag.name).joinToString("\n"))
                                    }
                                    vm.message.tryEmit("已屏蔽标签：${tag.name}")
                                    tagAction = null
                                }) { Text("屏蔽标签") }
                            }
                            Spacer(Modifier.navigationBarsPadding().height(PixivSpacing.compact))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val coordinator = LocalNavigationCoordinator.current
    val permitted = navigationPermission()
    val tabs =
        listOf(
            stringResource(R.string.tab_home),
            stringResource(R.string.ui_523e40a074),
            stringResource(R.string.ui_753ccc8e2e),
            stringResource(R.string.ui_f04090805c),
            stringResource(R.string.ui_a82c993d73),
        )
    val icons = listOf(materialSymbol(MaterialSymbol.Home), materialSymbol(MaterialSymbol.Explore),
        materialSymbol(MaterialSymbol.Feed), materialSymbol(MaterialSymbol.Search), materialSymbol(MaterialSymbol.Person))
    val selectedIcons = listOf(materialSymbol(MaterialSymbol.HomeFilled), materialSymbol(MaterialSymbol.ExploreFilled),
        materialSymbol(MaterialSymbol.FeedFilled), materialSymbol(MaterialSymbol.Search), materialSymbol(MaterialSymbol.PersonFilled))
    val holder = rememberSaveableStateHolder()
    val homeReselection = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val feedback = selectionFeedback()
    val selectTab: (Int) -> Unit = { index ->
        if (permitted()) {
            if (tab != index || index == 0) feedback()
            if (tab == 0 && index == 0) homeReselection.tryEmit(Unit)
            navigate(Home)
            if (coordinator != null) coordinator.selectTab(index) { tab = index }
            else tab = index
        }
    }
    Box(Modifier.fillMaxSize()) {
        val wide =
            androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width /
                androidx.compose.ui.platform.LocalDensity.current.density >= 840f
        val floating = !wide && settings.bottomBarStyle == "floating"
        Row(Modifier.fillMaxSize()) {
            if (wide)
                WideNavigationRail(
                    modifier = Modifier.fillMaxHeight().aboveWorkTransition(),
                    state = rememberWideNavigationRailState(WideNavigationRailValue.Expanded),
                    header = {
                        Text(
                            "Picaris",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(PixivSpacing.content),
                        )
                    },
                ) {
                    tabs.forEachIndexed { index, title ->
                        WideNavigationRailItem(
                            railExpanded = true,
                            selected = tab == index,
                            onClick = { selectTab(index) },
                            icon = { FeedbackIcon(if (tab == index) selectedIcons[index] else icons[index], title, tab == index) },
                            label = { Text(title) },
                        )
                    }
                }
            Box(Modifier.weight(1f)) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!wide && !floating)
                            HomeNavigationBar(
                                floating = false,
                                labels = tabs,
                                selectedIndex = tab,
                                onSelect = selectTab,
                            ) { index, selected ->
                                FeedbackIcon(if (selected) selectedIcons[index] else icons[index], null, selected)
                            }
                    },
                ) { padding ->
                    CompositionLocalProvider(
                        LocalHomeNavigationInset provides if (floating)
                            FloatingNavigationHeight + floatingNavigationBottomSpacing()
                        else 0.dp,
                    ) {
                        BottomNavigationPages(
                            tab = tab,
                            modifier = Modifier.padding(padding),
                        ) { currentTab ->
                            holder.SaveableStateProvider(currentTab) {
                                val tabNavigate = guardedNavigation(navigate)
                                val homeGrid = rememberLazyStaggeredGridState()
                                val homeList = rememberLazyListState()
                                ScrollingScreen(
                                    scrollBehaviorEnabled = false
                                ) {
                                    if (currentTab == 0) {
                                        LaunchedEffect(settings.contentKind) {
                                            homeReselection.collectLatest {
                                                if (settings.contentKind == "novel")
                                                    homeList.animateScrollToItem(0)
                                                else homeGrid.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                    when (currentTab) {
                                        0 ->
                                            RecommendedHomeScreen(vm, tabNavigate, homeGrid, homeList)
                                        1 -> DiscoverScreen(vm, tabNavigate)
                                        2 -> FollowScreen(vm, tabNavigate)
                                        3 -> SearchScreen(vm, tabNavigate, back = null)
                                        else -> ProfileScreen(vm, tabNavigate)
                                    }
                                }
                            }
                        }
                    }
                }
                if (floating) {
                    Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                        HomeNavigationBar(
                            floating = true,
                            labels = tabs,
                            selectedIndex = tab,
                            onSelect = selectTab,
                        ) { index, selected ->
                            FeedbackIcon(if (selected) selectedIcons[index] else icons[index], null, selected)
                        }
                    }
                }
            }
        }
    }
}
