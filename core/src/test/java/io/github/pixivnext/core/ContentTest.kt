package io.github.pixivnext.core

import org.junit.Assert.*
import org.junit.Test

class ContentTest {
    @Test
    fun filtersAdultAiAndBlockedContentWithoutMatchingEmptyRules() {
        val w = Work(id = 1, user = User(12, "artist"), tags = listOf(Tag("landscape")))
        assertTrue(Settings().allows(w))
        assertFalse(Settings().allows(w.copy(x_restrict = 1)))
        assertTrue(Settings(showAdult = true).allows(w.copy(x_restrict = 1)))
        assertFalse(Settings(hideAi = true).allows(w.copy(illust_ai_type = 2)))
        assertFalse(Settings(blockedTags = " other, Landscape\n").allows(w))
        assertFalse(Settings(blockedUsers = "2,12,5").allows(w))
        assertTrue(Settings(blockedUsers = "1").allows(w))
    }

    @Test
    fun selectsEveryOriginalPageInOrder() {
        val w =
            Work(
                meta_pages =
                    listOf(MetaPage(ImageUrls(original = "a")), MetaPage(ImageUrls(original = "b")))
            )
        assertEquals(listOf("a", "b"), w.originals)
        assertEquals(listOf("original"), Work(meta_single_page = SinglePage("original")).originals)
    }

    @Test
    fun toleratesMissingAndFutureApiFields() {
        val w =
            AppJson.decodeFromString<Work>(
                """{"id":123,"title":"work","unknown":true,"series":null,"tags":[{"name":"x","translated_name":null}]}"""
            )
        assertEquals(123, w.id)
        assertEquals("work", w.title)
        assertNull(w.series)
    }
}
