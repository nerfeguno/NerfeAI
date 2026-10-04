package com.nerfeai

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
        private const val PICK_MODEL_REQUEST = 100
        private const val MODEL_FILE_NAME = "nerfeai-model.gguf"

        init {
            System.loadLibrary("nerfeai")
        }
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()

        val existingModel = File(filesDir, MODEL_FILE_NAME)

        if (existingModel.exists()) {
            loadModel(existingModel)
        } else {
            statusText.text = "Import your Qwen GGUF model to begin."
            addMessage(
                "NerfeAI",
                "Hello! I'm NerfeAI. Import your GGUF model to start chatting offline."
            )
        }
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
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 4)
        }

        statusText = TextView(this).apply {
            text = "Preparing offline assistant..."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(8, 0, 8, 10)
        }

        val topButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val importButton = Button(this).apply {
            text = "Import Model"
            setOnClickListener { openModelPicker() }
        }

        val newChatButton = Button(this).apply {
            text = "New Chat"
            setOnClickListener {
                conversationHistory = ""
                chatLayout.removeAllViews()
                addMessage(
                    "NerfeAI",
                    if (modelReady) "New chat started. How can I help?"
                    else "Import your GGUF model to start chatting."
                )
            }
        }

        topButtons.addView(
            importButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        topButtons.addView(
            newChatButton,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        chatLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
        }

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                chatLayout,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
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
            setSingleLine(false)
            setBackgroundColor(Color.rgb(35, 42, 60))
            setPadding(14, 8, 14, 8)
        }

        sendButton = Button(this).apply {
            text = "Send"
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
        bottomBar.addView(
            sendButton,
            LinearLayout.LayoutParams(-2, -2)
        )

        root.addView(title)
        root.addView(statusText)
        root.addView(topButtons)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun openModelPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        startActivityForResult(intent, PICK_MODEL_REQUEST)
    }

    @Deprecated("Uses the system document picker for broad Android compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_MODEL_REQUEST &&
            resultCode == RESULT_OK
        ) {
            val uri: Uri = data?.data ?: return
            importModel(uri)
        }
    }

    private fun importModel(uri: Uri) {
        modelReady = false
        sendButton.isEnabled = false
        statusText.text = "Copying model into app storage..."

        Thread {
            try {
                val destination = File(filesDir, MODEL_FILE_NAME)

                val source = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open selected file.")

                source.use { inputStream ->
                    FileOutputStream(destination).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                runOnUiThread {
                    loadModel(destination)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Model import failed."
                    sendButton.isEnabled = true
                    Toast.makeText(
                        this,
                        e.message ?: "Could not import model.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun loadModel(file: File) {
        modelReady = false
        sendButton.isEnabled = false
        statusText.text = "Loading model into memory. Please wait..."

        Thread {
            val loaded = try {
                nativeLoadModel(file.absolutePath)
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                modelReady = loaded
                sendButton.isEnabled = true

                if (loaded) {
                    statusText.text = "Model loaded • Offline mode ready"
                    if (chatLayout.childCount == 0) {
                        addMessage(
                            "NerfeAI",
                            "Model loaded successfully. Ask me anything!"
                        )
                    }
                } else {
                    statusText.text = "Could not load model. Check the GGUF file."
                    Toast.makeText(
                        this,
                        "Model loading failed.",
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
                "Import and load your GGUF model first.",
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
            "Answer clearly and accurately. If you do not know something, " +
            "say so honestly.\n<|im_end|>\n" +
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
                statusText.text = "Model loaded • Offline mode ready"
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
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.90f
                )
            )
        }

        chatLayout.addView(row)

        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}
