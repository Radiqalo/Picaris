package io.github.radiqalo.picaris.core

/** Only known tokens are rendered; separators never become directory traversal. */
object DownloadNaming {
    val tokens = linkedMapOf("id" to "作品 ID", "title" to "作品名", "author" to "作者名",
        "authorId" to "作者 ID", "page" to "p数")
    val separators = linkedMapOf("" to "无分割", " " to "空格", "_" to "下划线", "-" to "连字符")

    fun safe(value: String): String = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim().trim('.').take(120).ifBlank { "Picaris" }

    fun render(parts: List<String>, work: Work, page: Int = 0, omitZero: Boolean = false, separator: String = "_"): String {
        val values = mapOf("id" to work.id.toString(), "title" to work.title,
            "author" to work.user.name, "authorId" to work.user.id.toString(),
            "page" to if (work.isNovel || (omitZero && page == 0)) "" else "p$page")
        val visible = parts.filter { it in tokens && (it != "page" || values["page"].orEmpty().isNotEmpty()) }
            .mapNotNull { values[it]?.takeIf(String::isNotEmpty) }
        val safeSeparator = separator.takeIf { it in separators } ?: "_"
        return safe(visible.joinToString(safeSeparator))
    }

    fun preview(parts: List<String>, omitZero: Boolean = false, separator: String = "_"): String {
        val values = mapOf(
            "id" to "ID",
            "title" to "作品名",
            "author" to "作者名",
            "authorId" to "ID",
            "page" to if (omitZero) "" else "p0",
        )
        val visible = parts.filter { it in tokens && (it != "page" || values["page"].orEmpty().isNotEmpty()) }
            .mapNotNull { values[it]?.takeIf(String::isNotEmpty) }
        val safeSeparator = separator.takeIf { it in separators } ?: "_"
        return safe(visible.joinToString(safeSeparator))
    }

    fun category(settings: Settings, work: Work): String = when {
        settings.downloadAiFolder && work.illust_ai_type == 2 -> "AI"
        settings.downloadAdultFolder && work.x_restrict != 0 -> "R18"
        else -> ""
    }
}
