package com.nerfeai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nerfeai.data.ChatSession

class DrawerView(
    private val activity: Activity,
    private val chatView: ChatView,
    private val themeManager: ThemeManager,
    private val onNewChat: () -> Unit,
    private val onThemeChanged: () -> Unit,
    private val onChatSelected: (String) -> Unit,
    private val onDeleteChat: (String, String) -> Unit,
    private val onDeleteAll: () -> Unit,
    private val isDarkThemeProvider: () -> Boolean
) {

    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var historyContainer: LinearLayout

    fun build(): View {

        val container =
            FrameLayout(activity)

        scrim =
            View(activity).apply {

                setBackgroundColor(
                    0x99000000.toInt()
                )

                visibility =
                    View.GONE

                setOnClickListener {
                    toggle(false)
                }
            }

        drawer =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(22),
                    dp(16),
                    dp(16)
                )

                setBackgroundColor(
                    themeManager.panelColor()
                )

                visibility =
                    View.GONE
            }

        val header =
            TextView(activity).apply {

                text = "NerfeAI"
                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    themeManager.textColor()
                )

                setPadding(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(20)
                )
            }

        drawer.addView(header)

        drawer.addView(
            action(
                "＋   New chat"
            ) {
                toggle(false)
                onNewChat()
            }
        )

        drawer.addView(
            action(
                if (isDarkThemeProvider()) {
                    "☀   Light appearance"
                } else {
                    "☾   Dark appearance"
                }
            ) {
                toggle(false)
                onThemeChanged()
            }
        )

        val label =
            TextView(activity).apply {

                text = "YOUR SPACE"
                textSize = 11f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    themeManager.secondaryTextColor()
                )

                setPadding(
                    dp(5),
                    dp(24),
                    dp(5),
                    dp(8)
                )
            }

        drawer.addView(label)

        historyContainer =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL
            }

        val historyScroll =
            ScrollView(activity).apply {
                addView(historyContainer)
            }

        drawer.addView(
            historyScroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        val deleteAll =
            action(
                "Delete all chat history"
            ) {
                onDeleteAll()
            }

        deleteAll.setTextColor(
            if (themeManager.isDarkTheme) {
                Color.rgb(
                    255,
                    130,
                    130
                )
            } else {
                Color.rgb(
                    180,
                    40,
                    40
                )
            }
        )

        drawer.addView(deleteAll)

        container.addView(
            scrim,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        container.addView(
            drawer,
            FrameLayout.LayoutParams(
                dp(285),
                -1,
                Gravity.START
            )
        )

        return container
    }

    fun toggle(
        show: Boolean
    ) {

        if (!::drawer.isInitialized) {
            return
        }

        drawer.visibility =
            if (show) {
                View.VISIBLE
            } else {
                View.GONE
            }

        scrim.visibility =
            if (show) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    fun refreshHistory(
        chats: List<ChatSession>,
        currentChatId: String
    ) {

        if (!::historyContainer.isInitialized) {
            return
        }

        historyContainer.removeAllViews()

        val ordered =
            chats.filter {
                it.messages.isNotEmpty()
            }

        if (ordered.isEmpty()) {

            historyContainer.addView(
                TextView(activity).apply {

                    text =
                        "No saved chats yet"

                    textSize = 13f

                    setTextColor(
                        themeManager.secondaryTextColor()
                    )

                    setPadding(
                        dp(8),
                        dp(10),
                        dp(8),
                        dp(10)
                    )
                }
            )

            return
        }

        ordered.forEach { chat ->

            val row =
                LinearLayout(activity).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL
                }

            val titleButton =
                TextView(activity).apply {

                    text =
                        (
                            if (
                                chat.id ==
                                currentChatId
                            ) {
                                "●  "
                            } else {
                                "   "
                            }
                        ) + chat.title

                    textSize = 13f

                    setTextColor(
                        themeManager.textColor()
                    )

                    setPadding(
                        dp(8),
                        dp(12),
                        dp(4),
                        dp(12)
                    )

                    maxLines = 2

                    background =
                        chatView.roundedDrawable(
                            if (
                                chat.id ==
                                currentChatId
                            ) {
                                themeManager.cardColor()
                            } else {
                                themeManager.panelColor()
                            },
                            dp(8).toFloat()
                        )

                    setOnClickListener {
                        onChatSelected(chat.id)
                    }
                }

            val deleteButton =
                TextView(activity).apply {

                    text = "×"
                    textSize = 22f

                    gravity =
                        Gravity.CENTER

                    setTextColor(
                        if (
                            themeManager.isDarkTheme
                        ) {
                            Color.rgb(
                                255,
                                130,
                                130
                            )
                        } else {
                            Color.rgb(
                                180,
                                40,
                                40
                            )
                        }
                    )

                    setPadding(
                        dp(10),
                        dp(6),
                        dp(10),
                        dp(6)
                    )

                    contentDescription =
                        "Delete ${chat.title}"

                    setOnClickListener {
                        onDeleteChat(
                            chat.id,
                            chat.title
                        )
                    }
                }

            row.addView(
                titleButton,
                LinearLayout.LayoutParams(
                    0,
                    -2,
                    1f
                )
            )

            row.addView(deleteButton)

            historyContainer.addView(row)
        }
    }

    private fun action(
        text: String,
        callback: () -> Unit
    ): TextView {

        return TextView(activity).apply {

            this.text = text

            textSize = 15f

            setTextColor(
                themeManager.textColor()
            )

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(10),
                dp(13),
                dp(10),
                dp(13)
            )

            setOnClickListener {
                callback()
            }
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return themeManager.dp(value)
    }
}