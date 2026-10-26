package app.chaster.client.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData

class LockStatusComplicationService : ComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = makeData()

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationDataSourceService.ComplicationRequestListener
    ) {
        listener.onComplicationData(makeData())
    }

    private fun makeData(): ComplicationData {
        val prefs = getSharedPreferences("wear_status", MODE_PRIVATE)
        val end = prefs.getLong("endMillis", 0L)
        val updated = prefs.getLong("updatedAt", 0L)
        val remaining = end - System.currentTimeMillis()
        val text = when {
            updated == 0L || System.currentTimeMillis() - updated > 6 * 60 * 60 * 1000L -> "Sync old"
            end <= 0L -> "No lock"
            remaining <= 0L -> "Ended"
            else -> {
                val minutes = remaining / 60000L
                if (minutes >= 1440L) "${minutes / 1440}d ${(minutes % 1440) / 60}h"
                else "${minutes / 60}h ${minutes % 60}m"
            }
        }
        val intent = Intent(this, WearMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder("Chaster lock countdown").build()
        ).setTapAction(pendingIntent).build()
    }
}
