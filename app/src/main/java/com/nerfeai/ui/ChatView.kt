package com.nerfeai.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ChatView(
    private val activity: Activity,
    private val themeManager: ThemeManager
) {

    lateinit var root: FrameLayout
        private set

    lateinit var chatLayout: LinearLayout
        private set

    lateinit var scrollView: ScrollView
        private set

    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var statusText: TextView

    private lateinit var mainLayout: LinearLayout

    private var menuAction: (() -> Unit)? = null
    private var newChatAction: (() -> Unit)? = null
    private var sendAction: (() -> Unit)? = null
    private var editorSendAction: (() -> Unit)? = null
    private var textChangedAction: (() -> Unit)? = null

    fun build(): View {

        themeManager.applySystemBars()

        root =
            FrameLayout(activity).apply {
                setBackgroundColor(
                    themeManager.backgroundColor()
                )
            }

        mainLayout =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    themeManager.backgroundColor()
                )
            }

        buildTopBar()
        buildChatArea()
        buildComposer()

        root.addView(
            mainLayout,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        return root
    }

    private fun buildTopBar() {

        val topBar =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(8)
                )

                setBackgroundColor(
                    themeManager.panelColor()
                )
            }

        val menu =
            TextView(activity).apply {

                text = "☰"
                textSize = 25f
                gravity = Gravity.CENTER

                setTextColor(
                    themeManager.textColor()
                )

                setPadding(
                    dp(8),
                    dp(4),
                    dp(12),
                    dp(4)
                )

                contentDescription =
                    "Open menu"

                setOnClickListener {
                    menuAction?.invoke()
                }
            }

        val titleColumn =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        val title =
            TextView(activity).apply {

                text = "NerfeAI"
                textSize = 19f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    themeManager.textColor()
                )
            }

        statusText =
            TextView(activity).apply {

                text =
                    "Preparing offline assistant..."

                textSize = 11f

                setTextColor(
                    themeManager.secondaryTextColor()
                )

                setPadding(
                    0,
                    dp(2),
                    0,
                    0
                )
            }

        titleColumn.addView(title)
        titleColumn.addView(statusText)

        val newChat =
            TextView(activity).apply {

                text = "＋"
                textSize = 28f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    themeManager.textColor()
                )

                setPadding(
                    dp(10),
                    0,
                    dp(6),
                    0
                )

                contentDescription =
                    "New chat"

                setOnClickListener {
                    newChatAction?.invoke()
                }
            }

        topBar.addView(menu)

        topBar.addView(
            titleColumn,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        topBar.addView(newChat)

        mainLayout.addView(topBar)
    }

    private fun buildChatArea() {

        chatLayout =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(14),
                    dp(12),
                    dp(14)
                )
            }

        scrollView =
            ScrollView(activity).apply {

                isFillViewport = true
                clipToPadding = false

                setBackgroundColor(
                    themeManager.backgroundColor()
                )

                addView(chatLayout)
            }

        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )
    }

    private fun buildComposer() {

        val outer =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(8)
                )

                setBackgroundColor(
                    themeManager.backgroundColor()
                )
            }

        val composer =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(5),
                    dp(6),
                    dp(5)
                )

                background =
                    roundedDrawable(
                        if (
                            themeManager.isDarkTheme
                        ) {
                            Color.rgb(
                                48,
                                48,
                                48
                            )
                        } else {
                            Color.WHITE
                        },
                        dp(24).toFloat(),
                        if (
                            themeManager.isDarkTheme
                        ) {
                            Color.rgb(
                                75,
                                75,
                                75
                            )
                        } else {
                            Color.rgb(
                                220,
                                220,
                                220
                            )
                        }
                    )
            }

        input =
            EditText(activity).apply {

                hint =
                    "Message NerfeAI"

                setTextColor(
                    themeManager.textColor()
                )

                setHintTextColor(
                    themeManager.secondaryTextColor()
                )

                textSize = 15f

                minLines = 1
                maxLines = 5

                imeOptions =
                    EditorInfo.IME_ACTION_SEND

                setPadding(
                    dp(8),
                    dp(7),
                    dp(8),
                    dp(7)
                )

                background = null
                setSingleLine(false)

                addTextChangedListener(
                    SimpleTextWatcher {
                        textChangedAction?.invoke()
                    }
                )

                setOnEditorActionListener {
                        _,
                        actionId,
                        _ ->

                    if (
                        actionId ==
                        EditorInfo.IME_ACTION_SEND
                    ) {
                        editorSendAction?.invoke()
                        true
                    } else {
                        false
                    }
                }
            }

        sendButton =
            TextView(activity).apply {

                text = "Send"
                textSize = 13f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(Color.WHITE)

                background =
                    roundedDrawable(
                        Color.rgb(
                            16,
                            163,
                            127
                        ),
                        dp(22).toFloat()
                    )

                isEnabled = false
                alpha = 0.55f

                contentDescription =
                    "Send message"

                setPadding(
                    dp(10),
                    0,
                    dp(10),
                    0
                )

                setOnClickListener {
                    sendAction?.invoke()
                }
            }

        composer.addView(
            input,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        composer.addView(
            sendButton,
            LinearLayout.LayoutParams(
                dp(60),
                dp(42)
            ).apply {
                marginStart = dp(4)
            }
        )

        outer.addView(composer)

        val footer =
            TextView(activity).apply {

                text =
                    "NerfeAI can make mistakes. Runs locally on your device."

                textSize = 10f
                gravity = Gravity.CENTER

                setTextColor(
                    themeManager.secondaryTextColor()
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(2)
                )
            }

        outer.addView(footer)

        mainLayout.addView(outer)
    }

    fun attachDrawer(
        drawer: View
    ) {

        root.addView(
            drawer
        )
    }

    fun setMenuAction(
        action: () -> Unit
    ) {
        menuAction = action
    }

    fun setNewChatAction(
        action: () -> Unit
    ) {
        newChatAction = action
    }

    fun setSendAction(
        action: () -> Unit
    ) {
        sendAction = action
    }

    fun setEditorSendAction(
        action: () -> Unit
    ) {
        editorSendAction = action
    }

    fun setTextChangedAction(
        action: () -> Unit
    ) {
        textChangedAction = action
    }

    fun getInput(): String {
        return input.text.toString()
    }

    fun getInputView(): EditText {
        return input
    }

    fun getDraft(): String {
        return input.text.toString()
    }

    fun setDraft(
        text: String
    ) {
        input.setText(text)
        input.setSelection(
            input.text.length
        )
    }

    fun setStatus(
        text: String
    ) {
        statusText.text = text
    }

    fun setSendEnabled(
        enabled: Boolean
    ) {
        sendButton.isEnabled = enabled

        sendButton.alpha =
            if (enabled) {
                1f
            } else {
                0.55f
            }
    }

    fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }

    fun dp(
        value: Int
    ): Int {
        return themeManager.dp(value)
    }

    fun roundedDrawable(
        color: Int,
        radius: Float,
        strokeColor: Int? = null
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                radius

            if (strokeColor != null) {
                setStroke(
                    dp(1),
                    strokeColor
                )
            }
        }
    }

    private class SimpleTextWatcher(
        private val callback: () -> Unit
    ) : android.text.TextWatcher {

        override fun beforeTextChanged(
            s: CharSequence?,
            start: Int,
            count: Int,
            after: Int
        ) {
        }

        override fun onTextChanged(
            s: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            callback()
        }

        override fun afterTextChanged(
            s: android.text.Editable?
        ) {
        }
    }
}