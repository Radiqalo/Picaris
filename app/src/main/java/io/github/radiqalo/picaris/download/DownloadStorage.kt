package io.github.radiqalo.picaris.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.radiqalo.picaris.core.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes publication and explicit organization so records always point to the saved file. */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()
    data class Destination(val tree: String, val path: String)

    suspend fun destination(task: DownloadEntity, s: Settings, refresh: Boolean = false): Destination {
        val work = AppJson.decodeFromString<Work>(task.workJson)
        val tree = if (task.kind == "novel") s.novelDownloadTree else s.downloadTree
        val base = when (task.kind) { "novel" -> "Documents/Picaris"; "ugoira-originals" -> "Download/Picaris"; else -> "Pictures/Picaris" }
        val category = DownloadNaming.category(s, work)
        val parts = mutableListOf<String>()
        if (category.isNotEmpty()) parts += category
        if (s.downloadAuthorFolder) {
            val identity = listOf(tree.ifEmpty { base }, category, work.user.id.toString())
            val key = AppJson.encodeToString(identity)
            val remembered = if (refresh) null else dao.downloadFolder(key)
            val folder = remembered ?: run {
                val requested = DownloadNaming.render(s.downloadAuthorTokens, work, separator = s.downloadAuthorSeparator)
                val collision = dao.downloadFolders().any { existing ->
                    existing.key != key && existing.name == requested &&
                        runCatching { AppJson.decodeFromString<List<String>>(existing.key).take(2) == identity.take(2) }.getOrDefault(false)
                }
                val chosen = if (collision) requested.take(95) + "_${work.user.id}" else requested
                dao.rememberDownloadFolder(DownloadFolder(key, chosen))
                chosen
            }
            parts += folder
        }
        return Destination(tree, (if (tree.isEmpty()) listOf(base) else emptyList<String>()).plus(parts).joinToString("/"))
    }

    fun name(task: DownloadEntity, s: Settings): String {
        val work = AppJson.decodeFromString<Work>(task.workJson)
        val extension = task.name.substringAfterLast('.', "txt").lowercase()
        return DownloadNaming.render(s.downloadFileTokens, work, task.page, s.downloadOmitPageZero, s.downloadFileSeparator) + ".$extension"
    }

    fun folder(destination: Destination): DocumentFile {
        var current = DocumentFile.fromTreeUri(context, destination.tree.toUri()) ?: error("下载目录授权不可用")
        destination.path.split('/').filter { it.isNotEmpty() }.forEach { part ->
            current = current.findFile(part)?.also { require(it.isDirectory) { "目录名称与文件冲突：$part" } }
                ?: current.createDirectory(part) ?: error("无法创建目录：$part")
        }
        return current
    }

    suspend fun save(task: DownloadEntity, file: File, cover: File? = null): Uri = lock.withLock {
        val s = settings.flow.first()
        val dest = destination(task, s)
        val preferred = name(task, s)
        val name = uniqueName(dest, preferred, cover != null)
        val uri = write(dest, name, mime(name), file)
        var coverUri: Uri? = null
        try {
            if (cover != null) {
                coverUri = if (task.kind == "novel") savePrivateNovelCover(task.id, cover)
                    else write(dest, name.substringBeforeLast('.') + "_cover.jpg", "image/jpeg", cover)
                dao.savedDownloadCover(task.id, coverUri.toString())
            }
            dao.savedDownloadName(task.id, name)
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            coverUri?.let(::deleteUri)
            throw e
        }
        uri
    }

    suspend fun saveArchive(task: DownloadEntity, archive: File, frames: ByteArray): Uri = lock.withLock {
        val s = settings.flow.first()
        val dest = destination(task, s)
        val work = AppJson.decodeFromString<Work>(task.workJson)
        val base = DownloadNaming.render(s.downloadFileTokens, work, omitZero = true, separator = s.downloadFileSeparator)
        var folderName = "${base}_${task.id}"
        var nested = dest.copy(path = listOf(dest.path, folderName).filter { it.isNotEmpty() }.joinToString("/"))
        var suffix = 1
        fun exists(): Boolean = if (dest.tree.isNotEmpty()) folder(dest).findFile(folderName) != null else
            context.contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(nested.path + "/"), null)?.use { it.moveToFirst() } == true
        while (exists()) {
            folderName = "${base}_${task.id}_${suffix++}"
            nested = dest.copy(path = listOf(dest.path, folderName).filter { it.isNotEmpty() }.joinToString("/"))
        }
        val saved = mutableListOf<Uri>()
        val temp = File.createTempFile("frames", ".json", context.cacheDir)
        try {
            temp.writeBytes(frames)
            saved += write(nested, "${base}_ugoira.zip", "application/zip", archive)
            saved += write(nested, "${base}_frames.json", "application/json", temp)
            dao.savedDownloadName(task.id, folderName)
        } catch (e: Exception) {
            saved.forEach { context.contentResolver.delete(it, null, null) }
            throw e
        } finally { temp.delete() }
        Uri.parse(if (dest.tree.isEmpty()) "media-folder|${nested.path}/" else "saf-folder|${dest.tree}|${nested.path}")
    }

    private fun uniqueName(dest: Destination, requested: String, hasCover: Boolean): String {
        val suffix = requested.substringAfterLast('.', "")
        val stem = requested.substringBeforeLast('.')
        var index = 0
        while (true) {
            val candidate = if (index == 0) requested else "$stem ($index).$suffix"
            val cover = candidate.substringBeforeLast('.') + "_cover.jpg"
            val conflict = if (dest.tree.isNotEmpty()) {
                val directory = folder(dest)
                directory.findFile(candidate) != null || (hasCover && directory.findFile(cover) != null)
            } else {
                fun exists(name: String): Boolean = context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(dest.path + "/", name), null,
                )?.use { it.moveToFirst() } == true
                exists(candidate) || (hasCover && exists(cover))
            }
            if (!conflict) return candidate
            index++
        }
    }

    fun mime(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "zip" -> "application/zip"
        "json" -> "application/json"
        else -> "text/plain"
    }

    fun write(destination: Destination, name: String, mime: String, file: File): Uri {
        val resolver = context.contentResolver
        val document = if (destination.tree.isNotEmpty()) {
            // SAF providers may silently replace an existing document: always choose a free name.
            val folder = folder(destination)
            var candidate = name
            var suffix = 1
            while (folder.findFile(candidate) != null) {
                candidate = name.substringBeforeLast('.') + " (${suffix++})." + name.substringAfterLast('.')
            }
            folder.createFile(mime, candidate) ?: error("无法创建下载文件")
        } else null
        val uri = document?.uri ?: resolver.insert(
            if (mime.startsWith("image/") && destination.path.startsWith("Pictures/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Files.getContentUri("external"),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, destination.path + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: error("无法创建下载文件")
        try {
            resolver.openOutputStream(uri, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("无法写入下载文件")
            if (document == null) resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Copy first, update the record, then remove the old file. Failed copies preserve the original. */
    suspend fun organize(account: Long): Pair<Int, Int> = withContext(Dispatchers.IO) {
        lock.withLock {
            val s = settings.flow.first()
            var done = 0
            var failed = 0
            val tasks = dao.finishedDownloads(account)
            val authors = mutableSetOf<List<String>>()
            for (task in tasks) {
                runCatching {
                    val work = AppJson.decodeFromString<Work>(task.workJson)
                    val key = listOf(if (task.kind == "novel") s.novelDownloadTree.ifEmpty { "Documents/Picaris" }
                        else s.downloadTree.ifEmpty { if (task.kind == "ugoira-originals") "Download/Picaris" else "Pictures/Picaris" },
                        DownloadNaming.category(s, work), work.user.id.toString())
                    if (authors.add(key)) destination(task, s, refresh = true)
                }
            }
            for (task in tasks) {
                try {
                    if (task.uri.startsWith("saf-folder|") || task.uri.startsWith("media-folder|")) {
                        organizeFolder(task, s)
                    } else {
                        val dest = destination(task, s)
                        val preferred = name(task, s)
                        val privateNovelCover = task.kind == "novel" && task.coverUri.toUri().scheme == "file"
                        val target = uniqueName(dest, preferred, task.coverUri.isNotEmpty() && !privateNovelCover)
                        val coverName = target.substringBeforeLast('.') + "_cover.jpg"
                        if (matches(task.uri, dest, target) && (task.coverUri.isEmpty() || privateNovelCover || matches(task.coverUri, dest, coverName))) {
                            done++
                            continue
                        }
                        val file = File.createTempFile("organize", ".part", context.cacheDir)
                        var newUri: Uri? = null
                        var newCover = task.coverUri.takeIf { privateNovelCover }.orEmpty()
                        try {
                            context.contentResolver.openInputStream(task.uri.toUri())?.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            } ?: error("原文件不可用")
                            newUri = write(dest, target, mime(target), file)
                            if (task.coverUri.isNotEmpty() && !privateNovelCover) {
                                context.contentResolver.openInputStream(task.coverUri.toUri())?.use { input ->
                                    file.outputStream().use { input.copyTo(it) }
                                } ?: error("封面不可用")
                                newCover = write(dest, target.substringBeforeLast('.') + "_cover.jpg", "image/jpeg", file).toString()
                            }
                            check(dao.relocateDownload(task.id, target, newUri.toString(), newCover) == 1) { "下载记录已变更" }
                        } catch (e: Exception) {
                            newUri?.let { context.contentResolver.delete(it, null, null) }
                            if (newCover.isNotEmpty() && newCover != task.coverUri) deleteUri(newCover.toUri())
                            throw e
                        } finally { file.delete() }
                        runCatching { deleteSourceAndPrune(task.uri.toUri()) }
                        if (task.coverUri.isNotEmpty() && !privateNovelCover) runCatching { deleteSourceAndPrune(task.coverUri.toUri()) }
                    }
                    done++
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failed++
                }
            }
            done to failed
        }
    }

    private fun matches(uri: String, dest: Destination, name: String): Boolean {
        val stem = Regex.escape(name.substringBeforeLast('.'))
        val extension = Regex.escape(name.substringAfterLast('.', ""))
        val collisionName = Regex("$stem(?: \\(\\d+\\))?\\.$extension")
        if (dest.tree.isNotEmpty()) return folder(dest).listFiles().any {
            it.uri.toString() == uri && collisionName.matches(it.name.orEmpty())
        }
        return context.contentResolver.query(uri.toUri(),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            null, null, null)?.use { it.moveToFirst() && collisionName.matches(it.getString(0)) && it.getString(1) == dest.path + "/" } == true
    }

    private suspend fun organizeFolder(task: DownloadEntity, s: Settings) {
        val sources = mutableListOf<Pair<String, Uri>>()
        var safTree: Uri? = null
        var oldSafPath: String? = null
        var oldMediaPath: String? = null
        if (task.uri.startsWith("saf-folder|")) {
            val (_, tree, name) = task.uri.split('|', limit = 3)
            safTree = tree.toUri()
            oldSafPath = name
            val folder = DocumentFile.fromTreeUri(context, tree.toUri())?.let { resolveDownloadFolder(it, name) } ?: error("原目录不可用")
            folder.listFiles().filter { it.isFile }.forEach { sources += (it.name ?: error("文件名不可用")) to it.uri }
        } else {
            oldMediaPath = task.uri.substringAfter('|').trimEnd('/')
            context.contentResolver.query(MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(task.uri.substringAfter('|')), null)?.use { cursor ->
                while (cursor.moveToNext()) sources += cursor.getString(1) to
                    Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), cursor.getLong(0).toString())
            }
        }
        require(sources.isNotEmpty()) { "原目录为空" }
        val dest = destination(task, s)
        val work = AppJson.decodeFromString<Work>(task.workJson)
        val folderName = DownloadNaming.render(s.downloadFileTokens, work, omitZero = true, separator = s.downloadFileSeparator) + "_${task.id}"
        val nested = dest.copy(path = listOf(dest.path, folderName).filter { it.isNotEmpty() }.joinToString("/"))
        val desired = if (dest.tree.isEmpty()) "media-folder|${nested.path}/" else "saf-folder|${dest.tree}|${nested.path}"
        if (desired == task.uri) return
        val saved = mutableListOf<Uri>()
        val temp = File.createTempFile("organize", ".part", context.cacheDir)
        try {
            sources.forEach { (name, uri) ->
                context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    ?: error("原文件不可用")
                val targetName = DownloadNaming.render(s.downloadFileTokens, work, omitZero = true, separator = s.downloadFileSeparator) +
                    if (name.endsWith("_frames.json")) "_frames.json" else if (name.endsWith("_ugoira.zip")) "_ugoira.zip" else "." + name.substringAfterLast('.')
                saved += write(nested, targetName, mime(targetName), temp)
            }
            val persisted = if (dest.tree.isEmpty()) "media-folder|${nested.path}/"
                else "saf-folder|${dest.tree}|${nested.path}"
            check(dao.relocateDownload(task.id, folderName, persisted, task.coverUri) == 1) { "下载记录已变更" }
        } catch (e: Exception) {
            saved.forEach { context.contentResolver.delete(it, null, null) }
            throw e
        } finally { temp.delete() }
        sources.forEach { (_, uri) -> runCatching { context.contentResolver.delete(uri, null, null) } }
        safTree?.let { tree -> oldSafPath?.let { path -> pruneEmptySafParents(tree, path) } }
        oldMediaPath?.let(::pruneEmptyManagedMediaParents)
    }

    /** Removes an old document after its replacement is recorded, then prunes empty app-owned parents. */
    private fun deleteSourceAndPrune(uri: Uri) {
        val mediaRelative = runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        val safParent = runCatching {
            val documentId = DocumentsContract.getDocumentId(uri)
            val permission = context.contentResolver.persistedUriPermissions.firstOrNull { grant ->
                val treeId = DocumentsContract.getTreeDocumentId(grant.uri)
                documentId.startsWith("$treeId/")
            } ?: return@runCatching null
            val treeId = DocumentsContract.getTreeDocumentId(permission.uri)
            documentId.removePrefix("$treeId/").substringBeforeLast('/', "")
                .takeIf { it.isNotEmpty() }?.let { permission.uri to it }
        }.getOrNull()
        context.contentResolver.delete(uri, null, null)
        safParent?.let { pruneEmptySafParents(it.first, it.second) }
        mediaRelative?.let { pruneEmptyManagedMediaParents(it.trimEnd('/')) }
    }

    private fun pruneEmptySafParents(tree: Uri, relativePath: String) {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return
        val segments = relativePath.split('/').filter(String::isNotBlank).toMutableList()
        while (segments.isNotEmpty()) {
            val parentPath = segments.joinToString("/")
            val folder = resolveDownloadFolder(root, parentPath) ?: return
            if (!folder.isDirectory || folder.listFiles().isNotEmpty()) return
            if (!folder.delete()) return
            segments.removeAt(segments.lastIndex)
        }
    }

    private fun pruneEmptyManagedMediaParents(relativePath: String) {
        val normalized = relativePath.trim('/').split('/').filter(String::isNotBlank)
        val managed = when {
            normalized.take(2) == listOf("Pictures", "Picaris") -> listOf("Pictures", "Picaris")
            normalized.take(2) == listOf("Documents", "Picaris") -> listOf("Documents", "Picaris")
            normalized.take(2) == listOf("Download", "Picaris") -> listOf("Download", "Picaris")
            else -> return
        }
        val external = Environment.getExternalStorageDirectory().canonicalFile
        var path = File(external, normalized.joinToString("/")).canonicalFile
        val managedRoot = File(external, managed.joinToString("/")).canonicalFile
        if (!path.toPath().startsWith(external.toPath())) return
        while (path != managedRoot && path.toPath().startsWith(managedRoot.toPath())) {
            if (!path.isDirectory || path.list()?.isNotEmpty() == true || !path.delete()) return
            path = path.parentFile ?: return
        }
    }

    private fun savePrivateNovelCover(id: Long, source: File): Uri {
        val directory = File(context.filesDir, "novel-covers").apply { check(isDirectory || mkdirs()) }
        val target = File(directory, "$id.jpg")
        source.inputStream().use { input -> target.outputStream().use(input::copyTo) }
        return Uri.fromFile(target)
    }

    private fun deleteUri(uri: Uri) {
        if (uri.scheme == "file") uri.path?.let(::File)?.delete()
        else context.contentResolver.delete(uri, null, null)
    }
}
