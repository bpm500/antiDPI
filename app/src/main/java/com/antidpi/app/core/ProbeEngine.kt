package com.antidpi.app.core

import com.antidpi.app.model.ProbeResult
import com.antidpi.app.model.ProbeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ProbeEngine {

    val DISCORD_TARGETS = listOf(
        ProbeTarget(
            name = "Discord gateway",
            url = "https://discord.com/api/v9/gateway",
            group = "Discord",
            okStatus = setOf(200),
            minBodyBytes = 10,
            bodyContains = "gateway.discord.gg"
        )
    )

    val YOUTUBE_TARGETS = listOf(
        ProbeTarget(
            name = "YT web",
            url = "https://www.youtube.com/generate_204",
            group = "YouTube",
            okStatus = setOf(204),
            minBodyBytes = 0
        ),
        ProbeTarget(
            name = "YT googlevideo",
            url = "https://redirector.googlevideo.com/generate_204",
            group = "YouTube",
            okStatus = null,
            minBodyBytes = 0
        ),
        ProbeTarget(
            name = "YT media",
            url = "https://i.ytimg.com/vi/9bZkp7q19f0/maxresdefault.jpg",
            group = "YouTube",
            okStatus = setOf(200),
            minBodyBytes = 65536
        )
    )

    val DEFAULT_TARGETS = DISCORD_TARGETS + YOUTUBE_TARGETS

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val MAX_HEADER_BYTES = 32 * 1024
    private const val MAX_CAPTURE_BYTES = 128 * 1024

    private class ProbeTrace {
        var stage: String = "connect"
        var statusCode: Int? = null
        var bodyBytes: Int = 0
    }

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
            return@withContext ProbeResult(
                targetName = target.name,
                groupName = target.group,
                url = target.url,
                ok = false,
                error = e.message ?: "Invalid URL"
            )
        }
        val host = uri.host ?: return@withContext ProbeResult(
            targetName = target.name,
            groupName = target.group,
            url = target.url,
            ok = false,
            error = "No host"
        )
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

        val ioTimeoutMs = if (target.minBodyBytes >= 65536) maxOf(timeoutMs, 8000) else timeoutMs
        var lastError: String? = null
        var lastStatus: Int? = null
        var lastBytes = 0
        var lastElapsedMs: Int? = null

        for (attempt in 0 until retries) {
            if (isStopped()) break
            val trace = ProbeTrace()
            val startNs = System.nanoTime()
            try {
                executeProbe(
                    socksPort = socksPort,
                    host = host,
                    port = port,
                    path = path,
                    useTls = (scheme == "https"),
                    connectTimeoutMs = timeoutMs,
                    ioTimeoutMs = ioTimeoutMs,
                    target = target,
                    trace = trace
                )
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                return@withContext ProbeResult(
                    targetName = target.name,
                    groupName = target.group,
                    url = target.url,
                    ok = true,
                    statusCode = trace.statusCode,
                    bodyBytes = trace.bodyBytes,
                    latencyMs = elapsedMs,
                    resolvedIp = resolvedIp
                )
            } catch (e: SocketTimeoutException) {
                lastElapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                lastStatus = trace.statusCode
                lastBytes = trace.bodyBytes
                lastError = "timeout at ${trace.stage} (${trace.bodyBytes} B)"
            } catch (e: Exception) {
                lastElapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt().coerceAtLeast(1)
                lastStatus = trace.statusCode
                lastBytes = trace.bodyBytes
                lastError = e.message ?: e.javaClass.simpleName
            }
            if (attempt < retries - 1 && !isStopped()) {
                delay(350)
            }
        }

        ProbeResult(
            targetName = target.name,
            groupName = target.group,
            url = target.url,
            ok = false,
            statusCode = lastStatus,
            bodyBytes = lastBytes,
            latencyMs = lastElapsedMs,
            resolvedIp = resolvedIp,
            error = lastError
        )
    }

    private fun executeProbe(
        socksPort: Int,
        host: String,
        port: Int,
        path: String,
        useTls: Boolean,
        connectTimeoutMs: Int,
        ioTimeoutMs: Int,
        target: ProbeTarget,
        trace: ProbeTrace
    ) {
        val raw = Socket()
        try {
            trace.stage = "socks-connect"
            raw.soTimeout = connectTimeoutMs
            raw.connect(InetSocketAddress("127.0.0.1", socksPort), connectTimeoutMs)

            trace.stage = "socks5"
            socks5Handshake(raw, host, port)

            if (useTls) {
                trace.stage = "tls"
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
                ssl.use { tlsSock ->
                    tlsSock.soTimeout = ioTimeoutMs
                    val params = SSLParameters().apply {
                        serverNames = listOf(SNIHostName(host))
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    tlsSock.sslParameters = params
                    tlsSock.startHandshake()
                    sendHttpGetAndValidate(tlsSock, host, path, target, trace)
                }
            } else {
                raw.soTimeout = ioTimeoutMs
                sendHttpGetAndValidate(raw, host, path, target, trace)
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

    private fun sendHttpGetAndValidate(
        socket: Socket,
        host: String,
        path: String,
        target: ProbeTarget,
        trace: ProbeTrace
    ) {
        trace.stage = "http-send"
        val req = buildString {
            append("GET ").append(path).append(" HTTP/1.1\r\n")
            append("Host: ").append(host).append("\r\n")
            append("User-Agent: ").append(USER_AGENT).append("\r\n")
            append("Accept: */*\r\n")
            append("Accept-Encoding: identity\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        val out = socket.getOutputStream()
        out.write(req)
        out.flush()

        trace.stage = "http-headers"
        val inp = socket.getInputStream()
        val headerRead = readHttpHeaders(inp)
        val headerLines = headerRead.headerText.split("\r\n", "\n")
        val statusLine = headerLines.firstOrNull()?.trim().orEmpty()
        if (!statusLine.startsWith("HTTP/")) {
            throw IllegalStateException("Invalid HTTP status line: '$statusLine'")
        }
        val statusCode = statusLine.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
            ?: throw IllegalStateException("Missing HTTP status code in '$statusLine'")
        trace.statusCode = statusCode

        var contentLength: Long? = null
        var isChunked = false
        for (i in 1 until headerLines.size) {
            val line = headerLines[i]
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "content-length" -> contentLength = value.toLongOrNull()
                "transfer-encoding" -> if (value.lowercase().contains("chunked")) isChunked = true
            }
        }

        trace.stage = "http-body"
        val captured = if (target.bodyContains != null) ByteArrayOutputStream() else null
        readHttpBody(
            inp = inp,
            initialOverflow = headerRead.overflow,
            statusCode = statusCode,
            contentLength = contentLength,
            isChunked = isChunked,
            captured = captured,
            trace = trace
        )

        val expectedStatuses = target.okStatus
        if (expectedStatuses != null && statusCode !in expectedStatuses) {
            throw IllegalStateException("HTTP $statusCode (expected ${expectedStatuses.joinToString("/")})")
        }
        if (trace.bodyBytes < target.minBodyBytes) {
            throw IllegalStateException("Body ${trace.bodyBytes} B < ${target.minBodyBytes} B")
        }
        val expectedSubstr = target.bodyContains
        if (expectedSubstr != null) {
            val bodyText = captured?.toByteArray()?.toString(Charsets.UTF_8).orEmpty()
            if (!bodyText.contains(expectedSubstr)) {
                throw IllegalStateException("Body missing '$expectedSubstr'")
            }
        }
    }

    private class HeaderResult(val headerText: String, val overflow: ByteArray)

    private fun readHttpHeaders(inp: InputStream): HeaderResult {
        val raw = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (raw.size() < MAX_HEADER_BYTES) {
            val n = inp.read(buf)
            if (n < 0) {
                throw EOFException("EOF before HTTP headers complete")
            }
            if (n == 0) continue
            raw.write(buf, 0, n)
            val all = raw.toByteArray()
            val crlfEnd = indexOfPattern(all, byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte()))
            if (crlfEnd >= 0) {
                val headerBytes = all.copyOfRange(0, crlfEnd)
                val overflow = all.copyOfRange(crlfEnd + 4, all.size)
                return HeaderResult(String(headerBytes, Charsets.ISO_8859_1), overflow)
            }
            val lfEnd = indexOfPattern(all, byteArrayOf('\n'.code.toByte(), '\n'.code.toByte()))
            if (lfEnd >= 0) {
                val headerBytes = all.copyOfRange(0, lfEnd)
                val overflow = all.copyOfRange(lfEnd + 2, all.size)
                return HeaderResult(String(headerBytes, Charsets.ISO_8859_1), overflow)
            }
        }
        throw IllegalStateException("HTTP headers exceed $MAX_HEADER_BYTES bytes")
    }

    private fun indexOfPattern(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..(data.size - pattern.size)) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun readHttpBody(
        inp: InputStream,
        initialOverflow: ByteArray,
        statusCode: Int,
        contentLength: Long?,
        isChunked: Boolean,
        captured: ByteArrayOutputStream?,
        trace: ProbeTrace
    ) {
        if (statusCode == 204 || statusCode == 304) {
            trace.bodyBytes = 0
            return
        }

        val stream = PrefixedInputStream(initialOverflow, inp)
        val buf = ByteArray(8192)

        fun recordChunk(chunk: ByteArray, len: Int) {
            trace.bodyBytes += len
            if (captured != null && captured.size() < MAX_CAPTURE_BYTES) {
                val toWrite = minOf(len, MAX_CAPTURE_BYTES - captured.size())
                if (toWrite > 0) captured.write(chunk, 0, toWrite)
            }
        }

        if (isChunked) {
            while (true) {
                val sizeLine = readAsciiLine(stream)
                    ?: throw EOFException("Unexpected EOF reading chunk size (${trace.bodyBytes} B)")
                val chunkHex = sizeLine.substringBefore(';').trim()
                val chunkLen = chunkHex.toIntOrNull(16)
                    ?: throw IOException("Invalid chunk size '$chunkHex'")
                if (chunkLen == 0) {
                    // Drain optional trailers up to blank line (ignore EOF on close)
                    while (true) {
                        val trailer = readAsciiLine(stream) ?: break
                        if (trailer.isEmpty()) break
                    }
                    break
                }
                var rem = chunkLen
                while (rem > 0) {
                    val n = stream.read(buf, 0, minOf(buf.size, rem))
                    if (n < 0) throw EOFException("Truncated chunk (${trace.bodyBytes} B)")
                    if (n > 0) {
                        recordChunk(buf, n)
                        rem -= n
                    }
                }
                // Consume CRLF after chunk payload
                val crlf = readAsciiLine(stream)
                if (crlf == null) throw EOFException("EOF after chunk data")
            }
            return
        }

        if (contentLength != null && contentLength >= 0L) {
            var remaining = contentLength
            while (remaining > 0L) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val n = stream.read(buf, 0, toRead)
                if (n < 0) {
                    throw EOFException("Truncated body (${trace.bodyBytes}/$contentLength B)")
                }
                if (n > 0) {
                    recordChunk(buf, n)
                    remaining -= n.toLong()
                }
            }
            return
        }

        while (true) {
            val n = stream.read(buf, 0, buf.size)
            if (n < 0) break
            if (n > 0) recordChunk(buf, n)
        }
    }

    private fun readAsciiLine(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = inp.read()
            if (b < 0) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                val len = sb.length
                if (len > 0 && sb[len - 1] == '\r') {
                    sb.setLength(len - 1)
                }
                return sb.toString()
            }
            if (sb.length < 8192) {
                sb.append(b.toChar())
            }
        }
    }

    private class PrefixedInputStream(
        private val prefix: ByteArray,
        private val tail: InputStream
    ) : InputStream() {
        private var pos = 0

        override fun read(): Int {
            if (pos < prefix.size) {
                return prefix[pos++].toInt() and 0xFF
            }
            return tail.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos < prefix.size) {
                val avail = prefix.size - pos
                val count = minOf(avail, len)
                System.arraycopy(prefix, pos, b, off, count)
                pos += count
                return count
            }
            return tail.read(b, off, len)
        }
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
