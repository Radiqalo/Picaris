package io.github.radiqalo.picaris.core

import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request

data class TransferResult(val bytes: Long, val total: Long, val validator: String)

/** Resumes only a server-validated prefix, and never publishes an incomplete response. */
class ResumableTransfer(private val client: OkHttpClient) {
    suspend fun transfer(
        url: String,
        file: File,
        validator: String,
        onProgress: suspend (Long, Long, String) -> Boolean,
    ): TransferResult? =
        withContext(Dispatchers.IO) {
            var previous = validator
            repeat(2) { attempt ->
                val existing = file.takeIf { it.exists() }?.length() ?: 0
                val offset =
                    if (previous.isNotBlank() && !previous.startsWith("W/")) existing else 0
                val request =
                    Request.Builder()
                        .url(url)
                        .apply {
                            if (offset > 0) {
                                header("Range", "bytes=$offset-")
                                header("If-Range", previous)
                            }
                        }
                        .build()
                val call = client.newCall(request)
                // A suspended child reacts when cancellation starts, including while execute/read
                // block.
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
                        if (response.code == 416 && offset > 0 && attempt == 0) {
                            file.delete()
                            previous = ""
                            return@repeat
                        }
                        check(response.isSuccessful) { "下载服务器返回 ${response.code}" }
                        val resumed = response.code == 206
                        val range =
                            response.header("Content-Range")?.let {
                                Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it)
                            }
                        val rangeStart = range?.groupValues?.get(1)?.toLongOrNull()
                        val rangeEnd = range?.groupValues?.get(2)?.toLongOrNull()
                        val rangeTotal = range?.groupValues?.get(3)?.toLongOrNull()
                        if (resumed) {
                            check(
                                offset > 0 && rangeStart == offset &&
                                    rangeEnd != null && rangeTotal != null &&
                                    rangeEnd >= offset && rangeEnd < rangeTotal
                            ) { "服务器返回了无效的续传范围" }
                            val length = response.body.contentLength()
                            check(length < 0 || length == rangeEnd - offset + 1) {
                                "服务器返回的续传长度与范围不一致"
                            }
                        }
                        val current =
                            response.header("ETag")?.takeUnless { it.startsWith("W/") }
                                ?: response.header("Last-Modified")
                                ?: ""
                        if (resumed && current.isNotEmpty() && current != previous) {
                            if (attempt == 0) {
                                file.delete()
                                previous = ""
                                return@repeat
                            }
                            error("文件在下载过程中发生变化")
                        }
                        val start = if (resumed) offset else 0
                        val total =
                            if (resumed) rangeTotal!!
                            else response.body.contentLength().coerceAtLeast(0)
                        if (!onProgress(start, total, current)) return@withContext null
                        var bytes = start
                        var last = 0L
                        FileOutputStream(file, resumed).use { out ->
                            response.body.byteStream().use { input ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    bytes += n
                                    if (System.currentTimeMillis() - last > 300) {
                                        if (!onProgress(bytes, total, current)) {
                                            out.fd.sync()
                                            return@withContext null
                                        }
                                        last = System.currentTimeMillis()
                                    }
                                }
                                out.fd.sync()
                            }
                        }
                        check(total == 0L || bytes == total) { "文件未完整下载，重试可以继续下载" }
                        if (!onProgress(bytes, total, current)) return@withContext null
                        return@withContext TransferResult(bytes, total, current)
                    }
                } catch (e: Exception) {
                    ensureActive()
                    throw e
                } finally {
                    cancellation.cancel()
                }
            }
            error("无法恢复下载，请重试")
        }
}
