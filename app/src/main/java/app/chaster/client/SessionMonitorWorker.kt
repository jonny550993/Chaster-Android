package app.chaster.client

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SessionMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tokenStore = TokenStore(applicationContext)
        if (tokenStore.read().isBlank()) return Result.success()
        return try {
            val locks = ChasterRepository(tokenStore).locks()
            val prefs = applicationContext.getSharedPreferences("background_session_monitor", Context.MODE_PRIVATE)
            val currentIds = locks.map { it.id }.toSet()
            if (!prefs.getBoolean("initialized", false)) {
                prefs.edit().putBoolean("initialized", true).putStringSet("lock_ids", currentIds).apply()
            } else {
                val previous = prefs.getStringSet("lock_ids", emptySet()).orEmpty()
                val newlySeen = locks.filter { it.id !in previous }
                prefs.edit().putStringSet("lock_ids", currentIds).apply()
                newlySeen.forEachIndexed { index, lock -> notifyNewSession(lock.name, lock.id.hashCode() + index) }
            }
            Result.success()
        } catch (e: ChasterHttpException) {
            when {
                e.statusCode == 429 || e.statusCode >= 500 -> Result.retry()
                else -> Result.success()
            }
        } catch (_: Exception) {
            // Network errors and other transient failures can be retried.
            Result.retry()
        }
    }

    private fun notifyNewSession(name: String, id: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "New lock sessions", NotificationManager.IMPORTANCE_DEFAULT))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("New Chaster session detected")
            .setContentText(name)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    companion object { const val CHANNEL_ID = "chaster_new_sessions" }
}
