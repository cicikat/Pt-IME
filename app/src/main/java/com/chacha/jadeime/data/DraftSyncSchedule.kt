package com.chacha.jadeime.data

/** Small batches: 5 seconds quiet or 15 seconds of continuous typing; retry after 60 seconds. */
class DraftSyncSchedule {
    private var firstEdit: Long? = null
    private var lastEdit = 0L
    private var nextAttempt = 0L
    private var retryUntil = 0L
    @Synchronized fun edited(now: Long) {
        if (firstEdit == null) firstEdit = now
        lastEdit = now
    }
    @Synchronized fun due(now: Long, realtime: Boolean, interval: Long): Boolean {
        if (now < nextAttempt && (!realtime || firstEdit == null || now < retryUntil)) return false
        val first = firstEdit
        return if (!realtime || first == null) true else now - lastEdit >= 5_000 || now - first >= 15_000
    }
    @Synchronized fun complete(startedAt: Long, now: Long, success: Boolean, realtime: Boolean, interval: Long) {
        if (success && lastEdit <= startedAt) firstEdit = null
        retryUntil = if (success) 0 else now + 60_000
        nextAttempt = now + if (!success) 60_000 else if (realtime) {
            if (firstEdit == null) 60_000 else 2_000
        } else interval
    }
}
