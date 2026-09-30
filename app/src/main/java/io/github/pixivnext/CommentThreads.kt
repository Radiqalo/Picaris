package io.github.pixivnext

import io.github.pixivnext.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ThreadKey(val work: WorkIdentity, val parentId: Long?)

data class ThreadState(
    val comments: List<Comment> = emptyList(),
    val next: String? = null,
    val total: Int? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val sendError: String? = null,
)

/** Route-independent comment state; reading/reply navigation does not refetch the first page. */
class CommentThreads(
    private val scope: CoroutineScope,
    private val fetch: suspend (Long, Work, Long?, String?) -> CommentsResponse,
    private val post: suspend (Long, Work, String, Long?) -> Comment,
) {
    constructor(
        scope: CoroutineScope,
        repo: WorkRepository,
    ) : this(
        scope,
        { account, work, parent, next -> repo.comments(account, work, parent, next) },
        { account, work, text, parent -> repo.postComment(account, work, text, parent) },
    )

    private val threads = mutableMapOf<ThreadKey, MutableStateFlow<ThreadState>>()

    fun state(account: Long, work: Work, parentId: Long? = null): StateFlow<ThreadState> =
        mutable(account, work, parentId).asStateFlow()

    private fun mutable(account: Long, work: Work, parentId: Long?) =
        threads.getOrPut(ThreadKey(work.identity(account), parentId)) {
            MutableStateFlow(ThreadState())
        }

    fun load(account: Long, work: Work, parentId: Long? = null, refresh: Boolean = false) {
        val target = mutable(account, work, parentId)
        val old = target.value
        if (old.loading || (!refresh && old.loaded && old.next == null && old.error == null)) return
        val reset = refresh || (old.loaded && old.next == null && old.error != null)
        target.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val result = fetch(account, work, parentId, if (reset) null else old.next)
                target.update { current ->
                    current.copy(
                        comments =
                            ((if (reset) emptyList() else current.comments) + result.comments)
                                .distinctBy { it.id },
                        next = result.next_url,
                        total = result.total_comments ?: current.total,
                        loaded = true,
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                target.update { it.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                target.update { it.copy(loading = false, error = e.message ?: "评论加载失败") }
            }
        }
    }

    fun send(account: Long, work: Work, text: String, parentId: Long?, done: () -> Unit) {
        val target = mutable(account, work, parentId)
        if (target.value.sending) return
        target.update { it.copy(sending = true, sendError = null) }
        scope.launch {
            try {
                val comment = post(account, work, text, parentId)
                target.update {
                    it.copy(
                        comments = (listOf(comment) + it.comments).distinctBy { c -> c.id },
                        total = it.total?.plus(1),
                        loaded = true,
                        sending = false,
                    )
                }
                if (parentId != null)
                    mutable(account, work, null).update {
                        it.copy(
                            comments =
                                it.comments.map { c ->
                                    if (c.id == parentId) c.copy(has_replies = true) else c
                                }
                        )
                    }
                done()
            } catch (e: CancellationException) {
                target.update { it.copy(sending = false) }
                throw e
            } catch (e: Exception) {
                target.update { it.copy(sending = false, sendError = e.message ?: "发送失败，请重试") }
            }
        }
    }
}
