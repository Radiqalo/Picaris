package io.github.pixivnext.ui

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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*
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

@Serializable data class Reader(val work: Work) : NavKey

@Serializable data class Author(val user: User) : NavKey

@Serializable
data class Collection(
    val title: String,
    val section: String,
    val kind: String = "illust",
    val userId: Long = 0,
    val word: String = "",
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
    LaunchedEffect(artworkReturn) {
        if (artworkReturn != null) {
            kotlinx.coroutines.delay(500)
            artworkReturn = null
        }
    }
    val backStack = rememberNavBackStack(Home)
    var authorNavigation by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val navigate: (NavKey) -> Unit = {
        authorNavigation = it is Author
        if (it == Home) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        } else if (it is Detail && backStack.lastOrNull() is Detail)
            backStack[backStack.lastIndex] = it
        else if (it is SearchResults && backStack.lastOrNull() is SearchResults)
            backStack[backStack.lastIndex] = it
        else backStack.add(it)
    }
    // Related works are a genuine drill-down: retain the current detail so Back returns to it.
    val navigateRelatedDetail: (Detail) -> Unit = {
        authorNavigation = false
        backStack.add(it)
    }
    val back: () -> Unit = {
        if (backStack.size > 1) {
            (backStack.lastOrNull() as? Detail)?.work?.let {
                artworkReturn = ArtworkReturnFeedback(it.type, it.id)
            }
            authorNavigation = backStack.lastOrNull() is Author
            backStack.removeAt(backStack.lastIndex)
        }
    }
    LaunchedEffect(Unit) {
        if (vm.takeLegacyNavigationReset())
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        vm.message.collect { snackbar.showSnackbar(it) }
    }
    var routedAccountId by rememberSaveable { mutableStateOf(account?.user?.id) }
    LaunchedEffect(account?.user?.id) {
        if (routedAccountId != account?.user?.id)
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
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
        }
    }
    PixivTheme(
        settings.theme,
        settings.dynamicColor,
        settings.seed,
        darkSystemBarIcons =
            if ((backStack.lastOrNull() as? Reader)?.work?.isNovel == false && settings.blackReader)
                false
            else null,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box {
                if (account == null && backStack.lastOrNull() !is Utility)
                    LoginScreen(vm) { navigate(Utility("settings")) }
                else
                    key(account?.user?.id, revision) {
                        val strategy = rememberListDetailSceneStrategy<NavKey>()
                        SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalWorkTransition provides this,
                                LocalImageTransitionEnabled provides !authorNavigation,
                                LocalTransitionTapRouter provides remember { TransitionTapRouter() },
                                LocalArtworkReturnFeedback provides artworkReturn,
                            ) {
                                val navigationMotion = rememberNavigationMotion()
                                val imageNavigation = remember(navigationMotion) {
                                    navigationMotion.metadata(NavigationMotionStyle.Zoom)
                                }
                                val pageDecorator = remember(navigationMotion) {
                                    NavigationPageSceneDecorator(navigationMotion::settled)
                                }
                                NavigationPageDisplay(
                                    backStack = backStack,
                                    modifier = Modifier.fillMaxSize(),
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    motion = navigationMotion,
                                    entryDecorators =
                                        listOf(rememberSaveableStateHolderNavEntryDecorator()),
                                    sceneDecoratorStrategies = listOf(pageDecorator),
                                    onBack = back,
                                    sceneStrategies =
                                        if (backStack.lastOrNull() is Detail) listOf(strategy)
                                        else emptyList(),
                                    entryProvider =
                                        entryProvider {
                                            entry<Home>(metadata = ListDetailSceneStrategy.listPane()) {
                                                CompositionLocalProvider(LocalFeedTapTargetsEnabled provides true) {
                                                    HomeScreen(vm, navigate)
                                                }
                                            }
                                            entry<Search>(metadata = ListDetailSceneStrategy.listPane()) {
                                                ScrollingScreen { SearchScreen(vm, navigate, back) }
                                            }
                                            entry<SearchResults>(metadata = ListDetailSceneStrategy.listPane()) {
                                                ScrollingScreen { SearchScreen(vm, navigate, back, it.query) }
                                            }
                                            entry<People> {
                                                ScrollingScreen(scrollBehaviorEnabled = false) { PeopleScreen(it, vm, navigate, back) }
                                            }
                                            entry<Replies> {
                                                ScrollingScreen { RepliesScreen(it, vm, navigate, back) }
                                            }
                                            entry<Comments> {
                                                ScrollingScreen {
                                                    CommentsScreen(it.work, vm, navigate, back)
                                                }
                                            }
                                            entry<Detail>(metadata = ListDetailSceneStrategy.detailPane() + imageNavigation) {
                                                val entry = it
                                                val isInteractive = { backStack.lastOrNull() == entry }
                                                NavigationExitContent(isInteractive) {
                                                    DetailPagerScreen(entry, vm,
                                                        navigate = { route ->
                                                            if (isInteractive()) navigate(route)
                                                        },
                                                        navigateRelatedDetail = { route ->
                                                            if (isInteractive()) navigateRelatedDetail(route)
                                                        },
                                                        back = { if (isInteractive()) back() },
                                                    )
                                                }
                                            }
                                            entry<Reader>(metadata = imageNavigation) {
                                                ScrollingScreen { ReaderScreen(it.work, vm, back) }
                                            }
                                            entry<Author>(metadata = ListDetailSceneStrategy.listPane()) {
                                                AuthorScreen(it.user, vm, navigate, back)
                                            }
                                            entry<Collection>(
                                                metadata = ListDetailSceneStrategy.listPane()
                                            ) {
                                                ScrollingScreen(scrollBehaviorEnabled = it.section == "ranking") { CollectionScreen(it, vm, navigate, back) }
                                            }
                                            entry<Utility> {
                                                ScrollingScreen(scrollBehaviorEnabled = false) {
                                                    UtilityScreen(it.page, vm, navigate, back)
                                                }
                                            }
                                        },
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
            }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, navigate: (NavKey) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val settings by vm.settings.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs =
        listOf(
            strings.getString(R.string.tab_home),
            strings.getString(R.string.ui_523e40a074),
            strings.getString(R.string.ui_753ccc8e2e),
            strings.getString(R.string.ui_f04090805c),
            strings.getString(R.string.ui_a82c993d73),
        )
    val icons = listOf(materialSymbol(MaterialSymbol.Home), materialSymbol(MaterialSymbol.Explore),
        materialSymbol(MaterialSymbol.Feed), Glyph.Search, materialSymbol(MaterialSymbol.Person))
    val selectedIcons = listOf(materialSymbol(MaterialSymbol.HomeFilled), materialSymbol(MaterialSymbol.ExploreFilled),
        materialSymbol(MaterialSymbol.FeedFilled), Glyph.Search, materialSymbol(MaterialSymbol.PersonFilled))
    val holder = rememberSaveableStateHolder()
    val homeReselection = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val feedback = selectionFeedback()
    val selectTab: (Int) -> Unit = { index ->
        if (tab != index || index == 0) feedback()
        if (tab == 0 && index == 0) homeReselection.tryEmit(Unit)
        tab = index
        navigate(Home)
    }
    Box(Modifier.fillMaxSize()) {
        val wide =
            androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width /
                androidx.compose.ui.platform.LocalDensity.current.density >= 840f
        Row(Modifier.fillMaxSize()) {
            if (wide)
                WideNavigationRail(
                    modifier = Modifier.fillMaxHeight().aboveWorkTransition(),
                    state = rememberWideNavigationRailState(WideNavigationRailValue.Expanded),
                    header = {
                        Text(
                            "PixivNext",
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
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!wide)
                        ShortNavigationBar(
                            modifier = Modifier.aboveWorkTransition(),
                            arrangement = ShortNavigationBarArrangement.EqualWeight
                        ) {
                            tabs.forEachIndexed { index, title ->
                                ShortNavigationBarItem(
                                    selected = tab == index,
                                    onClick = { selectTab(index) },
                                    icon = { FeedbackIcon(if (tab == index) selectedIcons[index] else icons[index], null, tab == index) },
                                    label = { Text(title) },
                                )
                            }
                        }
                },
            ) { padding ->
                BottomNavigationPages(
                    tab = tab,
                    modifier = Modifier.padding(padding),
                ) { currentTab ->
                    holder.SaveableStateProvider(currentTab) {
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
                                    RecommendedHomeScreen(vm, navigate, homeGrid, homeList)
                                1 -> DiscoverScreen(vm, navigate)
                                2 -> FollowScreen(vm, navigate)
                                3 -> SearchScreen(vm, navigate, back = null)
                                else -> ProfileScreen(vm, navigate)
                            }
                        }
                    }
                }
            }
        }
    }
}
