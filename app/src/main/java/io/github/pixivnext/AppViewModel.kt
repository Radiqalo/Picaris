package io.github.pixivnext

import androidx.lifecycle.*
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pixivnext.core.*
import io.github.pixivnext.download.DownloadManager
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
        bookmarkBusy.update { it + key }
        try {
            val current = bookmarkStates.value[key]?.apply(work) ?: work
            val result = repo.bookmark(account, current, public)
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

    fun search(word: String) {
        if (word.isNotBlank())
            run { dao.search(SearchEntity(accountId, word.trim(), System.currentTimeMillis())) }
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
