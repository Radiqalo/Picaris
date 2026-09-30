package io.github.pixivnext

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.pixivnext.core.*
import io.github.pixivnext.download.DownloadManager
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    val dao: LibraryDao,
    val network: Network,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val settings = settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val accounts = auth.data.stateIn(viewModelScope, SharingStarted.Eagerly, auth.data.value)
    val demo = savedState.getStateFlow("demo", false)
    val busy = MutableStateFlow(false)
    val message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val callback = MutableSharedFlow<android.net.Uri>(extraBufferCapacity = 1)
    val revision = MutableStateFlow(0)
    val accountId
        get() = if (demo.value) -1L else auth.active?.user?.id ?: 0L

    val active =
        combine(accounts, demo) { a, d ->
                if (d) Account(User(-1, "演示预览"), "", "", 0)
                else a.accounts.find { it.user.id == a.activeId }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                if (demo.value) Account(User(-1, "演示预览"), "", "", 0) else auth.active,
            )
    val history =
        combine(accounts, demo) { _, _ -> accountId }
            .distinctUntilChanged()
            .flatMapLatest { dao.history(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchHistory =
        combine(accounts, demo) { _, _ -> accountId }
            .distinctUntilChanged()
            .flatMapLatest { dao.searches(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloadList =
        combine(accounts, demo) { _, _ -> accountId }
            .distinctUntilChanged()
            .flatMapLatest { dao.downloads(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val feeds =
        RetainedFeedStore(
            viewModelScope,
            { key ->
                repo.feed(key.account, key.spec, key.demo, key.filter)
            },
        )
    val bookmarkStates = MutableStateFlow<Map<WorkIdentity, BookmarkState>>(emptyMap())
    val bookmarkBusy = MutableStateFlow<Set<WorkIdentity>>(emptySet())
    val comments = CommentThreads(viewModelScope, repo)
    private val peopleFeeds = mutableMapOf<Triple<Long, String, String>, Flow<PagingData<User>>>()

    fun people(section: String, restrict: String): Flow<PagingData<User>> {
        val account = accountId
        return peopleFeeds.getOrPut(Triple(account, section, restrict)) {
            repo.people(account, section, restrict, demo.value).cachedIn(viewModelScope)
        }
    }

    fun feed(spec: FeedSpec) =
        feeds.get(FeedSession(accountId, demo.value, spec, settings.value.contentFilter()))

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
                savedState["demo"] = false
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
                savedState["demo"] = false
            } finally {
                busy.value = false
            }
        }
    }

    fun select(id: Long) {
        run {
            savedState["demo"] = false
            auth.select(id)
            revision.value++
        }
    }

    fun preview() {
        savedState["demo"] = true
    }

    fun leave() {
        savedState["demo"] = false
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
        if (initial.demo >= 0) initial else repo.detail(accountId, initial.id, initial.isNovel)

    suspend fun tags(): List<Tag> = repo.tags(accountId)

    private val discoveryTrends = mutableMapOf<FeedSession, Deferred<List<TrendingTag>>>()
    private val discoveryAuthors = mutableMapOf<FeedSession, Deferred<List<UserPreview>>>()
    private val authorProfiles = mutableMapOf<Pair<Long, Long>, Deferred<AuthorDetails>>()
    private fun discoveryKey() = FeedSession(accountId, demo.value,
        FeedSpec(kind = settings.value.contentKind), settings.value.contentFilter())
    fun cachedTrendingTags(): List<TrendingTag>? = discoveryTrends[discoveryKey()]?.let {
        if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
    }
    fun cachedRecommendedAuthors(): List<UserPreview>? = discoveryAuthors[discoveryKey()]?.let {
        if (it.isCompleted && !it.isCancelled) runCatching { it.getCompleted() }.getOrNull() else null
    }

    suspend fun trendingTags(): List<TrendingTag> {
        val key = discoveryKey()
        if (discoveryTrends[key]?.isCancelled == true) discoveryTrends.remove(key)
        return discoveryTrends.getOrPut(key) { viewModelScope.async {
        if (key.demo) Demo.works.flatMap { work -> work.tags.map { TrendingTag(it, work) } }
            .distinctBy { it.tag.name }
        else repo.trendingTags(key.account)
        } }.await()
    }

    suspend fun recommendedAuthors(): List<UserPreview> {
        val key = discoveryKey()
        if (discoveryAuthors[key]?.isCancelled == true) discoveryAuthors.remove(key)
        return discoveryAuthors.getOrPut(key) { viewModelScope.async {
        if (key.demo) Demo.works.filterNot { it.isNovel }.groupBy { it.user.id }
            .values.map { UserPreview(it.first().user, it.take(3)) }
        else repo.recommendedAuthors(key.account)
        } }.await()
    }

    suspend fun searchUsers(word: String): List<User> =
        if (demo.value) Demo.works.map { it.user } else repo.searchUsers(accountId, word)

    fun clearSearch() = run { dao.clearSearch(accountId) }

    suspend fun beginLogin() = auth.startLogin()

    suspend fun setDownloadTree(uri: String) = settingsStore.update { it.copy(downloadTree = uri) }

    fun clearLibrary(history: Boolean) = run {
        if (history) dao.clearHistory(accountId) else dao.clearCache(accountId)
    }

    fun removeAccount(id: Long) = run { auth.remove(id) }

    fun downloadAction(id: Long, status: String) = run { downloads.action(id, status) }

    suspend fun authorDetails(initial: User): AuthorDetails {
        val account = accountId
        if (authorProfiles[account to initial.id]?.isCancelled == true)
            authorProfiles.remove(account to initial.id)
        return authorProfiles.getOrPut(account to initial.id) { viewModelScope.async {
            if (account == -1L) AuthorDetails(initial) else repo.authorDetails(account, initial.id)
        } }.await()
    }

    suspend fun user(initial: User): User =
        if (demo.value) initial else repo.user(accountId, initial.id).first

    suspend fun follow(user: User): User =
        if (demo.value) user.copy(is_followed = !user.is_followed) else repo.follow(accountId, user)

    suspend fun completed(work: Work) = dao.completed(accountId, work.id)

    suspend fun readingProgress(work: Work): Int? =
        dao.historyItem(accountId, work.id, work.type)?.progress

    suspend fun recordProgress(work: Work, page: Int) = repo.record(accountId, work, page)

    suspend fun novelBody(work: Work): NovelBody {
        if (work.demo >= 0) return NovelBody(Demo.novel)
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

    fun download(work: Work) {
        run {
            downloads.enqueue(accountId, work)
            message.emit("已加入下载队列")
        }
    }

    suspend fun downloadsNetwork() = network.okHttp()

    fun refresh() {
        feeds.clear()
        revision.value++
    }
}
