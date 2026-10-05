package com.nerfeai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    private var modelReady = false
    private var conversationHistory = ""
    private var isDarkTheme = true
    private var lastUserMessage = ""

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
        buildInterface()
        showWelcomeMessage()
        prepareBundledModel()
    }

    private fun buildInterface() {
        applySystemBarColors()

        root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor())
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
            text = "↑"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedDrawable(Color.rgb(16, 163, 127), dp(24).toFloat())
            isEnabled = false
            alpha = 0.55f
            contentDescription = "Send message"
            setOnClickListener { sendMessage() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
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

        val currentChat = TextView(this).apply {
            text = "  Current conversation"
            textSize = 14f
            setTextColor(textColor())
            setPadding(dp(8), dp(12), dp(8), dp(12))
            background = roundedDrawable(cardColor(), dp(10).toFloat())
        }

        drawer.addView(drawerHeader)
        drawer.addView(drawerNewChat)
        drawer.addView(drawerTheme)
        drawer.addView(chatsLabel)
        drawer.addView(currentChat)
        drawer.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

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
        conversationHistory = ""
        lastUserMessage = ""
        chatLayout.removeAllViews()
        showWelcomeMessage()
        input.text.clear()
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
        // Preserve the visible messages and draft while rebuilding the colors.
        val savedHistory = conversationHistory
        val savedDraft = input.text.toString()
        val savedMessages = mutableListOf<Pair<String, String>>()

        for (i in 0 until chatLayout.childCount) {
            val row = chatLayout.getChildAt(i) as? LinearLayout ?: continue
            val bubble = row.getChildAt(0) as? LinearLayout ?: continue
            if (bubble.childCount < 2) continue
            val role = (bubble.getChildAt(0) as? TextView)?.text?.toString() ?: continue
            val message = (bubble.getChildAt(1) as? TextView)?.text?.toString() ?: continue
            savedMessages.add(Pair(role, message))
        }

        buildInterface()
        conversationHistory = savedHistory
        if (savedMessages.isEmpty()) {
            showWelcomeMessage()
        } else {
            savedMessages.forEach { (role, message) -> addMessage(role, message) }
        }
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
        window.statusBarColor = if (isDarkTheme) DARK_BG else LIGHT_BG
        window.navigationBarColor = if (isDarkTheme) DARK_BG else LIGHT_BG
        window.decorView.systemUiVisibility =
            if (isDarkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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

        addMessage("You", userText)
        conversationHistory += "<|im_start|>user\n$userText<|im_end|>\n"

        val prompt =
            "<|im_start|>system\n" +
            "You are NerfeAI, a helpful offline AI assistant. Answer clearly and honestly.\n" +
            "<|im_end|>\n" +
            conversationHistory +
            "<|im_start|>assistant\n"

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
                addMessage("NerfeAI", answer)
                conversationHistory += "<|im_start|>assistant\n$answer<|im_end|>\n"
                trimConversationHistory()
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

    private fun addMessage(role: String, message: String): View {
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
            text = message
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
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return row
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
