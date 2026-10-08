package com.nerfeai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nerfeai.core.AppConstants
import com.nerfeai.data.ChatMessage

class ChatRenderer(
    private val activity: Activity,
    private val chatView: ChatView,
    private val themeManager: ThemeManager
) {

    private var renderingSavedMessages = false

    fun clear() {
        chatView.chatLayout.removeAllViews()
    }

    fun showWelcome() {

        val welcome =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    dp(20),
                    dp(26),
                    dp(20),
                    dp(26)
                )
            }

        val logo =
            TextView(activity).apply {

                text = "✳"
                textSize = 36f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        16,
                        163,
                        127
                    )
                )
            }

        val heading =
            TextView(activity).apply {

                text =
                    "How can I help you?"

                textSize = 23f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    themeManager.textColor()
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }

        val subtitle =
            TextView(activity).apply {

                text =
                    "Ask a question, learn something new, or brainstorm an idea."

                textSize = 14f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    themeManager.secondaryTextColor()
                )
            }

        welcome.addView(logo)
        welcome.addView(heading)
        welcome.addView(subtitle)

        chatView.chatLayout.addView(
            welcome
        )
    }

    fun renderMessages(
        messages: List<ChatMessage>
    ) {

        renderingSavedMessages = true

        messages.forEach { message ->

            addMessage(
                role = message.role,
                message = message.text,
                persist = false
            )
        }

        renderingSavedMessages = false

        chatView.scrollToBottom()
    }

    fun addMessage(
        role: String,
        message: String,
        persist: Boolean
    ): View {

        val isUser =
            role == "You"

        val row =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    if (isUser) {
                        Gravity.END
                    } else {
                        Gravity.START
                    }

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(7)
                )
            }

        val bubble =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(11),
                    dp(14),
                    dp(11)
                )

                background =
                    chatView.roundedDrawable(
                        when {

                            isUser &&
                                    themeManager.isDarkTheme ->
                                Color.rgb(
                                    45,
                                    75,
                                    68
                                )

                            isUser ->
                                Color.rgb(
                                    220,
                                    242,
                                    234
                                )

                            themeManager.isDarkTheme ->
                                themeManager.darkCard

                            else ->
                                themeManager.lightCard
                        },
                        dp(18).toFloat()
                    )
            }

        val roleText =
            TextView(activity).apply {

                text =
                    if (isUser) {
                        "You"
                    } else {
                        "NerfeAI"
                    }

                textSize = 12f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.rgb(
                        16,
                        163,
                        127
                    )
                )
            }

        val messageText =
            TextView(activity).apply {

                text = message

                textSize = 15f

                setTextColor(
                    themeManager.textColor()
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )

                setTextIsSelectable(true)

                setLineSpacing(
                    dp(2).toFloat(),
                    1f
                )
            }

        bubble.addView(roleText)
        bubble.addView(messageText)

        val bubbleWidth =
            if (isUser) {

                dp(300).coerceAtMost(
                    activity.resources
                        .displayMetrics
                        .widthPixels -
                            dp(80)
                )

            } else {
                -1
            }

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                bubbleWidth,
                -2
            ).apply {

                if (isUser) {
                    marginStart = dp(32)
                } else {
                    marginEnd = dp(12)
                }
            }
        )

        chatView.chatLayout.addView(row)

        chatView.scrollToBottom()

        return row
    }

    fun removeMessage(
        view: View
    ) {

        if (
            view.parent ==
            chatView.chatLayout
        ) {
            chatView.chatLayout.removeView(view)
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return chatView.dp(value)
    }
}