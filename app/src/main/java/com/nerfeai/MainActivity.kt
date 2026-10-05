package com.nerfeai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File
import java.io.FileOutputStream

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
    private val preferences by lazy { getSharedPreferences("nerfeai_chat_storage", Context.MODE_PRIVATE) }

    private data class ChatMessage(val role: String, val text: String)
    private data class ChatSession(
        val id: String,
        var title: String,
        var promptHistory: String = "",
        val messages: MutableList<ChatMessage> = mutableListOf()
    )

    companion object {
        private const val MODEL_ASSET = "nerfeai-model.gguf"
        private const val MODEL_FILE = "nerfeai-model.gguf"

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

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the available app area to resize when the keyboard opens.
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        isDarkTheme = savedInstanceState?.getString("theme", "dark") != "light"
        loadSavedChats()
        buildInterface()
        renderCurrentChat()
        input.setText(savedInstanceState?.getString("draft", "") ?: "")
        prepareBundledModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", if (::input.isInitialized) input.text.toString() else "")
        outState.putString("theme", if (isDarkTheme) "dark" else "light")
        super.onSaveInstanceState(outState)
    }

    private fun buildInterface() {
        applySystemBarColors()

        root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor())
        }

        // Android 15+ enforces edge-to-edge for many target SDK configurations.
        // On Android 11+ we handle system-bar and keyboard insets ourselves so
        // the title bar stays below the status bar and the composer stays above
        // the keyboard. Older Android versions continue using adjustResize.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                view.setPadding(
                    0,
                    systemBars.top,
                    0,
                    maxOf(systemBars.bottom, ime.bottom)
                )
                insets
            }
            root.post { root.requestApplyInsets() }
        }

        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor())
        }

        // Top app bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(panelColor())
        }

        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(textColor())
            setPadding(dp(8), dp(4), dp(12), dp(4))
            setOnClickListener { toggleDrawer(true) }
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
            setPadding(0, dp(2), 0, 0)
        }

        titleColumn.addView(title)
        titleColumn.addView(statusText, LinearLayout.LayoutParams(-2, -2))

        val newChatButton = TextView(this).apply {
            text = "＋"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(textColor())
            setPadding(dp(10), 0, dp(6), 0)
            contentDescription = "New chat"
            setOnClickListener { startNewChat() }
        }

        topBar.addView(menuButton)
        topBar.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(newChatButton)

        // Chat area
        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(chatLayout)
            setBackgroundColor(backgroundColor())
        }

        // Bottom composer, styled like a rounded ChatGPT input
        val composerOuter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(backgroundColor())
        }

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(6), dp(5))
            background = roundedDrawable(
                if (isDarkTheme) Color.rgb(48, 48, 48) else Color.WHITE,
                dp(24).toFloat(),
                if (isDarkTheme) Color.rgb(75, 75, 75) else Color.rgb(220, 220, 220)
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
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = null
            setSingleLine(false)
        }

        sendButton = TextView(this).apply {
            // Use a plain text label instead of a special arrow glyph that may
            // render incorrectly on some Android fonts/devices.
            text = "Send"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedDrawable(Color.rgb(16, 163, 127), dp(22).toFloat())
            isEnabled = false
            alpha = 0.55f
            contentDescription = "Send message"
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { sendMessage() }
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(42)).apply {
                marginStart = dp(4)
            }
        }

        input.addTextChangedListener(SimpleTextWatcher {
            val hasText = input.text.toString().trim().isNotEmpty()
            sendButton.isEnabled = modelReady && hasText
            sendButton.alpha = if (sendButton.isEnabled) 1f else 0.55f
        })

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(sendButton)
        composerOuter.addView(composer)

        val footer = TextView(this).apply {
            text = "NerfeAI can make mistakes. Runs locally on your device."
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(secondaryTextColor())
            setPadding(0, dp(7), 0, dp(2))
        }
        composerOuter.addView(footer)

        mainLayout.addView(topBar)
        mainLayout.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        mainLayout.addView(composerOuter)

        // Lightweight custom side panel, no extra AndroidX dependency needed.
        drawerScrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            setOnClickListener { toggleDrawer(false) }
        }

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(16))
            setBackgroundColor(panelColor())
            visibility = View.GONE
        }

        val drawerHeader = TextView(this).apply {
            text = "NerfeAI"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor())
            setPadding(dp(4), dp(4), dp(4), dp(20))
        }

        val drawerNewChat = drawerAction("＋   New chat") { 
            toggleDrawer(false)
            startNewChat()
        }
        val drawerTheme = drawerAction(
            if (isDarkTheme) "☀   Light appearance" else "☾   Dark appearance"
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
            setPadding(dp(5), dp(24), dp(5), dp(8))
        }

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val historyScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(historyContainer)
        }

        clearHistoryButton = drawerAction("Delete all chat history") {
            confirmClearAllHistory()
        }.apply {
            setTextColor(if (isDarkTheme) Color.rgb(255, 130, 130) else Color.rgb(180, 40, 40))
        }

        drawer.addView(drawerHeader)
        drawer.addView(drawerNewChat)
        drawer.addView(drawerTheme)
        drawer.addView(chatsLabel)
        drawer.addView(historyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        drawer.addView(clearHistoryButton)

        root.addView(mainLayout, FrameLayout.LayoutParams(-1, -1))
        root.addView(drawerScrim, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            drawer,
            FrameLayout.LayoutParams(dp(285), -1, Gravity.START)
        )

        setContentView(root)
    }

    private fun drawerAction(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(textColor())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(13), dp(10), dp(13))
            setOnClickListener { action() }
        }
    }

    private fun toggleDrawer(show: Boolean) {
        drawer.visibility = if (show) View.VISIBLE else View.GONE
        drawerScrim.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun startNewChat() {
        if (isGenerating) {
            Toast.makeText(this, "Please wait for the current response to finish.", Toast.LENGTH_SHORT).show()
            return
        }
        currentChatId = UUID.randomUUID().toString()
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
        var chat = savedChats.firstOrNull { it.id == currentChatId }
        if (chat == null) {
            chat = ChatSession(UUID.randomUUID().toString(), "New chat")
            currentChatId = chat.id
            savedChats.add(0, chat)
        }
        return chat
    }

    private fun loadSavedChats() {
        savedChats.clear()
        try {
            val raw = preferences.getString("chats_json", "[]") ?: "[]"
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val chat = ChatSession(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    title = item.optString("title", "Conversation"),
                    promptHistory = item.optString("promptHistory", "")
                )
                val messages = item.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until messages.length()) {
                    val message = messages.getJSONObject(j)
                    chat.messages.add(ChatMessage(message.optString("role"), message.optString("text")))
                }
                if (chat.messages.isNotEmpty()) savedChats.add(chat)
            }
            currentChatId = preferences.getString("current_chat_id", "") ?: ""
        } catch (_: Exception) {
            savedChats.clear()
            currentChatId = ""
        }
        if (savedChats.none { it.id == currentChatId }) {
            currentChatId = UUID.randomUUID().toString()
        }
    }

    private fun persistChats() {
        val array = JSONArray()
        savedChats.filter { it.messages.isNotEmpty() }.forEach { chat ->
            val item = JSONObject()
            item.put("id", chat.id)
            item.put("title", chat.title)
            item.put("promptHistory", chat.promptHistory)
            val messages = JSONArray()
            chat.messages.forEach { message ->
                messages.put(JSONObject().put("role", message.role).put("text", message.text))
            }
            item.put("messages", messages)
            array.put(item)
        }
        preferences.edit()
            .putString("chats_json", array.toString())
            .putString("current_chat_id", currentChatId)
            .apply()
    }

    private fun renderCurrentChat() {
        val chat = savedChats.firstOrNull { it.id == currentChatId }
        conversationHistory = chat?.promptHistory ?: ""
        lastUserMessage = chat?.messages?.lastOrNull { it.role == "You" }?.text ?: ""
        chatLayout.removeAllViews()
        if (chat == null || chat.messages.isEmpty()) {
            showWelcomeMessage()
        } else {
            isRenderingMessages = true
            chat.messages.forEach { addMessage(it.role, it.text) }
            isRenderingMessages = false
        }
        refreshHistoryList()
    }

    private fun openChat(chatId: String) {
        if (isGenerating) {
            Toast.makeText(this, "Please wait for the current response to finish.", Toast.LENGTH_SHORT).show()
            return
        }
        currentChatId = chatId
        persistChats()
        renderCurrentChat()
        toggleDrawer(false)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun refreshHistoryList() {
        if (!::historyContainer.isInitialized) return
        historyContainer.removeAllViews()
        val ordered = savedChats.filter { it.messages.isNotEmpty() }
        if (ordered.isEmpty()) {
            historyContainer.addView(TextView(this).apply {
                text = "No saved chats yet"
                textSize = 13f
                setTextColor(secondaryTextColor())
                setPadding(dp(8), dp(10), dp(8), dp(10))
            })
        }
        ordered.forEach { chat ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleButton = TextView(this).apply {
                text = (if (chat.id == currentChatId) "●  " else "   ") + chat.title
                textSize = 13f
                setTextColor(textColor())
                setPadding(dp(8), dp(12), dp(4), dp(12))
                maxLines = 2
                background = roundedDrawable(
                    if (chat.id == currentChatId) cardColor() else panelColor(), dp(8).toFloat()
                )
                setOnClickListener { openChat(chat.id) }
            }
            val deleteButton = TextView(this).apply {
                text = "×"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(if (isDarkTheme) Color.rgb(255, 130, 130) else Color.rgb(180, 40, 40))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                contentDescription = "Delete ${chat.title}"
                setOnClickListener { confirmDeleteChat(chat.id, chat.title) }
            }
            row.addView(titleButton, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(deleteButton)
            historyContainer.addView(row)
        }
    }

    private fun confirmDeleteChat(chatId: String, title: String) {
        if (isGenerating) {
            Toast.makeText(this, "Please wait for the current response to finish.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete conversation?")
            .setMessage("Delete ‘$title’? This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                savedChats.removeAll { it.id == chatId }
                if (currentChatId == chatId) {
                    currentChatId = UUID.randomUUID().toString()
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
            Toast.makeText(this, "Please wait for the current response to finish.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete all chat history?")
            .setMessage("All saved conversations will be permanently removed from this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete all") { _, _ ->
                savedChats.clear()
                currentChatId = UUID.randomUUID().toString()
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

    private fun showWelcomeMessage() {
        addWelcomeCard()
    }

    private fun addWelcomeCard() {
        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(26), dp(20), dp(26))
        }

        val logo = TextView(this).apply {
            text = "✳"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(16, 163, 127))
        }

        val heading = TextView(this).apply {
            text = "How can I help you?"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor())
            setPadding(0, dp(8), 0, dp(8))
        }

        val subtitle = TextView(this).apply {
            text = "Ask a question, learn something new, or brainstorm an idea."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(secondaryTextColor())
        }

        welcome.addView(logo)
        welcome.addView(heading)
        welcome.addView(subtitle)
        chatLayout.addView(welcome)
    }

    private fun rebuildForTheme() {
        val savedDraft = input.text.toString()
        buildInterface()
        renderCurrentChat()
        input.setText(savedDraft)
        statusText.text = if (modelReady) "Qwen loaded • Offline mode ready" else "Preparing offline assistant..."
        sendButton.isEnabled = modelReady && input.text.toString().trim().isNotEmpty()
        sendButton.alpha = if (sendButton.isEnabled) 1f else 0.55f
    }

    private fun backgroundColor() = if (isDarkTheme) DARK_BG else LIGHT_BG
    private fun panelColor() = if (isDarkTheme) DARK_PANEL else LIGHT_PANEL
    private fun cardColor() = if (isDarkTheme) DARK_CARD else LIGHT_CARD
    private fun textColor() = if (isDarkTheme) Color.rgb(236, 236, 236) else Color.rgb(35, 35, 35)
    private fun secondaryTextColor() = if (isDarkTheme) Color.rgb(165, 165, 165) else Color.rgb(105, 105, 105)

    private fun applySystemBarColors() {
        val barColor = if (isDarkTheme) DARK_BG else LIGHT_BG

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The root view draws behind the bars; its insets padding keeps the
            // app content clear of the status bar, navigation bar, and IME.
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
        }

        window.decorView.systemUiVisibility =
            if (isDarkTheme) 0 else
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    private fun roundedDrawable(color: Int, radius: Float, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun prepareBundledModel() {
        val modelFile = File(filesDir, MODEL_FILE)

        if (modelFile.exists() && modelFile.length() > 400_000_000L) {
            loadModel(modelFile)
            return
        }

        statusText.text = "Preparing offline model..."
        sendButton.isEnabled = false

        Thread {
            try {
                assets.open(MODEL_ASSET).use { source ->
                    FileOutputStream(modelFile).use { destination ->
                        source.copyTo(destination)
                    }
                }

                runOnUiThread { loadModel(modelFile) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Could not prepare bundled model."
                    Toast.makeText(
                        this,
                        e.message ?: "Model preparation failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun loadModel(file: File) {
        statusText.text = "Loading AI model into memory..."
        sendButton.isEnabled = false

        Thread {
            val loaded = try {
                nativeLoadModel(file.absolutePath)
            } catch (_: Exception) {
                false
            }

            runOnUiThread {
                modelReady = loaded
                sendButton.isEnabled = loaded && input.text.toString().trim().isNotEmpty()
                sendButton.alpha = if (sendButton.isEnabled) 1f else 0.55f

                if (loaded) {
                    statusText.text = "Qwen loaded • Offline mode ready"
                } else {
                    statusText.text = "Model loading failed."
                    Toast.makeText(
                        this,
                        "Could not load the bundled GGUF model.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun sendMessage() {
        if (isGenerating) return
        if (!modelReady) {
            Toast.makeText(this, "The AI model is still loading.", Toast.LENGTH_SHORT).show()
            return
        }

        val userText = input.text.toString().trim()
        if (userText.isEmpty()) return

        lastUserMessage = userText
        input.text.clear()

        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)

        conversationHistory += "<|im_start|>user\n$userText<|im_end|>\n"
        currentChat().promptHistory = conversationHistory
        addMessage("You", userText)

        val prompt =
            "<|im_start|>system\n" +
            "You are NerfeAI, a helpful offline AI assistant. Answer clearly and honestly.\n" +
            "<|im_end|>\n" +
            conversationHistory +
            "<|im_start|>assistant\n"

        isGenerating = true
        sendButton.isEnabled = false
        sendButton.alpha = 0.55f
        statusText.text = "NerfeAI is thinking..."
        val loadingMessage = addMessage("NerfeAI", "Thinking…")

        Thread {
            val answer = try {
                nativeGenerate(prompt).trim().ifEmpty { "I couldn't generate a response. Please try again." }
            } catch (e: Exception) {
                "Generation failed: ${e.message ?: "unknown error"}"
            }

            runOnUiThread {
                chatLayout.removeView(loadingMessage)
                addMessage("NerfeAI", answer, animate = true)
                conversationHistory += "<|im_start|>assistant\n$answer<|im_end|>\n"
                trimConversationHistory()
                currentChat().promptHistory = conversationHistory
                persistChats()
                refreshHistoryList()
                isGenerating = false
                sendButton.isEnabled = modelReady && input.text.toString().trim().isNotEmpty()
                sendButton.alpha = if (sendButton.isEnabled) 1f else 0.55f
                statusText.text = "Qwen loaded • Offline mode ready"
            }
        }.start()
    }

    private fun trimConversationHistory() {
        val marker = "<|im_start|>user\n"
        val starts = mutableListOf<Int>()
        var index = conversationHistory.indexOf(marker)
        while (index >= 0) {
            starts.add(index)
            index = conversationHistory.indexOf(marker, index + marker.length)
        }
        if (starts.size > 4) {
            conversationHistory = conversationHistory.substring(starts[starts.size - 4])
        }
    }

    private fun addMessage(role: String, message: String, animate: Boolean = false): View {
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
            setTextColor(if (isUser) Color.rgb(16, 163, 127) else Color.rgb(16, 163, 127))
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

        row.addView(bubble, LinearLayout.LayoutParams(
            if (isUser) dp(300).coerceAtMost(resources.displayMetrics.widthPixels - dp(80)) else -1,
            -2
        ).apply {
            if (isUser) marginStart = dp(32)
            else marginEnd = dp(12)
        })

        chatLayout.addView(row)
        if (!isRenderingMessages && message != "Thinking…") {
            val chat = currentChat()
            chat.messages.add(ChatMessage(role, message))
            if (role == "You") {
                if (chat.title == "New chat") {
                    chat.title = message.replace("\n", " ").trim().take(36).ifEmpty { "New chat" }
                }
                savedChats.remove(chat)
                savedChats.add(0, chat)
            }
            chat.promptHistory = conversationHistory
            persistChats()
            refreshHistoryList()
        }
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }

        if (animate && message.isNotEmpty()) {
            animateResponseText(messageText, message)
        }

        return row
    }

    /**
     * Reveals the generated answer progressively, giving NerfeAI a natural
     * typing/response animation. The complete answer has already been saved
     * to conversation history, so the animation is visual only.
     */

	private fun animateResponseText(
	    textView: TextView,
	    fullText: String
	) {
	    val handler = Handler(Looper.getMainLooper())

	    // Faster for long responses.
	    val charsPerStep = 3
	    val intervalMs = 12L

	    var position = 0

	    textView.text = ""

	    val animator = object : Runnable {

	        override fun run() {

	            // Always finish the complete response.
	            if (position >= fullText.length) {
	                textView.text = fullText

	                scrollView.post {
	                    scrollView.fullScroll(View.FOCUS_DOWN)
	                }

	                return
	            }

	            position = minOf(
	                position + charsPerStep,
	                fullText.length
	            )

	            textView.text = fullText.substring(
	                0,
	                position
	            )

	            scrollView.post {
	                scrollView.fullScroll(View.FOCUS_DOWN)
	            }

	            handler.postDelayed(
	                this,
	                intervalMs
	            )
	        }
	    }

	    handler.post(animator)
	}

    private class SimpleTextWatcher(private val onChanged: () -> Unit) :
        android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged()
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }
}
