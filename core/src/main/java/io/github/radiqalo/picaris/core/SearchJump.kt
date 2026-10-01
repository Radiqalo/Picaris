package io.github.radiqalo.picaris.core

import java.net.URI

/** Only explicit IDs and Pixiv links jump; bare numbers remain search keywords. */
sealed interface SearchJump {
    data class Artwork(val id: Long, val novel: Boolean) : SearchJump
    data class Artist(val id: Long) : SearchJump
}

fun parseSearchJump(input: String, novelMode: Boolean): SearchJump? {
    val value = input.trim()
    fun id(value: String?) = value?.toLongOrNull()?.takeIf { it > 0 }
    Regex("^(?:画师|作者|用户|user|artist|uid)(?:\\s*id)?\\s*[:：#]?\\s*(\\d+)$", RegexOption.IGNORE_CASE)
        .matchEntire(value)?.groupValues?.get(1)?.let { id(it) }
        ?.let { return SearchJump.Artist(it) }
    Regex("^(作品|插画|小说|illust|artwork|novel)(?:\\s*id)?\\s*[:：#]?\\s*(\\d+)$", RegexOption.IGNORE_CASE)
        .matchEntire(value)?.let { match ->
            id(match.groupValues[2])?.let {
                val kind = match.groupValues[1].lowercase()
                return SearchJump.Artwork(it, kind == "小说" || kind == "novel" || (kind == "作品" && novelMode))
            }
        }
    val link = Regex("(?:https?://)?(?:www\\.|touch\\.)?pixiv\\.net/[^\\s]+", RegexOption.IGNORE_CASE)
        .find(value)?.value ?: return null
    val uri = runCatching { URI(if (link.startsWith("http", true)) link else "https://$link") }.getOrNull()
        ?: return null
    if (uri.host?.lowercase() !in setOf("pixiv.net", "www.pixiv.net", "touch.pixiv.net")) return null
    val path = uri.path.orEmpty().removePrefix("/en").removePrefix("/ja")
    val parts = path.trim('/').split('/')
    val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2) pair[0] to pair[1] else null
    }.toMap()
    when {
        parts.firstOrNull() == "artworks" -> id(parts.getOrNull(1))?.let { return SearchJump.Artwork(it, false) }
        parts.firstOrNull() == "users" -> id(parts.getOrNull(1))?.let { return SearchJump.Artist(it) }
        path == "/novel/show.php" -> id(query["id"])?.let { return SearchJump.Artwork(it, true) }
        path == "/member_illust.php" -> id(query["illust_id"])?.let { return SearchJump.Artwork(it, false) }
        path == "/member.php" -> id(query["id"])?.let { return SearchJump.Artist(it) }
    }
    return null
}
