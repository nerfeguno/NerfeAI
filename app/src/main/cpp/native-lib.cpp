#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "NerfeAI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static std::mutex g_mutex;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nerfeai_MainActivity_nativeLoadModel(
        JNIEnv *env,
        jobject,
        jstring pathString) {

    if (pathString == nullptr) {
        return JNI_FALSE;
    }

    const char *path =
            env->GetStringUTFChars(pathString, nullptr);

    if (path == nullptr) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    // Free previous model if one is already loaded.
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_init();

    llama_model_params modelParams =
            llama_model_default_params();

    g_model =
            llama_model_load_from_file(path, modelParams);

    env->ReleaseStringUTFChars(pathString, path);

    if (g_model == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_nerfeai_MainActivity_nativeGenerate(
        JNIEnv *env,
        jobject,
        jstring promptString) {

    if (promptString == nullptr) {
        return env->NewStringUTF(
                "Error: Empty prompt."
        );
    }

    const char *promptChars =
            env->GetStringUTFChars(promptString, nullptr);

    if (promptChars == nullptr) {
        return env->NewStringUTF(
                "Error: Could not read prompt."
        );
    }

    std::string prompt(promptChars);

    env->ReleaseStringUTFChars(
            promptString,
            promptChars
    );

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_model == nullptr) {
        return env->NewStringUTF(
                "Please load a model first."
        );
    }

    /*
     * Larger context allows:
     *
     * - Longer conversation history
     * - Longer user messages
     * - Longer AI responses
     *
     * Qwen2.5 models support much larger contexts,
     * but 4096 is a reasonable Android starting point.
     */
    llama_context_params contextParams =
            llama_context_default_params();

    contextParams.n_ctx = 4096;

    /*
     * Number of tokens processed together.
     */
    contextParams.n_batch = 256;

    /*
     * CPU threads.
     *
     * Keep this moderate for Android.
     */
    contextParams.n_threads = 4;
    contextParams.n_threads_batch = 4;

    llama_context *ctx =
            llama_init_from_model(
                    g_model,
                    contextParams
            );

    if (ctx == nullptr) {
        return env->NewStringUTF(
                "Error: Could not initialize context."
        );
    }

    const llama_vocab *vocab =
            llama_model_get_vocab(g_model);

    /*
     * Allocate enough space for the prompt tokens.
     *
     * prompt.size() is measured in bytes, so this is
     * intentionally larger than the expected token count.
     */
    std::vector<llama_token> tokens(
            prompt.size() + 32
    );

    int32_t tokenCount =
            llama_tokenize(
                    vocab,
                    prompt.c_str(),
                    static_cast<int32_t>(
                            prompt.size()
                    ),
                    tokens.data(),
                    static_cast<int32_t>(
                            tokens.size()
                    ),
                    true,
                    true
            );

    if (tokenCount < 0) {

        /*
         * llama_tokenize may return the required
         * number of tokens when the buffer is too small.
         */
        llama_free(ctx);

        return env->NewStringUTF(
                "Error: Prompt is too large."
        );
    }

    tokens.resize(tokenCount);

    /*
     * Leave enough room inside the 4096-token
     * context for generation.
     *
     * 4096 total context
     * - 512 response tokens
     * = approximately 3584 prompt tokens
     */
    const size_t maxPromptTokens = 3584;

    if (tokens.empty()) {

        llama_free(ctx);

        return env->NewStringUTF(
                "Error: Empty prompt."
        );
    }

    if (tokens.size() > maxPromptTokens) {

        llama_free(ctx);

        return env->NewStringUTF(
                "Conversation is too long. "
                "Start a new chat."
        );
    }

    /*
     * Evaluate the complete prompt.
     */
    llama_batch batch =
            llama_batch_get_one(
                    tokens.data(),
                    static_cast<int32_t>(
                            tokens.size()
                    )
            );

    if (llama_decode(ctx, batch) != 0) {

        llama_free(ctx);

        return env->NewStringUTF(
                "Error: Prompt evaluation failed."
        );
    }

    /*
     * Greedy sampler.
     *
     * This keeps the current behavior of NerfeAI.
     */
    llama_sampler_chain_params samplerParams =
            llama_sampler_chain_default_params();

    llama_sampler *sampler =
            llama_sampler_chain_init(
                    samplerParams
            );

    if (sampler == nullptr) {

        llama_free(ctx);

        return env->NewStringUTF(
                "Error: Could not initialize sampler."
        );
    }

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_greedy()
    );

    /*
     * Maximum number of NEW tokens generated
     * for the assistant response.
     *
     * Previous value:
     *
     *     80
     *
     * New value:
     *
     *     512
     */
    const int maxGeneratedTokens = 512;

    std::string output;

    /*
     * Buffer used to convert generated tokens
     * into text.
     */
    char piece[1024];

    for (int i = 0;
         i < maxGeneratedTokens;
         ++i) {

        llama_token newToken =
                llama_sampler_sample(
                        sampler,
                        ctx,
                        -1
                );

        /*
         * Stop naturally when the model produces
         * an end-of-generation token.
         */
        if (llama_vocab_is_eog(
                    vocab,
                    newToken)) {

            break;
        }

        int pieceLength =
                llama_token_to_piece(
                        vocab,
                        newToken,
                        piece,
                        sizeof(piece),
                        0,
                        true
                );

        if (pieceLength > 0) {
            output.append(
                    piece,
                    pieceLength
            );
        }

        /*
         * Feed the newly generated token back
         * into the model.
         */
        batch =
                llama_batch_get_one(
                        &newToken,
                        1
                );

        if (llama_decode(
                    ctx,
                    batch
            ) != 0) {

            LOGE(
                    "llama_decode failed during generation"
            );

            break;
        }
    }

    llama_sampler_free(sampler);
    llama_free(ctx);

    if (output.empty()) {

        output =
                "No response generated. "
                "Please try again.";
    }

    return env->NewStringUTF(
            output.c_str()
    );
}
