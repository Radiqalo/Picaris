package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.CarouselParallaxScrollEffectState
import androidx.compose.material3.carousel.carouselParallaxScrollEffect
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
    val discoveryFeedback = selectionFeedback()
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
                    Text(rankingTitle, style = MaterialTheme.typography.labelLarge)
                }
                if (loadingTrends) {
                    DiscoveryPlaceholders()
                } else if (trends.isNotEmpty()) {
                    Text(
                        stringResource(R.string.discover_tags),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trends, key = { it.tag.name }) { trend ->
                            SuggestionChip(
                                onClick = {
                                    discoveryFeedback()
                                    navigate(
                                        Collection(
                                            trend.tag.name,
                                            "search",
                                            word = trend.tag.name,
                                            tagCover = trend.cover,
                                        ),
                                    )
                                },
                                label = { TagLabel(trend.tag) },
                            )
                        }
                    }
                    if (trends.size > 3) {
                        Text(
                            stringResource(R.string.discover_popular_tags),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val popularTrends = trends.drop(3).take(7)
                        val popularTagCarousel = rememberCarouselState(itemCount = { popularTrends.size })
                        HorizontalUncontainedCarousel(
                            state = popularTagCarousel,
                            modifier = Modifier.fillMaxWidth(),
                            itemWidth = 124.dp,
                            itemSpacing = PixivSpacing.compact,
                            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(popularTagCarousel),
                        ) { index ->
                            val trend = popularTrends[index]
                            val coverRatio = trend.cover.width.toFloat() / trend.cover.height.coerceAtLeast(1)
                            val itemHeight = 204.dp
                            val itemWidth = (itemHeight * coverRatio).coerceIn(124.dp, 240.dp)
                            Box(
                                Modifier
                                    .maskClip(MaterialTheme.shapes.medium)
                                    .width(itemWidth)
                                    .height(itemHeight)
                            ) {
                                WorkImage(
                                    trend.cover,
                                    Modifier.fillMaxSize(),
                                    url = trend.cover.imageForQuality(settings.feedImageQuality),
                                    rounded = false,
                                    overlay = {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                                                    ),
                                                ),
                                        )
                                        Surface(
                                            Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(start = 8.dp, bottom = 8.dp)
                                                .clip(MaterialTheme.shapes.small),
                                            color = Color.Black.copy(alpha = 0.48f),
                                            contentColor = Color.White,
                                        ) {
                                            Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                TagLabel(trend.tag, translationFirst = false)
                                            }
                                        }
                                    },
                                )
                                Surface(
                                    onClick = {
                                        discoveryFeedback()
                                        navigate(
                                            Collection(
                                                trend.tag.name,
                                                "search",
                                                word = trend.tag.name,
                                                tagCover = trend.cover,
                                            ),
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium),
                                    color = Color.Transparent,
                                    contentColor = Color.Transparent,
                                ) {}
                            }
                        }
                    }
                    trends.drop(3).take(3).forEach { trend ->
                        DiscoveryTagWorks(trend, vm, navigate, refreshVersion)
                    }
                }
                if (settings.contentKind != "novel") {
                    Text(
                        stringResource(R.string.discover_artists),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
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
                    fontWeight = FontWeight.SemiBold,
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
    val tagFeedback = selectionFeedback()
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = {
                tagFeedback()
                navigate(Collection(tag.name, "search", word = tag.name, tagCover = trend.cover))
            }) { Text("查看全部", style = MaterialTheme.typography.labelLarge) }
        }
        val workListState = key(spec) { rememberLazyListState() }
        val parallaxState = remember(workListState) { CarouselParallaxScrollEffectState(workListState) }
        LazyRow(
            state = workListState,
            horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            modifier = Modifier.fillMaxWidth().height(224.dp),
        ) {
            items(
                count = works.itemCount.coerceAtLeast(if (works.loadState.refresh is LoadState.Loading) 5 else 0),
                key = { index ->
                    if (index < works.itemCount) works.peek(index)?.id ?: -(index + 1L)
                    else Long.MIN_VALUE + index
                },
            ) { index ->
                if (index >= works.itemCount) {
                    Spacer(
                        Modifier
                            .width(144.dp)
                            .height(204.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else {
                    works[index]?.let { work ->
                        val identity = work.identity(vm.accountId)
                        val current = bookmarks[identity]?.apply(work) ?: work
                        val imageHeight = 204.dp
                        val imageAspect = if (current.isNovel) .9f else current.aspect
                        val cardShape = MaterialTheme.shapes.medium
                        Box(
                            Modifier
                                .width(imageHeight * imageAspect)
                                .height(imageHeight)
                                .carouselParallaxScrollEffect(index, parallaxState, cardShape)
                                .feedbackClickable {
                                    vm.record(current)
                                    navigate(Detail(current, spec, index))
                                },
                        ) {
                            WorkImage(
                                current,
                                Modifier.fillMaxSize(),
                                sharedTransition = true,
                            ) {
                                if (current.page_count > 1 || current.type == "ugoira" || current.isNovel) {
                                    Surface(
                                        Modifier.align(Alignment.TopStart).padding(PixivSpacing.compact),
                                        shape = MaterialTheme.shapes.small,
                                        color = Color.Black.copy(alpha = .48f),
                                        contentColor = Color.White,
                                    ) {
                                        Text(
                                            if (current.isNovel) stringResource(R.string.content_novel)
                                            else if (current.type == "ugoira") stringResource(R.string.ui_de9dcfdf88)
                                            else "${current.page_count}P",
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { vm.run { vm.bookmark(current) } },
                                    enabled = identity !in busy,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(PixivSpacing.compact).size(32.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.Black.copy(alpha = .48f),
                                    contentColor = Color.White,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        FeedbackIcon(
                                            if (current.is_bookmarked) materialSymbol(MaterialSymbol.FavoriteFilled)
                                            else materialSymbol(MaterialSymbol.Favorite),
                                            null,
                                            selected = current.is_bookmarked,
                                            modifier = Modifier.size(20.dp),
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

@Composable
private fun DiscoveryAuthorCard(
    preview: UserPreview,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                    url = work.imageForQuality(settings.feedImageQuality),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryPlaceholders() {
    Text(
        stringResource(R.string.discover_tags),
        style = MaterialTheme.typography.titleLarge,
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
        style = MaterialTheme.typography.titleLarge,
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
