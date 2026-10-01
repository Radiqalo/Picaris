package io.github.radiqalo.picaris.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.radiqalo.picaris.core.LibraryDao
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MaintenanceEntry {
    fun library(): LibraryDao
}

class MaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            val dao =
                EntryPointAccessors.fromApplication(
                        applicationContext,
                        MaintenanceEntry::class.java,
                    )
                    .library()
            val before = System.currentTimeMillis() - 7 * 24 * 3600_000L
            dao.expireCache(before)
            val finished = dao.finishedIds().toSet()
            File(applicationContext.filesDir, "transfer")
                .walkTopDown()
                .filter {
                    it.isFile &&
                        it.extension == "part" &&
                        (it.lastModified() < before ||
                            it.nameWithoutExtension.toLongOrNull() in finished)
                }
                .forEach { it.delete() }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
}
