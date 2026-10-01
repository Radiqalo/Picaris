package io.github.radiqalo.picaris

import android.content.Context
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
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.*
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
    val repo: WorkRepository,
    val auth: AuthRepository,
    val settingsStore: SettingsStore,
    val downloads: DownloadManager,
    private val downloadEvents: DownloadEvents,
    val dao: LibraryDao,
    val network: Network,
    private val pixivision: PixivisionRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    init {
        viewModelScope.launch {
            downloadEvents.completed.collect { title -> message.emit("$title 已下载") }
        }
    }

    val settings = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
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
    val history =
        accounts.map { data -> data.accounts.find { it.user.id == data.activeId }?.user?.id ?: 0L }
            .distinctUntilChanged()
            .flatMapLatest { dao.history(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchHistory =
        accounts.map { data -> data.accounts.find { it.user.id == data.activeId }?.user?.id ?: 0L }
            .distinctUntilChanged()
            .flatMapLatest { dao.searches(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloadList =
        accounts.map { data -> data.accounts.find { it.user.id == data.activeId }?.user?.id ?: 0L }
            .distinctUntilChanged()
            .flatMapLatest { dao.downloads(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cachedFeedBytes =
        accounts.map { data -> data.accounts.find { it.user.id == data.activeId }?.user?.id ?: 0L }
            .distinctUntilChanged()
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
    private val peopleFeeds = mutableMapOf<Triple<Long, String, String>, Flow<PagingData<User>>>()
    private val followedSeriesFeeds = mutableMapOf<Pair<Long, String>, Flow<PagingData<FollowedSeries>>>()

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

    private val seriesDetailsCache = mutableMapOf<Triple<Long, String, Long>, SeriesDetails>()

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

    suspend fun bookmark(work: Work, public: Boolean = true): Work {
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

    private val popularSearchTags = mutableMapOf<Long, Deferred<List<Tag>>>()

    fun cachedTags(): List<Tag>? = popularSearchTags[accountId]?.let {
        if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
    }

    suspend fun tags(): List<Tag> {
        val account = accountId
        if (popularSearchTags[account]?.isCancelled == true) popularSearchTags.remove(account)
        val request = popularSearchTags.getOrPut(account) { viewModelScope.async { repo.tags(account) } }
        return try {
            request.await()
        } catch (error: Throwable) {
            if (popularSearchTags[account] === request) popularSearchTags.remove(account)
            throw error
        }
    }

    suspend fun tagSuggestions(word: String): List<String> = repo.tagSuggestions(word)

    private val tagTranslationCache = mutableMapOf<String, String?>()
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

    private val discoveryTrends = mutableMapOf<FeedSession, Deferred<List<TrendingTag>>>()
    private val discoveryAuthors = mutableMapOf<FeedSession, Deferred<List<UserPreview>>>()
    private var pixivisionRequest: Deferred<List<PixivisionArticle>>? = null
    private val authorProfiles = mutableMapOf<Pair<Long, Long>, Deferred<AuthorDetails>>()
    private fun discoveryKey() = FeedSession(accountId,
        FeedSpec(kind = settings.value.contentKind), settings.value.contentFilter())
    fun cachedTrendingTags(): List<TrendingTag>? = discoveryTrends[discoveryKey()]?.let {
        if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
    }
    fun cachedRecommendedAuthors(): List<UserPreview>? = discoveryAuthors[discoveryKey()]?.let {
        if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
    }

    fun cachedPixivisionArticles(): List<PixivisionArticle>? {
        return pixivisionRequest?.let {
            if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
        }
    }

    suspend fun pixivisionArticles(): List<PixivisionArticle> {
        if (pixivisionRequest?.isCancelled == true) pixivisionRequest = null
        val request = pixivisionRequest ?: viewModelScope.async { pixivision.articles() }
            .also { pixivisionRequest = it }
        return request.await()
    }

    suspend fun trendingTags(): List<TrendingTag> {
        val key = discoveryKey()
        if (discoveryTrends[key]?.isCancelled == true) discoveryTrends.remove(key)
        return discoveryTrends.getOrPut(key) { viewModelScope.async {
            repo.trendingTags(key.account)
        } }.await()
    }

    suspend fun recommendedAuthors(): List<UserPreview> {
        val key = discoveryKey()
        if (discoveryAuthors[key]?.isCancelled == true) discoveryAuthors.remove(key)
        return discoveryAuthors.getOrPut(key) { viewModelScope.async {
            repo.recommendedAuthors(key.account)
        } }.await()
    }

    suspend fun searchUsers(word: String): List<User> =
        repo.searchUsers(accountId, word)

    fun clearSearch() = run { dao.clearSearch(accountId) }

    suspend fun beginLogin() = auth.startLogin()

    suspend fun setDownloadTree(uri: String) = settingsStore.update { it.copy(downloadTree = uri) }

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
        if (authorProfiles[account to initial.id]?.isCancelled == true)
            authorProfiles.remove(account to initial.id)
        return authorProfiles.getOrPut(account to initial.id) { viewModelScope.async {
            repo.authorDetails(account, initial.id)
        } }.await()
    }

    suspend fun user(initial: User): User =
        repo.user(accountId, initial.id).first

    suspend fun follow(user: User): User =
        repo.follow(accountId, user)

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
                val cancellation =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            call.cancel()
                        }
                    }
                try {
                    call.execute().use { response ->
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
                } finally {
                    cancellation.cancel()
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

    fun saveUgoiraSource(work: Work, directory: android.net.Uri) {
        run {
            val metadata = repo.ugoira(accountId, work.id)
            val url = metadata.zip_urls.original.ifEmpty { metadata.zip_urls.medium }
            require(url.toUri().scheme == "https" && url.toUri().host?.endsWith(".pximg.net") == true) {
                "无效的动图源文件地址"
            }
            val folder = DocumentFile.fromTreeUri(appContext, directory)?.takeIf { it.isDirectory }
                ?: error("所选目录不可用")
            val name = "${work.id}_ugoira.zip"
            var targetName = name
            var suffix = 1
            while (folder.findFile(targetName) != null) {
                targetName = "${work.id}_ugoira_${suffix++}.zip"
            }
            val temporaryName = "${work.id}_ugoira.zip.part"
            folder.findFile(temporaryName)?.delete()
            val document = folder.createFile("application/octet-stream", temporaryName)
                ?: error("无法在所选目录创建临时文件")
            try {
                withContext(Dispatchers.IO) {
                    val call = network.okHttp().newCall(Request.Builder().url(url).build())
                    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            call.cancel()
                        }
                    }
                    try {
                        call.execute().use { response ->
                            check(response.isSuccessful) { "源文件下载失败（${response.code}）" }
                            val output = appContext.contentResolver.openOutputStream(document.uri, "w")
                                ?: error("无法写入所选目录")
                            response.body.byteStream().use { input ->
                                output.use { input.copyTo(it) }
                            }
                        }
                    } finally {
                        cancellation.cancel()
                    }
                }
                check(document.renameTo(targetName)) { "ZIP 已下载，但无法在所选目录完成重命名" }
                message.emit("${work.title} 源文件已保存")
            } catch (error: Exception) {
                document.delete()
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
