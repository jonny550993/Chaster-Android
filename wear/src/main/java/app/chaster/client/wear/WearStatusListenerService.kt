package app.chaster.client.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import androidx.wear.tiles.TileService

class WearStatusListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != "/lock-status") return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            getSharedPreferences("wear_status", Context.MODE_PRIVATE).edit()
                .putString("name", map.getString("name") ?: "Chaster")
                .putLong("endMillis", map.getLong("endMillis", 0L))
                .putString("status", map.getString("status") ?: "Waiting for phone")
                .putLong("updatedAt", map.getLong("updatedAt", 0L))
                .apply()
            TileService.getUpdater(this).requestUpdate(LockStatusTileService::class.java)
        }
    }
}
