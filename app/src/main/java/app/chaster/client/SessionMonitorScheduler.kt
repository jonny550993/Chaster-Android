package app.chaster.client

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SessionMonitorScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SessionMonitorWorker>(15, TimeUnit.MINUTES)
            .addTag("chaster-session-monitor")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "chaster-session-monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
