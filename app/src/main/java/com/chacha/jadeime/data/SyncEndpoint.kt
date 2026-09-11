package com.chacha.jadeime.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SyncEndpoint {
    fun error(endpoint: String, token: String): String? {
        val url = endpoint.trim().toHttpUrlOrNull() ?: return "请填写完整的 http:// 或 https:// 地址"
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return "地址中不要包含用户名或密码"
        if (url.fragment != null) return "接口地址不能包含 # 片段"
        if (token.isBlank()) return "请填写配对密钥"
        if (token.any { it.code !in 32..126 }) return "配对密钥不能包含换行或非英文字符"
        return null
    }
}
