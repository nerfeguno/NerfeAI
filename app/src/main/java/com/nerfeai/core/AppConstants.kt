package com.nerfeai.core

object AppConstants {

    const val APP_NAME = "NerfeAI"

    const val MODEL_ASSET =
        "nerfeai-model.gguf"

    const val MODEL_FILE =
        "nerfeai-model.gguf"

    const val MIN_MODEL_SIZE =
        400_000_000L

    const val CHAT_PREFERENCES =
        "nerfeai_chat_storage"

    const val CHAT_JSON_KEY =
        "chats_json"

    const val CURRENT_CHAT_KEY =
        "current_chat_id"

    const val SYSTEM_PROMPT =
        "You are NerfeAI, a helpful offline AI assistant. " +
                "Answer clearly and honestly."

    const val READY_STATUS =
        "Qwen loaded • Offline mode ready"

    const val THINKING_STATUS =
        "NerfeAI is thinking..."

    const val THINKING_MESSAGE =
        "Thinking…"
}