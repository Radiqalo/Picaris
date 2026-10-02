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
            val first = decodeFrame(zip, frames.first(), "无法解码动图帧")
            val width = first.width
            val height = first.height
            require(width in 1..65535 && height in 1..65535) { "动图尺寸超出 GIF 格式范围" }
            writeShort(output, width)
            writeShort(output, height)
            output.write(0x70)
            output.write(0)
            output.write(0)
            output.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B, 0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30, 0x03, 0x01, 0x00, 0x00, 0x00))
            first.recycle()

            frames.forEach { frame ->
                val bitmap = decodeFrame(zip, frame, "无法解码动图帧: ${frame.file}")
                try {
                    require(bitmap.width == width && bitmap.height == height) { "动图帧尺寸不一致" }
                    writeGraphicControl(output, ((frame.delay + 5) / 10).coerceIn(1, 65535))
                    output.write(0x2C)
                    writeShort(output, 0)
                    writeShort(output, 0)
                    writeShort(output, width)
                    writeShort(output, height)
                    output.write(0x87)
                    val indexed = quantize(bitmap)
                    writePalette(output, indexed.palette)
                    output.write(8)
                    writeImageData(output, indexed.pixels)
                } finally {
                    bitmap.recycle()
                }
            }
            output.write(0x3B)
        }
    }

    private fun decodeFrame(
        zip: ZipFile,
        frame: Frame,
        failureMessage: String,
    ): android.graphics.Bitmap {
        val entry = zip.getEntry(frame.file) ?: error("动图帧缺失: ${frame.file}")
        return zip.getInputStream(entry).use(BitmapFactory::decodeStream) ?: error(failureMessage)
    }

    private fun writePalette(output: OutputStream, palette: IntArray) {
        palette.forEach { color ->
            output.write((color shr 16) and 0xFF)
            output.write((color shr 8) and 0xFF)
            output.write(color and 0xFF)
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

    private fun quantize(bitmap: android.graphics.Bitmap): IndexedImage {
        val colors = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(colors, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val counts = IntArray(1 shl 15)
        val redSums = LongArray(counts.size)
        val greenSums = LongArray(counts.size)
        val blueSums = LongArray(counts.size)
        colors.forEach { color ->
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            val key = ((red shr 3) shl 10) or ((green shr 3) shl 5) or (blue shr 3)
            counts[key]++
            redSums[key] += red
            greenSums[key] += green
            blueSums[key] += blue
        }
        val bins = counts.indices.mapNotNull { key ->
            val count = counts[key]
            if (count == 0) null else ColorBin(
                red = (key shr 10) and 31,
                green = (key shr 5) and 31,
                blue = key and 31,
                count = count,
                redSum = redSums[key],
                greenSum = greenSums[key],
                blueSum = blueSums[key],
            )
        }
        val boxes = java.util.PriorityQueue<ColorBox>(compareByDescending { it.score })
        boxes += colorBox(bins)
        while (boxes.size < 256) {
            val box = boxes.poll() ?: break
            if (box.bins.size < 2 || box.range == 0) {
                boxes += box
                break
            }
            val sorted = when (box.axis) {
                0 -> box.bins.sortedBy { it.red }
                1 -> box.bins.sortedBy { it.green }
                else -> box.bins.sortedBy { it.blue }
            }
            val halfWeight = box.weight / 2
            var accumulated = 0
            var splitAt = 1
            while (splitAt < sorted.lastIndex && accumulated < halfWeight) {
                accumulated += sorted[splitAt - 1].count
                splitAt++
            }
            boxes += colorBox(sorted.subList(0, splitAt))
            boxes += colorBox(sorted.subList(splitAt, sorted.size))
        }
        val palette = IntArray(256)
        boxes.toList().take(256).forEachIndexed { index, box ->
            val red = box.bins.sumOf { it.redSum } / box.weight
            val green = box.bins.sumOf { it.greenSum } / box.weight
            val blue = box.bins.sumOf { it.blueSum } / box.weight
            palette[index] = (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
        }
        val lookup = IntArray(1 shl 12)
        for (key in lookup.indices) {
            val red = (((key shr 8) and 15) shl 4) + 8
            val green = (((key shr 4) and 15) shl 4) + 8
            val blue = ((key and 15) shl 4) + 8
            var nearest = 0
            var nearestDistance = Int.MAX_VALUE
            palette.forEachIndexed { index, candidate ->
                val redDelta = red - ((candidate shr 16) and 0xFF)
                val greenDelta = green - ((candidate shr 8) and 0xFF)
                val blueDelta = blue - (candidate and 0xFF)
                val distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearest = index
                }
            }
            lookup[key] = nearest
        }
        val indices = ByteArray(colors.size) { index ->
            val color = colors[index]
            val key = ((((color shr 16) and 0xFF) shr 4) shl 8) or
                ((((color shr 8) and 0xFF) shr 4) shl 4) or ((color and 0xFF) shr 4)
            lookup[key].toByte()
        }
        return IndexedImage(palette, indices)
    }

    private fun colorBox(bins: List<ColorBin>): ColorBox {
        val redRange = bins.maxOf { it.red } - bins.minOf { it.red }
        val greenRange = bins.maxOf { it.green } - bins.minOf { it.green }
        val blueRange = bins.maxOf { it.blue } - bins.minOf { it.blue }
        val (axis, range) = when (maxOf(redRange, greenRange, blueRange)) {
            redRange -> 0 to redRange
            greenRange -> 1 to greenRange
            else -> 2 to blueRange
        }
        val weight = bins.sumOf { it.count }
        return ColorBox(bins, weight, axis, range, range.toDouble() * kotlin.math.sqrt(weight.toDouble()))
    }

    private data class IndexedImage(val palette: IntArray, val pixels: ByteArray)
    private data class ColorBin(
        val red: Int,
        val green: Int,
        val blue: Int,
        val count: Int,
        val redSum: Long,
        val greenSum: Long,
        val blueSum: Long,
    )
    private data class ColorBox(
        val bins: List<ColorBin>,
        val weight: Int,
        val axis: Int,
        val range: Int,
        val score: Double,
    )

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
                    val addedCode = nextCode++
                    dictionary[key] = addedCode
                    if (addedCode == (1 shl codeSize) && codeSize < 12) codeSize++
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
