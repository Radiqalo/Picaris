package io.github.radiqalo.picaris.download

import android.graphics.BitmapFactory
import io.github.radiqalo.picaris.core.Frame
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipFile

internal object GifEncoder {
    fun write(zip: ZipFile, frames: List<Frame>, destination: File) {
        require(frames.isNotEmpty()) { "动图没有可用帧" }
        destination.parentFile?.mkdirs()
        BufferedOutputStream(destination.outputStream()).use { output ->
            output.write("GIF89a".toByteArray(Charsets.US_ASCII))
            val first = zip.getInputStream(zip.getEntry(frames.first().file)
                ?: error("动图帧缺失: ${frames.first().file}")).use(BitmapFactory::decodeStream)
                ?: error("无法解码动图帧")
            val width = first.width
            val height = first.height
            require(width in 1..65535 && height in 1..65535) { "动图尺寸超出 GIF 格式范围" }
            writeShort(output, width)
            writeShort(output, height)
            output.write(0xF7)
            output.write(0)
            output.write(0)
            writePalette(output)
            output.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B, 0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30, 0x03, 0x01, 0x00, 0x00, 0x00))
            first.recycle()

            frames.forEach { frame ->
                val bitmap = zip.getInputStream(zip.getEntry(frame.file)
                    ?: error("动图帧缺失: ${frame.file}")).use(BitmapFactory::decodeStream)
                    ?: error("无法解码动图帧: ${frame.file}")
                try {
                    require(bitmap.width == width && bitmap.height == height) { "动图帧尺寸不一致" }
                    writeGraphicControl(output, ((frame.delay + 5) / 10).coerceIn(1, 65535))
                    output.write(0x2C)
                    writeShort(output, 0)
                    writeShort(output, 0)
                    writeShort(output, width)
                    writeShort(output, height)
                    output.write(0)
                    output.write(8)
                    writeImageData(output, quantize(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
            output.write(0x3B)
        }
    }

    private fun writePalette(output: OutputStream) {
        repeat(256) { index ->
            val red = ((index shr 5) and 7) * 255 / 7
            val green = ((index shr 2) and 7) * 255 / 7
            val blue = (index and 3) * 255 / 3
            output.write(red)
            output.write(green)
            output.write(blue)
        }
    }

    private fun writeGraphicControl(output: OutputStream, delay: Int) {
        output.write(0x21)
        output.write(0xF9)
        output.write(4)
        output.write(8)
        writeShort(output, delay)
        output.write(0)
        output.write(0)
    }

    private fun quantize(bitmap: android.graphics.Bitmap): ByteArray {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ByteArray(colors.size) { index ->
            val color = colors[index]
            ((((color shr 16) and 0xE0) or ((color shr 11) and 0x1C) or ((color shr 6) and 0x03))).toByte()
        }
    }

    private fun writeImageData(output: OutputStream, pixels: ByteArray) {
        val clearCode = 256
        val endCode = 257
        val dictionary = HashMap<Int, Int>(4096)
        val bytes = GifBitWriter()
        var codeSize = 9
        var nextCode = 258
        bytes.write(clearCode, codeSize)
        var prefix = pixels.first().toInt() and 0xFF
        for (index in 1 until pixels.size) {
            val suffix = pixels[index].toInt() and 0xFF
            val key = (prefix shl 8) or suffix
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
            } else {
                bytes.write(prefix, codeSize)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode++
                    if (nextCode == (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    bytes.write(clearCode, codeSize)
                    dictionary.clear()
                    codeSize = 9
                    nextCode = 258
                }
                prefix = suffix
            }
        }
        bytes.write(prefix, codeSize)
        bytes.write(endCode, codeSize)
        val compressed = bytes.finish()
        var offset = 0
        while (offset < compressed.size) {
            val count = minOf(255, compressed.size - offset)
            output.write(count)
            output.write(compressed, offset, count)
            offset += count
        }
        output.write(0)
    }

    private fun writeShort(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
    }

    private class GifBitWriter {
        private val output = java.io.ByteArrayOutputStream()
        private var bits = 0
        private var bitCount = 0

        fun write(code: Int, width: Int) {
            bits = bits or (code shl bitCount)
            bitCount += width
            while (bitCount >= 8) {
                output.write(bits and 0xFF)
                bits = bits ushr 8
                bitCount -= 8
            }
        }

        fun finish(): ByteArray {
            if (bitCount > 0) output.write(bits and 0xFF)
            return output.toByteArray()
        }
    }
}
