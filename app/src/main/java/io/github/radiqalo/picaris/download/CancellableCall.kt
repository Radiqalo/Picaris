package io.github.radiqalo.picaris.download

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/** Keeps the cancellation watcher alive until the response body has also been consumed. */
internal suspend fun <T> Call.useCancellable(block: (Response) -> T): T =
    withContext(Dispatchers.IO) {
        val cancellation =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    this@useCancellable.cancel()
                }
            }
        try {
            execute().use(block)
        } catch (error: IOException) {
            ensureActive()
            throw error
        } finally {
            cancellation.cancel()
        }
    }
