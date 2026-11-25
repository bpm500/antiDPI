package com.antidpi.app.core

import java.net.InetAddress
import java.net.ServerSocket

object PortPool {
    private const val PORT_MIN = 10820
    private const val PORT_MAX = 10990

    private val lock = Any()
    private val inUse = mutableSetOf<Int>()
    private var cursor = PORT_MIN

    fun acquire(): Int? {
        synchronized(lock) {
            val total = PORT_MAX - PORT_MIN + 1
            for (offset in 0 until total) {
                val port = PORT_MIN + ((cursor - PORT_MIN + offset) % total)
                if (port in inUse) continue
                if (isPortAvailable(port)) {
                    inUse.add(port)
                    cursor = if (port >= PORT_MAX) PORT_MIN else port + 1
                    return port
                }
            }
            return null
        }
    }

    fun release(port: Int) {
        synchronized(lock) {
            inUse.remove(port)
        }
    }

    fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use { ss ->
                ss.reuseAddress = true
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
