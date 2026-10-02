package io.github.radiqalo.picaris

import io.github.radiqalo.picaris.download.DownloadUriBindings
import io.github.radiqalo.picaris.download.downloadUriIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUriIdentityTest {
    @Test
    fun mediaCollectionsShareTheUnderlyingProviderRowIdentity() {
        assertEquals(
            downloadUriIdentity("content://media/external/images/media/42"),
            downloadUriIdentity("content://media/external_primary/file/42"),
        )
        assertNotEquals(
            downloadUriIdentity("content://media/external/images/media/42"),
            downloadUriIdentity("content://media/external/images/media/43"),
        )
        assertNotEquals(
            downloadUriIdentity("content://media/external_primary/file/42"),
            downloadUriIdentity("content://media/0123-4567/file/42"),
        )
    }

    @Test
    fun documentIdentityIgnoresTreeGrantButKeepsDocumentId() {
        assertEquals(
            downloadUriIdentity("content://provider/document/primary%3APictures%2FPicaris%2Fa.png"),
            downloadUriIdentity(
                "content://provider/tree/primary%3APictures/document/primary%3APictures%2FPicaris%2Fa.png",
            ),
        )
        assertNotEquals(
            downloadUriIdentity("content://provider/document/primary%3APictures%2FPicaris%2Fa.png"),
            downloadUriIdentity("content://other/document/primary%3APictures%2FPicaris%2Fa.png"),
        )
    }

    @Test
    fun displayNamesAndInvalidUrisCannotIdentifyFiles() {
        assertNull(downloadUriIdentity("a.png"))
        assertNull(downloadUriIdentity("content://media/external/images/media"))
        assertNull(downloadUriIdentity("file:///Pictures/Picaris/a.png"))
        assertNull(downloadUriIdentity("content://media/external/unrelated/42"))
    }

    @Test
    fun bindingsDoNotGuessByFileNameAndSupportLegacyFolders() {
        val original = "content://media/external/images/media/42"
        val replacement = "content://provider/document/renamed.png"
        val folder = "saf-folder|content://provider/tree/downloads|Picaris"
        val bindings =
            DownloadUriBindings(
                mapOf(downloadUriIdentity(original)!! to replacement),
                mapOf("Download/Picaris" to folder),
            )
        assertEquals(replacement, bindings.mainUri(original))
        assertEquals(folder, bindings.mainUri("media-folder|Download/Picaris/"))
        val unknown = "content://media/external/images/media/43"
        assertEquals(unknown, bindings.mainUri(unknown))
        assertEquals("renamed.png", bindings.mainUri("renamed.png"))
    }

    @Test
    fun reauthorizingAncestorTreeKeepsTheSameDocumentFolderIdentity() {
        val authority = "com.android.externalstorage.documents"
        val tree = "content://$authority/tree/primary%3APictures"
        val replacement = "saf-folder|$tree|Picaris"
        val bindings =
            DownloadUriBindings(
                emptyMap(),
                mapOf("document:$authority:primary:Pictures/Picaris" to replacement),
            )
        val oldTree = "content://$authority/tree/primary%3APictures%2FPicaris"
        assertEquals(replacement, bindings.mainUri("saf-folder|$oldTree|"))
    }
}
