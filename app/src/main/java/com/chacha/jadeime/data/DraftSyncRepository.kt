package com.chacha.jadeime.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in LAN sync of already-redacted draft rows. No content is logged. */
class DraftSyncRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("draft_sync", Context.MODE_PRIVATE)
    private val db = UserDataDatabase.create(context.applicationContext)
    private val client = OkHttpClient()
    var enabled: Boolean get() = prefs.getBoolean("enabled", false); set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    var endpoint: String get() = prefs.getString("endpoint", "") ?: ""; set(v) { prefs.edit().putString("endpoint", v.trim()).apply() }
    var token: String get() = prefs.getString("token", "") ?: ""; set(v) { prefs.edit().putString("token", v).apply() }
    var intervalMinutes: Int get() = prefs.getInt("interval_minutes", 15); set(v) { prefs.edit().putInt("interval_minutes", v.coerceIn(1, 1440)).apply() }
    private val mutex = Mutex()
    private var lastAttempt = 0L
    suspend fun syncIfDue(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAttempt < intervalMinutes * 60_000L) return false
        lastAttempt = now
        return sync()
    }
    suspend fun sync(): Boolean = mutex.withLock {
        val target = endpoint
        val credential = token
        if (!enabled || credential.isBlank() || !target.startsWith("https://", ignoreCase = true)) return@withLock false
        // A new pairing/receiver must not inherit acknowledgements from the old one.
        val receiver = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$target\n$credential".toByteArray()).joinToString("") { "%02x".format(it) }
        val prefix = "sent_${receiver}_"
        db.draftDao().deleteBefore(System.currentTimeMillis() - DraftRepository.THREE_HOURS_MS)
        val recent = db.draftDao().recent(System.currentTimeMillis() - DraftRepository.THREE_HOURS_MS)
        val retainedKeys = recent.map { "$prefix${it.id}" }.toSet()
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("sent_") && it !in retainedKeys }.forEach { remove(it) } }.apply()
        val rows = recent.filter { it.revision > prefs.getLong("$prefix${it.id}", 0) }.sortedBy { it.id }
        if (rows.isEmpty()) return@withLock true
        val array = JSONArray().apply { rows.forEach { put(JSONObject().put("id", it.id).put("created_at", it.createdAt).put("updated_at", it.updatedAt).put("revision", it.revision).put("app_package", it.appPackage).put("source", it.source).put("content", it.content)) } }
        runCatching {
            val request = Request.Builder().url(target).header("Authorization", "Bearer $credential").post(array.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    prefs.edit().apply { rows.forEach { putLong("$prefix${it.id}", it.revision) } }.apply()
                    true
                } else false
            }
        }.getOrDefault(false)
    }
}
