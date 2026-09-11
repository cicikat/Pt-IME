package com.chacha.jadeime.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DraftEditEvent(val seq: Long, val at_ms: Long, val kind: String,
                          val text: String, val outcome: String)

object DraftEditEvents {
    fun append(previous: String, event: DraftEditEvent): String {
        val events = runCatching { Json.decodeFromString<List<DraftEditEvent>>(previous) }.getOrDefault(emptyList())
        return Json.encodeToString((events + event.copy(text = DraftRedactor.redact(event.text).takeLast(1024))).takeLast(256))
    }
}
