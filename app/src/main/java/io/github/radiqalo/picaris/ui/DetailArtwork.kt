package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
internal fun DetailArtworkPage(
    work: Work,
    page: Int,
    modifier: Modifier,
    onOpenPage: (Int) -> Unit,
) {
    val url = work.previews.getOrNull(page) ?: return
    val fallbackAspect =
        if (work.width > 1 && work.height > 1) {
            work.width.toFloat() / work.height
        } else {
            work.aspect
        }
    var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val imageWidth =
            if (constraints.hasBoundedHeight) {
                minOf(
                    maxWidth,
                    maxHeight * aspect,
                )
            } else {
                maxWidth
            }
        WorkImage(
            work,
            Modifier
                .width(imageWidth)
                .aspectRatio(aspect)
                .clickable { onOpenPage(page) }
                .testTag(if (page == 0) "detailImage" else "detailImage:$page"),
            url = url,
            scale = ContentScale.Fit,
            sharedTransition = page == 0,
            rounded = false,
            onImageAspectRatio = { aspect = it },
        )
    }
}

@Composable
internal fun DetailArtworkFlow(
    work: Work,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onOpenPage: (Int) -> Unit,
) {
    val pages = work.previews
    if (pages.size == 1) {
        val url = pages.first()
        val fallbackAspect =
            if (work.width > 1 && work.height > 1) {
                work.width.toFloat() / work.height
            } else {
                work.aspect
            }
        var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
        BoxWithConstraints(
            modifier = modifier.testTag("detailImages").padding(contentPadding).clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            WorkImage(
                work,
                Modifier
                    .width(minOf(maxWidth, maxHeight * aspect))
                    .aspectRatio(aspect)
                    .clickable { onOpenPage(0) }
                    .testTag("detailImage"),
                url = url,
                scale = ContentScale.Fit,
                sharedTransition = true,
                rounded = false,
                onImageAspectRatio = { aspect = it },
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.testTag("detailImages"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(pages.size, key = { page -> "${work.id}:$page" }) { page ->
            val url = pages[page]
            val fallbackAspect =
                if (work.width > 1 && work.height > 1) {
                    work.width.toFloat() / work.height
                } else {
                    work.aspect
                }
            var aspect by remember(url) { mutableFloatStateOf(fallbackAspect) }
            WorkImage(
                work,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clickable { onOpenPage(page) }
                    .testTag(if (page == 0) "detailImage" else "detailImage:$page"),
                url = url,
                scale = ContentScale.Fit,
                sharedTransition = page == 0,
                rounded = false,
                onImageAspectRatio = { aspect = it },
            )
        }
    }
}

@Composable
internal fun RelatedWorkStrip(
    work: Work,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    navigateRelatedDetail: (Detail) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val spec = remember(work.id) { FeedSpec(section = "related", userId = work.id) }
    val flow = remember(spec, vm.accountId, settings.contentFilter()) { vm.feed(spec) }
    val related = flow.collectAsLazyPagingItems()
    val bookmarks by vm.bookmarkStates.collectAsStateWithLifecycle()
    val busy by vm.bookmarkBusy.collectAsStateWithLifecycle()
    val relatedTitle = stringResource(R.string.ui_29ffbeb614)
    val openAll = {
        navigate(Collection(relatedTitle, "related", userId = work.id))
    }
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        Text(
            relatedTitle,
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (related.itemCount == 0 && related.loadState.refresh is LoadState.Loading) {
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
                items(minOf(5, related.itemCount), key = { index ->
                    related.peek(index)?.let { "${it.type}_${it.id}" } ?: "related_$index"
                }) { index ->
                    related[index]?.let { artwork ->
                        val identity = artwork.identity(vm.accountId)
                        val current = bookmarks[identity]?.apply(artwork) ?: artwork
                        Box(Modifier.width(180.dp * current.aspect)) {
                            WorkCard(
                                current,
                                likedBusy = identity in busy,
                                showMetadata = false,
                                onLike = { vm.run { vm.bookmark(current) } },
                                onClick = {
                                    vm.record(current)
                                    navigateRelatedDetail(Detail(current, spec, index))
                                },
                            )
                        }
                    }
                }
            }
            item(key = "more") {
                FilledTonalIconButton(onClick = openAll) {
                    AppIcon(materialSymbol(MaterialSymbol.ChevronRight), "查看全部相关作品")
                }
            }
        }
    }
}
