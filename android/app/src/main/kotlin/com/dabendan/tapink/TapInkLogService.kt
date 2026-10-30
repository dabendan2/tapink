package com.dabendan.tapink

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.FileDescriptor
import java.io.PrintWriter

class TapInkLogService : Service() {

    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "TapInkLogService started for dumpsys inspection")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = intent?.getStringExtra("state")
        if (!state.isNullOrEmpty()) {
            lastState = state
        }
        val err = intent?.getStringExtra("error")
        if (!err.isNullOrEmpty()) {
            lastError = err
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        writer.println("==================================================")
        writer.println("          TAPINK NFC E-PAPER SERVICE DUMP         ")
        writer.println("==================================================")
        writer.println("Package Name  : com.dabendan.tapink")
        writer.println("Last UI State : $lastState")
        writer.println("Last Error    : $lastError")
        writer.println("--------------------------------------------------")
        writer.println("               BUFFERED DEBUG LOGS                ")
        writer.println("--------------------------------------------------")
        val logs = LogManager.getLogs()
        if (logs.isEmpty()) {
            writer.println("(empty)")
        } else {
            for (line in logs) {
                writer.println(line)
            }
        }
        writer.println("==================================================")
    }

    companion object {
        private const val TAG = "TapInkLogService"
        @Volatile var lastState: String = "IDLE"
        @Volatile var lastError: String = "NONE"
    }
}
