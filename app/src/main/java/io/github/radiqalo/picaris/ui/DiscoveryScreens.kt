package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun RecommendedHomeScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    gridState: LazyStaggeredGridState,
    listState: LazyListState,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    FeedGrid(
        FeedSpec(kind = settings.contentKind),
        vm,
        navigate,
        Modifier.fillMaxSize(),
        gridState = gridState,
        listState = listState,
        topPadding =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + PixivSpacing.content,
    )
}

@Composable
fun DiscoverScreen(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    DiscoveryLanding(vm, navigate)
}

@Composable
private fun DiscoveryLanding(
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    var refreshingDiscovery by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trendResult by produceState<List<TrendingTag>?>(
        vm.cachedTrendingTags(),
        vm.accountId,
        settings.contentKind,
        settings.contentFilter(),
        refreshVersion,
    ) {
        value = vm.cachedTrendingTags()
        if (settings.contentKind == "novel") {
            value = emptyList()
            return@produceState
        }
        value =
            try {
                vm.trendingTags()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
    }
    val authorResult by produceState<List<UserPreview>?>(
        vm.cachedRecommendedAuthors(),
        vm.accountId,
        settings.contentKind,
        settings.contentFilter(),
        refreshVersion,
    ) {
        value = vm.cachedRecommendedAuthors()
        value =
            if (settings.contentKind == "novel") {
                emptyList()
            } else {
                try {
                    vm.recommendedAuthors()
                } catch (
                    e: kotlinx.coroutines.CancellationException,
                ) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            }
    }
    val trends = trendResult.orEmpty()
    val loadingTrends = trendResult == null && settings.contentKind != "novel"
    val rankingTitle = stringResource(R.string.ui_d00981d6ce)
    FeedGrid(
        FeedSpec(kind = settings.contentKind),
        vm,
        navigate,
        Modifier.fillMaxSize(),
        topPadding =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + PixivSpacing.content,
        onRefresh = {
            if (!refreshingDiscovery) {
                refreshingDiscovery = true
                refreshVersion++
            }
        },
        feedRefreshVersion = refreshVersion,
        refreshingOverride = refreshingDiscovery,
        onRefreshFinished = { refreshingDiscovery = false },
        scrollHeaderWhileEmpty = true,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.content)) {
                if (refreshVersion == 0) PixivisionCarousel(vm)
                else PixivisionCarousel(vm, refreshVersion)
                TextButton(
                    onClick = { navigate(Collection(rankingTitle, "ranking")) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    AppIcon(materialSymbol(MaterialSymbol.Leaderboard), null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(rankingTitle)
                }
                if (loadingTrends) {
                    DiscoveryPlaceholders()
                } else if (trends.isNotEmpty()) {
                    Text(
                        stringResource(R.string.discover_tags),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val tagColors =
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            trends.take(3),
                            key = { _, trend -> trend.tag.name },
                        ) { index, trend ->
                            val containerColor = tagColors[index % tagColors.size]
                            Surface(
                                onClick = {
                                    navigate(
                                        Collection(
                                            trend.tag.name,
                                            "search",
                                            word = trend.tag.name,
                                            tagCover = trend.cover,
                                        ),
                                    )
                                },
                                modifier = Modifier.height(48.dp),
                                shape = MaterialTheme.shapes.small,
                                color = containerColor,
                                contentColor = contentColorFor(containerColor),
                            ) {
                                Box(
                                    Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TagLabel(trend.tag)
                                }
                            }
                        }
                    }
                    if (trends.size > 3) {
                        Text(
                            stringResource(R.string.discover_popular_tags),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(trends.drop(3).take(7), key = { it.tag.name }) { trend ->
                                Card(
                                    onClick = {
                                        navigate(
                                            Collection(
                                                trend.tag.name,
                                                "search",
                                                word = trend.tag.name,
                                                tagCover = trend.cover,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.width(124.dp).height(204.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    WorkImage(
                                        trend.cover,
                                        Modifier.fillMaxSize(),
                                        rounded = false,
                                        overlay = {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.78f),
                                                            ),
                                                        ),
                                                    ),
                                            )
                                            Column(
                                                Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(10.dp),
                                            ) {
                                                CompositionLocalProvider(
                                                    LocalContentColor provides Color.White,
                                                ) {
                                                    TagLabel(trend.tag, translationFirst = false)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    trends.take(3).forEach { trend ->
                        DiscoveryTagWorks(trend, vm, navigate, refreshVersion)
                    }
                }
                if (settings.contentKind != "novel") {
                    Text(
                        stringResource(R.string.discover_artists),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                        if (authorResult == null) {
                            items(3) {
                                Spacer(
                                    Modifier
                                        .width(260.dp)
                                        .height(300.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            MaterialTheme.shapes.large,
                                        ),
                                )
                            }
                        } else {
                            items(authorResult.orEmpty(), key = { it.user.id }) { preview ->
                                DiscoveryAuthorCard(preview, vm, navigate)
                            }
                        }
                    }
                }
                Text(
                    if (settings.contentKind == "novel") {
                        stringResource(R.string.discover_novels)
                    } else {
                        stringResource(R.string.discover_works)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
    )
}

@Composable
private fun DiscoveryTagWorks(
    trend: TrendingTag,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    refreshVersion: Int,
) {
    val tag = trend.tag
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spec =
        remember(tag.name, trend.cover.id) {
            FeedSpec(section = "related", userId = trend.cover.id)
        }
    val flow = remember(spec, vm.accountId, settings.contentFilter(), refreshVersion) {
        vm.feed(spec, refreshVersion)
    }
    val works = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${if (settings.showTagTranslations) tag.translated_name ?: tag.name else tag.name} · 相关作品",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                navigate(Collection(tag.name, "search", word = tag.name, tagCover = trend.cover))
            }) { Text("查看全部") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
            if (works.itemCount == 0 && works.loadState.refresh is LoadState.Loading) {
                items(5) {
                    Spacer(
                        Modifier
                            .width(144.dp)
                            .height(180.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.small,
                            ),
                    )
                }
            } else {
                items(minOf(5, works.itemCount), key = { index ->
                    works.peek(index)?.id
                        ?: -index.toLong()
                }) { index ->
                    works[index]?.let { work ->
                        val identity = work.identity(vm.accountId)
                        val current = bookmarks[identity]?.apply(work) ?: work
                        Box(Modifier.width(180.dp * current.aspect)) {
                            WorkCard(
                                current,
                                likedBusy = identity in busy,
                                showMetadata = settings.showHomeMetadata,
                                onLike = { vm.run { vm.bookmark(current) } },
                                onClick = {
                                    vm.record(current)
                                    navigate(Detail(current, spec, index))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryAuthorCard(
    preview: UserPreview,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    var user by remember(preview.user) { mutableStateOf(preview.user) }
    var busy by remember { mutableStateOf(false) }
    val background by produceState<String?>(null, preview.user.id, vm.accountId) {
        value =
            try {
                vm.authorDetails(preview.user).profile.background_image_url
            } catch (
                e: kotlinx.coroutines.CancellationException,
            ) {
                throw e
            } catch (_: Exception) {
                null
            }
    }
    ElevatedCard(onClick = { navigate(Author(user)) }, modifier = Modifier.width(260.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            background?.takeIf { it.isNotBlank() }?.let { url ->
                coil3.compose.AsyncImage(
                    url,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(PixivSpacing.content),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
        ) {
            Avatar(user, Modifier.size(64.dp), sharedTransition = true)
            Text(
                user.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.comment.isNotBlank()) {
                Text(
                    user.comment,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilledTonalButton(onClick = {
                vm.run {
                    busy = true
                    try {
                        user = vm.follow(user)
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy) { Text(if (user.is_followed) "已关注" else "关注") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.tight)) {
            preview.illusts.take(3).forEach { work ->
                WorkImage(
                    work,
                    Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable {
                            vm.record(work)
                            navigate(Detail(work))
                        },
                )
            }
        }
    }
}

@Composable
private fun DiscoveryPlaceholders() {
    Text(
        stringResource(R.string.discover_tags),
        style = MaterialTheme.typography.titleMedium,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(3) {
            SuggestionChip(
                onClick = {},
                enabled = false,
                modifier = Modifier.width(112.dp),
                label = { Spacer(Modifier.height(20.dp)) },
            )
        }
    }
    Text(
        stringResource(R.string.discover_popular_tags),
        style = MaterialTheme.typography.titleMedium,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(5) {
            Spacer(
                Modifier
                    .width(124.dp)
                    .height(204.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    ),
            )
        }
    }
    repeat(3) {
        Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
            Spacer(
                Modifier
                    .width(160.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
                items(5) {
                    Spacer(
                        Modifier
                            .width(144.dp)
                            .height(200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}
