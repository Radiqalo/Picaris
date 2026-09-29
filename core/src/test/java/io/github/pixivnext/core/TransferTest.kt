package io.github.pixivnext.core

import java.nio.file.Files
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TransferTest {
    private fun fixture(block: suspend CoroutineScope.(MockWebServer, java.io.File) -> Unit) =
        runBlocking {
            val server = MockWebServer()
            server.start()
            val file = Files.createTempFile("pixiv-transfer", ".part").toFile()
            try {
                block(server, file)
            } finally {
                server.shutdown()
                file.delete()
            }
        }

    @Test
    fun validRangeAppendsExactlyToTheExistingPrefix() = fixture { server, file ->
        file.writeText("abc")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-5/6")
                .setHeader("ETag", "\"same\"")
                .setBody("def")
        )
        val result =
            ResumableTransfer(OkHttpClient()).transfer(
                server.url("/file").toString(),
                file,
                "\"same\"",
            ) { _, _, _ ->
                true
            }
        assertEquals("abcdef", file.readText())
        assertEquals(6L, result!!.bytes)
        val request = server.takeRequest()
        assertEquals("bytes=3-", request.getHeader("Range"))
        assertEquals("\"same\"", request.getHeader("If-Range"))
    }

    @Test
    fun changedResourceReplacesThePrefixInsteadOfAppending() = fixture { server, file ->
        file.writeText("old")
        server.enqueue(MockResponse().setHeader("ETag", "\"new\"").setBody("replacement"))
        ResumableTransfer(OkHttpClient()).transfer(
            server.url("/file").toString(),
            file,
            "\"old\"",
        ) { _, _, _ ->
            true
        }
        assertEquals("replacement", file.readText())
    }

    @Test
    fun invalidRangeDoesNotCorruptTheExistingFile() = fixture { server, file ->
        file.writeText("abc")
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 1-3/4")
                .setBody("xyz")
        )
        try {
            ResumableTransfer(OkHttpClient()).transfer(
                server.url("/file").toString(),
                file,
                "\"same\"",
            ) { _, _, _ ->
                true
            }
            fail()
        } catch (_: IllegalStateException) {}
        assertEquals("abc", file.readText())
    }

    @Test
    fun rangeNotSatisfiableRestartsOnceAndCompletes() = fixture { server, file ->
        file.writeText("abc")
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(MockResponse().setBody("new"))
        ResumableTransfer(OkHttpClient()).transfer(
            server.url("/file").toString(),
            file,
            "\"same\"",
        ) { _, _, _ ->
            true
        }
        assertEquals("new", file.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingValidatorForcesSafeFullDownload() = fixture { server, file ->
        file.writeText("unverified")
        server.enqueue(MockResponse().setBody("correct"))
        ResumableTransfer(OkHttpClient()).transfer(server.url("/file").toString(), file, "") {
            _,
            _,
            _ ->
            true
        }
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals("correct", file.readText())
    }

    @Test
    fun cancellationClosesARequestEvenWhenTheServerNeverResponds() = fixture { server, file ->
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)
        )
        val request =
            async(Dispatchers.IO) {
                ResumableTransfer(OkHttpClient()).transfer(
                    server.url("/blocked").toString(),
                    file,
                    "",
                ) { _, _, _ ->
                    true
                }
            }
        assertNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS))
        withTimeout(2000) { request.cancelAndJoin() }
        assertTrue(request.isCancelled)
        assertEquals(0L, file.length())
    }
}
