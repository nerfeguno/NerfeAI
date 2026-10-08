package com.nerfeai.ai

interface NerfeAIEngine {

    fun loadModel(
        path: String
    ): Boolean

    fun generate(
        prompt: String
    ): String

    fun isReady(): Boolean
}