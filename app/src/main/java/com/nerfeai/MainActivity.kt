package com.nerfeai

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
    private lateinit var sendButton: Button

    private var modelReady = false
    private var conversationHistory = ""

    companion object {
        private const val MODEL_ASSET = "nerfeai-model.gguf"
        private const val MODEL_FILE = "nerfeai-model.gguf"

        init {
            System.loadLibrary("nerfeai")
        }
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()

        addMessage(
            "NerfeAI",
            "Welcome! Your AI model is bundled with this app. " +
            "The first startup may take a while while the model is prepared."
        )

        prepareBundledModel()
    }

    private fun buildInterface() {
        window.statusBarColor = Color.rgb(15, 20, 35)
        window.navigationBarColor = Color.rgb(15, 20, 35)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(15, 20, 35))
        }

        val title = TextView(this).apply {
            text = "NerfeAI"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 8, 8, 4)
        }

        statusText = TextView(this).apply {
            text = "Preparing offline assistant..."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(8, 0, 8, 10)
        }

        val newChatButton = Button(this).apply {
            text = "New Chat"
            setOnClickListener {
                conversationHistory = ""
                chatLayout.removeAllViews()
                addMessage("NerfeAI", "New chat started. How can I help?")
            }
        }

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
        }

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(chatLayout)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        input = EditText(this).apply {
            hint = "Message NerfeAI..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 15f
            minLines = 1
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundColor(Color.rgb(35, 42, 60))
            setPadding(14, 8, 14, 8)
        }

        sendButton = Button(this).apply {
            text = "Send"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        bottomBar.addView(
            input,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        bottomBar.addView(sendButton)

        root.addView(title)
        root.addView(statusText)
        root.addView(newChatButton)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun prepareBundledModel() {
        val modelFile = File(filesDir, MODEL_FILE)

        if (modelFile.exists() && modelFile.length() > 400_000_000L) {
            loadModel(modelFile)
            return
        }

        statusText.text = "Copying bundled AI model to app storage..."
        sendButton.isEnabled = false

        Thread {
            try {
                assets.open(MODEL_ASSET).use { source ->
                    FileOutputStream(modelFile).use { destination ->
                        source.copyTo(destination)
                    }
                }

                runOnUiThread {
                    loadModel(modelFile)
                }
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
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                modelReady = loaded
                sendButton.isEnabled = loaded

                if (loaded) {
                    statusText.text = "Qwen loaded • Offline mode ready"
                    addMessage(
                        "NerfeAI",
                        "I'm ready! Ask me anything. You can chat offline."
                    )
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
            Toast.makeText(
                this,
                "The AI model is still loading.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val userText = input.text.toString().trim()
        if (userText.isEmpty()) return

        input.text.clear()

        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)

        addMessage("You", userText)

        conversationHistory +=
            "<|im_start|>user\n$userText<|im_end|>\n"

        val prompt =
            "<|im_start|>system\n" +
            "You are NerfeAI, a helpful offline AI assistant. " +
            "Answer clearly and honestly.\n<|im_end|>\n" +
            conversationHistory +
            "<|im_start|>assistant\n"

        sendButton.isEnabled = false
        statusText.text = "NerfeAI is thinking..."

        Thread {
            val answer = try {
                nativeGenerate(prompt)
            } catch (e: Exception) {
                "Generation failed: ${e.message ?: "unknown error"}"
            }

            runOnUiThread {
                addMessage("NerfeAI", answer)

                conversationHistory +=
                    "<|im_start|>assistant\n$answer<|im_end|>\n"

                sendButton.isEnabled = modelReady
                statusText.text = "Qwen loaded • Offline mode ready"
            }
        }.start()
    }

    private fun addMessage(role: String, message: String) {
        val isUser = role == "You"

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)

            background = GradientDrawable().apply {
                setColor(
                    if (isUser) Color.rgb(34, 83, 111)
                    else Color.rgb(35, 42, 60)
                )
                cornerRadius = 20f
            }
        }

        val roleText = TextView(this).apply {
            text = role
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (isUser) Color.rgb(173, 225, 255)
                else Color.rgb(100, 220, 190)
            )
        }

        val messageText = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 0)
            setTextIsSelectable(true)
        }

        bubble.addView(roleText)
        bubble.addView(messageText)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(0, 5, 0, 5)

            addView(
                bubble,
                LinearLayout.LayoutParams(0, -2, 0.90f)
            )
        }

        chatLayout.addView(row)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}
