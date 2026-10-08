package com.nerfeai.core

import android.content.Context
import android.util.Log

object AppLogger {

    private const val TAG = "NerfeAI"

    fun initialize(
        context: Context
    ) {
        Log.i(
            TAG,
            "NerfeAI initialized: ${context.packageName}"
        )
    }

    fun d(
        message: String
    ) {
        Log.d(TAG, message)
    }

    fun i(
        message: String
    ) {
        Log.i(TAG, message)
    }

    fun w(
        message: String
    ) {
        Log.w(TAG, message)
    }

    fun e(
        message: String,
        throwable: Throwable? = null
    ) {
        Log.e(
            TAG,
            message,
            throwable
        )
    }
}