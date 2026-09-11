package com.chacha.jadeime.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Dns
import java.net.Proxy
import java.net.UnknownHostException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in LAN sync of already-redacted draft rows. No content is logged. */
class DraftSyncRepository(context: Context) {
    data class SyncStatus(val uploaded: Int, val pending: Int)

    private val prefs = context.applicationContext.getSharedPreferences("draft_sync", Context.MODE_PRIVATE)
    private val secret = PairingSecret(prefs)
    private val privacy = DraftPrivacySettings(context)
    init {
        if (!prefs.getBoolean("v1_consent_reset", false)) {
            val previous = prefs.getString("token", "").orEmpty()
            if (previous.isNotEmpty()) secret.write(previous)
            prefs.edit().remove("token").putBoolean("enabled", false).putBoolean("v1_consent_reset", true).apply()
        }
    }
    private val db = UserDataDatabase.create(context.applicationContext)
    private fun client(allowRemote: Boolean) = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .dns(object : Dns {
          override fun lookup(hostname: String): List<java.net.InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || (!allowRemote && addresses.any { !LanAddressPolicy.allows(it) })) {
                throw UnknownHostException("Draft sync requires a private LAN address")
            }
            return addresses
          }
        })
        .addNetworkInterceptor { chain ->
            val address = chain.connection()?.route()?.socketAddress?.address
            if (address == null || (!allowRemote && !LanAddressPolicy.allows(address))) throw java.io.IOException("Non-LAN destination blocked")
            chain.proceed(chain.request())
        }.build()
    var enabled: Boolean get() = prefs.getBoolean("enabled", false); set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    var endpoint: String get() = prefs.getString("endpoint", "") ?: ""; set(v) { prefs.edit().putString("endpoint", v.trim()).putBoolean("enabled", false).apply() }
    var token: String get() = secret.read(); set(v) { secret.write(v); enabled = false }
    var intervalMinutes: Int get() = prefs.getInt("interval_minutes", 15); set(v) { prefs.edit().putInt("interval_minutes", v.coerceIn(1, 1440)).apply() }
    var allowRemote: Boolean get() = prefs.getBoolean("allow_remote", false); set(v) { prefs.edit().putBoolean("allow_remote", v).putBoolean("enabled", false).apply() }

    suspend fun saveConfiguration(target: String, credential: String, minutes: Int, remote: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(SyncEndpoint.error(target, credential) == null)
            require(minutes in 1..1440)
            enabled = false
            token = credential
            prefs.edit().putString("endpoint", target.trim()).putInt("interval_minutes", minutes)
                .putBoolean("allow_remote", remote).commit()
        }
    }
    private val mutex = Mutex()
    private var lastAttempt = 0L
    suspend fun syncIfDue(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAttempt < intervalMinutes * 60_000L) return false
        lastAttempt = now
        return sync()
    }
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        val target = endpoint
        val credential = token
        val url = target.toHttpUrlOrNull()
        if (!enabled || !privacy.enabled || SyncEndpoint.error(target, credential) != null) return@withLock false
        // A new pairing/receiver must not inherit acknowledgements from the old one.
        val prefix = sentPrefix(target, credential)
        db.draftDao().deleteBefore(System.currentTimeMillis() - DraftRepository.THREE_HOURS_MS)
        val recent = db.draftDao().recent(System.currentTimeMillis() - DraftRepository.THREE_HOURS_MS)
        val retainedKeys = recent.map { "$prefix${it.id}" }.toSet()
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("sent_") && it !in retainedKeys }.forEach { remove(it) } }.apply()
        val rows = recent.filter { it.revision > prefs.getLong("$prefix${it.id}", 0) }.sortedBy { it.id }
        if (rows.isEmpty()) return@withLock true
        val array = JSONArray().apply { rows.forEach { put(JSONObject().put("id", it.id).put("created_at", it.createdAt).put("updated_at", it.updatedAt).put("revision", it.revision).put("app_package", it.appPackage).put("source", it.source).put("content", it.content)) } }
        runCatching {
            val request = Request.Builder().url(target).header("Authorization", "Bearer $credential").post(array.toString().toRequestBody("application/json".toMediaType())).build()
            client(allowRemote).newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    prefs.edit().apply { rows.forEach { putLong("$prefix${it.id}", it.revision) } }.apply()
                    true
                } else false
            }
        }.getOrDefault(false)
    } }

    suspend fun status(): SyncStatus = mutex.withLock {
        val now = System.currentTimeMillis()
        db.draftDao().deleteBefore(now - DraftRepository.THREE_HOURS_MS)
        val rows = db.draftDao().recent(now - DraftRepository.THREE_HOURS_MS)
        val prefix = sentPrefix(endpoint, token)
        val uploaded = rows.count { it.revision <= prefs.getLong("$prefix${it.id}", 0) }
        SyncStatus(uploaded = uploaded, pending = rows.size - uploaded)
    }

    suspend fun uploadStates(rows: List<DraftEntryRow>): Map<Long, Boolean> = mutex.withLock {
        val prefix = sentPrefix(endpoint, token)
        rows.associate { it.id to (it.revision <= prefs.getLong("$prefix${it.id}", 0)) }
    }

    /** Sends synthetic content through the real receiver path without acknowledging user drafts. */
    suspend fun testUpload(): String = withContext(Dispatchers.IO) { mutex.withLock {
        val target = endpoint
        val credential = token
        val url = target.toHttpUrlOrNull()
        SyncEndpoint.error(target, credential)?.let { return@withLock it }
        val now = System.currentTimeMillis()
        val body = JSONArray().put(
            JSONObject()
                .put("id", now)
                .put("created_at", now)
                .put("updated_at", now)
                .put("revision", 1)
                .put("app_package", "com.chacha.jadeime.sync_test")
                .put("source", "keyboard")
                .put("content", "测"),
        )
        execute(target, credential, body)
    } }

    private fun execute(target: String, credential: String, body: JSONArray): String = try {
        val request = Request.Builder().url(target)
            .header("Authorization", "Bearer $credential")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client(allowRemote).newCall(request).execute().use {
            when {
                it.isSuccessful -> "连接成功，已发送测试字“测”"
                it.code == 401 || it.code == 403 -> "密钥无效或没有权限（${it.code}）"
                it.code in 300..399 -> "接口发生重定向，请填写最终地址（${it.code}）"
                else -> "服务器返回 ${it.code}，请检查接口路径"
            }
        }
    } catch (_: javax.net.ssl.SSLException) {
        "证书校验失败，请检查 HTTPS 证书"
    } catch (_: UnknownHostException) {
        if (allowRemote) "域名无法解析，请检查网络和地址" else "地址无法解析或不属于内网；穿透域名请开启“允许公网地址”"
    } catch (_: java.io.InterruptedIOException) {
        "连接超时，请检查网络和服务端"
    } catch (_: java.io.IOException) {
        "连接失败，请检查地址、端口和网络"
    } catch (_: IllegalArgumentException) {
        "地址或密钥格式不正确"
    }

    private fun sentPrefix(target: String, credential: String): String {
        val receiver = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$target\n$credential".toByteArray()).joinToString("") { "%02x".format(it) }
        return "sent_${receiver}_"
    }
}
