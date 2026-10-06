package com.nerfeai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.core.content.FileProvider

import org.json.JSONArray
import org.json.JSONObject

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID


class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var chatLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private lateinit var root: FrameLayout
    private lateinit var mainLayout: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var drawerScrim: View
    private lateinit var historyContainer: LinearLayout
    private lateinit var clearHistoryButton: TextView

    private var modelReady = false
    private var conversationHistory = ""
    private var isDarkTheme = true
    private var lastUserMessage = ""
    private var isRenderingMessages = false
    private var isGenerating = false

    private val savedChats = mutableListOf<ChatSession>()
    private var currentChatId = ""

    private val preferences by lazy {
        getSharedPreferences(
            "nerfeai_chat_storage",
            Context.MODE_PRIVATE
        )
    }

    private data class ChatMessage(
        val role: String,
        val text: String
    )

    private data class ChatSession(
        val id: String,
        var title: String,
        var promptHistory: String = "",
        val messages: MutableList<ChatMessage> = mutableListOf()
    )

    /*
     * Information downloaded from GitHub's update.json.
     */
    private data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkName: String,
        val apkUrl: String,
        val sha256: String,
        val releaseUrl: String
    )

    companion object {

        private const val MODEL_ASSET = "nerfeai-model.gguf"
        private const val MODEL_FILE = "nerfeai-model.gguf"

        // Long conversations are capped so the UI, JSON storage, and
        // native llama.cpp prompt cannot grow without bounds.
        private const val MAX_VISIBLE_MESSAGES = 24
        private const val MAX_SAVED_MESSAGES = 100
        private const val MAX_PROMPT_MESSAGES = 8
        private const val MAX_PROMPT_CHARS = 9000
        private const val MAX_MESSAGE_CHARS = 12000

        private val DARK_BG = Color.rgb(33, 33, 33)
        private val DARK_PANEL = Color.rgb(42, 42, 42)
        private val DARK_CARD = Color.rgb(48, 48, 48)

        private val LIGHT_BG = Color.rgb(248, 248, 248)
        private val LIGHT_PANEL = Color.WHITE
        private val LIGHT_CARD = Color.rgb(239, 239, 239)

        init {
            System.loadLibrary("nerfeai")
        }
    }

    /*
     * Native llama.cpp functions.
     */
    private external fun nativeLoadModel(path: String): Boolean

    private external fun nativeGenerate(prompt: String): String


    // ============================================================
    // Activity lifecycle
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Allow the app area to resize when the keyboard opens.
         */
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        isDarkTheme =
            savedInstanceState?.getString("theme", "dark") != "light"

        loadSavedChats()

        buildInterface()

        renderCurrentChat()

        input.setText(
            savedInstanceState?.getString("draft", "") ?: ""
        )

        prepareBundledModel()

        /*
         * Check GitHub for updates shortly after startup.
         *
         * This runs separately from model loading, so an Internet
         * problem will never prevent NerfeAI from starting.
         */
        window.decorView.postDelayed({
            checkForUpdates()
        }, 1500L)
    }


    override fun onSaveInstanceState(outState: Bundle) {

        outState.putString(
            "draft",
            if (::input.isInitialized) {
                input.text.toString()
            } else {
                ""
            }
        )

        outState.putString(
            "theme",
            if (isDarkTheme) "dark" else "light"
        )

        super.onSaveInstanceState(outState)
    }


    // ============================================================
    // Main interface
    // ============================================================

    private fun buildInterface() {

        applySystemBarColors()

        root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor())
        }

        /*
         * Android 11+ / Android 15 edge-to-edge handling.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            window.setDecorFitsSystemWindows(false)

            root.setOnApplyWindowInsetsListener { view, insets ->

                val systemBars =
                    insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                    )

                val ime =
                    insets.getInsets(
                        android.view.WindowInsets.Type.ime()
                    )

                view.setPadding(
                    0,
                    systemBars.top,
                    0,
                    maxOf(
                        systemBars.bottom,
                        ime.bottom
                    )
                )

                insets
            }

            root.post {
                root.requestApplyInsets()
            }
        }

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor())
        }


        // ========================================================
        // Top bar
        // ========================================================

        val topBar = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )

            setBackgroundColor(panelColor())
        }


        val menuButton = TextView(this).apply {

            text = "☰"

            textSize = 25f

            gravity = Gravity.CENTER

            setTextColor(textColor())

            setPadding(
                dp(8),
                dp(4),
                dp(12),
                dp(4)
            )

            setOnClickListener {
                toggleDrawer(true)
            }
        }


        val titleColumn = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_VERTICAL
        }


        val title = TextView(this).apply {

            text = "NerfeAI"

            textSize = 19f

            typeface = Typeface.DEFAULT_BOLD

            setTextColor(textColor())
        }


        statusText = TextView(this).apply {

            text = "Preparing offline assistant..."

            textSize = 11f

            setTextColor(secondaryTextColor())

            setPadding(
                0,
                dp(2),
                0,
                0
            )
        }


        titleColumn.addView(title)

        titleColumn.addView(
            statusText,
            LinearLayout.LayoutParams(-2, -2)
        )


        val newChatButton = TextView(this).apply {

            text = "＋"

            textSize = 28f

            gravity = Gravity.CENTER

            setTextColor(textColor())

            setPadding(
                dp(10),
                0,
                dp(6),
                0
            )

            contentDescription = "New chat"

            setOnClickListener {
                startNewChat()
            }
        }


        topBar.addView(menuButton)

        topBar.addView(
            titleColumn,
            LinearLayout.LayoutParams(
                0,
                -2,
                1f
            )
        )

        topBar.addView(newChatButton)


        // ========================================================
        // Chat area
        // ========================================================

        chatLayout = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(14),
                dp(12),
                dp(14)
            )
        }


        scrollView = ScrollView(this).apply {

            isFillViewport = true

            clipToPadding = false

            addView(chatLayout)

            setBackgroundColor(backgroundColor())
        }


        // ========================================================
        // Composer
        // ========================================================

        val composerOuter = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
            )

            setBackgroundColor(backgroundColor())
        }


        val composer = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(10),
                dp(5),
                dp(6),
                dp(5)
            )

            background = roundedDrawable(
                if (isDarkTheme) {
                    Color.rgb(48, 48, 48)
                } else {
                    Color.WHITE
                },
                dp(24).toFloat(),
                if (isDarkTheme) {
                    Color.rgb(75, 75, 75)
                } else {
                    Color.rgb(220, 220, 220)
                }
            )
        }


        input = EditText(this).apply {

            hint = "Message NerfeAI"

            setTextColor(textColor())

            setHintTextColor(secondaryTextColor())

            textSize = 15f

            minLines = 1

            maxLines = 5

            imeOptions = EditorInfo.IME_ACTION_SEND

            setPadding(
                dp(8),
                dp(7),
                dp(8),
                dp(7)
            )

            background = null

            setSingleLine(false)
        }


        sendButton = TextView(this).apply {

            text = "Send"

            textSize = 13f

            typeface = Typeface.DEFAULT_BOLD

            gravity = Gravity.CENTER

            setTextColor(Color.WHITE)

            background = roundedDrawable(
                Color.rgb(16, 163, 127),
                dp(22).toFloat()
            )

            isEnabled = false

            alpha = 0.55f

            contentDescription = "Send message"

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )

            setOnClickListener {
                sendMessage()
            }

            layoutParams =
                LinearLayout.LayoutParams(
                    dp(60),
                    dp(42)
                ).apply {
                    marginStart = dp(4)
                }
        }


        input.addTextChangedListener(
            SimpleTextWatcher {

                val hasText =
                    input.text
                        .toString()
                        .trim()
                        .isNotEmpty()

                sendButton.isEnabled =
                    modelReady && hasText

                sendButton.alpha =
                    if (sendButton.isEnabled) {
                        1f
                    } else {
                        0.55f
                    }
            }
        )


        input.setOnEditorActionListener {
                _,
                actionId,
                _ ->

            if (actionId == EditorInfo.IME_ACTION_SEND) {

                sendMessage()

                true

            } else {

                false
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

        composer.addView(sendButton)

        composerOuter.addView(composer)


        val footer = TextView(this).apply {

            text =
                "NerfeAI can make mistakes. Runs locally on your device. @Efren Guno"

            textSize = 10f

            gravity = Gravity.CENTER

            setTextColor(secondaryTextColor())

            setPadding(
                0,
                dp(7),
                0,
                dp(2)
            )
        }

        composerOuter.addView(footer)


        mainLayout.addView(topBar)

        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        mainLayout.addView(composerOuter)


        // ========================================================
        // Drawer scrim
        // ========================================================

        drawerScrim = View(this).apply {

            setBackgroundColor(
                0x99000000.toInt()
            )

            visibility = View.GONE

            setOnClickListener {
                toggleDrawer(false)
            }
        }


        // ========================================================
        // Drawer
        // ========================================================

        drawer = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(16),
                dp(22),
                dp(16),
                dp(16)
            )

            setBackgroundColor(panelColor())

            visibility = View.GONE
        }


        val drawerHeader = TextView(this).apply {

            text = "NerfeAI"

            textSize = 22f

            typeface = Typeface.DEFAULT_BOLD

            setTextColor(textColor())

            setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(20)
            )
        }


        val drawerNewChat =
            drawerAction("＋   New chat") {

                toggleDrawer(false)

                startNewChat()
            }


        val drawerTheme =
            drawerAction(
                if (isDarkTheme) {
                    "☀   Light appearance"
                } else {
                    "☾   Dark appearance"
                }
            ) {

                isDarkTheme = !isDarkTheme

                toggleDrawer(false)

                rebuildForTheme()
            }


        val chatsLabel = TextView(this).apply {

            text = "YOUR SPACE"

            textSize = 11f

            typeface = Typeface.DEFAULT_BOLD

            setTextColor(secondaryTextColor())

            setPadding(
                dp(5),
                dp(24),
                dp(5),
                dp(8)
            )
        }


        historyContainer = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
        }


        val historyScroll = ScrollView(this).apply {

            isFillViewport = false

            addView(historyContainer)
        }


        clearHistoryButton =
            drawerAction("Delete all chat history") {

                confirmClearAllHistory()

            }.apply {

                setTextColor(
                    if (isDarkTheme) {
                        Color.rgb(255, 130, 130)
                    } else {
                        Color.rgb(180, 40, 40)
                    }
                )
            }


        drawer.addView(drawerHeader)

        drawer.addView(drawerNewChat)

        drawer.addView(drawerTheme)

        drawer.addView(chatsLabel)

        drawer.addView(
            historyScroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        drawer.addView(clearHistoryButton)


        root.addView(
            mainLayout,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        root.addView(
            drawerScrim,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        root.addView(
            drawer,
            FrameLayout.LayoutParams(
                dp(285),
                -1,
                Gravity.START
            )
        )


        setContentView(root)
    }


    private fun drawerAction(
        label: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {

            text = label

            textSize = 15f

            setTextColor(textColor())

            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(10),
                dp(13),
                dp(10),
                dp(13)
            )

            setOnClickListener {
                action()
            }
        }
    }


    private fun toggleDrawer(show: Boolean) {

        drawer.visibility =
            if (show) {
                View.VISIBLE
            } else {
                View.GONE
            }

        drawerScrim.visibility =
            if (show) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }


    // ============================================================
    // Chat management
    // ============================================================

    private fun startNewChat() {

        if (isGenerating) {

            Toast.makeText(
                this,
                "Please wait for the current response to finish.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        currentChatId =
            UUID.randomUUID().toString()

        conversationHistory = ""

        lastUserMessage = ""

        chatLayout.removeAllViews()

        showWelcomeMessage()

        input.text.clear()

        persistChats()

        refreshHistoryList()

        toggleDrawer(false)
    }


    private fun currentChat(): ChatSession {

        var chat =
            savedChats.firstOrNull {
                it.id == currentChatId
            }

        if (chat == null) {

            chat = ChatSession(
                UUID.randomUUID().toString(),
                "New chat"
            )

            currentChatId = chat.id

            savedChats.add(0, chat)
        }

        return chat
    }


    private fun loadSavedChats() {

        savedChats.clear()

        try {

            val raw =
                preferences.getString(
                    "chats_json",
                    "[]"
                ) ?: "[]"

            val array = JSONArray(raw)

            for (i in 0 until array.length()) {

                val item =
                    array.getJSONObject(i)

                val chat = ChatSession(

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


                val startIndex =
                    (messages.length() - MAX_SAVED_MESSAGES).coerceAtLeast(0)

                for (j in startIndex until messages.length()) {

                    val message =
                        messages.getJSONObject(j)

                    chat.messages.add(
                        ChatMessage(
                            message.optString("role"),
                            message.optString("text").take(MAX_MESSAGE_CHARS)
                        )
                    )
                }


                if (chat.messages.isNotEmpty()) {
                    savedChats.add(chat)
                }
            }


            currentChatId =
                preferences.getString(
                    "current_chat_id",
                    ""
                ) ?: ""

        } catch (_: Exception) {

            savedChats.clear()

            currentChatId = ""
        }


        if (
            savedChats.none {
                it.id == currentChatId
            }
        ) {

            currentChatId =
                UUID.randomUUID().toString()
        }
    }


    private fun persistChats() {

        // Keep the persistent JSON bounded. SharedPreferences is not a database,
        // so unbounded chat history can eventually cause slow writes or crashes.
        val array = JSONArray()

        savedChats
            .filter { it.messages.isNotEmpty() }
            .take(MAX_SAVED_MESSAGES)
            .forEach { chat ->

                val item = JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("promptHistory", chat.promptHistory.take(MAX_PROMPT_CHARS))

                val messages = JSONArray()

                chat.messages
                    .takeLast(MAX_SAVED_MESSAGES)
                    .forEach { message ->
                        messages.put(
                            JSONObject()
                                .put("role", message.role)
                                .put("text", message.text.take(MAX_MESSAGE_CHARS))
                        )
                    }

                item.put("messages", messages)
                array.put(item)
            }

        preferences
            .edit()
            .putString("chats_json", array.toString())
            .putString("current_chat_id", currentChatId)
            .apply()
    }


    private fun renderCurrentChat() {

        val chat =
            savedChats.firstOrNull {
                it.id == currentChatId
            }

        conversationHistory =
            chat?.promptHistory ?: ""

        lastUserMessage =
            chat?.messages
                ?.lastOrNull { it.role == "You" }
                ?.text ?: ""

        chatLayout.removeAllViews()

        if (chat == null || chat.messages.isEmpty()) {

            showWelcomeMessage()

        } else {

            isRenderingMessages = true

            // Never recreate hundreds/thousands of TextViews when a long chat
            // is opened. The complete saved chat remains capped in storage,
            // while the UI only renders the most recent messages.
            val visibleMessages =
                chat.messages.takeLast(MAX_VISIBLE_MESSAGES)

            visibleMessages.forEach {
                addMessage(it.role, it.text)
            }

            isRenderingMessages = false
        }

        refreshHistoryList()

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }


    private fun openChat(chatId: String) {

        if (isGenerating) {

            Toast.makeText(
                this,
                "Please wait for the current response to finish.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        currentChatId = chatId

        persistChats()

        renderCurrentChat()

        toggleDrawer(false)

        scrollView.post {
            scrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }


    private fun refreshHistoryList() {

        if (!::historyContainer.isInitialized) {
            return
        }

        historyContainer.removeAllViews()


        val ordered =
            savedChats.filter {
                it.messages.isNotEmpty()
            }


        if (ordered.isEmpty()) {

            historyContainer.addView(
                TextView(this).apply {

                    text = "No saved chats yet"

                    textSize = 13f

                    setTextColor(
                        secondaryTextColor()
                    )

                    setPadding(
                        dp(8),
                        dp(10),
                        dp(8),
                        dp(10)
                    )
                }
            )
        }


        ordered.forEach { chat ->

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL
                }


            val titleButton =
                TextView(this).apply {

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

                    setTextColor(textColor())

                    setPadding(
                        dp(8),
                        dp(12),
                        dp(4),
                        dp(12)
                    )

                    maxLines = 2

                    background =
                        roundedDrawable(
                            if (
                                chat.id ==
                                currentChatId
                            ) {
                                cardColor()
                            } else {
                                panelColor()
                            },
                            dp(8).toFloat()
                        )

                    setOnClickListener {
                        openChat(chat.id)
                    }
                }


            val deleteButton =
                TextView(this).apply {

                    text = "×"

                    textSize = 22f

                    gravity = Gravity.CENTER

                    setTextColor(
                        if (isDarkTheme) {
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
                        confirmDeleteChat(
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


    private fun confirmDeleteChat(
        chatId: String,
        title: String
    ) {

        if (isGenerating) {

            Toast.makeText(
                this,
                "Please wait for the current response to finish.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        AlertDialog.Builder(this)

            .setTitle(
                "Delete conversation?"
            )

            .setMessage(
                "Delete ‘$title’? This cannot be undone."
            )

            .setNegativeButton(
                "Cancel",
                null
            )

            .setPositiveButton(
                "Delete"
            ) { _, _ ->

                savedChats.removeAll {
                    it.id == chatId
                }


                if (currentChatId == chatId) {

                    currentChatId =
                        UUID.randomUUID().toString()

                    conversationHistory = ""

                    lastUserMessage = ""

                    chatLayout.removeAllViews()

                    showWelcomeMessage()
                }


                persistChats()

                refreshHistoryList()
            }

            .show()
    }


    private fun confirmClearAllHistory() {

        if (isGenerating) {

            Toast.makeText(
                this,
                "Please wait for the current response to finish.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        AlertDialog.Builder(this)

            .setTitle(
                "Delete all chat history?"
            )

            .setMessage(
                "All saved conversations will be permanently removed from this device."
            )

            .setNegativeButton(
                "Cancel",
                null
            )

            .setPositiveButton(
                "Delete all"
            ) { _, _ ->

                savedChats.clear()

                currentChatId =
                    UUID.randomUUID().toString()

                conversationHistory = ""

                lastUserMessage = ""

                chatLayout.removeAllViews()

                showWelcomeMessage()

                persistChats()

                refreshHistoryList()

                toggleDrawer(false)
            }

            .show()
    }


    // ============================================================
    // Welcome screen
    // ============================================================

    private fun showWelcomeMessage() {
        addWelcomeCard()
    }


    private fun addWelcomeCard() {

        val welcome =
            LinearLayout(this).apply {

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
            TextView(this).apply {

                text = "✳"

                textSize = 36f

                gravity = Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        16,
                        163,
                        127
                    )
                )
            }


        val heading =
            TextView(this).apply {

                text =
                    "How can I help you?"

                textSize = 23f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity = Gravity.CENTER

                setTextColor(textColor())

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(8)
                )
            }


        val subtitle =
            TextView(this).apply {

                text =
                    "Ask a question, learn something new, or brainstorm an idea."

                textSize = 14f

                gravity = Gravity.CENTER

                setTextColor(
                    secondaryTextColor()
                )
            }


        welcome.addView(logo)

        welcome.addView(heading)

        welcome.addView(subtitle)

        chatLayout.addView(welcome)
    }


    // ============================================================
    // Theme
    // ============================================================

    private fun rebuildForTheme() {

        val savedDraft =
            input.text.toString()

        buildInterface()

        renderCurrentChat()

        input.setText(savedDraft)

        statusText.text =
            if (modelReady) {
                "Qwen loaded • Offline mode ready"
            } else {
                "Preparing offline assistant..."
            }

        sendButton.isEnabled =
            modelReady &&
                    input.text
                        .toString()
                        .trim()
                        .isNotEmpty()

        sendButton.alpha =
            if (sendButton.isEnabled) {
                1f
            } else {
                0.55f
            }
    }


    private fun backgroundColor() =
        if (isDarkTheme) {
            DARK_BG
        } else {
            LIGHT_BG
        }


    private fun panelColor() =
        if (isDarkTheme) {
            DARK_PANEL
        } else {
            LIGHT_PANEL
        }


    private fun cardColor() =
        if (isDarkTheme) {
            DARK_CARD
        } else {
            LIGHT_CARD
        }


    private fun textColor() =
        if (isDarkTheme) {
            Color.rgb(
                236,
                236,
                236
            )
        } else {
            Color.rgb(
                35,
                35,
                35
            )
        }


    private fun secondaryTextColor() =
        if (isDarkTheme) {
            Color.rgb(
                165,
                165,
                165
            )
        } else {
            Color.rgb(
                105,
                105,
                105
            )
        }


    private fun applySystemBarColors() {

        val barColor =
            if (isDarkTheme) {
                DARK_BG
            } else {
                LIGHT_BG
            }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            window.statusBarColor =
                Color.TRANSPARENT

            window.navigationBarColor =
                Color.TRANSPARENT

        } else {

            window.statusBarColor =
                barColor

            window.navigationBarColor =
                barColor
        }


        window.decorView.systemUiVisibility =
            if (isDarkTheme) {

                0

            } else {

                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
    }


    private fun roundedDrawable(
        color: Int,
        radius: Float,
        strokeColor: Int? = null
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius = radius

            if (strokeColor != null) {
                setStroke(
                    dp(1),
                    strokeColor
                )
            }
        }
    }


    private fun dp(value: Int): Int =
        (
            value *
                    resources.displayMetrics.density
            ).toInt()


    // ============================================================
    // Offline AI model
    // ============================================================

    private fun prepareBundledModel() {

        val modelFile =
            File(
                filesDir,
                MODEL_FILE
            )


        if (
            modelFile.exists() &&
            modelFile.length() > 400_000_000L
        ) {

            loadModel(modelFile)

            return
        }


        statusText.text =
            "Preparing offline model..."

        sendButton.isEnabled = false


        Thread {

            try {

                assets
                    .open(MODEL_ASSET)
                    .use { source ->

                        FileOutputStream(
                            modelFile
                        ).use { destination ->

                            source.copyTo(
                                destination
                            )
                        }
                    }


                runOnUiThread {
                    loadModel(modelFile)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "Could not prepare bundled model."

                    Toast.makeText(
                        this,
                        e.message
                            ?: "Model preparation failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }


    private fun loadModel(file: File) {

        statusText.text =
            "Loading AI model into memory..."

        sendButton.isEnabled = false


        Thread {

            val loaded =
                try {

                    nativeLoadModel(
                        file.absolutePath
                    )

                } catch (_: Exception) {

                    false
                }


            runOnUiThread {

                modelReady = loaded

                sendButton.isEnabled =
                    loaded &&
                            input.text
                                .toString()
                                .trim()
                                .isNotEmpty()

                sendButton.alpha =
                    if (sendButton.isEnabled) {
                        1f
                    } else {
                        0.55f
                    }


                if (loaded) {

                    statusText.text =
                        "Qwen loaded • Offline mode ready"

                } else {

                    statusText.text =
                        "Model loading failed."

                    Toast.makeText(
                        this,
                        "Could not load the bundled GGUF model.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        }.start()
    }


    // ============================================================
    // Chat generation
    // ============================================================

    private fun sendMessage() {

        if (isGenerating) return

        if (!modelReady) {
            Toast.makeText(
                this,
                "The AI model is still loading.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        var userText = input.text.toString().trim()

        if (userText.isEmpty()) return

        // Prevent a single pasted message from consuming the entire native
        // context or making the persisted JSON unexpectedly large.
        if (userText.length > MAX_MESSAGE_CHARS) {
            userText = userText.take(MAX_MESSAGE_CHARS)
            Toast.makeText(
                this,
                "Message was shortened to keep the conversation stable.",
                Toast.LENGTH_SHORT
            ).show()
        }

        lastUserMessage = userText
        input.text.clear()

        val keyboard =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        keyboard.hideSoftInputFromWindow(input.windowToken, 0)

        val chat = currentChat()

        // Store the user message first, then build a bounded prompt from only
        // the latest turns. This fixes the old behavior where conversationHistory
        // could grow before nativeGenerate() was called.
        chat.messages.add(ChatMessage("You", userText))

        if (chat.title == "New chat") {
            chat.title = userText
                .replace("\n", " ")
                .trim()
                .take(36)
                .ifEmpty { "New chat" }
        }

        savedChats.remove(chat)
        savedChats.add(0, chat)

        // Keep only a bounded amount of saved data.
        if (chat.messages.size > MAX_SAVED_MESSAGES) {
            repeat(chat.messages.size - MAX_SAVED_MESSAGES) {
                chat.messages.removeAt(0)
            }
        }

        conversationHistory =
            buildConversationHistory(chat.messages)

        chat.promptHistory = conversationHistory

        // Render the newly added user message directly. addMessage() is told
        // not to save it again because it is already in chat.messages.
        addMessage("You", userText, persist = false)

        val prompt = buildNativePrompt(chat.messages)

        isGenerating = true
        sendButton.isEnabled = false
        sendButton.alpha = 0.55f
        statusText.text = "NerfeAI is thinking..."

        val loadingMessage = addMessage(
            "NerfeAI",
            "Thinking…",
            persist = false
        )

        persistChats()
        refreshHistoryList()

        Thread {

            val answer = try {
                nativeGenerate(prompt)
                    .trim()
                    .ifEmpty {
                        "I couldn't generate a response. Please try again."
                    }
            } catch (e: Exception) {
                "Generation failed: ${e.message ?: "unknown error"}"
            }

            runOnUiThread {

                chatLayout.removeView(loadingMessage)

                val safeAnswer = answer.take(MAX_MESSAGE_CHARS)

                // Add the assistant response once.
                addMessage(
                    "NerfeAI",
                    safeAnswer,
                    animate = true,
                    persist = false
                )

                chat.messages.add(
                    ChatMessage("NerfeAI", safeAnswer)
                )

                if (chat.messages.size > MAX_SAVED_MESSAGES) {
                    repeat(chat.messages.size - MAX_SAVED_MESSAGES) {
                        chat.messages.removeAt(0)
                    }
                }

                conversationHistory =
                    buildConversationHistory(chat.messages)

                chat.promptHistory = conversationHistory

                persistChats()
                refreshHistoryList()

                isGenerating = false

                sendButton.isEnabled =
                    modelReady &&
                            input.text.toString().trim().isNotEmpty()

                sendButton.alpha =
                    if (sendButton.isEnabled) 1f else 0.55f

                statusText.text =
                    "Qwen loaded • Offline mode ready"
            }

        }.start()
    }


    private fun buildConversationHistory(
        messages: List<ChatMessage>
    ): String {

        val selected = mutableListOf<String>()
        var length = 0

        // Walk backwards so the newest messages are always preferred.
        messages.takeLast(MAX_PROMPT_MESSAGES).asReversed().forEach { message ->
            val role = if (message.role == "You") "user" else "assistant"
            val block =
                "<|im_start|>$role\n" +
                        message.text.take(MAX_MESSAGE_CHARS) +
                        "<|im_end|>\n"

            if (length + block.length <= MAX_PROMPT_CHARS) {
                selected.add(block)
                length += block.length
            }
        }

        return selected.asReversed().joinToString("")
    }


    private fun buildNativePrompt(
        messages: List<ChatMessage>
    ): String {

        val recent = messages.takeLast(MAX_PROMPT_MESSAGES).toMutableList()

        // Drop oldest messages until the prompt is safely below the native
        // character budget. The newest user message is always retained.
        while (recent.size > 1) {
            val history = buildConversationHistory(recent)
            val prompt =
                "<|im_start|>system\n" +
                "You are NerfeAI, a helpful offline AI assistant. Answer clearly and honestly.\n" +
                "<|im_end|>\n" +
                history +
                "<|im_start|>assistant\n"

            if (prompt.length <= MAX_PROMPT_CHARS) {
                return prompt
            }

            recent.removeAt(0)
        }

        val history = buildConversationHistory(recent)

        return (
            "<|im_start|>system\n" +
            "You are NerfeAI, a helpful offline AI assistant. Answer clearly and honestly.\n" +
            "<|im_end|>\n" +
            history +
            "<|im_start|>assistant\n"
        )
    }


    private fun trimConversationHistory() {
        // Kept for compatibility with older saved data. New prompts are built
        // directly from bounded ChatMessage objects, so this is no longer the
        // primary protection against oversized native prompts.
        conversationHistory =
            conversationHistory.takeLast(MAX_PROMPT_CHARS)
    }


    // ============================================================
    // Messages
    // ============================================================

    private fun addMessage(
        role: String,
        message: String,
        animate: Boolean = false,
        persist: Boolean = true
    ): View {

        val isUser = role == "You"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, dp(7), 0, dp(7))
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = roundedDrawable(
                when {
                    isUser && isDarkTheme -> Color.rgb(45, 75, 68)
                    isUser -> Color.rgb(220, 242, 234)
                    isDarkTheme -> DARK_CARD
                    else -> LIGHT_CARD
                },
                dp(18).toFloat()
            )
        }

        val roleText = TextView(this).apply {
            text = if (isUser) "You" else "NerfeAI"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(16, 163, 127))
        }

        val messageText = TextView(this).apply {
            text = if (animate) "" else message
            textSize = 15f
            setTextColor(textColor())
            setPadding(0, dp(4), 0, 0)
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

        bubble.addView(roleText)
        bubble.addView(messageText)

        row.addView(
            bubble,
            LinearLayout.LayoutParams(
                if (isUser) {
                    dp(300).coerceAtMost(
                        resources.displayMetrics.widthPixels - dp(80)
                    )
                } else {
                    -1
                },
                -2
            ).apply {
                if (isUser) marginStart = dp(32) else marginEnd = dp(12)
            }
        )

        chatLayout.addView(row)

        // Only mutate storage when this is a genuinely new message. Rendering
        // an existing conversation must never duplicate its messages.
        if (
            !isRenderingMessages &&
            persist &&
            message != "Thinking…"
        ) {
            val chat = currentChat()
            chat.messages.add(
                ChatMessage(role, message.take(MAX_MESSAGE_CHARS))
            )

            if (role == "You" && chat.title == "New chat") {
                chat.title = message
                    .replace("\n", " ")
                    .trim()
                    .take(36)
                    .ifEmpty { "New chat" }
            }

            if (chat.messages.size > MAX_SAVED_MESSAGES) {
                repeat(chat.messages.size - MAX_SAVED_MESSAGES) {
                    chat.messages.removeAt(0)
                }
            }

            chat.promptHistory = buildConversationHistory(chat.messages)
            persistChats()
            refreshHistoryList()
        }

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }

        if (animate && message.isNotEmpty()) {
            animateResponseText(messageText, message)
        }

        return row
    }


    // ============================================================
    // Response animation
    // ============================================================

    private fun animateResponseText(
        textView: TextView,
        fullText: String
    ) {

        val handler = Handler(Looper.getMainLooper())
        val step = 4
        val intervalMs = 25L
        var position = 0

        textView.text = ""

        val animator = object : Runnable {
            override fun run() {
                if (!textView.isAttachedToWindow) return

                position = (position + step).coerceAtMost(fullText.length)
                textView.text = fullText.substring(0, position)

                if (position < fullText.length) {
                    handler.postDelayed(this, intervalMs)
                } else {
                    scrollView.post {
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }

        handler.post(animator)
    }


    // ============================================================
    // GitHub Update Checker
    // ============================================================

    private fun checkForUpdates() {

        Thread {

            try {

                val repository =
                    BuildConfig.UPDATE_REPOSITORY


                /*
                 * Do nothing if a repository wasn't configured.
                 */
                if (
                    repository.isBlank() ||
                    repository ==
                    "YOUR_GITHUB_USERNAME/NerfeAI-Android"
                ) {

                    return@Thread
                }


                val updateUrl =
                    "https://github.com/$repository/releases/latest/download/update.json"


                val connection =
                    URL(updateUrl)
                        .openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    10000

                connection.instanceFollowRedirects =
                    true


                val responseCode =
                    connection.responseCode


                if (
                    responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {

                    connection.disconnect()

                    return@Thread
                }


                val jsonText =
                    connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }


                connection.disconnect()


                val json =
                    JSONObject(jsonText)


                val update =
                    UpdateInfo(

                        versionCode =
                            json.getInt(
                                "versionCode"
                            ),

                        versionName =
                            json.getString(
                                "versionName"
                            ),

                        apkName =
                            json.getString(
                                "apkName"
                            ),

                        apkUrl =
                            json.getString(
                                "apkUrl"
                            ),

                        sha256 =
                            json.getString(
                                "sha256"
                            ),

                        releaseUrl =
                            json.getString(
                                "releaseUrl"
                            )
                    )


                val currentVersionCode =
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.P
                    ) {

                        packageManager
                            .getPackageInfo(
                                packageName,
                                0
                            )
                            .longVersionCode
                            .toInt()

                    } else {

                        @Suppress("DEPRECATION")
                        packageManager
                            .getPackageInfo(
                                packageName,
                                0
                            )
                            .versionCode
                    }


                /*
                 * Only show the dialog when the GitHub
                 * version is newer.
                 */
                if (
                    update.versionCode <=
                    currentVersionCode
                ) {

                    return@Thread
                }


                runOnUiThread {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        showUpdateDialog(
                            update
                        )
                    }
                }

            } catch (e: Exception) {

                /*
                 * IMPORTANT:
                 *
                 * NerfeAI is offline-first.
                 *
                 * Internet/update failures must never
                 * prevent the AI from working.
                 */
                e.printStackTrace()
            }

        }.start()
    }


    // ============================================================
    // Update dialog
    // ============================================================

    private fun showUpdateDialog(
        update: UpdateInfo
    ) {

        val message =
            """
            A new version of NerfeAI is available.

            Current version:
            ${BuildConfig.VERSION_NAME}

            New version:
            ${update.versionName}

            The update will download the new APK and open the Android installer.
            """.trimIndent()


        AlertDialog.Builder(this)

            .setTitle(
                "NerfeAI Update Available"
            )

            .setMessage(message)

            .setPositiveButton(
                "Update"
            ) { _, _ ->

                downloadUpdate(update)
            }

            .setNegativeButton(
                "Later",
                null
            )

            .setCancelable(true)

            .show()
    }


    // ============================================================
    // APK download
    // ============================================================

    private fun downloadUpdate(
        update: UpdateInfo
    ) {

        val progressText =
            TextView(this).apply {

                text =
                    "Preparing update..."

                textSize = 15f

                setTextColor(
                    textColor()
                )

                setPadding(
                    dp(24),
                    dp(20),
                    dp(24),
                    dp(20)
                )
            }


        val dialog =
            AlertDialog.Builder(this)

                .setTitle(
                    "Downloading NerfeAI"
                )

                .setView(progressText)

                .setCancelable(false)

                .create()


        dialog.show()


        Thread {

            var connection:
                    HttpURLConnection? = null


            try {

                val apkFile =
                    File(
                        cacheDir,
                        update.apkName
                    )


                connection =
                    URL(update.apkUrl)
                        .openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    30000

                connection.instanceFollowRedirects =
                    true


                connection.connect()


                if (
                    connection.responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {

                    throw Exception(
                        "Download failed: HTTP ${connection.responseCode}"
                    )
                }


                val totalBytes =
                    connection.contentLengthLong


                var downloadedBytes =
                    0L


                BufferedInputStream(
                    connection.inputStream
                ).use { inputStream ->

                    FileOutputStream(
                        apkFile
                    ).use { outputStream ->

                        val buffer =
                            ByteArray(8192)


                        while (true) {

                            val count =
                                inputStream.read(
                                    buffer
                                )


                            if (count == -1) {
                                break
                            }


                            outputStream.write(
                                buffer,
                                0,
                                count
                            )


                            downloadedBytes +=
                                count


                            if (
                                totalBytes > 0
                            ) {

                                val percent =
                                    (
                                        downloadedBytes *
                                                100L /
                                                totalBytes
                                        ).toInt()


                                runOnUiThread {

                                    if (
                                        !isFinishing &&
                                        !isDestroyed
                                    ) {

                                        progressText.text =
                                            "Downloading update...\n\n$percent%"
                                    }
                                }
                            }
                        }
                    }
                }


                connection.disconnect()

                connection = null


                runOnUiThread {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        progressText.text =
                            "Verifying downloaded APK..."
                    }
                }


                /*
                 * Verify the APK before giving it
                 * to Android's package installer.
                 */
                val actualSha256 =
                    calculateSha256(
                        apkFile
                    )


                if (
                    !actualSha256.equals(
                        update.sha256,
                        ignoreCase = true
                    )
                ) {

                    apkFile.delete()

                    runOnUiThread {

                        if (
                            !isFinishing &&
                            !isDestroyed
                        ) {

                            dialog.dismiss()

                            Toast.makeText(
                                this,
                                "Update verification failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    return@Thread
                }


                runOnUiThread {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        dialog.dismiss()

                        installApk(
                            apkFile
                        )
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                connection?.disconnect()


                runOnUiThread {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {

                        dialog.dismiss()

                        Toast.makeText(
                            this,
                            "Could not download update.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

        }.start()
    }


    // ============================================================
    // SHA-256 verification
    // ============================================================

    private fun calculateSha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )


        FileInputStream(
            file
        ).use { inputStream ->

            val buffer =
                ByteArray(8192)


            while (true) {

                val count =
                    inputStream.read(
                        buffer
                    )


                if (count == -1) {
                    break
                }


                digest.update(
                    buffer,
                    0,
                    count
                )
            }
        }


        return digest
            .digest()
            .joinToString("") {

                "%02x".format(it)
            }
    }


    // ============================================================
    // Android APK installer
    // ============================================================

    @Suppress("DEPRECATION")
    private fun installApk(
        apkFile: File
    ) {

        try {

            /*
             * Android 8.0+ requires the user to allow
             * this application to install APK files.
             */
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                if (
                    !packageManager
                        .canRequestPackageInstalls()
                ) {

                    AlertDialog.Builder(this)

                        .setTitle(
                            "Allow APK Installation"
                        )

                        .setMessage(
                            """
                            Android needs permission to install the NerfeAI update.

                            On the next screen, enable:

                            "Allow from this source"

                            Then return to NerfeAI and start the update again.
                            """.trimIndent()
                        )

                        .setPositiveButton(
                            "Open Settings"
                        ) { _, _ ->

                            val intent =
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse(
                                        "package:$packageName"
                                    )
                                )

                            startActivity(intent)
                        }

                        .setNegativeButton(
                            "Cancel",
                            null
                        )

                        .show()

                    return
                }
            }


            /*
             * FileProvider creates a secure content:// URI.
             */
            val apkUri =
                FileProvider.getUriForFile(
                    this,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apkFile
                )


            val intent =
                Intent(
                    Intent.ACTION_INSTALL_PACKAGE
                ).apply {

                    data = apkUri

                    flags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK

                    putExtra(
                        Intent.EXTRA_NOT_UNKNOWN_SOURCE,
                        true
                    )
                }


            startActivity(intent)

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Could not start Android installer.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // Text watcher
    // ============================================================

    private class SimpleTextWatcher(
        private val onChanged: () -> Unit
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

            onChanged()
        }


        override fun afterTextChanged(
            s: android.text.Editable?
        ) {
        }
    }
}
