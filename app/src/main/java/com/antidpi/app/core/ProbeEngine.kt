package com.antidpi.app.core

import com.antidpi.app.model.ProbeResult
import com.antidpi.app.model.ProbeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ProbeEngine {

    val DEFAULT_TARGETS = listOf(
        ProbeTarget("Discord", "https://discord.com/api/v9/gateway"),
        ProbeTarget("YouTube", "https://www.youtube.com/generate_204")
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun probeThroughSocks5(
        socksPort: Int,
        target: ProbeTarget,
        timeoutMs: Int = 5000,
        retries: Int = 2,
        isStopped: () -> Boolean = { false }
    ): ProbeResult = withContext(Dispatchers.IO) {
        val uri = try {
            URI(target.url)
        } catch (e: Exception) {
            return@withContext ProbeResult(target.name, false, null, null, e.message)
        }
        val host = uri.host ?: return@withContext ProbeResult(target.name, false, null, null, "No host")
        val scheme = uri.scheme?.lowercase() ?: "https"
        val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
        val path = buildString {
            append(if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath)
            if (!uri.rawQuery.isNullOrEmpty()) {
                append('?')
                append(uri.rawQuery)
            }
        }

        val resolvedIp = try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) {
            null
        }

        var lastError: String? = null
        for (attempt in 0 until retries) {
            if (isStopped()) break
            val startNs = System.nanoTime()
            try {
                val ok = executeProbe(socksPort, host, port, path, scheme == "https", timeoutMs)
                if (ok) {
                    val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                    return@withContext ProbeResult(target.name, true, elapsedMs, resolvedIp)
                } else {
                    lastError = "Invalid HTTP response"
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
            if (attempt < retries - 1 && !isStopped()) {
                delay(350)
            }
        }

        ProbeResult(target.name, false, null, resolvedIp, lastError)
    }

    private fun executeProbe(
        socksPort: Int,
        host: String,
        port: Int,
        path: String,
        useTls: Boolean,
        timeoutMs: Int
    ): Boolean {
        val raw = Socket()
        try {
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress("127.0.0.1", socksPort), timeoutMs)

            socks5Handshake(raw, host, port)

            if (useTls) {
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
                ssl.use { tlsSock ->
                    tlsSock.soTimeout = timeoutMs
                    val params = SSLParameters().apply {
                        serverNames = listOf(SNIHostName(host))
                    }
                    tlsSock.sslParameters = params
                    tlsSock.startHandshake()
                    return sendHttpGetAndCheck(tlsSock, host, path)
                }
            } else {
                return sendHttpGetAndCheck(raw, host, path)
            }
        } finally {
            try {
                raw.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun socks5Handshake(socket: Socket, host: String, port: Int) {
        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        // Greeting: VER=5, NMETHODS=1, NO AUTH (0x00)
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()

        val methodResp = readExact(inp, 2)
        if (methodResp[0] != 0x05.toByte() || methodResp[1] != 0x00.toByte()) {
            throw IllegalStateException("SOCKS5 auth failed: ${methodResp[1]}")
        }

        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) {
            throw IllegalArgumentException("Hostname too long for SOCKS5")
        }

        // CONNECT: VER=5, CMD=1(CONNECT), RSV=0, ATYP=3(DOMAIN), LEN, HOST, PORT(2B)
        val req = ByteArray(7 + hostBytes.size)
        req[0] = 0x05
        req[1] = 0x01
        req[2] = 0x00
        req[3] = 0x03
        req[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
        req[5 + hostBytes.size] = ((port shr 8) and 0xFF).toByte()
        req[6 + hostBytes.size] = (port and 0xFF).toByte()
        out.write(req)
        out.flush()

        val head = readExact(inp, 4)
        if (head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) {
            throw IllegalStateException("SOCKS5 connect rejected: rep=${head[1].toInt() and 0xFF}")
        }

        val addrLen = when (head[3].toInt() and 0xFF) {
            0x01 -> 4
            0x04 -> 16
            0x03 -> readExact(inp, 1)[0].toInt() and 0xFF
            else -> throw IllegalStateException("SOCKS5 unknown ATYP: ${head[3]}")
        }
        readExact(inp, addrLen + 2)
    }

    private fun sendHttpGetAndCheck(socket: Socket, host: String, path: String): Boolean {
        val req = buildString {
            append("GET ").append(path).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: ").append(USER_AGENT).append("\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        val out = socket.getOutputStream()
        out.write(req)
        out.flush()

        val buf = ByteArray(512)
        val read = socket.getInputStream().read(buf)
        if (read < 5) return false
        val prefix = String(buf, 0, 5, Charsets.US_ASCII)
        return prefix == "HTTP/"
    }

    private fun readExact(inp: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var off = 0
        while (off < count) {
            val n = inp.read(buf, off, count - off)
            if (n < 0) throw EOFException("Unexpected EOF from SOCKS5")
            off += n
        }
        return buf
    }
}
