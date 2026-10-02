package io.github.radiqalo.picaris.download

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import io.github.radiqalo.picaris.core.LibraryDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DownloadAccessRecovery(
    private val context: Context,
    private val dao: LibraryDao,
    private val mediaUri: (Uri) -> Uri? = { document ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getMediaUri(context, document)
        } else {
            null
        }
    },
) {
    suspend fun restore(tree: Uri): Int = rebind(index(tree))

    internal suspend fun rebind(bindings: DownloadUriBindings): Int {
        var restored = 0
        for (task in dao.allDownloads().filter { it.status == "complete" }) {
            currentCoroutineContext().ensureActive()
            val uri = bindings.mainUri(task.uri)
            val cover = bindings.file(task.coverUri)
            if (uri != task.uri || cover != task.coverUri) {
                restored += dao.rebindDownloadUri(task.id, task.uri, task.coverUri, uri, cover)
            }
        }
        return restored
    }

    private suspend fun index(tree: Uri): DownloadUriBindings {
        val root = checkNotNull(DocumentFile.fromTreeUri(context, tree)) { "无法读取所选目录" }
        check(root.isDirectory && root.canRead()) { "所选目录不可读取" }
        val files = mutableMapOf<String, String>()
        val folders = mutableMapOf<String, String>()
        val queue = ArrayDeque<DocumentFile>().apply { add(root) }
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val document = queue.removeFirst()
            if (!visited.add(document.uri.toString())) continue
            check(visited.size <= MAX_RECOVERY_DOCUMENTS) { "目录文件过多，请选择更小的下载目录" }
            if (document.isDirectory) {
                indexFolder(tree, document, folders)
                queue.addAll(document.listFiles().toList())
            } else if (document.canRead()) {
                indexFile(document.uri, files)
            }
        }
        return DownloadUriBindings(files, folders)
    }

    private fun indexFolder(
        tree: Uri,
        document: DocumentFile,
        folders: MutableMap<String, String>,
    ) {
        val id = DocumentsContract.getDocumentId(document.uri)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        if (tree.authority == "com.android.externalstorage.documents" &&
            (id == rootId || id.startsWith("$rootId/"))
        ) {
            val relative = if (id == rootId) "" else id.removePrefix("$rootId/")
            val replacement = "saf-folder|$tree|$relative"
            downloadUriIdentity(document.uri.toString())?.let {
                folders["document:$it"] = replacement
            }
            if (id.startsWith("primary:")) {
                folders[id.removePrefix("primary:").trimEnd('/')] = replacement
            }
        }
    }

    private fun indexFile(
        uri: Uri,
        files: MutableMap<String, String>,
    ) {
        downloadUriIdentity(uri.toString())?.let { files[it] = uri.toString() }
        if (uri.authority in
            setOf("com.android.externalstorage.documents", "com.android.providers.media.documents")
        ) {
            // The system translates document IDs to their original MediaStore row IDs.
            mediaUri(uri)?.toString()?.let(::downloadUriIdentity)?.let {
                files[it] = uri.toString()
            }
        }
    }

    private companion object {
        const val MAX_RECOVERY_DOCUMENTS = 100_000
    }
}
