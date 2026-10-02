package io.github.radiqalo.picaris

import io.github.radiqalo.picaris.download.useCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CancellableCallTest {
    private class FakeCall(
        val load: () -> Response,
    ) : Call by unusedCall() {
        val cancelled = CountDownLatch(1)
        private val request = Request.Builder().url("https://example.com/").build()

        override fun request() = request

        override fun execute() = load()

        override fun enqueue(responseCallback: Callback) = error("Synchronous execution only")

        override fun cancel() = cancelled.countDown()

        override fun isExecuted() = true

        override fun isCanceled() = cancelled.count == 0L

        override fun timeout() = Timeout.NONE

        override fun clone(): Call = FakeCall(load)
    }

    @Test
    fun successfulResponseIsConsumedAndWatcherIsReleased() =
        runBlocking {
            val call =
                FakeCall {
                    Response
                        .Builder()
                        .request(Request.Builder().url("https://example.com/").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("response".toResponseBody())
                        .build()
                }
            assertEquals("response", call.useCancellable { it.body.string() })
            assertTrue(call.isCanceled())
        }

    @Test
    fun cancellingCoroutineUnblocksSynchronousRequest() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            lateinit var call: FakeCall
            call =
                FakeCall {
                    started.complete(Unit)
                    check(call.cancelled.await(5, TimeUnit.SECONDS)) { "Request was not cancelled" }
                    throw IOException("cancelled")
                }
            val job = async { call.useCancellable { error("No response expected") } }
            withTimeout(5000) { started.await() }
            job.cancelAndJoin()
            assertTrue(call.isCanceled())
        }
}

private fun unusedCall(): Call =
    Proxy.newProxyInstance(
        Call::class.java.classLoader,
        arrayOf(Call::class.java),
    ) { _, method, _ ->
        error("Unexpected call to ${method.name}")
    } as Call
