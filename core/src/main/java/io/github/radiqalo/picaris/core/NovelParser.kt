package io.github.radiqalo.picaris.core

import kotlinx.serialization.json.*

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
fun parseNovelHtml(html: String): NovelBody {
    val anchor =
        Regex("""Object\.defineProperty\(\s*window\s*,\s*['"]pixiv['"]""").find(html)?.range?.first
            ?: error("无法识别小说正文，接口格式可能已更新")
    val value = Regex("""\bvalue\s*:""").find(html, anchor)?.range?.first ?: error("小说正文数据缺失")
    val start = html.indexOf('{', value).takeIf { it >= 0 } ?: error("小说正文数据缺失")
    var depth = 0
    var quoted = false
    var escaped = false
    var end = -1
    for (i in start until html.length) {
        val c = html[i]
        if (quoted) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else
            when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i + 1
                        break
                    }
                }
            }
    }
    check(end > start) { "小说数据未完整返回" }
    val parser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }
    val root =
        parser.parseToJsonElement(html.substring(start, end)).jsonObject["novel"]?.jsonObject
            ?: error("作品正文不可访问")
    val text = root["text"]?.jsonPrimitive?.contentOrNull ?: error("作品没有正文")
    val images = mutableMapOf<String, String>()
    fun imageUrl(e: JsonElement?): String? {
        if (e is JsonObject) {
            for (key in listOf("original", "regular", "large")) (e[key] as? JsonPrimitive)
                ?.contentOrNull
                ?.takeIf { it.startsWith("https://") }
                ?.let {
                    return it
                }
            for (v in e.values) imageUrl(v)?.let {
                return it
            }
        }
        if (e is JsonArray)
            for (v in e) imageUrl(v)?.let {
                return it
            }
        return null
    }
    for (key in listOf("uploadedImages", "pixivImages")) (root[key] as? JsonObject)?.forEach {
        (id, image) ->
        imageUrl(image)?.let { images[id] = it }
    }
    return NovelBody(text, images)
}
