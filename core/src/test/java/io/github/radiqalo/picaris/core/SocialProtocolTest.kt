package io.github.radiqalo.picaris.core

import org.junit.Assert.*
import org.junit.Test

class SocialProtocolTest {
    @Test
    fun commentsAndRepliesUseCorrectWorkKindAndIdentifiers() {
        val novel = Work(42, type = "novel")
        val illust = Work(42)
        assertEquals("v3/illust/comments", commentEndpoint(illust, null).first)
        assertEquals("42", commentEndpoint(illust, null).second["illust_id"])
        assertEquals("v3/novel/comments", commentEndpoint(novel, null).first)
        assertEquals("42", commentEndpoint(novel, null).second["novel_id"])
        assertEquals(
            "v2/novel/comment/replies" to mapOf("comment_id" to "7"),
            commentEndpoint(novel, 7),
        )
    }

    @Test
    fun decodesTextStampsRepliesAndOptionalServerFields() {
        val result =
            AppJson.decodeFromString<CommentsResponse>(
                """{"comments":[
          {"id":1,"comment":null,"user":{"id":2,"name":"reader"},"stamp":{"stamp_id":4,"stamp_url":"https://i.pximg.net/stamp.png"},"has_replies":true},
          {"id":3,"comment":"reply","parent_comment":{"id":1,"comment":"parent"},"unknown":"ignored"}
        ],"next_url":"https://app-api.pixiv.net/v3/illust/comments?offset=30","total_comments":22}"""
            )
        assertEquals("", result.comments.first().comment)
        assertTrue(result.comments.first().has_replies)
        assertEquals(4L, result.comments.first().stamp!!.stamp_id)
        assertEquals(1L, result.comments.last().parent_comment!!.id)
        assertEquals(22, result.total_comments)
        assertNotNull(result.next_url)
    }
}
