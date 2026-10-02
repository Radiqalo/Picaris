package io.github.radiqalo.picaris

import android.content.Context
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.provider.MediaStore
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import androidx.paging.*
import coil3.SingletonImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.download.DownloadManager
import io.github.radiqalo.picaris.download.DownloadEvents
import io.github.radiqalo.picaris.download.resolveDownloadFolder
import io.github.radiqalo.picaris.download.useCancellable
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel
@Inject
constructor(
    private val savedState: SavedStateHandle,
    private val repo: WorkRepository,
    internal val auth: AuthRepository,
    val settingsStore: SettingsStore,
    internal val downloads: DownloadManager,
    private val downloadEvents: DownloadEvents,
    private val downloadStorage: io.github.radiqalo.picaris.download.DownloadStorage,
    internal val dao: LibraryDao,
    private val network: Network,
    private val pixivision: PixivisionRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    init {
        viewModelScope.launch {
            downloadEvents.completed.collect { title -> message.emit("$title 已下载") }
        }
    }

    val settings = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
        val credentialReadError = auth.readError

        fun retryCredentials() = run { auth.retryCredentials() }

    val accounts = auth.data.stateIn(viewModelScope, SharingStarted.Eagerly, auth.data.value)
    private var legacyNavigationReset = savedState.remove<Boolean>("demo") == true
    val busy = MutableStateFlow(false)
    val message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val callback = MutableSharedFlow<android.net.Uri>(extraBufferCapacity = 1)
    val revision = MutableStateFlow(0)
    val accountId
        get() = auth.active?.user?.id ?: 0L

    val active =
        accounts.map { data -> data.accounts.find { it.user.id == data.activeId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, auth.active)
        private val activeAccountIds =
            accounts
                .map { data ->
                    data.accounts
                        .find { it.user.id == data.activeId }
                        ?.user
                        ?.id ?: 0L
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, accountId)

        val history =
            activeAccountIds
                .flatMapLatest { dao.history(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        val searchHistory =
            activeAccountIds
                .flatMapLatest { dao.searches(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        val downloadList =
            activeAccountIds
                .flatMapLatest { dao.downloads(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        val cachedFeedBytes =
            activeAccountIds
                .flatMapLatest { dao.cachedFeedBytes(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val imageCacheBytes = MutableStateFlow(0L)

    private val feeds =
        RetainedFeedStore(
            viewModelScope,
            { key ->
                repo.feed(key.account, key.spec, key.filter)
            },
        )
    val bookmarkStates = MutableStateFlow<Map<WorkIdentity, BookmarkState>>(emptyMap())
    val bookmarkBusy = MutableStateFlow<Set<WorkIdentity>>(emptySet())
    val comments = CommentThreads(viewModelScope, repo)
    private val peopleFeeds = BoundedLruCache<Triple<Long, String, String>, Flow<PagingData<User>>>()
    private val followedSeriesFeeds = BoundedLruCache<Pair<Long, String>, Flow<PagingData<FollowedSeries>>>()

    fun people(section: String, restrict: String): Flow<PagingData<User>> {
        val account = accountId
        return peopleFeeds.getOrPut(Triple(account, section, restrict)) {
            repo.people(account, section, restrict).cachedIn(viewModelScope)
        }
    }

    fun feed(spec: FeedSpec) =
        feeds.get(FeedSession(accountId, spec, settings.value.contentFilter()))

    fun followedSeries(kind: String): Flow<PagingData<FollowedSeries>> {
        val account = accountId
        return followedSeriesFeeds.getOrPut(account to kind) {
            repo.followedSeries(account, kind).cachedIn(viewModelScope)
        }
    }

    private val seriesDetailsCache = BoundedLruCache<Triple<Long, String, Long>, SeriesDetails>()

    fun cachedSeriesDetails(kind: String, id: Long): SeriesDetails? =
        seriesDetailsCache[Triple(accountId, kind, id)]

    suspend fun seriesDetails(kind: String, id: Long): SeriesDetails {
        val account = accountId
        val key = Triple(account, kind, id)
        return seriesDetailsCache[key] ?: repo.seriesDetails(account, kind, id)
            .also { seriesDetailsCache[key] = it }
    }

    suspend fun setSeriesWatched(kind: String, id: Long, watched: Boolean) {
        val account = accountId
        repo.setSeriesWatched(account, kind, id, watched)
        val key = Triple(account, kind, id)
        seriesDetailsCache[key]?.let { seriesDetailsCache[key] = it.copy(isWatched = watched) }
    }

    suspend fun bookmark(work: Work, public: Boolean = !settings.value.defaultPrivateBookmarks): Work {
        val account = accountId
        val key = work.identity(account)
        if (key in bookmarkBusy.value) return bookmarkStates.value[key]?.apply(work) ?: work
        val previous = bookmarkStates.value[key]
        val current = previous?.apply(work) ?: work
        bookmarkBusy.update { it + key }
        bookmarkStates.update {
            it +
                (key to BookmarkState(
                    !current.is_bookmarked,
                    (current.total_bookmarks + if (current.is_bookmarked) -1 else 1)
                        .coerceAtLeast(0),
                ))
        }
        try {
            val result = try {
                repo.bookmark(account, current, public)
            } catch (e: Exception) {
                bookmarkStates.update {
                    if (previous == null) it - key else it + (key to previous)
                }
                throw e
            }
            bookmarkStates.update {
                it + (key to BookmarkState(result.is_bookmarked, result.total_bookmarks))
            }
            dao.historyItem(account, work.id, work.type)?.let { repo.record(account, result) }
            if (result.is_bookmarked && !current.is_bookmarked && settings.value.autoDownloadAfterBookmark) {
                val queued = downloads.enqueue(account, result)
                message.emit(when {
                    queued.queued > 0 -> "已收藏并加入下载队列"
                    queued.alreadyDownloaded > 0 -> "已收藏，作品已下载"
                    else -> "已收藏，作品已在下载队列中"
                })
            }
            return result
        } finally {
            bookmarkBusy.update { it - key }
        }
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.emit(e.message ?: "操作失败，请检查网络")
            }
        }
    }

    fun loginToken(token: String) {
        run {
            busy.value = true
            try {
                auth.importToken(token)
            } finally {
                busy.value = false
            }
        }
    }

    fun callback(uri: android.net.Uri) {
        run {
            val code = uri.getQueryParameter("code") ?: return@run
            busy.value = true
            try {
                auth.finishLogin(code)
            } finally {
                busy.value = false
            }
        }
    }

    fun select(id: Long) {
        run {
            auth.select(id)
            revision.value++
        }
    }

    fun takeLegacyNavigationReset(): Boolean = legacyNavigationReset.also {
        legacyNavigationReset = false
    }

    fun update(block: (Settings) -> Settings) {
        run {
            settingsStore.update(block)
            network.okHttp()
        }
    }

    fun selectContentKind(kind: String) {
        require(kind == "illust" || kind == "novel")
        run { settingsStore.update { it.copy(contentKind = kind) } }
    }

    fun search(word: String) {
        if (word.isNotBlank())
            run { dao.search(SearchEntity(accountId, word.trim(), System.currentTimeMillis())) }
    }

    suspend fun detail(initial: Work): Work =
        repo.detail(accountId, initial.id, initial.isNovel)

    private val popularSearchTags = SingleFlightCache<Long, List<Tag>>(viewModelScope)

    fun cachedTags(): List<Tag>? = popularSearchTags.completed(accountId)

    suspend fun tags(): List<Tag> {
        val account = accountId
        return popularSearchTags.get(account) { repo.tags(account) }.await()
    }

    suspend fun tagSuggestions(word: String): List<String> = repo.tagSuggestions(word)

    private val tagTranslationCache = BoundedLruCache<String, String?>()
    suspend fun tagTranslation(name: String): String? {
        if (tagTranslationCache.containsKey(name)) return tagTranslationCache[name]
        return repo.tagTranslation(name).also { tagTranslationCache[name] = it }
    }

    private var profileSync: Job? = null
    private var profileSyncAccount: Long? = null
    fun syncAccountProfile() {
        val account = accountId
        if (account <= 0L) return
        if (profileSyncAccount == account && profileSync?.isActive == true) return
        profileSync?.cancel()
        profileSyncAccount = account
        profileSync = viewModelScope.launch {
            try {
                val details = repo.authorDetails(account, account)
                auth.updateUser(account, details.user)
                authorProfiles.remove(account to account)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Preserve the last known profile while offline. */ }
        }
    }

    private val discoveryTrends = SingleFlightCache<FeedSession, List<TrendingTag>>(viewModelScope)
    private val discoveryAuthors = SingleFlightCache<FeedSession, List<UserPreview>>(viewModelScope)
    private val pixivisionCache = SingleFlightCache<Unit, List<PixivisionArticle>>(viewModelScope)
    private val authorProfiles = SingleFlightCache<Pair<Long, Long>, AuthorDetails>(viewModelScope)
    private fun discoveryKey() = FeedSession(accountId,
        FeedSpec(kind = settings.value.contentKind), settings.value.contentFilter())
    fun cachedTrendingTags(): List<TrendingTag>? = discoveryTrends.completed(discoveryKey())
    fun cachedRecommendedAuthors(): List<UserPreview>? = discoveryAuthors.completed(discoveryKey())

    fun cachedPixivisionArticles(): List<PixivisionArticle>? = pixivisionCache.completed(Unit)

    suspend fun pixivisionArticles(): List<PixivisionArticle> =
        pixivisionCache.get(Unit) { pixivision.articles() }.await()

    suspend fun trendingTags(): List<TrendingTag> {
        val key = discoveryKey()
        return discoveryTrends.get(key) { repo.trendingTags(key.account) }.await()
    }

    suspend fun recommendedAuthors(): List<UserPreview> {
        val key = discoveryKey()
        return discoveryAuthors.get(key) { repo.recommendedAuthors(key.account) }.await()
    }

    suspend fun searchUsers(word: String): List<User> =
        repo.searchUsers(accountId, word)

    fun clearSearch() = run { dao.clearSearch(accountId) }

    suspend fun beginLogin() = auth.startLogin()

    suspend fun setDownloadTree(uri: String, novel: Boolean = false) = settingsStore.update {
        if (novel) it.copy(novelDownloadTree = uri) else it.copy(downloadTree = uri)
    }

    fun organizeDownloads() = run {
        val (done, failed) = downloadStorage.organize(accountId)
        message.emit("已整理 $done 项，失败 $failed 项；失败项保留原文件")
    }

    fun exportAppData(uri: Uri) = run {
        withContext(Dispatchers.IO) {
            val archive = AppDataArchive(
                version = 1,
                settings = settingsStore.flow.first(),
                history = dao.allHistory(),
                searches = dao.allSearches(),
                downloads = dao.allDownloads(),
                downloadFolders = dao.allDownloadFolders(),
            )
            val entries = linkedMapOf(
                "app-data.json" to AppJson.encodeToString(archive).encodeToByteArray(),
            )
            val total = entries.values.sumOf { it.size.toLong() }
            require(total <= MAX_APP_DATA_BACKUP) { "应用数据超过备份限制" }
            appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { (name, bytes) ->
                        zip.putNextEntry(java.util.zip.ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
            } ?: error("无法写入应用数据备份")
        }
        message.emit("应用数据 ZIP 已导出（不含图片缓存和登录凭据）")
    }

    fun importAppData(uri: Uri) = run {
        withContext(Dispatchers.IO) {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val zip = ZipInputStream(input)
                var payload: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name == "app-data.json" && payload == null) { "备份 ZIP 内容无效" }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= MAX_APP_DATA_BACKUP) { "应用数据备份超过限制" }
                        out.write(buffer, 0, count)
                    }
                    payload = out.toByteArray()
                    zip.closeEntry()
                }
                payload ?: error("ZIP 中没有应用数据")
            } ?: error("无法读取应用数据备份")
            val backup = AppJson.decodeFromString<AppDataArchive>(bytes.decodeToString())
            require(backup.version == 1) { "不支持的应用数据备份版本" }
            require(backup.history.size <= MAX_APP_DATA_ROWS && backup.searches.size <= MAX_APP_DATA_ROWS &&
                backup.downloads.size <= MAX_APP_DATA_ROWS && backup.downloadFolders.size <= MAX_APP_DATA_ROWS) {
                "应用数据记录数量超过限制"
            }
            settingsStore.update { backup.settings }
            dao.restoreHistory(backup.history)
            dao.restoreSearches(backup.searches)
            dao.restoreDownloads(backup.downloads.map { if (it.status == "running") it.copy(status = "paused") else it })
            dao.restoreDownloadFolders(backup.downloadFolders)
        }
        message.emit("应用数据已导入；未包含缓存图片与登录凭据")
    }

    private fun savedFileExists(value: String): Boolean = runCatching {
        when {
            value.startsWith("saf-folder|") -> {
                val (_, tree, path) = value.split('|', limit = 3)
                val root = DocumentFile.fromTreeUri(appContext, Uri.parse(tree)) ?: return@runCatching false
                resolveDownloadFolder(root, path)?.listFiles()?.isNotEmpty() == true
            }
            value.startsWith("media-folder|") -> appContext.contentResolver.query(
                MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(value.substringAfter('|')), null,
            )?.use { it.moveToFirst() } == true
            value.isBlank() -> false
            else -> appContext.contentResolver.openAssetFileDescriptor(Uri.parse(value), "r")?.use { true } ?: false
        }
    }.getOrDefault(false)

    fun refreshImageCacheSize() = run {
        imageCacheBytes.value = withContext(Dispatchers.IO) {
            SingletonImageLoader.get(appContext).diskCache?.size ?: 0L
        }
    }

    fun clearLibrary(history: Boolean) = run {
        if (history) {
            dao.clearHistory(accountId)
        } else {
            dao.clearCache(accountId)
            withContext(Dispatchers.IO) {
                SingletonImageLoader.get(appContext).diskCache?.clear()
                imageCacheBytes.value = SingletonImageLoader.get(appContext).diskCache?.size ?: 0L
            }
        }
    }

    fun removeAccount(id: Long) = run { auth.remove(id) }

    fun downloadAction(id: Long, status: String) = run { downloads.action(id, status) }

    fun downloadBatchAction(ids: Set<Long>, status: String) {
        val account = accountId
        run { downloads.batchAction(account, ids, status) }
    }

    fun retryDownloads(ids: Set<Long>) {
        val account = accountId
        run { downloads.retry(account, ids) }
    }

    fun removeDownloadRecords(ids: Set<Long>, deleteFiles: Boolean) {
        val account = accountId
        run { downloads.removeRecords(account, ids, deleteFiles) }
    }

    suspend fun authorDetails(initial: User): AuthorDetails {
        val account = accountId
        return authorProfiles.get(account to initial.id) {
            repo.authorDetails(account, initial.id)
        }.await()
    }

    suspend fun user(initial: User): User =
        repo.user(accountId, initial.id).first

    suspend fun follow(user: User): User =
        repo.follow(accountId, user)

    suspend fun follow(user: User, public: Boolean): User =
        if (public) repo.follow(accountId, user.copy(is_followed = false), true)
        else repo.followPrivately(accountId, user)

    suspend fun isPrivatelyFollowing(userId: Long): Boolean =
        repo.isPrivatelyFollowing(accountId, userId)

    suspend fun setBookmarkVisibility(work: Work, public: Boolean) {
        val account = accountId
        val key = work.identity(account)
        if (key in bookmarkBusy.value) return
        bookmarkBusy.update { it + key }
        try {
            repo.setBookmarkVisibility(account, work, public)
            bookmarkStates.update { states ->
                val current = states[key]?.apply(work) ?: work
                states + (key to BookmarkState(true, current.total_bookmarks))
            }
        } finally {
            bookmarkBusy.update { it - key }
        }
    }

    suspend fun isBookmarkPrivate(work: Work): Boolean =
        repo.isBookmarkPrivate(accountId, work)

    suspend fun completed(work: Work) = dao.completed(accountId, work.id)

    suspend fun readingProgress(work: Work): Int? =
        dao.historyItem(accountId, work.id, work.type)?.progress

    suspend fun recordProgress(work: Work, page: Int) = repo.record(accountId, work, page)

    suspend fun novelBody(work: Work): NovelBody {
        val downloaded = completed(work).firstOrNull { it.kind == "novel" && it.uri.isNotEmpty() }
        val local = downloaded?.let { task ->
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver
                        .openInputStream(task.uri.toUri())
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }
                    .getOrNull()
            }
        }
        return if (local != null) NovelBody(local) else repo.novel(accountId, work.id)
    }

    fun ugoiraFrames(work: Work, retry: Int, playing: () -> Boolean): Flow<ImageBitmap> = flow {
        val (metadata, file) = ugoiraArchive(work, retry)
        ZipFile(file).use { zip ->
            while (currentCoroutineContext().isActive) {
                for (frame in metadata.frames) {
                    while (!playing()) delay(100)
                    emit(decodeUgoiraFrame(zip, frame))
                    delay(frame.delay.toLong().coerceAtLeast(16))
                }
            }
        }
    }
        .flowOn(Dispatchers.IO)

    private suspend fun ugoiraArchive(work: Work, retry: Int): Pair<Ugoira, File> {
        val account = accountId
        val completed = dao.completed(account, work.id)
        val metadata =
            completed
                .firstOrNull { it.kind == "frames" }
                ?.let { task ->
                    withContext(Dispatchers.IO) {
                        runCatching {
                            appContext.contentResolver
                                .openInputStream(task.uri.toUri())
                                ?.bufferedReader()
                                ?.use { AppJson.decodeFromString<Ugoira>(it.readText()) }
                        }
                            .getOrNull()
                    }
                } ?: repo.ugoira(account, work.id)
        require(metadata.frames.isNotEmpty() && metadata.frames.size <= 10000) { "动图帧信息无效" }
        val file = File(appContext.cacheDir, "ugoira_${account}_${work.id}.zip")
        withContext(Dispatchers.IO) {
            if (retry > 0) file.delete()
            if (!file.exists()) {
                completed
                    .firstOrNull { it.kind == "ugoira" }
                    ?.let { saved ->
                        runCatching {
                            appContext.contentResolver.openInputStream(saved.uri.toUri())?.use {
                                input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                        }
                            .onFailure { file.delete() }
                    }
            }
            if (!file.exists()) {
                val partial = File(appContext.cacheDir, "ugoira_${work.id}.part")
                val call =
                    network
                        .okHttp()
                        .newCall(Request.Builder().url(metadata.zip_urls.medium).build())
                try {
                        call.useCancellable { response ->
                        check(response.isSuccessful) { "动图下载失败（${response.code}）" }
                        response.body.byteStream().use { input ->
                            partial.outputStream().use { out ->
                                val buffer = ByteArray(65536)
                                var bytes = 0L
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    bytes += n
                                    check(bytes < 256 * 1024 * 1024) { "动图文件过大" }
                                    out.write(buffer, 0, n)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    ensureActive()
                    throw e
                }
                ensureActive()
                check(partial.renameTo(file)) { "动图文件保存失败" }
            }
        }
        return metadata to file
    }

    private suspend fun decodeUgoiraFrame(zip: ZipFile, frame: Frame): ImageBitmap =
        withContext(Dispatchers.IO) {
            val entry = zip.getEntry(frame.file) ?: error("动图帧缺失")
            check(entry.size in 1..32 * 1024 * 1024) { "动图帧大小无效" }
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inJustDecodeBounds = false
            options.inSampleSize =
                (maxOf(options.outWidth, options.outHeight) / 1600).coerceAtLeast(1)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                ?: error("动图帧解码失败")
        }

    fun record(work: Work) {
        run { repo.record(accountId, work) }
    }

    fun download(work: Work, pages: Set<Int>? = null, ugoiraAsGif: Boolean = false) {
        run {
            val result = downloads.enqueue(accountId, work, pages, ugoiraAsGif)
            when {
                result.queued == 0 && result.alreadyDownloaded > 0 ->
                    message.emit("已下载")
                result.queued == 0 && result.alreadyQueued > 0 ->
                    message.emit("${work.title} 已在下载队列中")
                result.alreadyDownloaded > 0 ->
                    message.emit("${work.title}：${result.alreadyDownloaded} 张已下载，其余已加入队列")
                else -> message.emit("已加入下载队列")
            }
        }
    }

    fun saveUgoiraSource(work: Work, destination: android.net.Uri) {
        run {
            val metadata = repo.ugoira(accountId, work.id)
            val url = metadata.zip_urls.original.ifEmpty { metadata.zip_urls.medium }
            require(url.toUri().scheme == "https" && url.toUri().host?.endsWith(".pximg.net") == true) {
                "无效的动图源文件地址"
            }
            try {
                withContext(Dispatchers.IO) {
                    val call = network.okHttp().newCall(Request.Builder().url(url).build())
                        call.useCancellable { response ->
                            check(response.isSuccessful) { "源文件下载失败（${response.code}）" }
                            val output =
                                appContext.contentResolver.openOutputStream(destination, "w")
                                    ?: error("无法写入所选文件")
                            response.body.byteStream().use { input ->
                                output.use { input.copyTo(it) }
                            }
                        }
                }
                message.emit("${work.title} 源文件已保存")
            } catch (error: Exception) {
                appContext.contentResolver.delete(destination, null, null)
                throw error
            }
        }
    }

    suspend fun estimateDownloadBytes(work: Work, pages: Set<Int>? = null): Long? {
        if (work.isNovel) return null
        val urls = if (work.type == "ugoira") {
            val zip = repo.ugoira(accountId, work.id).zip_urls
            listOf(zip.original.ifBlank { zip.medium }).filter(String::isNotBlank)
        } else {
            val originals = work.originals
            (pages ?: originals.indices.toSet()).sorted().mapNotNull(originals::getOrNull)
        }
        if (urls.isEmpty()) return null
        val client = network.okHttp()
        val semaphore = Semaphore(4)
        val sizes = coroutineScope {
            urls.map { url ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        runCatching {
                            client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                                if (response.isSuccessful)
                                response.header("Content-Length")?.toLongOrNull()
                                        ?: response.body.contentLength().takeIf { it >= 0L }
                                else null
                            }
                        }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        return sizes.takeIf { values -> values.all { it != null } }
            ?.sumOf { it ?: 0L }
    }

    suspend fun downloadsNetwork() = network.okHttp()

    fun refresh() {
        feeds.clear()
        revision.value++
    }
}

private const val MAX_APP_DATA_BACKUP = 128 * 1024 * 1024
private const val MAX_APP_DATA_ROWS = 200_000
private const val MAX_WORK_JSON = 1024 * 1024

@Serializable
private data class AppDataArchive(
    val version: Int,
    val settings: Settings,
    val history: List<HistoryEntity>,
    val searches: List<SearchEntity>,
    val downloads: List<DownloadEntity>,
    val downloadFolders: List<DownloadFolder>,
)
