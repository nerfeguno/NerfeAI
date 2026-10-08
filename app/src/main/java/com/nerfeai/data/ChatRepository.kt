package com.nerfeai.data

import android.content.Context
import com.nerfeai.core.AppConstants
import com.nerfeai.core.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatRepository(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            AppConstants.CHAT_PREFERENCES,
            Context.MODE_PRIVATE
        )

    private val chats =
        mutableListOf<ChatSession>()

    var currentChatId: String =
        ""
        private set

    fun load() {

        chats.clear()

        try {

            val raw =
                preferences.getString(
                    AppConstants.CHAT_JSON_KEY,
                    "[]"
                ) ?: "[]"

            val array =
                JSONArray(raw)

            for (
                i in 0 until array.length()
            ) {

                val item =
                    array.getJSONObject(i)

                val chat =
                    ChatSession(
                        id = item.optString(
                            "id",
                            UUID.randomUUID().toString()
                        ),
                        title = item.optString(
                            "title",
                            "Conversation"
                        ),
                        promptHistory =
                            item.optString(
                                "promptHistory",
                                ""
                            )
                    )

                val messages =
                    item.optJSONArray(
                        "messages"
                    ) ?: JSONArray()

                for (
                    j in 0 until messages.length()
                ) {

                    val message =
                        messages.getJSONObject(j)

                    chat.messages.add(
                        ChatMessage(
                            role = message.optString(
                                "role"
                            ),
                            text = message.optString(
                                "text"
                            )
                        )
                    )
                }

                if (
                    chat.messages.isNotEmpty()
                ) {
                    chats.add(chat)
                }
            }

            currentChatId =
                preferences.getString(
                    AppConstants.CURRENT_CHAT_KEY,
                    ""
                ) ?: ""

        } catch (e: Exception) {

            AppLogger.e(
                "Could not load saved chats.",
                e
            )

            chats.clear()
            currentChatId = ""
        }
    }

    fun save() {

        try {

            val array =
                JSONArray()

            chats
                .filter {
                    it.messages.isNotEmpty()
                }
                .forEach { chat ->

                    val item =
                        JSONObject()

                    item.put(
                        "id",
                        chat.id
                    )

                    item.put(
                        "title",
                        chat.title
                    )

                    item.put(
                        "promptHistory",
                        chat.promptHistory
                    )

                    val messages =
                        JSONArray()

                    chat.messages.forEach { message ->

                        messages.put(
                            JSONObject()
                                .put(
                                    "role",
                                    message.role
                                )
                                .put(
                                    "text",
                                    message.text
                                )
                        )
                    }

                    item.put(
                        "messages",
                        messages
                    )

                    array.put(item)
                }

            preferences.edit()
                .putString(
                    AppConstants.CHAT_JSON_KEY,
                    array.toString()
                )
                .putString(
                    AppConstants.CURRENT_CHAT_KEY,
                    currentChatId
                )
                .apply()

        } catch (e: Exception) {

            AppLogger.e(
                "Could not save chats.",
                e
            )
        }
    }

    fun getChats(): List<ChatSession> {
        return chats.toList()
    }

    fun getChat(
        id: String
    ): ChatSession? {
        return chats.firstOrNull {
            it.id == id
        }
    }

    fun getOrCreateChat(
        id: String
    ): ChatSession {

        val existing =
            getChat(id)

        if (existing != null) {
            return existing
        }

        val chat =
            ChatSession(
                id = id,
                title = "New chat"
            )

        chats.add(
            0,
            chat
        )

        return chat
    }

    fun setCurrentChat(
        id: String
    ) {
        currentChatId = id
    }

    fun deleteChat(
        id: String
    ) {
        chats.removeAll {
            it.id == id
        }
    }

    fun deleteAll() {
        chats.clear()
    }

    fun renameFromFirstMessage(
        chat: ChatSession,
        message: String
    ) {

        if (
            chat.title != "New chat"
        ) {
            return
        }

        chat.title =
            message
                .replace("\n", " ")
                .trim()
                .take(36)
                .ifEmpty {
                    "New chat"
                }

        chats.remove(chat)
        chats.add(
            0,
            chat
        )
    }
}