package com.uwb.ranging

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UwbRangingApp : Application() {
    companion object {
        const val TAG = "UWBRanging"
    }

    override fun onCreate() {
        super.onCreate()

        // 全局异常捕获，写入文件方便排查
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val log = buildString {
                    appendLine("=== CRASH at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())} ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine(sw.toString())
                    appendLine("=== END ===")
                }
                val logFile = File(getExternalFilesDir(null), "crash.log")
                logFile.appendText(log)
                Log.e(TAG, "CRASH: ${throwable.message}", throwable)
            } catch (e: Exception) {
                // ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "App started, crash handler installed")
    }
}
