package com.antidpi.app.core

import android.content.Context
import com.antidpi.app.data.ConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections

class CiAdpiProcessManager(private val context: Context) {

    class RunningInstance(
        val port: Int,
        val process: Process,
        private val logs: MutableList<String>,
        private val readerThread: Thread
    ) {
        fun isAlive(): Boolean = process.isAlive

        fun recentOutput(): String = synchronized(logs) {
            logs.takeLast(12).joinToString("\n")
        }

        fun stop() {
            try {
                process.destroy()
                var waited = 0
                while (process.isAlive && waited < 300) {
                    Thread.sleep(30)
                    waited += 30
                }
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
            } finally {
                readerThread.interrupt()
                PortPool.release(port)
            }
        }
    }

    private fun binaryFile(): File {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        return File(libDir, "libciadpi.so")
    }

    suspend fun start(
        args: String,
        hostlistFile: File? = null,
        preferPort: Int? = null,
        noIpv6: Boolean = false,
        debugLog: Boolean = false,
        onLog: ((String) -> Unit)? = null
    ): Result<RunningInstance> = withContext(Dispatchers.IO) {
        val bin = binaryFile()
        if (!bin.exists()) {
            return@withContext Result.failure(IllegalStateException("libciadpi.so not found in ${bin.parent}"))
        }
        if (!bin.canExecute()) {
            bin.setExecutable(true)
        }

        val port = preferPort ?: PortPool.acquire()
            ?: return@withContext Result.failure(IllegalStateException("No free local SOCKS5 port"))

        val cmd = buildCommand(
            binPath = bin.absolutePath,
            port = port,
            rawArgs = args,
            hostlistFile = hostlistFile,
            noIpv6 = noIpv6,
            debugLog = debugLog
        )
        val logs = Collections.synchronizedList(mutableListOf<String>())

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            if (preferPort == null) PortPool.release(port)
            return@withContext Result.failure(e)
        }

        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(logs) {
                            if (logs.size > 200) logs.removeAt(0)
                            logs.add(line)
                        }
                        onLog?.invoke(line)
                    }
                }
            } catch (_: Exception) {
            }
        }.apply {
            isDaemon = true
            start()
        }

        val instance = RunningInstance(port, process, logs, readerThread)

        // Wait up to 1500ms for SOCKS5 port to open or detect early crash
        var ready = false
        for (i in 0 until 30) {
            if (!process.isAlive) {
                val exit = process.exitValue()
                delay(50)
                val out = instance.recentOutput()
                instance.stop()
                return@withContext Result.failure(
                    IllegalStateException("ciadpi exited ($exit)${if (out.isNotBlank()) ": $out" else ""}")
                )
            }
            if (canConnectLocal(port)) {
                ready = true
                break
            }
            delay(50)
        }

        if (!ready) {
            val out = instance.recentOutput()
            instance.stop()
            return@withContext Result.failure(
                IllegalStateException("ciadpi did not open port $port${if (out.isNotBlank()) ": $out" else ""}")
            )
        }

        Result.success(instance)
    }

    suspend fun validateArgs(args: String): Result<Unit> {
        val started = start(args = args, hostlistFile = null)
        return started.fold(
            onSuccess = { inst ->
                inst.stop()
                Result.success(Unit)
            },
            onFailure = { err ->
                Result.failure(err)
            }
        )
    }

    private fun canConnectLocal(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 150)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun buildCommand(
            binPath: String,
            port: Int,
            rawArgs: String,
            hostlistFile: File?,
            noIpv6: Boolean = false,
            debugLog: Boolean = false
        ): List<String> {
            val tokens = ConfigRepository.tokenizeArgs(rawArgs)
            val cleaned = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                when {
                    t == "-i" || t == "--ip" || t == "-p" || t == "--port" || t == "-x" || t == "--debug" -> {
                        i += 2
                        continue
                    }
                    t.startsWith("--ip=") || t.startsWith("--port=") || t.startsWith("--debug=") ||
                        t == "-D" || t == "--daemon" || t == "-X" || t == "--no-ipv6" -> {
                        i += 1
                        continue
                    }
                    else -> {
                        cleaned.add(t)
                        i += 1
                    }
                }
            }

            val cmd = mutableListOf(binPath, "-i", "127.0.0.1", "-p", port.toString())
            if (noIpv6) cmd.add("-X")
            if (debugLog) {
                cmd.add("-x")
                cmd.add("1")
            }
            if (hostlistFile != null && hostlistFile.exists()) {
                cmd.add("--hosts")
                cmd.add(hostlistFile.absolutePath)
                var idx = 0
                while (idx < cleaned.size) {
                    val tok = cleaned[idx]
                    cmd.add(tok)
                    if (tok == "-A" && idx + 1 < cleaned.size) {
                        idx++
                        cmd.add(cleaned[idx])
                        cmd.add("--hosts")
                        cmd.add(hostlistFile.absolutePath)
                    } else if (tok.startsWith("--auto=") || tok.startsWith("-A=")) {
                        cmd.add("--hosts")
                        cmd.add(hostlistFile.absolutePath)
                    }
                    idx++
                }
                return cmd
            }
            cmd.addAll(cleaned)
            return cmd
        }
    }
}
