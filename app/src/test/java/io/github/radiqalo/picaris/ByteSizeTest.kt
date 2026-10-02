package io.github.radiqalo.picaris

import io.github.radiqalo.picaris.ui.formatByteSize
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ByteSizeTest {
    @Test
    fun byteThresholdsAndLargeValuesKeepExistingLabels() {
        val expected =
            mapOf(
                -1L to "-1 B",
                0L to "0 B",
                1023L to "1023 B",
                1024L to "1.0 KB",
                1048575L to "1024.0 KB",
                1048576L to "1.0 MB",
                1073741824L to "1024.0 MB",
            )
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            expected.forEach { (bytes, label) -> assertEquals(label, formatByteSize(bytes)) }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
