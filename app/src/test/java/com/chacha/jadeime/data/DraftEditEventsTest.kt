package com.chacha.jadeime.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DraftEditEventsTest {
    @Test fun boundedAndRedactedWithStableSequence() {
        var events = "[]"
        for (i in 1L..300L) events = DraftEditEvents.append(events, DraftEditEvent(i, i, "delete_backward", "secret123", "requested"))
        val decoded = Json.decodeFromString<List<DraftEditEvent>>(events)
        assertEquals(256, decoded.size)
        assertEquals(45L, decoded.first().seq)
        assertEquals("secret***", decoded.last().text)
    }
    @Test fun newEditsDoNotShortenManualIntervalButWakeRealtimeIdle() {
        val manual = DraftSyncSchedule()
        manual.complete(0, 0, true, false, 900000)
        manual.edited(1000)
        assertFalse(manual.due(10000, false, 900000))
        assertTrue(manual.due(900000, false, 900000))
        val realtime = DraftSyncSchedule()
        realtime.complete(0, 0, true, true, 900000)
        realtime.edited(1000)
        assertFalse(realtime.due(5000, true, 900000))
        assertTrue(realtime.due(6000, true, 900000))
    }
    @Test fun quietContinuousAndRetryWindows() {
        val schedule = DraftSyncSchedule()
        schedule.edited(0)
        assertFalse(schedule.due(4000, true, 900000))
        assertTrue(schedule.due(5000, true, 900000))
        for (i in 1L..14L) schedule.edited(i * 1000)
        assertTrue(schedule.due(15000, true, 900000))
        schedule.complete(15000, 16000, false, true, 900000)
        assertFalse(schedule.due(75000, true, 900000))
        assertTrue(schedule.due(76000, true, 900000))
    }
}
