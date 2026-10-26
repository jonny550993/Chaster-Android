package app.chaster.client

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.OffsetDateTime

internal object WearStatusSync {
    fun publish(context: Context, name: String, status: String, endMillis: Long?) {
        val request = PutDataMapRequest.create("/lock-status").apply {
            dataMap.putString("name", name)
            dataMap.putString("status", status)
            dataMap.putLong("endMillis", endMillis ?: 0L)
            dataMap.putLong("updatedAt", System.currentTimeMillis())
        }.setUrgent().asPutDataRequest()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
    }

    fun parseEndMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }
}
