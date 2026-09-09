package com.chacha.jadeime.data

import android.content.Context
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
    private var lastId: Long get() = prefs.getLong("last_id", 0); set(v) { prefs.edit().putLong("last_id", v).apply() }
    suspend fun sync(): Boolean {
        if (!enabled || token.isBlank() || !endpoint.startsWith("https://", ignoreCase = true)) return false
        val rows = db.draftDao().after(lastId)
        if (rows.isEmpty()) return true
        val array = JSONArray().apply { rows.forEach { put(JSONObject().put("id", it.id).put("created_at", it.createdAt).put("app_package", it.appPackage).put("source", it.source).put("content", it.content)) } }
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(array.toString().toRequestBody("application/json".toMediaType())).build()
        return runCatching { client.newCall(request).execute().use { if (it.isSuccessful) { lastId = rows.last().id; true } else false } }.getOrDefault(false)
    }
}
