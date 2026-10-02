package io.github.radiqalo.picaris.download

import java.net.URI

private const val FOLDER_URI_PARTS = 3

internal class DownloadUriBindings(
    private val files: Map<String, String>,
    private val folders: Map<String, String>,
) {
    fun file(value: String): String = downloadUriIdentity(value)?.let(files::get) ?: value

    fun mainUri(value: String): String =
        when {
            value.startsWith("media-folder|") ->
                folders[value.substringAfter('|').trimEnd('/')]
                    ?: value
            value.startsWith("saf-folder|") ->
                documentFolderIdentity(value)?.let(folders::get)
                    ?: value
            else -> file(value)
        }
}

private fun documentFolderIdentity(value: String): String? =
    runCatching {
        val parts = value.split('|', limit = FOLDER_URI_PARTS)
        if (parts.size != FOLDER_URI_PARTS) {
            null
        } else {
            val tree = URI(parts[1])
            if (tree.authority != "com.android.externalstorage.documents") {
                null
            } else {
                val encoded = tree.rawPath.substringAfter("/tree/").substringBefore('/')
                val root = URI("content://id/$encoded").path.removePrefix("/").trimEnd('/')
                val relative = parts[2].trim('/')
                val separator = if (root.endsWith(':')) "" else "/"
                val id = if (relative.isEmpty()) root else "$root$separator$relative"
                "document:${tree.authority}:$id"
            }
        }
    }.getOrNull()
