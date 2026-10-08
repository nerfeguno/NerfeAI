package com.nerfeai.data

data class ChatSession(
    val id: String,
    var title: String,
    var promptHistory: String = "",
    val messages: MutableList<ChatMessage> =
        mutableListOf()
)