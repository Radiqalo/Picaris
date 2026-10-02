package io.github.radiqalo.picaris.download

import android.app.*
import android.app.job.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.radiqalo.picaris.MainActivity
import io.github.radiqalo.picaris.R
import io.github.radiqalo.picaris.core.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

@Singleton
class DownloadEvents @Inject constructor() {
    val completed = MutableSharedFlow<String>(extraBufferCapacity = 4)
}

data class DownloadEnqueueResult(
    val queued: Int = 0,
    val alreadyDownloaded: Int = 0,
    val alreadyQueued: Int = 0,
)

private enum class QueueDisposition { QUEUED, DOWNLOADED, ACTIVE }

@Singleton
class DownloadManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
    private val repo: WorkRepository,
) {
    suspend fun enqueue(
        account: Long,
        initial: Work,
        pages: Set<Int>? = null,
        ugoiraAsGif: Boolean = false,
    ): DownloadEnqueueResult {
        require(pages == null || pages.isNotEmpty()) { "请至少选择一张图片" }
        val missingOriginals = !initial.isNovel && initial.type != "ugoira" &&
            (if (initial.page_count > 1) initial.meta_pages.size < initial.page_count ||
                initial.meta_pages.any { it.image_urls.original.isBlank() }
            else initial.meta_single_page.original_image_url.isBlank() && initial.image_urls.original.isBlank())
        val work = if (missingOriginals) repo.detail(account, initial.id) else initial
        val metadata = AppJson.encodeToString(work)
        var queued = 0
        var alreadyDownloaded = 0
        var alreadyQueued = 0
        suspend fun add(item: DownloadEntity) {
            when (queue(item)) {
                QueueDisposition.QUEUED -> queued++
                QueueDisposition.DOWNLOADED -> alreadyDownloaded++
                QueueDisposition.ACTIVE -> alreadyQueued++
            }
        }
        if (work.isNovel) {
            add(
                DownloadEntity(
                    accountId = account,
                    workId = work.id,
                    kind = "novel",
                    page = 0,
                    title = work.title,
                    url = "novel:${work.id}",
                    name = "${work.id}.txt",
                    workJson = metadata,
                )
            )
        } else if (work.type == "ugoira") {
            val u = repo.ugoira(account, work.id)
            add(
                DownloadEntity(
                    accountId = account,
                    workId = work.id,
                    kind = if (ugoiraAsGif) "ugoira-gif" else "ugoira-originals",
                    page = 0,
                    title = work.title,
                    url = u.zip_urls.original.ifEmpty { u.zip_urls.medium },
                    name = if (ugoiraAsGif) "${work.id}.gif" else "${work.id}_${safeFolderName(work.title)}",
                    workJson = metadata,
                )
            )
        } else {
            val requestedPages = (pages ?: work.originals.indices.toSet()).sorted()
            require(requestedPages.all { it in work.originals.indices }) { "所选图片不可用，请刷新作品详情后重试" }
            requestedPages.forEach { index ->
                val url = work.originals[index]
                add(
                    DownloadEntity(
                        accountId = account,
                        workId = work.id,
                        kind = work.type,
                        page = index,
                        title = work.title,
                        url = url,
                        name =
                            "${work.id}_p${index}.${url.toUri().lastPathSegment?.substringAfterLast('.')?.takeIf { it.length in 2..5 } ?: "jpg"}",
                        workJson = metadata,
                    )
                )
            }
        }
        if (queued > 0) schedule()
        return DownloadEnqueueResult(queued, alreadyDownloaded, alreadyQueued)
    }

    private suspend fun queue(item: DownloadEntity): QueueDisposition {
        if (dao.enqueue(item) > 0) return QueueDisposition.QUEUED
        val existing =
            dao.existingDownload(item.accountId, item.workId, item.kind, item.page)
                ?: return QueueDisposition.ACTIVE
        if (existing.status == "complete") {
            if (downloadedFileExists(existing)) return QueueDisposition.DOWNLOADED
            if (dao.retryCompletedDownload(existing.id) > 0) return QueueDisposition.QUEUED
        }
        if (existing.status in setOf("queued", "running")) return QueueDisposition.ACTIVE
        return if (dao.requeueDownload(existing.id, item.url, item.workJson, item.name) > 0)
            QueueDisposition.QUEUED else QueueDisposition.ACTIVE
    }

    private suspend fun downloadedFileExists(task: DownloadEntity): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    task.uri.startsWith("saf-folder|") -> {
                        val (_, tree, name) = task.uri.split("|", limit = 3)
                        DocumentFile.fromTreeUri(context, tree.toUri())?.let { resolveDownloadFolder(it, name) }?.isDirectory == true
                    }
                    task.uri.startsWith("media-folder|") -> {
                        val path = task.uri.substringAfter('|')
                        context.contentResolver.query(
                            MediaStore.Files.getContentUri("external"),
                            arrayOf(MediaStore.MediaColumns._ID),
                            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                            arrayOf(path),
                            null,
                        )?.use { it.moveToFirst() } == true
                    }
                    else -> context.contentResolver
                        .openAssetFileDescriptor(task.uri.toUri(), "r")
                        ?.use { true } ?: false
                }
            }.getOrDefault(false)
        }

    fun schedule() {
        val builder = JobInfo.Builder(1001, ComponentName(context, DownloadService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(10_000_000, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setUserInitiated(true)
        }
        val info = builder.build()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(1001) != null) return
        check(scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS) { "无法启动下载，请保持应用在前台后重试" }
    }

    suspend fun action(id: Long, status: String) {
        require(status in setOf("queued", "paused", "cancelled")) { "无效的下载操作" }
        if (dao.changeDownloadStatus(id, status) > 0 && status == "queued") schedule()
    }

    suspend fun batchAction(account: Long, ids: Set<Long>, status: String) {
        require(status in setOf("queued", "paused", "cancelled")) { "无效的下载操作" }
        var changed = false
        for (id in ids) {
            if (dao.download(id)?.accountId == account)
                changed = dao.changeDownloadStatus(id, status) > 0 || changed
        }
        if (changed && status == "queued") schedule()
    }

    suspend fun retry(account: Long, ids: Set<Long>) {
        var changed = false
        for (id in ids) {
            val task = dao.download(id)?.takeIf { it.accountId == account } ?: continue
            changed = when (task.status) {
                "complete" -> {
                    if (downloadedFileExists(task)) changed
                    else dao.retryCompletedDownload(id) > 0 || changed
                }
                "paused", "failed", "cancelled" ->
                    dao.changeDownloadStatus(id, "queued") > 0 || changed
                else -> changed
            }
        }
        if (changed) schedule()
    }

    suspend fun removeRecords(account: Long, ids: Set<Long>, deleteFiles: Boolean) {
        withContext(Dispatchers.IO) {
            for (id in ids) {
                val initial = dao.download(id)?.takeIf { it.accountId == account } ?: continue
                if (initial.status in setOf("queued", "running", "paused", "failed"))
                    dao.changeDownloadStatus(id, "cancelled")
                val task = dao.download(id) ?: continue
                if (deleteFiles && task.status == "complete" && task.uri.isNotBlank())
                    runCatching {
                        when {
                            task.uri.startsWith("saf-folder|") -> {
                                val (_, tree, name) = task.uri.split("|", limit = 3)
                                deleteSafFolder(context, tree.toUri(), name)
                            }
                            task.uri.startsWith("media-folder|") -> {
                                deleteMediaFolder(context, task.uri.substringAfter('|'))
                            }
                            else -> context.contentResolver.delete(task.uri.toUri(), null, null)
                        }
                    }
                if (deleteFiles && task.coverUri.isNotEmpty()) runCatching {
                    val cover = task.coverUri.toUri()
                    if (cover.scheme == "file") cover.path?.let(::File)?.delete()
                    else context.contentResolver.delete(cover, null, null)
                }
                if (dao.deleteFinishedDownload(account, id) > 0)
                    File(context.filesDir, "transfer/$account/$id.part").delete()
            }
        }
    }
}

private fun safeFolderName(title: String): String = title
    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim()
    .take(80)
    .ifBlank { "ugoira" }

internal fun resolveDownloadFolder(root: DocumentFile, path: String): DocumentFile? {
    var current = root
    for (part in path.split('/').filter { it.isNotEmpty() }) current = current.findFile(part) ?: return null
    return current
}

private fun deleteSafFolder(context: Context, treeUri: Uri, name: String) {
    val folder = DocumentFile.fromTreeUri(context, treeUri)?.let { resolveDownloadFolder(it, name) } ?: return
    fun remove(document: DocumentFile) {
        document.listFiles().forEach { child ->
            if (child.isDirectory) remove(child) else child.delete()
        }
        document.delete()
    }
    remove(folder)
}

private fun deleteMediaFolder(context: Context, relativePath: String) {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
    context.contentResolver.query(collection, projection, selection, arrayOf(relativePath), null)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        while (cursor.moveToNext()) {
            val uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString())
            context.contentResolver.delete(uri, null, null)
        }
    }
}

@AndroidEntryPoint
class DownloadService : JobService() {
    @Inject lateinit var dao: LibraryDao
    @Inject lateinit var downloadEvents: DownloadEvents
    @Inject lateinit var network: Network
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var storage: DownloadStorage
    @Inject lateinit var repo: WorkRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("downloads", "作品下载", NotificationManager.IMPORTANCE_LOW)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(params, 2001, notification("准备下载", 0, 0), JOB_END_NOTIFICATION_POLICY_REMOVE)
        } else {
            nm.notify(2001, notification("准备下载", 0, 0))
        }
        running = scope.launch {
            try {
                dao.recoverDownloads()
                while (isActive) {
                    val concurrency = settings.flow.first().downloadConcurrency.coerceIn(1, 10)
                    val batch = dao.queuedDownloads(concurrency)
                    if (batch.isEmpty()) break
                    coroutineScope {
                        batch.map { task ->
                            async {
                                try {
                                    transfer(task, params)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    dao.failDownload(task.id, e.message ?: "下载失败")
                                }
                            }
                        }.awaitAll()
                    }
                }
            } finally {
                withContext(NonCancellable) { dao.recoverDownloads() }
                getSystemService(NotificationManager::class.java).cancel(2001)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || params.stopReason != JobParameters.STOP_REASON_USER
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun transfer(initial: DownloadEntity, params: JobParameters) {
        if (dao.claimDownload(initial.id) == 0) return
        var task = initial.copy(status = "running", error = "")
        val dir = File(filesDir, "transfer/${task.accountId}").apply { mkdirs() }
        val part = File(dir, "${task.id}.part")
        if (task.kind == "novel") part.writeText(repo.novel(task.accountId, task.workId).text)
        else if (task.kind == "frames") part.writeText(task.url.removePrefix("frames:"))
        else {
            val uri = task.url.toUri()
            require(uri.scheme == "https" && uri.host?.endsWith(".pximg.net") == true) {
                "无效的作品下载地址"
            }
            val result =
                ResumableTransfer(network.okHttp()).transfer(task.url, part, task.etag) {
                    bytes,
                    total,
                    etag ->
                    val current = dao.progressDownload(task.id, bytes, total, etag) > 0
                    if (current)
                        getSystemService(NotificationManager::class.java)
                            .notify(2001, notification(task.title, bytes, total))
                    current
                } ?: return
            task = task.copy(bytes = result.bytes, total = result.total, etag = result.validator)
        }
        if (dao.download(task.id)?.status != "running") return
        val cover = File(dir, "${task.id}.cover.part")
        val uri = try {
            if (task.kind == "novel") {
                val work = AppJson.decodeFromString<Work>(task.workJson)
                val source = work.cover.toUri()
                require(source.scheme == "https" && source.host?.endsWith(".pximg.net") == true) { "小说封面不可用" }
                ResumableTransfer(network.okHttp()).transfer(work.cover, cover, "") { _, _, _ ->
                    dao.download(task.id)?.status == "running"
                } ?: return
                storage.save(task, part, cover)
            } else publish(task, part)
        } finally { cover.delete() }
        if (dao.completeDownload(task.id, part.length(), uri.toString()) == 0) {
            deletePublished(uri)
            dao.download(task.id)?.coverUri?.takeIf { it.isNotEmpty() }?.let { deletePublished(it.toUri()) }
            return
        }
        part.delete()
        if (dao.activeDownloadsForWork(task.accountId, task.workId) == 0) {
            val work = runCatching { AppJson.decodeFromString<Work>(task.workJson) }.getOrNull()
            val title = work?.title ?: task.title
            downloadEvents.completed.tryEmit(title)
            val currentSettings = settings.flow.first()
            if (currentSettings.autoBookmarkAfterDownload && work != null && !work.is_bookmarked) {
                runCatching { repo.bookmark(task.accountId, work, public = !currentSettings.defaultPrivateBookmarks) }
                    .onFailure { downloadEvents.completed.tryEmit("下载完成，自动收藏失败：${work.title}") }
            }
        }
    }

    private suspend fun publish(task: DownloadEntity, file: File): Uri {
        if (task.kind == "ugoira-originals") return publishUgoiraOriginals(task, file)
        if (task.kind == "ugoira-gif") return publishUgoiraGif(task, file)
        return storage.save(task, file)
    }

    private suspend fun publishUgoiraOriginals(task: DownloadEntity, archive: File): Uri {
        val metadata = repo.ugoira(task.accountId, task.workId)
        require(metadata.frames.isNotEmpty()) { "动图没有可用帧" }
        return storage.saveArchive(task, archive, AppJson.encodeToString(metadata).toByteArray(Charsets.UTF_8))
    }

    private fun deletePublished(uri: Uri) {
        when {
            uri.scheme == "file" -> uri.path?.let(::File)?.delete()
            uri.toString().startsWith("saf-folder|") -> {
                val (_, tree, name) = uri.toString().split("|", limit = 3)
                deleteSafFolder(this, tree.toUri(), name)
            }
            uri.toString().startsWith("media-folder|") ->
                deleteMediaFolder(this, uri.toString().substringAfter('|'))
            else -> contentResolver.delete(uri, null, null)
        }
    }

    private suspend fun publishUgoiraGif(task: DownloadEntity, archive: File): Uri {
        val metadata = repo.ugoira(task.accountId, task.workId)
        require(metadata.frames.isNotEmpty()) { "动图没有可用帧" }
        val gif = File(cacheDir, "${task.accountId}_${task.workId}_${task.id}.gif")
        try {
            java.util.zip.ZipFile(archive).use { zip ->
                GifEncoder.write(zip, metadata.frames, gif)
            }
            val gifTask = task.copy(kind = "ugoira-gif-file", name = "${task.workId}.gif")
            return publish(gifTask, gif)
        } finally {
            gif.delete()
        }
    }

    private fun notification(title: String, bytes: Long, total: Long): Notification {
        val intent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).putExtra("downloads", true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (total > 0) "${bytes*100/total}%" else "正在下载作品")
            .setContentIntent(intent)
            .setOngoing(true)
            .setProgress(100, if (total > 0) (bytes * 100 / total).toInt() else 0, total <= 0)
            .build()
    }
}
