package io.github.pixivnext.core

import android.text.Html
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class PixivisionArticle(
    val id: Long,
    val title: String,
    val cover: String,
    val url: String,
    val demo: Int = -1,
)

fun parsePixivisionArticles(html: String): List<PixivisionArticle> {
    val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    val articles = Regex("""<article\b[^>]*>(.*?)</article>""", options)
    val link = Regex("""href\s*=\s*["']/zh/a/(\d+)["']""", options)
    val heading = Regex("""<h2\b[^>]*>(.*?)</h2>""", options)
    val image = Regex("""background-image\s*:\s*url\(\s*["']?([^"')\s]+)""", options)
    fun text(value: String) = Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    return articles.findAll(html).mapNotNull { match ->
        val body = match.groupValues[1]
        val id = link.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
        val title = heading.find(body)?.groupValues?.get(1)?.let(::text).orEmpty()
        val cover = image.find(body)?.groupValues?.get(1)?.let(::text)?.toHttpUrlOrNull()
        if (title.isBlank() || cover == null || cover.scheme != "https" ||
            !cover.host.endsWith(".pximg.net")
        ) return@mapNotNull null
        PixivisionArticle(id, title, cover.toString(), "https://www.pixivision.net/zh/a/$id")
    }.distinctBy { it.id }.take(5).toList().also {
        check(it.isNotEmpty()) { "无法识别 PIXIVISION 特辑，网站格式可能已更新" }
    }
}

@Singleton
class PixivisionRepository @Inject constructor(private val network: Network) {
    suspend fun articles(): List<PixivisionArticle> {
        val response = network.client().get("https://www.pixivision.net/zh/") {
            header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9")
        }
        check(response.status.isSuccess()) { "PIXIVISION 加载失败（HTTP ${response.status.value}）" }
        return parsePixivisionArticles(response.bodyAsText())
    }
}
