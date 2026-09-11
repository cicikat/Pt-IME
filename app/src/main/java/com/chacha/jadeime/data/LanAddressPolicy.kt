package com.chacha.jadeime.data

import java.net.InetAddress

/** Local unicast plus the shared address range used by paired Tailscale peers. */
object LanAddressPolicy {
    fun allows(address: InetAddress): Boolean {
        val b = address.address.map { it.toInt() and 255 }
        return when (b.size) {
            4 -> b[0] == 10 || (b[0] == 172 && b[1] in 16..31) ||
                (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254) || b[0] == 127 ||
                (b[0] == 100 && b[1] in 64..127)
            16 -> (b[0] and 254) == 252 || (b[0] == 254 && (b[1] and 192) == 128) ||
                (b.take(15).all { it == 0 } && b[15] == 1)
            else -> false
        }
    }
}
