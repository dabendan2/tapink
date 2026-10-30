package com.dabendan.tapink

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    const val DEFAULT_TAG = "TapInk"
    const val MAX_LOG_LINES = 500

    private val logBuffer = CopyOnWriteArrayList<String>()
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    var androidLogEnabled: Boolean = true

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (androidLogEnabled) {
            try { Log.d(tag, message) } catch (_: Throwable) {}
        }
        appendLog("DEBUG", tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (androidLogEnabled) {
            try { Log.i(tag, message) } catch (_: Throwable) {}
        }
        appendLog("INFO", tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (androidLogEnabled) {
            try { Log.w(tag, message) } catch (_: Throwable) {}
        }
        appendLog("WARN", tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (androidLogEnabled) {
            try { Log.e(tag, message, throwable) } catch (_: Throwable) {}
        }
        val detail = if (throwable != null) {
            "$message | ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else {
            message
        }
        appendLog("ERROR", tag, detail)
    }

    private fun appendLog(level: String, tag: String, message: String) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val line = "[$timestamp] [$level/$tag] $message"
        logBuffer.add(line)
        while (logBuffer.size > MAX_LOG_LINES) {
            logBuffer.removeAt(0)
        }
        for (listener in listeners) {
            try {
                listener(line)
            } catch (_: Throwable) {
            }
        }
    }

    fun getLogs(): List<String> = logBuffer.toList()

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    fun clearLogs() {
        logBuffer.clear()
    }
}
