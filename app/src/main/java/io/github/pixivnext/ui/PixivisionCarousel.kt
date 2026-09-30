package io.github.pixivnext.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.github.pixivnext.AppViewModel
import io.github.pixivnext.R
import io.github.pixivnext.core.PixivisionArticle
import io.github.pixivnext.designsystem.PixivSpacing
import kotlinx.coroutines.CancellationException

private data class PixivisionContent(
    val articles: List<PixivisionArticle>? = null,
    val failed: Boolean = false,
)

@Composable
fun PixivisionCarousel(vm: AppViewModel) {
    val strings = LocalResources.current
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState(PixivisionContent(vm.cachedPixivisionArticles()), retry) {
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
    if (articles.isNullOrEmpty()) {
        Card(Modifier.fillMaxWidth().height(200.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.fillMaxSize().padding(PixivSpacing.content),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact, Alignment.CenterVertically),
            ) {
                Text(strings.getString(R.string.discover_pixivision), style = MaterialTheme.typography.titleMedium)
                if (result.failed) {
                    Text(strings.getString(R.string.pixivision_load_failed))
                    TextButton(onClick = { retry++ }) { Text(strings.getString(R.string.pixivision_retry)) }
                } else CircularWavyProgressIndicator(Modifier.size(32.dp))
            }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { articles.size })
    Column(verticalArrangement = Arrangement.spacedBy(PixivSpacing.compact)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageWidth = (maxWidth * 0.9f).coerceAtMost(520.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = PixivSpacing.compact,
                modifier = Modifier.fillMaxWidth().testTag("pixivisionCarousel"),
                key = { articles[it].id },
            ) { page ->
                val article = articles[page]
                Card(
                    onClick = { CustomTabsIntent.Builder().build().launchUrl(context, article.url.toUri()) },
                    enabled = article.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.65f)) {
                        AsyncImage(
                            model = article.cover,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.matchParentSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))),
                        ))
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(PixivSpacing.content),
                            verticalArrangement = Arrangement.spacedBy(PixivSpacing.tight),
                        ) {
                            Text(
                                strings.getString(R.string.discover_pixivision),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Text(
                                article.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = strings.getString(R.string.pixivision_page, pager.currentPage + 1, articles.size)
            },
            horizontalArrangement = Arrangement.spacedBy(PixivSpacing.compact, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(articles.size) { index ->
                Spacer(Modifier.size(if (index == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (index == pager.currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            }
        }
    }
}
