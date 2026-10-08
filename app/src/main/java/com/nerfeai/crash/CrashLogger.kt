package com.nerfeai.crash

import android.content.Context
import com.nerfeai.core.AppLogger
import java.io.File

class CrashLogger(
    private val context: Context
) {

    fun install() {

        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler {
                thread,
                throwable ->

            try {

                val directory =
                    File(
                        context.filesDir,
                        "crash_logs"
                    )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val timestamp =
                    System.currentTimeMillis()

                val file =
                    File(
                        directory,
                        "crash_$timestamp.txt"
                    )

                file.writeText(
                    buildString {

                        appendLine(
                            "NerfeAI Crash Report"
                        )

                        appendLine(
                            "===================="
                        )

                        appendLine()

                        appendLine(
                            "Thread: ${thread.name}"
                        )

                        appendLine()

                        appendLine(
                            "Timestamp: $timestamp"
                        )

                        appendLine()

                        appendLine(
                            "Exception:"
                        )

                        appendLine(
                            throwable.stackTraceToString()
                        )
                    }
                )

            } catch (e: Exception) {

                AppLogger.e(
                    "Crash logger failed.",
                    e
                )
            }

            previousHandler?.uncaughtException(
                thread,
                throwable
            )
        }
    }
}