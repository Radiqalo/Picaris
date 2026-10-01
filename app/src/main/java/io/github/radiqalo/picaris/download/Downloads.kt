package io.github.radiqalo.picaris.download

import android.app.*
import android.app.job.*
import android.content.*
import android.net.Uri
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

@Singleton
class DownloadManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
    private val repo: WorkRepository,
) {
    suspend fun enqueue(account: Long, initial: Work, pages: Set<Int>? = null) {
        require(pages == null || pages.isNotEmpty()) { "请至少选择一张图片" }
        val missingOriginals = !initial.isNovel && initial.type != "ugoira" &&
            (if (initial.page_count > 1) initial.meta_pages.size < initial.page_count ||
                initial.meta_pages.any { it.image_urls.original.isBlank() }
            else initial.meta_single_page.original_image_url.isBlank() && initial.image_urls.original.isBlank())
        val work = if (missingOriginals) repo.detail(account, initial.id) else initial
        val metadata = AppJson.encodeToString(work)
        if (work.isNovel) {
            queue(
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
            queue(
                DownloadEntity(
                    accountId = account,
                    workId = work.id,
                    kind = "ugoira",
                    page = 0,
                    title = work.title,
                    url = u.zip_urls.original.ifEmpty { u.zip_urls.medium },
                    name = "${work.id}_ugoira.zip",
                    workJson = metadata,
                )
            )
            queue(
                DownloadEntity(
                    accountId = account,
                    workId = work.id,
                    kind = "frames",
                    page = 0,
                    title = "${work.title} · 帧时序",
                    url = "frames:${AppJson.encodeToString(u)}",
                    name = "${work.id}_frames.json",
                    workJson = metadata,
                )
            )
        } else {
            val requestedPages = (pages ?: work.originals.indices.toSet()).sorted()
            require(requestedPages.all { it in work.originals.indices }) { "所选图片不可用，请刷新作品详情后重试" }
            requestedPages.forEach { index ->
                val url = work.originals[index]
                queue(
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
        schedule()
    }

    private suspend fun queue(item: DownloadEntity) {
        if (dao.enqueue(item) > 0) return
        val existing =
            dao.existingDownload(item.accountId, item.workId, item.kind, item.page) ?: return
        if (existing.status == "complete") {
            val readable =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.contentResolver
                                .openAssetFileDescriptor(existing.uri.toUri(), "r")
                                ?.use { true } ?: false
                        }
                        .getOrDefault(false)
                }
            if (readable) return
        }
        dao.requeueDownload(existing.id, item.url, item.workJson, item.name)
    }

    fun schedule() {
        val info =
            JobInfo.Builder(1001, ComponentName(context, DownloadService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setUserInitiated(true)
                .setEstimatedNetworkBytes(10_000_000, 0)
                .build()
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

    suspend fun removeRecords(account: Long, ids: Set<Long>, deleteFiles: Boolean) {
        withContext(Dispatchers.IO) {
            for (id in ids) {
                val initial = dao.download(id)?.takeIf { it.accountId == account } ?: continue
                if (initial.status in setOf("queued", "running", "paused", "failed"))
                    dao.changeDownloadStatus(id, "cancelled")
                val task = dao.download(id) ?: continue
                if (deleteFiles && task.status == "complete" && task.uri.isNotBlank())
                    runCatching { context.contentResolver.delete(task.uri.toUri(), null, null) }
                if (dao.deleteFinishedDownload(account, id) > 0)
                    File(context.filesDir, "transfer/$account/$id.part").delete()
            }
        }
    }
}

@AndroidEntryPoint
class DownloadService : JobService() {
    @Inject lateinit var dao: LibraryDao
    @Inject lateinit var downloadEvents: DownloadEvents
    @Inject lateinit var network: Network
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var repo: WorkRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("downloads", "作品下载", NotificationManager.IMPORTANCE_LOW)
        )
        setNotification(
            params,
            2001,
            notification("准备下载", 0, 0),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        running = scope.launch {
            try {
                dao.recoverDownloads()
                while (isActive) {
                    val task = dao.nextDownload() ?: break
                    try {
                        transfer(task, params)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        dao.failDownload(task.id, e.message ?: "下载失败")
                    }
                }
            } finally {
                withContext(NonCancellable) { dao.recoverDownloads() }
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return params.stopReason != JobParameters.STOP_REASON_USER
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
        val uri = publish(task, part)
        if (dao.completeDownload(task.id, part.length(), uri.toString()) == 0)
            contentResolver.delete(uri, null, null)
        part.delete()
        if (dao.activeDownloadsForWork(task.accountId, task.workId) == 0) {
            val title = runCatching { AppJson.decodeFromString<Work>(task.workJson).title }
                .getOrDefault(task.title)
            downloadEvents.completed.tryEmit(title)
        }
    }

    private suspend fun publish(task: DownloadEntity, file: File): Uri {
        val s = settings.flow.first()
        val mime =
            when (file.extension) {
                else ->
                    when (task.name.substringAfterLast('.')) {
                        "jpg",
                        "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "zip" -> "application/zip"
                        "json" -> "application/json"
                        else -> "text/plain"
                    }
            }
        if (s.downloadTree.isNotEmpty()) {
            val root = DocumentFile.fromTreeUri(this, s.downloadTree.toUri()) ?: error("下载目录不可用")
            val doc = root.createFile(mime, task.name) ?: error("无法创建文件，请重新授权下载目录")
            try {
                contentResolver.openOutputStream(doc.uri, "w")!!.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            } catch (e: Exception) {
                doc.delete()
                throw e
            }
            return doc.uri
        }
        val collection =
            if (mime.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, task.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (mime.startsWith("image/")) "Pictures/Picaris" else "Download/Picaris",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri = contentResolver.insert(collection, values) ?: error("无法创建下载文件")
        try {
            contentResolver.openOutputStream(uri, "w")!!.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
        return uri
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
