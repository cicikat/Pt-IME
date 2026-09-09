package com.chacha.jadeime.data

import android.content.Context

class DraftPrivacySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("draft_privacy", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
}
