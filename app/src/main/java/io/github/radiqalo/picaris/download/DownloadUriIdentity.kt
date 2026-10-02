package io.github.radiqalo.picaris.download

import java.net.URI

/** Identity comes from provider IDs, never the displayed filename or download database row ID. */
internal fun downloadUriIdentity(value: String): String? =
    runCatching {
        val uri = URI(value)
        if (uri.scheme != "content") {
            null
        } else {
            when (uri.authority) {
                null -> null
                "media" -> mediaIdentity(uri.path.orEmpty())
                else -> documentIdentity(uri)
            }
        }
    }.getOrNull()

private fun mediaIdentity(path: String): String? {
    val parts = path.trim('/').split('/')
    val id = parts.lastOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val collection = parts.drop(1).dropLast(1).joinToString("/")
    val volume = parts.first().let { if (it == "external") "external_primary" else it }
    return if (collection in
        setOf("images/media", "video/media", "audio/media", "file", "downloads")
    ) {
        "media:$volume:$id"
    } else {
        null
    }
}

private fun documentIdentity(uri: URI): String? {
    val encodedId = uri.rawPath?.substringAfter("/document/", "")?.takeIf(String::isNotEmpty)
    return encodedId?.let { "${uri.authority}:${URI("content://id/$it").path.removePrefix("/")}" }
}
