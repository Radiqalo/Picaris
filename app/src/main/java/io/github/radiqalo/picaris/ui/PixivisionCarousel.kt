package io.github.radiqalo.picaris.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.PixivisionArticle
import io.github.radiqalo.picaris.designsystem.PixivSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val PixivisionMaxItemWidth = 520.dp
private val PixivisionMinSmallItemWidth = 36.dp
private val PixivisionMaxSmallItemWidth = 64.dp
private const val PixivisionHeroAspectRatio = 1.65f

private data class PixivisionContent(
    val articles: List<PixivisionArticle>? = null,
    val failed: Boolean = false,
)

@Composable
fun PixivisionCarousel(vm: AppViewModel, refreshVersion: Int = 0) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState(
        PixivisionContent(vm.cachedPixivisionArticles()),
        retry,
        refreshVersion,
    ) {
        value = PixivisionContent(vm.cachedPixivisionArticles())
        value = try {
            PixivisionContent(vm.pixivisionArticles())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            PixivisionContent(failed = true)
        }
    }
    val articles = result.articles
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val heroWidth =
            (maxWidth - (PixivisionMinSmallItemWidth + PixivSpacing.compact) * 2)
                .coerceAtLeast(0.dp)
                .coerceAtMost(PixivisionMaxItemWidth)
        val heroHeight = heroWidth / PixivisionHeroAspectRatio
        if (articles.isNullOrEmpty()) {
            val placeholderCarousel = rememberCarouselState(itemCount = { 3 })
            HorizontalCenteredHeroCarousel(
                state = placeholderCarousel,
                modifier = Modifier.fillMaxWidth().testTag("pixivisionCarousel"),
                maxItemWidth = PixivisionMaxItemWidth,
                minSmallItemWidth = PixivisionMinSmallItemWidth,
                maxSmallItemWidth = PixivisionMaxSmallItemWidth,
                itemSpacing = PixivSpacing.compact,
            ) { placeholderIndex ->
                Box(
                    Modifier
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (placeholderIndex == placeholderCarousel.currentItem) {
                        Column(
                            Modifier.padding(PixivSpacing.content),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact),
                        ) {
                            Text(
                                stringResource(R.string.discover_pixivision),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (result.failed) {
                                Text(stringResource(R.string.pixivision_load_failed))
                                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.pixivision_retry)) }
                            } else CircularWavyProgressIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }
        } else {
            val carousel = rememberCarouselState(itemCount = { articles.size })
            val carouselScope = rememberCoroutineScope()
            val pageDescription = stringResource(R.string.pixivision_page, carousel.currentItem + 1, articles.size)
            HorizontalCenteredHeroCarousel(
                state = carousel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pixivisionCarousel")
                    .semantics { stateDescription = pageDescription },
                maxItemWidth = PixivisionMaxItemWidth,
                minSmallItemWidth = PixivisionMinSmallItemWidth,
                maxSmallItemWidth = PixivisionMaxSmallItemWidth,
                itemSpacing = PixivSpacing.compact,
            ) { articleIndex ->
                val article = articles[articleIndex]
                val itemInfo = carouselItemDrawInfo
                val targetTextAlpha =
                    if (itemInfo.maxSize > 0f) {
                        (itemInfo.size / itemInfo.maxSize).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val textAlpha by animateFloatAsState(targetTextAlpha, label = "pixivisionTitleFade")
                Box(
                    Modifier
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .fillMaxWidth()
                        .height(heroHeight)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable {
                            if (carousel.currentItem == articleIndex) {
                                if (article.url.isNotBlank()) {
                                    CustomTabsIntent.Builder().build().launchUrl(context, article.url.toUri())
                                }
                            } else {
                                carouselScope.launch { carousel.animateScrollToItem(articleIndex) }
                            }
                        },
                ) {
                    AsyncImage(
                        model = article.cover,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                ),
                            ),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(PixivSpacing.content)
                            .graphicsLayer { alpha = textAlpha },
                        verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                    ) {
                        Text(
                            stringResource(R.string.discover_pixivision),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            article.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
