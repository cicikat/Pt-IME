package com.chacha.jadeime.data

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LanAddressPolicyTest {
    @Test fun permitsPrivateRanges() {
        listOf("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.1.2", "127.0.0.1", "169.254.1.1", "::1", "fd12::1", "fe80::1")
            .forEach { assertTrue(it, LanAddressPolicy.allows(InetAddress.getByName(it))) }
    }
    @Test fun rejectsPublicAndSpecialDestinations() {
        listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "100.64.0.1", "::", "ff02::1", "2001:4860:4860::8888", "::ffff:8.8.8.8")
            .forEach { assertFalse(it, LanAddressPolicy.allows(InetAddress.getByName(it))) }
    }
}
