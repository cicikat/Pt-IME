package com.chacha.jadeime.data

import org.junit.Assert.*
import org.junit.Test

class SyncEndpointTest {
    @Test fun `http lan and https tunnel are valid explicit endpoints`() {
        assertNull(SyncEndpoint.error("http://192.168.1.10:8000/v1/ime/drafts", "test-token"))
        assertNull(SyncEndpoint.error("https://receiver.example/v1/ime/drafts", "test-token"))
    }
    @Test fun `invalid endpoint and credentials produce actionable errors`() {
        for (url in listOf("", "192.168.1.2", "ftp://host/path", "https://user:password@host", "https://host/#fragment")) {
            assertNotNull(SyncEndpoint.error(url, "token"))
        }
        assertNotNull(SyncEndpoint.error("http://localhost", ""))
        assertNotNull(SyncEndpoint.error("http://localhost", "token\nInjected"))
    }
}
