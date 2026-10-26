package app.chaster.client.wear

import android.content.Context
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class LockStatusTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences("wear_status", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "Chaster") ?: "Chaster"
        val end = prefs.getLong("endMillis", 0L)
        val status = prefs.getString("status", "Waiting for phone") ?: "Waiting for phone"
        val updated = prefs.getLong("updatedAt", 0L)
        val remaining = end - System.currentTimeMillis()
        val time = if (end > 0L && remaining > 0L) {
            val seconds = remaining / 1000L
            "%dd %02dh %02dm".format(seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60)
        } else if (end > 0L) "Time is up" else "No active timer"
        val stale = updated == 0L || System.currentTimeMillis() - updated > 6 * 60 * 60 * 1000L
        val column = LayoutElementBuilders.Column.Builder()
            .addContent(LayoutElementBuilders.Text.Builder().setText("Chaster").build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(name.take(28)).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(time).build())
            .addContent(LayoutElementBuilders.Text.Builder().setText(if (stale) "Sync may be old" else status).build())
            .build()
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(column).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build())
            .build()
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder().setResourcesVersion("1").setTileTimeline(timeline).build()
        )
    }
}
