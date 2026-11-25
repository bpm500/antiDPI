package com.antidpi.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val TAG = "antiDPI"
    private const val MAX_LOG_BYTES = 1024L * 1024L

    private val lock = Any()
    private var logDir: File? = null
    private var appLogFile: File? = null
    private var crashLogFile: File? = null
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            logDir = dir
            appLogFile = File(dir, "app.log")
            crashLogFile = File(dir, "crash.log")
        }
    }

    fun d(tag: String, msg: String) {
        Log.d("$TAG/$tag", msg)
        append("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i("$TAG/$tag", msg)
        append("I", tag, msg)
    }

    fun w(tag: String, msg: String, err: Throwable? = null) {
        Log.w("$TAG/$tag", msg, err)
        val extra = err?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        append("W", tag, "$msg$extra")
    }

    fun e(tag: String, msg: String, err: Throwable? = null) {
        Log.e("$TAG/$tag", msg, err)
        val extra = err?.let { " | ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        append("E", tag, "$msg$extra")
    }

    fun recordCrash(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine("=== CRASH ${formatNow()} thread=${thread.name} ===")
            appendLine(sw.toString().trimEnd())
            appendLine()
        }
        Log.e("$TAG/Crash", text)
        append("F", "Crash", "Uncaught on ${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}")
        synchronized(lock) {
            val file = crashLogFile ?: return
            try {
                if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    val rotated = File(file.parentFile, "crash.log.1")
                    if (rotated.exists()) rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(text)
            } catch (_: Exception) {
            }
        }
    }

    fun readLastLines(maxLines: Int = 100): List<String> {
        synchronized(lock) {
            val file = appLogFile ?: return emptyList()
            if (!file.exists()) return emptyList()
            return try {
                val lines = file.readLines()
                if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun readCrashTail(maxLines: Int = 40): List<String> {
        synchronized(lock) {
            val file = crashLogFile ?: return emptyList()
            if (!file.exists()) return emptyList()
            return try {
                val lines = file.readLines()
                if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun append(level: String, tag: String, msg: String) {
        val line = "${formatNow()} $level/$tag: $msg\n"
        synchronized(lock) {
            val file = appLogFile ?: return
            try {
                if (file.exists() && file.length() >= MAX_LOG_BYTES) {
                    val rotated = File(file.parentFile, "app.log.1")
                    if (rotated.exists()) rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line)
            } catch (_: Exception) {
            }
        }
    }

    private fun formatNow(): String = synchronized(timeFmt) {
        timeFmt.format(Date())
    }
}
