package app.chaster.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Public API adapter. See https://api.chaster.app/api */
class ChasterApi(private val token: String) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val base = "https://api.chaster.app"

    fun getLocks(): List<ChasterLock> {
        val json = get("/api/locks")
        val array = findArray(json, listOf("items", "data", "locks", "results"))
            ?: throw IOException("Unexpected lock-list response. Check the current Chaster OpenAPI schema.")
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val o = item.asJsonObject
            val id = string(o, "_id", "id", "lockId")
            if (id.isBlank()) return@mapNotNull null
            ChasterLock(id, string(o, "name", "title").ifBlank { "Chaster lock" },
                string(o, "status", "state").ifBlank { "Status unavailable" },
                string(o, "endDate", "end_date", "unlockDate", "unlockAt").ifBlank { null },
                string(o, "createdAt", "created_at", "startDate").ifBlank { null })
        }
    }

    fun getProfile(): ChasterProfile {
        val json = get("/api/profile")
        val o = if (json.isJsonObject && json.asJsonObject.get("user")?.isJsonObject == true)
            json.asJsonObject.getAsJsonObject("user") else json.asJsonObject
        return ChasterProfile(string(o, "username", "name", "displayName").ifBlank { "Chaster user" },
            string(o, "email").ifBlank { null })
    }

    fun getHistory(locks: List<ChasterLock>): List<HistoryEntry> =
        locks.flatMap { lock ->
            val json = get("/api/locks/${lock.id}/history")
            val array = findArray(json, listOf("items", "data", "history", "logs", "results"))
                ?: throw IOException("Unexpected history response for lock ${lock.id}.")
            array.mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val o = item.asJsonObject
                HistoryEntry(lock.id, lock.name,
                    string(o, "title", "message", "description", "type").ifBlank { "Lock event" },
                    string(o, "createdAt", "created_at", "date", "timestamp").ifBlank { null })
            }
        }.sortedByDescending { it.date.orEmpty() }

    private fun get(path: String): JsonElement {
        val request = Request.Builder().url("$base$path").header("Authorization", "Bearer $token")
            .header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Chaster API returned HTTP ${response.code} for $path")
            if (body.isBlank()) throw IOException("Chaster API returned an empty response for $path")
            return try { JsonParser.parseString(body) }
            catch (_: Exception) { throw IOException("Chaster API returned invalid JSON for $path") }
        }
    }

    private fun findArray(json: JsonElement, keys: List<String>): JsonArray? {
        if (json.isJsonArray) return json.asJsonArray
        if (!json.isJsonObject) return null
        val obj = json.asJsonObject
        for (key in keys) {
            val value = obj.get(key)
            if (value?.isJsonArray == true) return value.asJsonArray
        }
        for ((_, value) in obj.entrySet()) if (value.isJsonObject) {
            for (key in keys) {
                val nested = value.asJsonObject.get(key)
                if (nested?.isJsonArray == true) return nested.asJsonArray
            }
        }
        return null
    }

    private fun string(obj: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.get(key)
            if (value != null && !value.isJsonNull && value.isJsonPrimitive)
                return try { value.asString } catch (_: Exception) { value.toString() }
        }
        return ""
    }
}
