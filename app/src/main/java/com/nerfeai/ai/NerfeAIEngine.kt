package com.nerfeai.ai

import com.nerfeai.core.AppLogger

class NativeAIEngine : NerfeAIEngine {

    @Volatile
    private var ready = false

    init {
        System.loadLibrary("nerfeai")
        AppLogger.i(
            "Native llama.cpp library loaded."
        )
    }

    private external fun nativeLoadModel(
        path: String
    ): Boolean

    private external fun nativeGenerate(
        prompt: String
    ): String

    override fun loadModel(
        path: String
    ): Boolean {

        ready =
            try {
                nativeLoadModel(path)
            } catch (e: Exception) {

                AppLogger.e(
                    "Native model loading failed.",
                    e
                )

                false
            }

        return ready
    }

    override fun generate(
        prompt: String
    ): String {

        if (!ready) {
            return "Please wait for the AI model to load."
        }

        return try {
            nativeGenerate(prompt)
        } catch (e: Exception) {

            AppLogger.e(
                "Native generation failed.",
                e
            )

            "Generation failed: ${
                e.message ?: "unknown error"
            }"
        }
    }

    override fun isReady(): Boolean {
        return ready
    }
}