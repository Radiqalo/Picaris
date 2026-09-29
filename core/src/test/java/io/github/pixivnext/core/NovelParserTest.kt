package io.github.pixivnext.core

import org.junit.Assert.*
import org.junit.Test

class NovelParserTest {
    @Test
    fun extractsEscapedTextAndInlineImagesWithTrailingCommas() {
        val html =
            """<script>Object.defineProperty(window, 'pixiv', { value: {"novel":{"text":"a } quote \" b\n[uploadedimage:42]","uploadedImages":{"42":{"urls":{"original":"https://i.pximg.net/example.png"}}},},},});</script>"""
        val result = parseNovelHtml(html)
        assertEquals("a } quote \" b\n[uploadedimage:42]", result.text)
        assertEquals("https://i.pximg.net/example.png", result.images["42"])
    }

    @Test
    fun acceptsDoubleQuotedWindowProperty() {
        val html =
            """<script>Object.defineProperty(window, "pixiv", {value: {"novel":{"text":"hello"}}});</script>"""
        assertEquals("hello", parseNovelHtml(html).text)
    }

    @Test
    fun rejectsLoginPageInsteadOfSilentlyShowingEmptyNovel() {
        assertThrows(IllegalStateException::class.java) {
            parseNovelHtml("<html>please log in</html>")
        }
    }

    @Test
    fun rejectsTruncatedNovelObject() {
        assertThrows(IllegalStateException::class.java) {
            parseNovelHtml("Object.defineProperty(window, 'pixiv', {value: {\"novel\":{")
        }
    }

    @Test
    fun acceptsWhitespaceInJavascriptWrapper() {
        val html =
            """Object.defineProperty( window , 'pixiv', { value : {"novel":{"text":"正文"}} });"""
        assertEquals("正文", parseNovelHtml(html).text)
    }
}
