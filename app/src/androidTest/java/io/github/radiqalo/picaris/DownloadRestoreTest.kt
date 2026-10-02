package io.github.radiqalo.picaris

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.radiqalo.picaris.core.AppJson
import io.github.radiqalo.picaris.core.DownloadEntity
import io.github.radiqalo.picaris.core.PixivDatabase
import io.github.radiqalo.picaris.core.Settings
import io.github.radiqalo.picaris.download.DownloadAccessRecovery
import io.github.radiqalo.picaris.download.DownloadFileState
import io.github.radiqalo.picaris.download.downloadFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DownloadRestoreTest {
    @Test
    fun zipRestoreRebindsByUriAfterAccessIsLostWithoutUsingFileNames() =
        runBlocking {
            val application = ApplicationProvider.getApplicationContext<Context>()
            val fixture = Fixture(application)
            val db = Room.inMemoryDatabaseBuilder(application, PixivDatabase::class.java).build()
            try {
                val original = record(91, 42, "recorded.png")
                val unmatched = record(92, 99, "renamed.png")
                val archive =
                    AppDataArchive(
                        1,
                        Settings(),
                        emptyList(),
                        emptyList(),
                        listOf(original, unmatched),
                        emptyList(),
                    )
                val zipBytes =
                    ByteArrayOutputStream()
                        .apply {
                            ZipOutputStream(this).use { zip ->
                                zip.putNextEntry(ZipEntry("app-data.json"))
                                zip.write(AppJson.encodeToString(archive).encodeToByteArray())
                                zip.closeEntry()
                            }
                        }.toByteArray()
                val restored =
                    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                        assertEquals("app-data.json", zip.nextEntry.name)
                        AppJson.decodeFromString<AppDataArchive>(zip.readBytes().decodeToString())
                    }
                db.library().restoreDownloads(restored.downloads)
                assertEquals(
                    DownloadFileState.ACCESS_REQUIRED,
                    downloadFileState(fixture.context, original.uri),
                )
                assertEquals(original.uri, db.library().download(91)!!.uri)
                fixture.authorized = true
                val recovered =
                    DownloadAccessRecovery(fixture.context, db.library(), fixture::mediaUri)
                        .restore(fixture.tree)
                assertEquals(1, recovered)
                val saved = db.library().download(91)!!
                assertEquals(fixture.document("renamed.png").toString(), saved.uri)
                assertEquals("recorded.png", saved.name)
                assertEquals("complete", saved.status)
                assertEquals(unmatched.uri, db.library().download(92)!!.uri)
                assertEquals(
                    DownloadFileState.AVAILABLE,
                    downloadFileState(fixture.context, saved.uri),
                )
                fixture.context.contentResolver.openInputStream(Uri.parse(saved.uri))!!.use {
                    assertArrayEquals("original file".encodeToByteArray(), it.readBytes())
                }
                assertEquals(
                    0,
                    db.library().rebindDownloadUri(91, original.uri, "", "content://wrong", ""),
                )
                assertEquals(saved.uri, db.library().download(91)!!.uri)
            } finally {
                db.close()
                fixture.close()
            }
        }

    private fun record(
        id: Long,
        mediaId: Long,
        name: String,
    ) = DownloadEntity(
        id = id,
        accountId = 1,
        workId = id,
        kind = "illust",
        page = 0,
        title = "restore fixture",
        url = "https://i.pximg.net/fixture.png",
        name = name,
        status = "complete",
        uri = "content://media/external/images/media/$mediaId",
    )

    private class Fixture(
        application: Context,
    ) {
        var authorized = false
        private val rootId = "primary:Pictures/Picaris"
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, rootId)
        private val original =
            File.createTempFile("restore-original", ".bin", application.cacheDir).apply {
                writeText("original file")
            }
        private val other =
            File.createTempFile("restore-other", ".bin", application.cacheDir).apply {
                writeText("different file with the old name")
            }
        private lateinit var resolver: ContentResolver
        val context =
            object : ContextWrapper(application) {
                override fun getContentResolver(): ContentResolver = resolver

                override fun checkCallingOrSelfUriPermission(
                    uri: Uri,
                    flags: Int,
                ): Int =
                    if (authorized) {
                        PackageManager.PERMISSION_GRANTED
                    } else {
                        PackageManager.PERMISSION_DENIED
                    }
            }

        init {
            val provider =
                object : ContentProvider() {
                    override fun onCreate() = true

                    override fun getType(uri: Uri): String = "image/png"

                    override fun insert(
                        uri: Uri,
                        values: ContentValues?,
                    ): Uri? = error("Read only fixture")

                    override fun delete(
                        uri: Uri,
                        selection: String?,
                        args: Array<out String>?,
                    ): Int = error("Read only fixture")

                    override fun update(
                        uri: Uri,
                        values: ContentValues?,
                        selection: String?,
                        args: Array<out String>?,
                    ): Int = error("Read only fixture")

                    override fun query(
                        uri: Uri,
                        projection: Array<out String>?,
                        selection: String?,
                        args: Array<out String>?,
                        sort: String?,
                    ): Cursor {
                        checkAccess(uri)
                        val columns =
                            projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val cursor = MatrixCursor(columns)
                        val id = DocumentsContract.getDocumentId(uri)
                        val ids =
                            if (uri.lastPathSegment == "children") {
                                listOf("$rootId/renamed.png", "$rootId/recorded.png")
                            } else {
                                listOf(id)
                            }
                        for (docId in ids) {
                            cursor.addRow(
                                columns.map { column ->
                                    when (column) {
                                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> docId
                                        DocumentsContract.Document.COLUMN_DISPLAY_NAME ->
                                            docId
                                                .substringAfterLast(
                                                    '/',
                                                )
                                        DocumentsContract.Document.COLUMN_MIME_TYPE ->
                                            if (docId ==
                                                rootId
                                            ) {
                                                DocumentsContract.Document.MIME_TYPE_DIR
                                            } else {
                                                "image/png"
                                            }
                                        DocumentsContract.Document.COLUMN_FLAGS -> 0
                                        DocumentsContract.Document.COLUMN_SIZE -> 13L
                                        else -> 0L
                                    }
                                },
                            )
                        }
                        return cursor
                    }

                    override fun openAssetFile(
                        uri: Uri,
                        mode: String,
                    ): AssetFileDescriptor {
                        checkAccess(uri)
                        val file =
                            if (DocumentsContract
                                    .getDocumentId(
                                        uri,
                                    ).endsWith("renamed.png")
                            ) {
                                original
                            } else {
                                other
                            }
                        return AssetFileDescriptor(
                            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                            0,
                            -1,
                        )
                    }
                }
            provider.attachInfo(
                context,
                ProviderInfo().apply {
                    authority = "$AUTHORITY;media"
                    exported = true
                },
            )
            resolver = ContentResolver.wrap(provider)
        }

        private fun checkAccess(uri: Uri) {
            if (!authorized || uri.authority == "media") throw SecurityException("Lost file access")
        }

        fun document(name: String): Uri =
            DocumentsContract.buildDocumentUriUsingTree(tree, "$rootId/$name")

        fun mediaUri(document: Uri): Uri =
            Uri.parse(
                "content://media/external_primary/file/" +
                    if (DocumentsContract
                            .getDocumentId(
                                document,
                            ).endsWith("renamed.png")
                    ) {
                        "42"
                    } else {
                        "43"
                    },
            )

        fun close() {
            original.delete()
            other.delete()
        }

        companion object {
            const val AUTHORITY = "com.android.externalstorage.documents"
        }
    }
}
