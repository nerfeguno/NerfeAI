package com.nerfeai

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : Activity() {

    // ============================================================
    // UI
    // ============================================================

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


    // ============================================================
    // Application state
    // ============================================================

    private var modelReady = false

    /*
     * IMPORTANT:
     *
     * This contains the complete saved conversation.
     *
     * We NO LONGER trim this to four messages.
     *
     * The native layer decides how much of this history can fit
     * inside the model's 2048-token context.
     */
    private var conversationHistory = ""

    private var isDarkTheme = true

    private var lastUserMessage = ""

    private var isRenderingMessages = false

    /*
     * Prevents multiple generations from running at the same time.
     */
    @Volatile
    private var isGenerating = false

    /*
     * Prevents multiple model-copy/load operations from being
     * started simultaneously.
     */
    @Volatile
    private var isPreparingModel = false

    private val savedChats =
        mutableListOf<ChatSession>()

    private var currentChatId = ""

    private val preferences by lazy {
        getSharedPreferences(
            "nerfeai_chat_storage",
            Context.MODE_PRIVATE
        )
    }


    // ============================================================
    // Chat data structures
    // ============================================================

    private data class ChatMessage(
        val role: String,
        val text: String
    )

    private data class ChatSession(
        val id: String,
        var title: String,
        var promptHistory: String = "",
        val messages: MutableList<ChatMessage> =
            mutableListOf()
    )


    // ============================================================
    // Constants
    // ============================================================

    companion object {

        private const val MODEL_ASSET =
            "nerfeai-model.gguf"

        private const val MODEL_FILE =
            "nerfeai-model.gguf"

        /*
         * Used to reject an obviously incomplete model file.
         *
         * Adjust this if your actual GGUF size is different.
         */
        private const val MIN_MODEL_SIZE =
            400_000_000L


        // Dark theme
        private val DARK_BG =
            Color.rgb(33, 33, 33)

        private val DARK_PANEL =
            Color.rgb(42, 42, 42)

        private val DARK_CARD =
            Color.rgb(48, 48, 48)


        // Light theme
        private val LIGHT_BG =
            Color.rgb(248, 248, 248)

        private val LIGHT_PANEL =
            Color.WHITE

        private val LIGHT_CARD =
            Color.rgb(239, 239, 239)


        /*
         * JNI library.
         */
        init {
            System.loadLibrary("nerfeai")
        }
    }


    // ============================================================
    // Native methods
    // ============================================================

    /*
     * Loads the GGUF model into the native llama.cpp backend.
     */
    private external fun nativeLoadModel(
        path: String
    ): Boolean


    /*
     * Generates an answer using llama.cpp.
     */
    private external fun nativeGenerate(
        prompt: String
    ): String


    // ============================================================
    // Activity lifecycle
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * Install Java/Kotlin crash logging before doing
         * application initialization.
         */
        installCrashLogger()


        /*
         * Restore theme.
         */
        isDarkTheme =
            savedInstanceState
                ?.getString(
                    "theme",
                    "dark"
                ) != "light"


        /*
         * Load persistent chats.
         */
        loadSavedChats()


        /*
         * Build interface.
         */
        buildInterface()


        /*
         * Restore current conversation.
         */
        renderCurrentChat()


        /*
         * Restore unsent draft after rotation.
         */
        input.setText(
            savedInstanceState
                ?.getString(
                    "draft",
                    ""
                )
                ?: ""
        )


        /*
         * Prepare bundled model.
         */
        prepareBundledModel()
    }


    override fun onSaveInstanceState(
        outState: Bundle
    ) {

        /*
         * Save current input draft.
         */
        outState.putString(
            "draft",
            if (::input.isInitialized) {
                input.text.toString()
            } else {
                ""
            }
        )


        /*
         * Save theme.
         */
        outState.putString(
            "theme",
            if (isDarkTheme) {
                "dark"
            } else {
                "light"
            }
        )


        super.onSaveInstanceState(outState)
    }


    // ============================================================
    // Crash diagnostics
    // ============================================================

    private fun installCrashLogger() {

        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()


        Thread.setDefaultUncaughtExceptionHandler {
                thread,
                throwable ->

            try {

                val crashDirectory =
                    File(
                        filesDir,
                        "crash_logs"
                    )


                if (!crashDirectory.exists()) {
                    crashDirectory.mkdirs()
                }


                val timestamp =
                    System.currentTimeMillis()


                val crashFile =
                    File(
                        crashDirectory,
                        "crash_$timestamp.txt"
                    )


                crashFile.writeText(
                    buildString {

                        appendLine(
                            "NerfeAI Crash Report"
                        )

                        appendLine(
                            "===================="
                        )

                        appendLine()

                        appendLine(
                            "Thread: ${thread.name}"
                        )

                        appendLine()

                        appendLine(
                            "Model ready: $modelReady"
                        )

                        appendLine(
                            "Generating: $isGenerating"
                        )

                        appendLine(
                            "Conversation bytes: " +
                                    conversationHistory.length
                        )

                        appendLine()

                        appendLine(
                            "Exception:"
                        )

                        appendLine(
                            throwable.stackTraceToString()
                        )
                    }
                )

            } catch (_: Exception) {

                /*
                 * Never allow crash logging itself to cause
                 * another crash.
                 */
            }


            /*
             * Continue normal Android crash processing.
             */
            previousHandler?.uncaughtException(
                thread,
                throwable
            )
        }
    }


    // ============================================================
    // Main interface
    // ============================================================

    private fun buildInterface() {

        applySystemBarColors()


        root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    backgroundColor()
                )
            }


        mainLayout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    backgroundColor()
                )
            }


        // --------------------------------------------------------
        // Top application bar
        // --------------------------------------------------------

        val topBar =
            LinearLayout(this).apply {

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
                    panelColor()
                )
            }


        val menuButton =
            TextView(this).apply {

                text = "☰"

                textSize = 25f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    textColor()
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
                    toggleDrawer(true)
                }
            }


        val titleColumn =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(this).apply {

                text = "NerfeAI"

                textSize = 19f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    textColor()
                )
            }


        statusText =
            TextView(this).apply {

                text =
                    "Preparing offline assistant..."

                textSize = 11f

                setTextColor(
                    secondaryTextColor()
                )

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
            LinearLayout.LayoutParams(
                -2,
                -2
            )
        )


        val newChatButton =
            TextView(this).apply {

                text = "＋"

                textSize = 28f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    textColor()
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

        topBar.addView(
            newChatButton
        )


        // --------------------------------------------------------
        // Chat area
        // --------------------------------------------------------

        chatLayout =
            LinearLayout(this).apply {

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
            ScrollView(this).apply {

                isFillViewport =
                    true

                clipToPadding =
                    false

                addView(
                    chatLayout
                )

                setBackgroundColor(
                    backgroundColor()
                )
            }


        // --------------------------------------------------------
        // Composer
        // --------------------------------------------------------

        val composerOuter =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(8)
                )

                setBackgroundColor(
                    backgroundColor()
                )
            }


        val composer =
            LinearLayout(this).apply {

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
                        if (isDarkTheme) {
                            Color.rgb(
                                48,
                                48,
                                48
                            )
                        } else {
                            Color.WHITE
                        },
                        dp(24).toFloat(),
                        if (isDarkTheme) {
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
            EditText(this).apply {

                hint =
                    "Message NerfeAI"

                setTextColor(
                    textColor()
                )

                setHintTextColor(
                    secondaryTextColor()
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
            }


        sendButton =
            TextView(this).apply {

                /*
                 * Use plain "Send" instead of a Unicode arrow.
                 * This avoids font rendering problems on some
                 * Android devices.
                 */
                text = "Send"

                textSize = 13f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.WHITE
                )

                background =
                    roundedDrawable(
                        Color.rgb(
                            16,
                            163,
                            127
                        ),
                        dp(22).toFloat()
                    )

                isEnabled =
                    false

                alpha =
                    0.55f

                contentDescription =
                    "Send message"

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
                        marginStart =
                            dp(4)
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
                    modelReady &&
                            !isGenerating &&
                            hasText


                sendButton.alpha =
                    if (
                        sendButton.isEnabled
                    ) {
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

            if (
                actionId ==
                EditorInfo.IME_ACTION_SEND
            ) {

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


        composer.addView(
            sendButton
        )


        composerOuter.addView(
            composer
        )


        val footer =
            TextView(this).apply {

                text =
                    "NerfeAI can make mistakes. Runs locally on your device."

                textSize = 10f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    secondaryTextColor()
                )

                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(2)
                )
            }


        composerOuter.addView(
            footer
        )


        mainLayout.addView(
            topBar
        )


        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )


        mainLayout.addView(
            composerOuter
        )


        // --------------------------------------------------------
        // Drawer
        // --------------------------------------------------------

        drawerScrim =
            View(this).apply {

                setBackgroundColor(
                    0x99000000.toInt()
                )

                visibility =
                    View.GONE

                setOnClickListener {
                    toggleDrawer(false)
                }
            }


        drawer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(16),
                    dp(22),
                    dp(16),
                    dp(16)
                )

                setBackgroundColor(
                    panelColor()
                )

                visibility =
                    View.GONE
            }


        val drawerHeader =
            TextView(this).apply {

                text =
                    "NerfeAI"

                textSize = 22f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    textColor()
                )

                setPadding(
                    dp(4),
                    dp(4),
                    dp(4),
                    dp(20)
                )
            }


        val drawerNewChat =
            drawerAction(
                "＋   New chat"
            ) {

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

                isDarkTheme =
                    !isDarkTheme

                toggleDrawer(false)

                rebuildForTheme()
            }


        val chatsLabel =
            TextView(this).apply {

                text =
                    "YOUR SPACE"

                textSize = 11f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    secondaryTextColor()
                )

                setPadding(
                    dp(5),
                    dp(24),
                    dp(5),
                    dp(8)
                )
            }


        historyContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL
            }


        val historyScroll =
            ScrollView(this).apply {

                isFillViewport =
                    false

                addView(
                    historyContainer
                )
            }


        clearHistoryButton =
            drawerAction(
                "Delete all chat history"
            ) {

                confirmClearAllHistory()
            }.apply {

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
            }


        drawer.addView(
            drawerHeader
        )

        drawer.addView(
            drawerNewChat
        )

        drawer.addView(
            drawerTheme
        )

        drawer.addView(
            chatsLabel
        )

        drawer.addView(
            historyScroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        drawer.addView(
            clearHistoryButton
        )


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


        setContentView(
            root
        )
    }


    // ============================================================
    // Drawer
    // ============================================================

    private fun drawerAction(
        label: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {

            text = label

            textSize = 15f

            setTextColor(
                textColor()
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
                action()
            }
        }
    }


    private fun toggleDrawer(
        show: Boolean
    ) {

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
    // New chat
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


        conversationHistory =
            ""


        lastUserMessage =
            ""


        chatLayout.removeAllViews()


        showWelcomeMessage()


        input.text.clear()


        persistChats()


        refreshHistoryList()


        toggleDrawer(false)
    }


    // ============================================================
    // Current chat
    // ============================================================

    private fun currentChat(): ChatSession {

        var chat =
            savedChats.firstOrNull {
                it.id == currentChatId
            }


        if (chat == null) {

            chat =
                ChatSession(
                    UUID.randomUUID().toString(),
                    "New chat"
                )


            currentChatId =
                chat.id


            savedChats.add(
                0,
                chat
            )
        }


        return chat
    }


    // ============================================================
    // Load saved chats
    // ============================================================

    private fun loadSavedChats() {

        savedChats.clear()


        try {

            val raw =
                preferences.getString(
                    "chats_json",
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
                        id =
                            item.optString(
                                "id",
                                UUID.randomUUID()
                                    .toString()
                            ),

                        title =
                            item.optString(
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
                            role =
                                message.optString(
                                    "role"
                                ),

                            text =
                                message.optString(
                                    "text"
                                )
                        )
                    )
                }


                if (
                    chat.messages.isNotEmpty()
                ) {

                    savedChats.add(
                        chat
                    )
                }
            }


            currentChatId =
                preferences.getString(
                    "current_chat_id",
                    ""
                ) ?: ""

        } catch (_: Exception) {

            savedChats.clear()

            currentChatId =
                ""
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


    // ============================================================
    // Persist chats
    // ============================================================

    private fun persistChats() {

        try {

            val array =
                JSONArray()


            savedChats
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


                    /*
                     * IMPORTANT:
                     *
                     * Save the COMPLETE conversation history.
                     *
                     * We no longer truncate it to four turns.
                     */
                    item.put(
                        "promptHistory",
                        chat.promptHistory
                    )


                    val messages =
                        JSONArray()


                    chat.messages.forEach {
                            message ->

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


                    array.put(
                        item
                    )
                }


            preferences.edit()
                .putString(
                    "chats_json",
                    array.toString()
                )
                .putString(
                    "current_chat_id",
                    currentChatId
                )
                .apply()

        } catch (_: Exception) {

            /*
             * Persistence failure should never crash
             * the application.
             */
        }
    }


    // ============================================================
    // Render current chat
    // ============================================================

    private fun renderCurrentChat() {

        val chat =
            savedChats.firstOrNull {
                it.id == currentChatId
            }


        /*
         * Restore the complete saved prompt history.
         */
        conversationHistory =
            chat?.promptHistory ?: ""


        lastUserMessage =
            chat?.messages
                ?.lastOrNull {
                    it.role == "You"
                }
                ?.text
                ?: ""


        chatLayout.removeAllViews()


        if (
            chat == null ||
            chat.messages.isEmpty()
        ) {

            showWelcomeMessage()

        } else {

            isRenderingMessages =
                true


            chat.messages.forEach {
                    message ->

                addMessage(
                    message.role,
                    message.text
                )
            }


            isRenderingMessages =
                false
        }


        refreshHistoryList()
    }


    // ============================================================
    // Open saved chat
    // ============================================================

    private fun openChat(
        chatId: String
    ) {

        if (isGenerating) {

            Toast.makeText(
                this,
                "Please wait for the current response to finish.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        currentChatId =
            chatId


        persistChats()


        renderCurrentChat()


        toggleDrawer(false)


        scrollView.post {
            scrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }


    // ============================================================
    // Refresh history list
    // ============================================================

    private fun refreshHistoryList() {

        if (
            !::historyContainer.isInitialized
        ) {
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

                    text =
                        "No saved chats yet"

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


        ordered.forEach {
                chat ->

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

                    setTextColor(
                        textColor()
                    )

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
                        openChat(
                            chat.id
                        )
                    }
                }


            val deleteButton =
                TextView(this).apply {

                    text = "×"

                    textSize = 22f

                    gravity =
                        Gravity.CENTER

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


            row.addView(
                deleteButton
            )


            historyContainer.addView(
                row
            )
        }
    }


    // ============================================================
    // Delete one conversation
    // ============================================================

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


                if (
                    currentChatId ==
                    chatId
                ) {

                    currentChatId =
                        UUID.randomUUID()
                            .toString()


                    conversationHistory =
                        ""


                    lastUserMessage =
                        ""


                    chatLayout.removeAllViews()


                    showWelcomeMessage()
                }


                persistChats()


                refreshHistoryList()
            }

            .show()
    }


    // ============================================================
    // Delete all conversations
    // ============================================================

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
                    UUID.randomUUID()
                        .toString()


                conversationHistory =
                    ""


                lastUserMessage =
                    ""


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
            TextView(this).apply {

                text =
                    "How can I help you?"

                textSize = 23f

                typeface =
                    Typeface.DEFAULT_BOLD

                gravity =
                    Gravity.CENTER

                setTextColor(
                    textColor()
                )

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

                gravity =
                    Gravity.CENTER

                setTextColor(
                    secondaryTextColor()
                )
            }


        welcome.addView(
            logo
        )


        welcome.addView(
            heading
        )


        welcome.addView(
            subtitle
        )


        chatLayout.addView(
            welcome
        )
    }


    // ============================================================
    // Theme rebuild
    // ============================================================

    private fun rebuildForTheme() {

        val savedDraft =
            input.text.toString()


        buildInterface()


        renderCurrentChat()


        input.setText(
            savedDraft
        )


        statusText.text =
            if (modelReady) {
                "Qwen loaded • Offline mode ready"
            } else {
                "Preparing offline assistant..."
            }


        updateSendButtonState()
    }


    // ============================================================
    // Appearance
    // ============================================================

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

        window.statusBarColor =
            if (isDarkTheme) {
                DARK_BG
            } else {
                LIGHT_BG
            }


        window.navigationBarColor =
            if (isDarkTheme) {
                DARK_BG
            } else {
                LIGHT_BG
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

            setColor(
                color
            )

            cornerRadius =
                radius


            if (
                strokeColor != null
            ) {

                setStroke(
                    dp(1),
                    strokeColor
                )
            }
        }
    }


    private fun dp(
        value: Int
    ): Int {

        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }


    // ============================================================
    // Bundled model preparation
    // ============================================================

    private fun prepareBundledModel() {

        if (isPreparingModel) {
            return
        }


        isPreparingModel =
            true


        val modelFile =
            File(
                filesDir,
                MODEL_FILE
            )


        /*
         * If the model already exists and appears complete,
         * don't copy it from assets again.
         */
        if (
            modelFile.exists() &&
            modelFile.length() >
            MIN_MODEL_SIZE
        ) {

            loadModel(
                modelFile
            )

            return
        }


        statusText.text =
            "Preparing offline model..."


        sendButton.isEnabled =
            false


        sendButton.alpha =
            0.55f


        Thread {

            try {

                /*
                 * Copy into a temporary file first.
                 *
                 * If the application is interrupted during the
                 * copy, we don't leave a partially written GGUF
                 * pretending to be the real model.
                 */
                val temporaryFile =
                    File(
                        filesDir,
                        "$MODEL_FILE.tmp"
                    )


                if (
                    temporaryFile.exists()
                ) {

                    temporaryFile.delete()
                }


                assets.open(
                    MODEL_ASSET
                ).use { source ->

                    FileOutputStream(
                        temporaryFile
                    ).use { destination ->

                        source.copyTo(
                            destination
                        )
                    }
                }


                /*
                 * Verify the copied file before replacing the
                 * previous model.
                 */
                if (
                    !temporaryFile.exists() ||
                    temporaryFile.length() <=
                    MIN_MODEL_SIZE
                ) {

                    throw IllegalStateException(
                        "Bundled model file is incomplete."
                    )
                }


                /*
                 * Replace the old model.
                 */
                if (
                    modelFile.exists()
                ) {

                    modelFile.delete()
                }


                if (
                    !temporaryFile.renameTo(
                        modelFile
                    )
                ) {

                    throw IllegalStateException(
                        "Could not finalize model file."
                    )
                }


                runOnUiThread {

                    isPreparingModel =
                        false


                    loadModel(
                        modelFile
                    )
                }

            } catch (e: Exception) {

                isPreparingModel =
                    false


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


    // ============================================================
    // Model loading
    // ============================================================

    private fun loadModel(
        file: File
    ) {

        if (
            !file.exists()
        ) {

            modelReady =
                false

            statusText.text =
                "Model file not found."

            updateSendButtonState()

            return
        }


        statusText.text =
            "Loading AI model into memory..."


        sendButton.isEnabled =
            false


        sendButton.alpha =
            0.55f


        Thread {

            val loaded =
                try {

                    nativeLoadModel(
                        file.absolutePath
                    )

                } catch (
                    e: Exception
                ) {

                    false
                }


            runOnUiThread {

                modelReady =
                    loaded


                isPreparingModel =
                    false


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


                updateSendButtonState()
            }

        }.start()
    }


    // ============================================================
    // Send button state
    // ============================================================

    private fun updateSendButtonState() {

        if (
            !::sendButton.isInitialized ||
            !::input.isInitialized
        ) {
            return
        }


        val hasText =
            input.text
                .toString()
                .trim()
                .isNotEmpty()


        sendButton.isEnabled =
            modelReady &&
                    !isGenerating &&
                    hasText


        sendButton.alpha =
            if (
                sendButton.isEnabled
            ) {
                1f
            } else {
                0.55f
            }
    }


    // ============================================================
    // Send message
    // ============================================================

    private fun sendMessage() {

        /*
         * Prevent duplicate generations.
         */
        if (isGenerating) {
            return
        }


        /*
         * Model must be ready.
         */
        if (!modelReady) {

            Toast.makeText(
                this,
                "The AI model is still loading.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val userText =
            input.text
                .toString()
                .trim()


        if (
            userText.isEmpty()
        ) {
            return
        }


        lastUserMessage =
            userText


        /*
         * Clear input immediately.
         */
        input.text.clear()


        /*
         * Hide keyboard.
         */
        val keyboard =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager


        keyboard.hideSoftInputFromWindow(
            input.windowToken,
            0
        )


        // --------------------------------------------------------
        // Add user message to COMPLETE conversation history
        // --------------------------------------------------------

        conversationHistory +=
            "<|im_start|>user\n" +
                    userText +
                    "<|im_end|>\n"


        /*
         * Save full history.
         */
        currentChat().promptHistory =
            conversationHistory


        /*
         * Display user message.
         */
        addMessage(
            "You",
            userText
        )


        // --------------------------------------------------------
        // Build prompt
        // --------------------------------------------------------

        val prompt =
            "<|im_start|>system\n" +
                    "You are NerfeAI, a helpful offline AI assistant. " +
                    "Answer clearly and honestly.\n" +
                    "<|im_end|>\n" +
                    conversationHistory +
                    "<|im_start|>assistant\n"


        // --------------------------------------------------------
        // Start generation
        // --------------------------------------------------------

        isGenerating =
            true


        updateSendButtonState()


        statusText.text =
            "NerfeAI is thinking..."


        /*
         * Temporary UI message.
         *
         * We deliberately do NOT save "Thinking…" as a real
         * conversation message.
         */
        val loadingMessage =
            addMessage(
                "NerfeAI",
                "Thinking…"
            )


        /*
         * Native inference must never run on the Android UI
         * thread.
         */
        Thread {

            val answer =
                try {

                    val result =
                        nativeGenerate(
                            prompt
                        )


                    result
                        .trim()
                        .ifEmpty {
                            "I couldn't generate a response. Please try again."
                        }

                } catch (
                    e: Exception
                ) {

                    "Generation failed: ${
                        e.message
                            ?: "unknown error"
                    }"
                }


            runOnUiThread {

                /*
                 * Remove temporary "Thinking…" bubble.
                 */
                if (
                    loadingMessage.parent ==
                    chatLayout
                ) {

                    chatLayout.removeView(
                        loadingMessage
                    )
                }


                /*
                 * Display generated answer.
                 */
                addMessage(
                    "NerfeAI",
                    answer
                )


                // ------------------------------------------------
                // Add AI response to COMPLETE conversation
                // ------------------------------------------------

                conversationHistory +=
                    "<|im_start|>assistant\n" +
                            answer +
                            "<|im_end|>\n"


                /*
                 * IMPORTANT:
                 *
                 * There is NO trimConversationHistory() here.
                 *
                 * The old version had:
                 *
                 *     trimConversationHistory()
                 *
                 * which limited the application to four user turns.
                 *
                 * The native C++ layer now performs token-aware
                 * trimming only when preparing the model prompt.
                 */


                currentChat().promptHistory =
                    conversationHistory


                /*
                 * Save complete history.
                 */
                persistChats()


                refreshHistoryList()


                isGenerating =
                    false


                statusText.text =
                    "Qwen loaded • Offline mode ready"


                updateSendButtonState()


                /*
                 * Scroll to latest response.
                 */
                scrollView.post {
                    scrollView.fullScroll(
                        View.FOCUS_DOWN
                    )
                }
            }

        }.start()
    }


    // ============================================================
    // Chat message rendering
    // ============================================================

    private fun addMessage(
        role: String,
        message: String
    ): View {

        val isUser =
            role == "You"


        val row =
            LinearLayout(this).apply {

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
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(11),
                    dp(14),
                    dp(11)
                )


                background =
                    roundedDrawable(
                        when {

                            isUser &&
                                    isDarkTheme ->

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


                            isDarkTheme ->

                                DARK_CARD


                            else ->

                                LIGHT_CARD
                        },

                        dp(18).toFloat()
                    )
            }


        val roleText =
            TextView(this).apply {

                text =
                    if (isUser) {
                        "You"
                    } else {
                        "NerfeAI"
                    }


                textSize =
                    12f


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
            TextView(this).apply {

                text =
                    message


                textSize =
                    15f


                setTextColor(
                    textColor()
                )


                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )


                setTextIsSelectable(
                    true
                )


                setLineSpacing(
                    dp(2).toFloat(),
                    1f
                )
            }


        bubble.addView(
            roleText
        )


        bubble.addView(
            messageText
        )


        val bubbleWidth =
            if (isUser) {

                dp(300).coerceAtMost(
                    resources.displayMetrics.widthPixels -
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

                    marginStart =
                        dp(32)

                } else {

                    marginEnd =
                        dp(12)
                }
            }
        )


        chatLayout.addView(
            row
        )


        // --------------------------------------------------------
        // Persist real messages
        // --------------------------------------------------------

        if (
            !isRenderingMessages &&
            message != "Thinking…"
        ) {

            val chat =
                currentChat()


            chat.messages.add(
                ChatMessage(
                    role,
                    message
                )
            )


            /*
             * Automatically name a new conversation after the
             * first user message.
             */
            if (
                role == "You" &&
                chat.title == "New chat"
            ) {

                chat.title =
                    message
                        .replace(
                            "\n",
                            " "
                        )
                        .trim()
                        .take(36)
                        .ifEmpty {
                            "New chat"
                        }


                /*
                 * Move active conversation to the top.
                 */
                savedChats.remove(
                    chat
                )


                savedChats.add(
                    0,
                    chat
                )
            }


            /*
             * Keep the complete prompt history.
             */
            chat.promptHistory =
                conversationHistory


            persistChats()


            refreshHistoryList()
        }


        /*
         * Scroll to bottom.
         */
        scrollView.post {

            scrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }


        return row
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
