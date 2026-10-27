package app.chaster.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public API adapter. Endpoint paths are checked against https://api.chaster.app/api. */
class ChasterApi(private val token: String) {
    init {
        require(token.isNotBlank()) { "Chaster access token is required." }
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val base = "https://api.chaster.app"

    fun getLocks(): List<ChasterLock> {
        val json = get("/locks")
        val array = findArray(json, listOf("items", "data", "locks", "results"))
            ?: throw IOException("Unexpected lock-list response. Check the current Chaster OpenAPI schema.")
        return array.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val o = item.asJsonObject
            val id = string(o, "_id", "id", "lockId")
            if (!isValidChasterLockId(id)) return@mapNotNull null
            ChasterLock(
                id = id,
                name = string(o, "name", "title").ifBlank { "Chaster lock" },
                status = string(o, "status", "state").ifBlank { "Status unavailable" },
                endDate = string(o, "endDate", "end_date", "unlockDate", "unlockAt").takeIf { it.isNotBlank() },
                createdAt = string(o, "createdAt", "created_at", "startDate", "start_date").takeIf { it.isNotBlank() },
                minDuration = number(o, "minDuration", "minimumDuration", "min_duration"),
                maxDuration = number(o, "maxDuration", "maximumDuration", "max_duration"),
                minDate = string(o, "minDate", "min_date", "minLimitDate", "minimumDate").takeIf { it.isNotBlank() },
                maxDate = string(o, "maxDate", "max_date", "maxLimitDate", "maximumDate").takeIf { it.isNotBlank() },
                requirements = number(o, "requirements", "requirementCount", "requirementsCount")?.toInt()
                    ?: countArray(o, "requirements", "requirementLinks"),
                apiDetails = flatten(o)
            )
        }
    }

    fun addTime(lockId: String, seconds: Long) {
        validateLockId(lockId)
        require(seconds > 0) { "Duration must be positive." }
        postJson("/locks/$lockId/update-time", """{"duration":$seconds}""")
    }

    fun increaseMaximum(lockId: String, seconds: Long) {
        validateLockId(lockId)
        require(seconds > 0) { "Duration must be positive." }
        postJson("/locks/$lockId/max-limit-date", """{"duration":$seconds}""")
    }

    fun increaseMinimum(lockId: String, seconds: Long) {
        validateLockId(lockId)
        require(seconds > 0) { "Duration must be positive." }
        postJson("/locks/$lockId/min-limit-date", """{"duration":$seconds}""")
    }

    fun getProfile(): ChasterProfile {
        val json = get("/auth/profile")
        if (!json.isJsonObject) throw IOException("Unexpected profile response. Check the current Chaster OpenAPI schema.")
        val root = json.asJsonObject
        val o = if (root.get("user")?.isJsonObject == true) root.getAsJsonObject("user") else root
        return ChasterProfile(string(o, "username", "name", "displayName").ifBlank { "Chaster user" },
            string(o, "email").takeIf { it.isNotBlank() }, flatten(o))
    }

    fun getHistory(locks: List<ChasterLock>): List<HistoryEntry> =
        locks.flatMap { lock ->
            validateLockId(lock.id)
            val json = postJson("/locks/${lock.id}/history", "{}")
            val array = findArray(json, listOf("items", "data", "history", "logs", "results"))
                ?: throw IOException("Unexpected history response for lock ${lock.id}.")
            array.mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val o = item.asJsonObject
                HistoryEntry(lock.id, lock.name,
                    string(o, "title", "message", "description", "type").ifBlank { "Lock event" },
                    string(o, "createdAt", "created_at", "date", "timestamp").takeIf { it.isNotBlank() })
            }
        }.sortedByDescending { it.date.orEmpty() }

    private fun validateLockId(lockId: String) {
        require(isValidChasterLockId(lockId)) { "Invalid lock ID." }
    }

    private fun postJson(path: String, jsonBody: String): JsonElement {
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("$base$path").header("Authorization", "Bearer $token")
            .header("Accept", "application/json").post(body).build()
        return execute(request, path)
    }

    private fun get(path: String): JsonElement {
        val request = Request.Builder().url("$base$path").header("Authorization", "Bearer $token")
            .header("Accept", "application/json").get().build()
        return execute(request, path)
    }

    private fun execute(request: Request, path: String): JsonElement {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ChasterHttpException(response.code, path)
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

    private fun flatten(
        obj: JsonObject,
        prefix: String = "",
        depth: Int = 0,
        limit: Int = 500
    ): List<Pair<String, String>> {
        // API profile data is remote input. Bound recursion, entry count, and value sizes
        // so malformed/deep or unexpectedly large responses cannot exhaust the UI process.
        if (depth >= 8 || limit <= 0) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for ((key, value) in obj.entrySet()) {
            if (result.size >= limit) break
            val label = (if (prefix.isBlank()) key else "$prefix.$key").take(200)
            when {
                value.isJsonNull -> Unit
                value.isJsonObject -> result.addAll(flatten(value.asJsonObject, label, depth + 1, limit - result.size))
                value.isJsonArray -> {
                    val summary = value.asJsonArray.take(50).joinToString(", ") { element ->
                        if (element.isJsonObject || element.isJsonArray) element.toString().take(500)
                        else element.asStringOrJson().take(500)
                    }.take(2000)
                    result.add(label to summary)
                }
                else -> result.add(label to value.asStringOrJson().take(2000))
            }
        }
        return result
    }

    private fun JsonElement.asStringOrJson(): String = try { asString } catch (_: Exception) { toString() }

    private fun countArray(obj: JsonObject, vararg keys: String): Int? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonArray) return value.asJsonArray.size()
        }
        return null
    }

    private fun number(obj: JsonObject, vararg keys: String): Long? {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonNull || !value.isJsonPrimitive) continue
            val parsed = runCatching { value.asLong }.getOrNull()
            if (parsed != null) return parsed
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

internal fun isValidChasterLockId(lockId: String): Boolean =
    lockId.matches(Regex("[A-Za-z0-9_-]{1,128}"))
