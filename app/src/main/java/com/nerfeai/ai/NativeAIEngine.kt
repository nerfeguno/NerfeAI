package com.nerfeai.ai

class NativeAIEngine : NerfeAIEngine {

    private var ready = false

    init {
        System.loadLibrary("nerfeai")
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
            } catch (_: Exception) {
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

        return nativeGenerate(
            prompt
        )
    }

    override fun isReady(): Boolean {
        return ready
    }
}