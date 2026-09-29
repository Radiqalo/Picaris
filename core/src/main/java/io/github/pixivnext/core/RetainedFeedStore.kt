package io.github.pixivnext.core

import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

/** Retains loaded pages across route/tab disposal; only explicit refresh creates new pages. */
class RetainedFeedStore(
    private val parent: CoroutineScope,
    private val source: (FeedSession) -> Flow<PagingData<Work>>,
    private val capacity: Int = 32,
) {
    private data class Entry(val scope: CoroutineScope, val flow: Flow<PagingData<Work>>)

    private val sessions = LinkedHashMap<FeedSession, Entry>(16, .75f, true)

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun get(key: FeedSession): Flow<PagingData<Work>> {
        sessions.keys
            .filter { it.account != key.account || it.demo != key.demo || it.filter != key.filter }
            .forEach { sessions.remove(it)?.scope?.cancel() }
        sessions[key]?.let {
            return it.flow
        }
        val scope =
            CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
        val entry = Entry(scope, source(key).cachedIn(scope))
        sessions[key] = entry
        if (sessions.size > capacity) {
            // Keep the four home destinations even after browsing many search/author routes.
            val oldest =
                sessions.keys.firstOrNull {
                    it.spec.section !in setOf("recommended", "follow", "bookmarks") ||
                        it.spec.userId != 0L ||
                        it.spec.word.isNotEmpty()
                }
            if (oldest != null) sessions.remove(oldest)?.scope?.cancel()
        }
        return entry.flow
    }

    @Synchronized
    fun clear() {
        sessions.values.forEach { it.scope.cancel() }
        sessions.clear()
    }
}
