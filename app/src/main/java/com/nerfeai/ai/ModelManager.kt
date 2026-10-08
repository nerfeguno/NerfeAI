package com.nerfeai.ai

import android.content.Context
import com.nerfeai.core.AppConstants
import com.nerfeai.core.AppLogger
import java.io.File
import java.io.FileOutputStream

class ModelManager(
    private val context: Context,
    private val engine: NerfeAIEngine
) {

    fun prepareBundledModel(
        onStatus: (String) -> Unit,
        onReadyToLoad: (File) -> Unit,
        onError: (String) -> Unit
    ) {

        val modelFile =
            File(
                context.filesDir,
                AppConstants.MODEL_FILE
            )

        if (
            modelFile.exists() &&
            modelFile.length() >
            AppConstants.MIN_MODEL_SIZE
        ) {

            onReadyToLoad(modelFile)
            return
        }

        onStatus(
            "Preparing offline model..."
        )

        Thread {

            try {

                val temporaryFile =
                    File(
                        context.filesDir,
                        "${AppConstants.MODEL_FILE}.tmp"
                    )

                if (
                    temporaryFile.exists()
                ) {
                    temporaryFile.delete()
                }

                context.assets
                    .open(AppConstants.MODEL_ASSET)
                    .use { source ->

                        FileOutputStream(
                            temporaryFile
                        ).use { destination ->

                            source.copyTo(
                                destination
                            )
                        }
                    }

                if (
                    !temporaryFile.exists() ||
                    temporaryFile.length() <=
                    AppConstants.MIN_MODEL_SIZE
                ) {

                    throw IllegalStateException(
                        "Bundled model file is incomplete."
                    )
                }

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

                context.mainExecutor.execute {
                    onReadyToLoad(modelFile)
                }

            } catch (e: Exception) {

                AppLogger.e(
                    "Model preparation failed.",
                    e
                )

                context.mainExecutor.execute {
                    onError(
                        e.message
                            ?: "Model preparation failed."
                    )
                }
            }

        }.start()
    }

    fun loadModel(
        file: File,
        onResult: (Boolean) -> Unit
    ) {

        Thread {

            val result =
                try {
                    engine.loadModel(
                        file.absolutePath
                    )
                } catch (e: Exception) {

                    AppLogger.e(
                        "Model loading exception.",
                        e
                    )

                    false
                }

            context.mainExecutor.execute {
                onResult(result)
            }

        }.start()
    }
}