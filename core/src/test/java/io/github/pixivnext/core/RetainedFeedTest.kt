package io.github.pixivnext.core

import androidx.paging.PagingData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RetainedFeedTest {
    private val home = FeedSession(10, false, FeedSpec(), Settings().contentFilter())

    @Test
    fun resubscribingAfterNavigationReusesLoadedGeneration() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var generations = 0
        val store =
            RetainedFeedStore(
                scope,
                {
                    flow {
                        generations++
                        emit(PagingData.from(listOf(Work(1))))
                    }
                },
            )
        try {
            val first = store.get(home)
            withTimeout(2000) { first.first() }
            store.get(home.copy(spec = FeedSpec(section = "follow")))
            assertSame(first, store.get(home))
            withTimeout(2000) { store.get(home).first() }
            assertEquals(1, generations)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun longBrowsingKeepsHomeButEvictsOlderCollections() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = RetainedFeedStore(scope, { flowOf(PagingData.empty()) }, capacity = 3)
        try {
            val savedHome = store.get(home)
            val search = home.copy(spec = FeedSpec(section = "search", word = "one"))
            val oldSearch = store.get(search)
            repeat(8) {
                store.get(home.copy(spec = FeedSpec(section = "search", word = "query$it")))
            }
            assertSame(savedHome, store.get(home))
            assertNotSame(oldSearch, store.get(search))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun accountAndFilterChangesStartSeparateSessions() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = RetainedFeedStore(scope, { flowOf(PagingData.empty()) })
        try {
            val first = store.get(home)
            assertNotSame(first, store.get(home.copy(account = 20)))
            val second = store.get(home)
            assertNotSame(first, second)
            assertNotSame(second, store.get(home.copy(filter = home.filter.copy(hideAi = true))))
        } finally {
            scope.cancel()
        }
    }
}
