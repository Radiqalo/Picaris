package io.github.pixivnext.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class SeedSchemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    @Test
    fun extremeSeedsKeepTextReadableInBothThemes() {
        for (seed in
            listOf(0xFFFFFFFF, 0xFF000000, 0xFF00FF00, 0xFFFF0000, 0xFF6256CA)) for (dark in
            listOf(false, true)) {
            val s = seededColors(seed, dark)
            assertTrue("primary text: $seed / $dark", contrast(s.primary, s.onPrimary) >= 4.45f)
            assertTrue("surface text: $seed / $dark", contrast(s.surface, s.onSurface) >= 4.45f)
        }
    }

    @Test
    fun changingTheSeedAlsoChangesTheDarkPalette() {
        assertNotEquals(
            seededColors(0xFF00FF00, true).primary,
            seededColors(0xFFFF0000, true).primary,
        )
        assertNotEquals(
            seededColors(0xFF6256CA, true).surface,
            seededColors(0xFF6256CA, false).surface,
        )
    }
}
