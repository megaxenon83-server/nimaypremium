package com.omp.chat.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.omp.chat.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to `filesDir/last-crash.txt` so a crash on
 * a physical phone can be read and copied on the next launch.
 *
 * The file contains no token or message content: only build/device metadata and
 * the stack trace.
 */
object CrashReporter {

    private const val FILE_NAME = "last-crash.txt"
    private const val TAG = "OmpChatCrash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(appContext, thread, error)
            } catch (writeError: Throwable) {
                Log.e(TAG, "Could not persist crash report", writeError)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return try {
            if (file.exists() && file.length() > 0) file.readText() else null
        } catch (error: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (error: Exception) {
            // nothing to do
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("OmpChat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Time: $stamp")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(Log.getStackTraceString(error))
        }
        Log.e(TAG, report)
        File(context.filesDir, FILE_NAME).writeText(report)
    }
}
