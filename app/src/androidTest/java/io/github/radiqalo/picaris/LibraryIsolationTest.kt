package io.github.radiqalo.picaris

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.radiqalo.picaris.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryIsolationTest {
    @Test
    fun accountHistoryCacheSearchAndDownloadAreIsolated() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixivDatabase::class.java,
                )
                .build()
        try {
            val dao = db.library()
            dao.record(HistoryEntity(1, 9, "illust", "first", 10))
            dao.record(HistoryEntity(2, 9, "illust", "second", 20))
            assertEquals("first", dao.history(1).first().single().json)
            assertEquals("second", dao.history(2).first().single().json)
            dao.cache(CachedFeed(1, "feed", "account1", 1))
            dao.cache(CachedFeed(2, "feed", "account2", 1))
            assertEquals("account1", dao.cached(1, "feed")!!.json)
            dao.search(SearchEntity(1, "tag", 1))
            assertTrue(dao.searches(2).first().isEmpty())
            val item =
                DownloadEntity(
                    accountId = 1,
                    workId = 9,
                    kind = "illust",
                    page = 0,
                    title = "test",
                    url = "https://i.pximg.net/test.jpg",
                    name = "test.jpg",
                )
            assertTrue(dao.enqueue(item) > 0)
            assertEquals(-1L, dao.enqueue(item))
            assertTrue(dao.downloads(2).first().isEmpty())
            dao.clearHistory(1)
            assertTrue(dao.history(1).first().isEmpty())
            assertEquals(1, dao.history(2).first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun pausedDownloadCannotBeOverwrittenByTransferProgress() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixivDatabase::class.java,
                )
                .build()
        try {
            val dao = db.library()
            val id =
                dao.enqueue(
                    DownloadEntity(
                        accountId = 1,
                        workId = 9,
                        kind = "illust",
                        page = 0,
                        title = "test",
                        url = "https://i.pximg.net/test.jpg",
                        name = "test.jpg",
                    )
                )
            assertEquals(1, dao.claimDownload(id))
            assertEquals(0, dao.claimDownload(id))
            assertEquals(1, dao.progressDownload(id, 5, 10, "v1"))
            dao.status(id, "paused")
            assertEquals(0, dao.progressDownload(id, 10, 10, "v1"))
            assertEquals(0, dao.completeDownload(id, 10, "content://test"))
            assertEquals("paused", dao.download(id)!!.status)
            assertEquals(5L, dao.download(id)!!.bytes)
            assertEquals(1, dao.requeueDownload(id, "https://i.pximg.net/new.jpg", "{}"))
            assertEquals(1, dao.claimDownload(id))
            assertEquals(1, dao.completeDownload(id, 10, "content://test"))
            assertEquals("complete", dao.download(id)!!.status)
        } finally {
            db.close()
        }
    }
}
