package app.chaster.client

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class LockCountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private const val PREFS = "lock_countdown_widget"
        private const val KEY_NAME = "name"
        private const val KEY_END_MILLIS = "end_millis"

        fun updateFromLocks(context: Context, locks: List<ChasterLock>) {
            val now = System.currentTimeMillis()
            val futureLock = locks.mapNotNull { lock ->
                parseEnd(lock.endDate)?.takeIf { it > now }?.let { lock to it }
            }.minByOrNull { it.second }

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (futureLock == null) {
                prefs.edit().putString(KEY_NAME, if (locks.isEmpty()) "Open Chaster to refresh" else "No active lock")
                    .putLong(KEY_END_MILLIS, 0L).apply()
            } else {
                prefs.edit().putString(KEY_NAME, futureLock.first.name)
                    .putLong(KEY_END_MILLIS, futureLock.second).apply()
            }
            WearSyncPublisher.publish(
                context,
                futureLock?.first?.name ?: if (locks.isEmpty()) "Open Chaster to refresh" else "No active lock",
                futureLock?.second ?: 0L,
                if (futureLock == null) "No active lock" else "Active lock"
            )
            updateAll(context)
        }

        private fun parseEnd(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            return try {
                try { Instant.parse(value).toEpochMilli() }
                catch (_: DateTimeParseException) { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            } catch (_: Exception) { null }
        }

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LockCountdownWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_NAME, "Open Chaster to refresh") ?: "Open Chaster to refresh"
            val endMillis = prefs.getLong(KEY_END_MILLIS, 0L)
            val views = RemoteViews(context.packageName, R.layout.lock_countdown_widget)
            views.setTextViewText(R.id.widget_lock_name, name)
            val remaining = endMillis - System.currentTimeMillis()
            if (endMillis > 0L && remaining > 0L) {
                views.setViewVisibility(R.id.widget_countdown, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_no_countdown, android.view.View.GONE)
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + remaining, null, true)
                views.setChronometerCountDown(R.id.widget_countdown, true)
            } else {
                views.setViewVisibility(R.id.widget_countdown, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_no_countdown, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_no_countdown, if (endMillis > 0L) "Time is up" else "🔒  Time left unavailable")
            }
            val openIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
