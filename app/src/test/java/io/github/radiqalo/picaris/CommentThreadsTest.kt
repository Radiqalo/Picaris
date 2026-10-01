package io.github.radiqalo.picaris

import io.github.radiqalo.picaris.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CommentThreadsTest {
    private val work = Work(9)

    @Test
    fun failedRefreshAtEndOfListCanRetryFirstPage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val store =
            CommentThreads(
                scope,
                { _, _, _, next ->
                    assertNull(next)
                    when (++calls) {
                        1 -> CommentsResponse(listOf(Comment(1)))
                        2 -> throw java.io.IOException("offline")
                        else -> CommentsResponse(listOf(Comment(2)))
                    }
                },
                { _, _, _, _ -> error("unused") },
            )
        try {
            store.load(1, work)
            store.load(1, work, refresh = true)
            assertEquals(1L, store.state(1, work).value.comments.single().id)
            assertNotNull(store.state(1, work).value.error)
            store.load(1, work)
            assertEquals(3, calls)
            assertEquals(2L, store.state(1, work).value.comments.single().id)
            assertNull(store.state(1, work).value.error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun concurrentLoadsAreMergedAndReturnDoesNotReload() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<CommentsResponse>()
        var calls = 0
        val store =
            CommentThreads(
                scope,
                { _, _, _, _ ->
                    calls++
                    response.await()
                },
                { _, _, _, _ -> error("unused") },
            )
        try {
            store.load(1, work)
            store.load(1, work)
            assertEquals(1, calls)
            response.complete(CommentsResponse(listOf(Comment(1)), total_comments = 1))
            store.load(1, work)
            assertEquals(1, calls)
            assertEquals(1, store.state(1, work).value.comments.size)
            assertTrue(store.state(2, work).value.comments.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedNextPageRetainsItemsAndCursorThenRetriesWithoutDuplicates() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val store =
            CommentThreads(
                scope,
                { _, _, _, next ->
                    when (++calls) {
                        1 -> CommentsResponse(listOf(Comment(1)), "next")
                        2 -> {
                            assertEquals("next", next)
                            throw java.io.IOException("offline")
                        }
                        else -> {
                            assertEquals("next", next)
                            CommentsResponse(listOf(Comment(1), Comment(2)))
                        }
                    }
                },
                { _, _, _, _ -> error("unused") },
            )
        try {
            store.load(1, work)
            store.load(1, work)
            assertEquals(listOf(1L), store.state(1, work).value.comments.map { it.id })
            assertEquals("next", store.state(1, work).value.next)
            store.load(1, work)
            assertEquals(listOf(1L, 2L), store.state(1, work).value.comments.map { it.id })
            assertNull(store.state(1, work).value.error)
            assertNull(store.state(1, work).value.next)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedSendKeepsExistingCommentsAndSuccessfulReplyUpdatesParent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var fail = true
        var done = false
        val store =
            CommentThreads(
                scope,
                { _, _, _, _ -> CommentsResponse(listOf(Comment(11))) },
                { _, _, _, parent ->
                    assertEquals(11L, parent)
                    if (fail) throw java.io.IOException("offline") else Comment(12, "reply")
                },
            )
        try {
            store.load(1, work)
            store.send(1, work, "reply", 11) { done = true }
            assertFalse(done)
            assertNotNull(store.state(1, work, 11).value.sendError)
            assertEquals(1, store.state(1, work).value.comments.size)
            fail = false
            store.send(1, work, "reply", 11) { done = true }
            assertTrue(done)
            assertTrue(store.state(1, work).value.comments.single().has_replies)
            assertEquals(12L, store.state(1, work, 11).value.comments.single().id)
        } finally {
            scope.cancel()
        }
    }
}
