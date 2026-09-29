package io.github.pixivnext

import android.app.Application
import android.content.Context
import androidx.work.*
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import io.github.pixivnext.core.Network
import io.github.pixivnext.download.MaintenanceWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PixivApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject lateinit var network: Network
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setJobSchedulerJobIdRange(20000, 30000).build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "library-maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build(),
            )
    }

    override fun newImageLoader(context: Context): ImageLoader = network.imageLoader(context)
}
