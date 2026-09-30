package io.github.pixivnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.core.contentFilter
import io.github.pixivnext.designsystem.PixivSpacing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun DetailPagerScreen(
    route: Detail,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    navigateRelatedDetail: (Detail) -> Unit,
    back: () -> Unit,
) {
    val source = route.source
    if (source == null || route.work.isNovel) {
        DetailScreen(route.work, vm, navigate, navigateRelatedDetail, back)
        return
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val flow = remember(source, vm.accountId, settings.contentFilter()) { vm.feed(source) }
    val items = flow.collectAsLazyPagingItems()
    var retainedPageCount by rememberSaveable { mutableIntStateOf(route.position + 1) }
    SideEffect {
        retainedPageCount =
            if (items.loadState.refresh is LoadState.NotLoading &&
                items.loadState.append.endOfPaginationReached && items.itemCount > 0
            ) items.itemCount
            else maxOf(retainedPageCount, items.itemCount)
    }
    val pager = rememberPagerState(initialPage = route.position) {
        maxOf(retainedPageCount, items.itemCount)
    }
    val navigation = LocalNavAnimatedContentScope.current.transition
    val sharedTransition = LocalWorkTransition.current
    val imageTransitionsEnabled = LocalImageTransitionEnabled.current
    LaunchedEffect(pager, items) {
        snapshotFlow { items.itemSnapshotList.items.getOrNull(pager.settledPage) }
            .filterNotNull()
            .distinctUntilChanged { previous, current -> previous.type == current.type && previous.id == current.id }
            .collect { vm.record(it) }
    }
    LaunchedEffect(pager.currentPage, items.itemCount) {
        if (pager.currentPage >= items.itemCount && items.itemCount > 0)
            items[items.itemCount - 1]
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().testTag("detailPager"),
            userScrollEnabled = !navigation.isRunning && sharedTransition?.isTransitionActive != true &&
                items.itemCount > pager.settledPage,
        ) { page ->
            val work = if (page < items.itemCount) items[page] else null
            val initial = work ?: route.work.takeIf { page == route.position }
            if (initial == null) LoadingState()
            else key(initial.type, initial.id) {
                val activePage = page == pager.settledPage && !pager.isScrollInProgress
                CompositionLocalProvider(
                    LocalImageTransitionEnabled provides (imageTransitionsEnabled && activePage),
                ) {
                    DetailScreen(
                        initial, vm,
                        navigate = { if (activePage) navigate(it) },
                        navigateRelatedDetail = { if (activePage) navigateRelatedDetail(it) },
                        back = { if (activePage) back() },
                    )
                }
            }
        }
        val error = items.loadState.refresh is LoadState.Error ||
            (items.loadState.append is LoadState.Error && pager.settledPage >= items.itemCount - 2)
        if (error) Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(PixivSpacing.content),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            TextButton(onClick = { items.retry() }) { Text("加载相邻作品失败，重试") }
        }
    }
}
