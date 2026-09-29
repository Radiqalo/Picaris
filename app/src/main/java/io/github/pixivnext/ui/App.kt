package io.github.pixivnext.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.navigation3.ui.NavDisplay
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.*
import io.github.pixivnext.designsystem.*
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Search : NavKey

@Serializable data class People(val section: String, val title: String) : NavKey

@Serializable data class Replies(val work: Work, val comment: Comment) : NavKey

@Serializable data class Comments(val work: Work) : NavKey

@Serializable data class Detail(val work: Work) : NavKey

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
    val revision by vm.revision.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    val snackbar = remember { SnackbarHostState() }
    val navigate: (NavKey) -> Unit = {
        if (it == Home) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        } else if (it is Detail && backStack.lastOrNull() is Detail)
            backStack[backStack.lastIndex] = it
        else backStack.add(it)
    }
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    LaunchedEffect(Unit) { vm.message.collect { snackbar.showSnackbar(it) } }
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
                if (id != null) vm.run { navigate(Detail(vm.repo.detail(vm.accountId, id))) }
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
                        NavDisplay(
                            backStack = backStack,
                            entryDecorators =
                                listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            onBack = back,
                            sceneStrategies =
                                if (backStack.lastOrNull() is Detail) listOf(strategy)
                                else emptyList(),
                            entryProvider =
                                entryProvider {
                                    entry<Home>(metadata = ListDetailSceneStrategy.listPane()) {
                                        HomeScreen(vm, navigate)
                                    }
                                    entry<Search>(metadata = ListDetailSceneStrategy.listPane()) {
                                        ScrollingScreen { SearchScreen(vm, navigate, back) }
                                    }
                                    entry<People> {
                                        ScrollingScreen { PeopleScreen(it, vm, navigate, back) }
                                    }
                                    entry<Replies> {
                                        ScrollingScreen { RepliesScreen(it, vm, navigate, back) }
                                    }
                                    entry<Comments> {
                                        ScrollingScreen {
                                            CommentsScreen(it.work, vm, navigate, back)
                                        }
                                    }
                                    entry<Detail>(metadata = ListDetailSceneStrategy.detailPane()) {
                                        ScrollingScreen {
                                            DetailScreen(it.work, vm, navigate, back)
                                        }
                                    }
                                    entry<Reader> {
                                        ScrollingScreen { ReaderScreen(it.work, vm, back) }
                                    }
                                    entry<Author>(metadata = ListDetailSceneStrategy.listPane()) {
                                        ScrollingScreen {
                                            AuthorScreen(it.user, vm, navigate, back)
                                        }
                                    }
                                    entry<Collection>(
                                        metadata = ListDetailSceneStrategy.listPane()
                                    ) {
                                        ScrollingScreen { CollectionScreen(it, vm, navigate, back) }
                                    }
                                    entry<Utility> {
                                        ScrollingScreen {
                                            UtilityScreen(it.page, vm, navigate, back)
                                        }
                                    }
                                },
                        )
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

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs =
        listOf(
            strings.getString(R.string.ui_523e40a074),
            strings.getString(R.string.ui_753ccc8e2e),
            strings.getString(R.string.ui_d07cee786a),
            strings.getString(R.string.ui_a82c993d73),
        )
    val icons = listOf(Glyph.Discover, Glyph.Feed, Glyph.Heart, Glyph.Person)
    val holder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize()) {
        val wide =
            androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width /
                androidx.compose.ui.platform.LocalDensity.current.density >= 840f
        Row(Modifier.fillMaxSize()) {
            if (wide)
                NavigationRail(Modifier.fillMaxHeight().width(96.dp).padding(top = 16.dp)) {
                    Text(
                        "p",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 28.dp),
                    )
                    tabs.forEachIndexed { index, title ->
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = {
                                tab = index
                                navigate(Home)
                            },
                            icon = { AppIcon(icons[index], title) },
                            label = { Text(title) },
                        )
                    }
                }
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!wide)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEachIndexed { index, title ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = {
                                        tab = index
                                        navigate(Home)
                                    },
                                    icon = { AppIcon(icons[index], null) },
                                    label = { Text(title) },
                                )
                            }
                        }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    holder.SaveableStateProvider(tab) {
                        ScrollingScreen {
                            when (tab) {
                                0 ->
                                    DiscoverScreen(vm, navigate) {
                                        navigate(Search)
                                    }
                                1 -> FollowScreen(vm, navigate)
                                2 -> BookmarkScreen(vm, navigate)
                                else -> ProfileScreen(vm, navigate)
                            }
                        }
                    }
                }
            }
        }
    }
}
