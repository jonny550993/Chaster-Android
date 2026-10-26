package app.chaster.client

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Sends only display-safe lock status to paired Wear OS devices.
 * Account credentials and API keys are deliberately never included.
 */
internal object WearSyncPublisher {
    private const val PATH = "/lock-status"

    fun publish(context: Context, name: String, endMillis: Long, status: String) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString("name", name.take(80))
            dataMap.putLong("endMillis", endMillis.coerceAtLeast(0L))
            dataMap.putString("status", status.take(80))
            dataMap.putLong("updatedAt", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
    }
}
