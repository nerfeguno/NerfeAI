package com.nerfeai

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Toast
import com.nerfeai.ai.ModelManager
import com.nerfeai.ai.NativeAIEngine
import com.nerfeai.ai.NerfeAIEngine
import com.nerfeai.core.AppLogger
import com.nerfeai.crash.CrashLogger
import com.nerfeai.data.ChatRepository
import com.nerfeai.data.ChatSession
import com.nerfeai.ui.ChatRenderer
import com.nerfeai.ui.ChatView
import com.nerfeai.ui.DrawerView
import com.nerfeai.ui.ThemeManager
import com.nerfeai.update.UpdateManager
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var chatView: ChatView
    private lateinit var chatRenderer: ChatRenderer
    private lateinit var drawerView: DrawerView

    private lateinit var chatRepository: ChatRepository
    private lateinit var aiEngine: NerfeAIEngine
    private lateinit var modelManager: ModelManager

    private lateinit var crashLogger: CrashLogger
    private lateinit var updateManager: UpdateManager

    private var modelReady = false

    private var conversationHistory = ""

    private var isDarkTheme = true

    @Volatile
    private var isGenerating = false

    @Volatile
    private var isPreparingModel = false

    private var currentChatId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.initialize(this)

        crashLogger = CrashLogger(this)
        crashLogger.install()

        isDarkTheme =
            savedInstanceState
                ?.getString("theme", "dark") != "light"

        chatRepository = ChatRepository(this)

        aiEngine = NativeAIEngine()

        modelManager = ModelManager(
            context = this,
            engine = aiEngine
        )

        updateManager = UpdateManager(this)

        chatRepository.load()

        currentChatId =
            chatRepository.currentChatId

        if (currentChatId.isBlank()) {
            currentChatId = UUID.randomUUID().toString()
            chatRepository.setCurrentChat(currentChatId)
        }

        buildInterface()

        renderCurrentChat()

        val draft =
            savedInstanceState?.getString("draft", "")

        chatView.setDraft(draft ?: "")

        updateManager.showUpdateIfNeeded()

        prepareModel()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            "draft",
            if (::chatView.isInitialized) {
                chatView.getDraft()
            } else {
                ""
            }
        )

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

    private fun buildInterface() {

        chatView = ChatView(
            activity = this,
            themeManager = ThemeManager(this)
        )

        setContentView(chatView.build())

        chatRenderer = ChatRenderer(
            activity = this,
            chatView = chatView,
            themeManager = ThemeManager(this)
        )

        drawerView = DrawerView(
            activity = this,
            chatView = chatView,
            themeManager = ThemeManager(this),
            onNewChat = {
                startNewChat()
            },
            onThemeChanged = {
                isDarkTheme = !isDarkTheme
                rebuildForTheme()
            },
            onChatSelected = { chatId ->
                openChat(chatId)
            },
            onDeleteChat = { chatId, title ->
                confirmDeleteChat(chatId, title)
            },
            onDeleteAll = {
                confirmClearAllHistory()
            },
            isDarkThemeProvider = {
                isDarkTheme
            }
        )

        chatView.attachDrawer(
            drawerView.build()
        )

        chatView.setMenuAction {
            drawerView.toggle(true)
        }

        chatView.setNewChatAction {
            startNewChat()
        }

        chatView.setSendAction {
            sendMessage()
        }

        chatView.setEditorSendAction {
            sendMessage()
        }

        chatView.setTextChangedAction {
            updateSendButtonState()
        }

        refreshHistoryList()
    }

    private fun prepareModel() {

        if (isPreparingModel) {
            return
        }

        isPreparingModel = true

        chatView.setStatus(
            "Preparing offline assistant..."
        )

        modelManager.prepareBundledModel(
            onStatus = { status ->
                runOnUiThread {
                    chatView.setStatus(status)
                }
            },
            onReadyToLoad = { file ->
                runOnUiThread {
                    loadModel(file)
                }
            },
            onError = { message ->
                runOnUiThread {
                    isPreparingModel = false
                    modelReady = false

                    chatView.setStatus(
                        "Could not prepare bundled model."
                    )

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()

                    updateSendButtonState()
                }
            }
        )
    }

    private fun loadModel(
        file: java.io.File
    ) {

        chatView.setStatus(
            "Loading AI model into memory..."
        )

        chatView.setSendEnabled(false)

        modelManager.loadModel(
            file = file,
            onResult = { loaded ->
                runOnUiThread {

                    modelReady = loaded
                    isPreparingModel = false

                    if (loaded) {
                        chatView.setStatus(
                            "Qwen loaded • Offline mode ready"
                        )
                    } else {
                        chatView.setStatus(
                            "Model loading failed."
                        )

                        Toast.makeText(
                            this,
                            "Could not load the bundled GGUF model.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    updateSendButtonState()
                }
            }
        )
    }

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

        chatRepository.setCurrentChat(
            currentChatId
        )

        chatRenderer.clear()

        chatRenderer.showWelcome()

        chatView.setDraft("")

        chatRepository.save()

        refreshHistoryList()

        drawerView.toggle(false)
    }

    private fun renderCurrentChat() {

        val chat =
            chatRepository.getChat(
                currentChatId
            )

        conversationHistory =
            chat?.promptHistory ?: ""

        chatRenderer.clear()

        if (
            chat == null ||
            chat.messages.isEmpty()
        ) {
            chatRenderer.showWelcome()
        } else {

            chatRenderer.renderMessages(
                chat.messages
            )
        }

        refreshHistoryList()
    }

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

        currentChatId = chatId

        chatRepository.setCurrentChat(
            currentChatId
        )

        renderCurrentChat()

        drawerView.toggle(false)

        chatView.scrollToBottom()
    }

    private fun refreshHistoryList() {

        if (!::drawerView.isInitialized) {
            return
        }

        drawerView.refreshHistory(
            chatRepository.getChats(),
            currentChatId
        )
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

        android.app.AlertDialog.Builder(this)
            .setTitle("Delete conversation?")
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

                chatRepository.deleteChat(
                    chatId
                )

                if (currentChatId == chatId) {

                    currentChatId =
                        UUID.randomUUID().toString()

                    conversationHistory = ""

                    chatRepository.setCurrentChat(
                        currentChatId
                    )

                    chatRenderer.clear()
                    chatRenderer.showWelcome()
                }

                chatRepository.save()

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

        android.app.AlertDialog.Builder(this)
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

                chatRepository.deleteAll()

                currentChatId =
                    UUID.randomUUID().toString()

                conversationHistory = ""

                chatRepository.setCurrentChat(
                    currentChatId
                )

                chatRenderer.clear()
                chatRenderer.showWelcome()

                chatRepository.save()

                refreshHistoryList()

                drawerView.toggle(false)
            }
            .show()
    }

    private fun sendMessage() {

        if (isGenerating) {
            return
        }

        if (!modelReady) {
            Toast.makeText(
                this,
                "The AI model is still loading.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val userText =
            chatView.getInput()
                .trim()

        if (userText.isEmpty()) {
            return
        }

        chatView.setDraft("")

        hideKeyboard()

        conversationHistory +=
            "<|im_start|>user\n" +
                    userText +
                    "<|im_end|>\n"

        val chat =
            chatRepository.getOrCreateChat(
                currentChatId
            )

        chat.promptHistory =
            conversationHistory

        chatRenderer.addMessage(
            role = "You",
            message = userText,
            persist = true
        )

        chatRepository.save()

        val prompt =
            "<|im_start|>system\n" +
                    "You are NerfeAI, a helpful offline AI assistant. " +
                    "Answer clearly and honestly.\n" +
                    "<|im_end|>\n" +
                    conversationHistory +
                    "<|im_start|>assistant\n"

        isGenerating = true

        updateSendButtonState()

        chatView.setStatus(
            "NerfeAI is thinking..."
        )

        val loadingMessage =
            chatRenderer.addMessage(
                role = "NerfeAI",
                message = "Thinking…",
                persist = false
            )

        Thread {

            val answer =
                try {
                    aiEngine.generate(prompt)
                        .trim()
                        .ifEmpty {
                            "I couldn't generate a response. Please try again."
                        }
                } catch (e: Exception) {
                    "Generation failed: ${
                        e.message ?: "unknown error"
                    }"
                }

            runOnUiThread {

                chatRenderer.removeMessage(
                    loadingMessage
                )

                chatRenderer.addMessage(
                    role = "NerfeAI",
                    message = answer,
                    persist = true
                )

                conversationHistory +=
                    "<|im_start|>assistant\n" +
                            answer +
                            "<|im_end|>\n"

                val current =
                    chatRepository.getOrCreateChat(
                        currentChatId
                    )

                current.promptHistory =
                    conversationHistory

                chatRepository.save()

                refreshHistoryList()

                isGenerating = false

                chatView.setStatus(
                    "Qwen loaded • Offline mode ready"
                )

                updateSendButtonState()

                chatView.scrollToBottom()
            }
        }.start()
    }

    private fun updateSendButtonState() {

        if (!::chatView.isInitialized) {
            return
        }

        val hasText =
            chatView.getInput()
                .trim()
                .isNotEmpty()

        chatView.setSendEnabled(
            modelReady &&
                    !isGenerating &&
                    hasText
        )
    }

    private fun rebuildForTheme() {

        val draft =
            chatView.getDraft()

        buildInterface()

        renderCurrentChat()

        chatView.setDraft(draft)

        chatView.setStatus(
            if (modelReady) {
                "Qwen loaded • Offline mode ready"
            } else {
                "Preparing offline assistant..."
            }
        )

        updateSendButtonState()
    }

    private fun hideKeyboard() {

        val input =
            chatView.getInputView()

        val keyboard =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        keyboard.hideSoftInputFromWindow(
            input.windowToken,
            0
        )
    }
}