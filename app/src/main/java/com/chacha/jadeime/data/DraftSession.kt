package com.chacha.jadeime.data

/** Input activity, including uncommitted pinyin, keeps a session alive. No text here. */
class DraftSession {
    private var app: String? = null
    private var lastActivity: Long? = null
    private var generation = 0L

    fun activity(appPackage: String, now: Long): Long {
        val last = lastActivity
        if (app != appPackage || last == null || now - last >= IDLE_MS || now < last) generation++
        app = appPackage
        lastActivity = now
        return generation
    }

    fun breakSession() { app = null; lastActivity = null }

    companion object { const val IDLE_MS = 5 * 60 * 1000L }
}
