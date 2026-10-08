#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "NerfeAI"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


static constexpr uint32_t NERFEAI_CONTEXT_SIZE = 2048;

static constexpr uint32_t NERFEAI_MAX_GENERATION = 256;

static constexpr uint32_t NERFEAI_MAX_PROMPT_TOKENS =
        NERFEAI_CONTEXT_SIZE - NERFEAI_MAX_GENERATION;

static constexpr uint32_t NERFEAI_BATCH_SIZE = 256;

static constexpr uint32_t NERFEAI_THREADS = 4;


static llama_model *g_model = nullptr;

static std::string g_model_path;

static std::mutex g_mutex;

static bool g_backend_initialized = false;


static bool remove_oldest_conversation_turn(
        std::string &prompt) {

    const std::string userMarker =
            "<|im_start|>user\n";

    const std::string assistantMarker =
            "<|im_start|>assistant\n";

    const std::string endMarker =
            "<|im_end|>\n";


    const size_t firstUser =
            prompt.find(userMarker);

    if (firstUser == std::string::npos) {
        LOGW(
                "No user message found while trimming prompt."
        );

        return false;
    }


    const size_t firstAssistant =
            prompt.find(
                    assistantMarker,
                    firstUser + userMarker.size()
            );

    if (firstAssistant == std::string::npos) {

        LOGW(
                "No complete assistant turn found."
        );

        return false;
    }


    const size_t assistantEnd =
            prompt.find(
                    endMarker,
                    firstAssistant + assistantMarker.size()
            );

    if (assistantEnd == std::string::npos) {

        LOGW(
                "Assistant end marker not found."
        );

        return false;
    }


    const size_t eraseEnd =
            assistantEnd + endMarker.size();


    prompt.erase(
            firstUser,
            eraseEnd - firstUser
    );

    LOGI(
            "Removed oldest conversation turn."
    );

    return true;
}


static int32_t tokenize_prompt(
        const llama_vocab *vocab,
        const std::string &prompt,
        std::vector<llama_token> &tokens) {

    if (vocab == nullptr) {

        LOGE(
                "tokenize_prompt: vocabulary is null."
        );

        return -1;
    }


    const size_t capacity =
            std::max<size_t>(
                    prompt.size() + 32,
                    64
            );


    tokens.resize(capacity);

    const int32_t count =
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


    if (count < 0) {

        LOGE(
                "Tokenization failed. Return=%d, prompt bytes=%zu",
                count,
                prompt.size()
        );

        return count;
    }

    tokens.resize(
            static_cast<size_t>(count)
    );

    return count;
}


static bool fit_prompt_to_context(
        const llama_vocab *vocab,
        std::string &prompt,
        std::vector<llama_token> &tokens) {

    constexpr int MAX_TRIM_ATTEMPTS = 128;


    for (
            int attempt = 0;
            attempt < MAX_TRIM_ATTEMPTS;
            ++attempt) {

        const int32_t count =
                tokenize_prompt(
                        vocab,
                        prompt,
                        tokens
                );

        if (count < 0) {

            LOGE(
                    "Unable to tokenize prompt."
            );

            return false;
        }

        LOGI(
                "Prompt tokens=%d / %u, bytes=%zu",
                count,
                NERFEAI_MAX_PROMPT_TOKENS,
                prompt.size()
        );

        if (
                count <=
                static_cast<int32_t>(
                        NERFEAI_MAX_PROMPT_TOKENS
                )
        ) {

            return true;
        }


        if (
                !remove_oldest_conversation_turn(
                        prompt
                )
        ) {

            LOGW(
                    "Prompt is too large and cannot be trimmed."
            );

            return false;
        }
    }


    LOGE(
            "Prompt trimming exceeded safety limit."
    );


    return false;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_nerfeai_MainActivity_nativeLoadModel(
        JNIEnv *env,
        jobject,
        jstring pathString) {

    if (pathString == nullptr) {

        LOGE(
                "nativeLoadModel: pathString is null."
        );

        return JNI_FALSE;
    }

    const char *pathChars =
            env->GetStringUTFChars(
                    pathString,
                    nullptr
            );

    if (pathChars == nullptr) {

        LOGE(
                "nativeLoadModel: GetStringUTFChars failed."
        );

        return JNI_FALSE;
    }

    const std::string requestedPath(
            pathChars
    );

    env->ReleaseStringUTFChars(
            pathString,
            pathChars
    );


    std::lock_guard<std::mutex> lock(
            g_mutex
    );

    LOGI(
            "Model load requested: %s",
            requestedPath.c_str()
    );

    if (
            g_model != nullptr &&
            g_model_path == requestedPath
    ) {

        LOGI(
                "Model already loaded. Skipping reload."
        );

        return JNI_TRUE;
    }

    if (!g_backend_initialized) {

        LOGI(
                "Initializing llama backend."
        );

        llama_backend_init();

        g_backend_initialized = true;

        LOGI(
                "llama backend initialized."
        );
    }

    if (g_model != nullptr) {

        LOGI(
                "Releasing previously loaded model."
        );

        llama_model_free(
                g_model
        );

        g_model = nullptr;

        g_model_path.clear();
    }

    llama_model_params modelParams =
            llama_model_default_params();

    LOGI(
            "Loading GGUF model..."
    );

    g_model =
            llama_model_load_from_file(
                    requestedPath.c_str(),
                    modelParams
            );

    if (g_model == nullptr) {

        LOGE(
                "Failed to load model: %s",
                requestedPath.c_str()
        );

        return JNI_FALSE;
    }


    g_model_path =
            requestedPath;

    LOGI(
            "Model loaded successfully: %s",
            g_model_path.c_str()
    );

    return JNI_TRUE;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_nerfeai_MainActivity_nativeGenerate(
        JNIEnv *env,
        jobject,
        jstring promptString) {

    if (promptString == nullptr) {

        LOGE(
                "nativeGenerate: promptString is null."
        );

        return env->NewStringUTF(
                "Error: Prompt is null."
        );
    }

    const char *promptChars =
            env->GetStringUTFChars(
                    promptString,
                    nullptr
            );

    if (promptChars == nullptr) {

        LOGE(
                "nativeGenerate: GetStringUTFChars failed."
        );

        return env->NewStringUTF(
                "Error: Could not read prompt."
        );
    }

    std::string prompt(
            promptChars
    );

    env->ReleaseStringUTFChars(
            promptString,
            promptChars
    );

    LOGI(
            "Generation requested. Prompt bytes=%zu",
            prompt.size()
    );

    std::lock_guard<std::mutex> lock(
            g_mutex
    );


    if (g_model == nullptr) {

        LOGE(
                "Generation requested without loaded model."
        );

        return env->NewStringUTF(
                "Please wait for the AI model to finish loading."
        );
    }

    const llama_vocab *vocab =
            llama_model_get_vocab(
                    g_model
            );

    if (vocab == nullptr) {

        LOGE(
                "Could not obtain model vocabulary."
        );

        return env->NewStringUTF(
                "Error: Could not access model vocabulary."
        );
    }

    std::vector<llama_token> tokens;

    if (
            !fit_prompt_to_context(
                    vocab,
                    prompt,
                    tokens
            )
    ) {

        LOGE(
                "Prompt could not be fitted into context."
        );

        return env->NewStringUTF(
                "This message is too large for the current "
                "context. Please shorten the latest message."
        );
    }

    if (tokens.empty()) {

        LOGE(
                "Tokenized prompt is empty."
        );

        return env->NewStringUTF(
                "Error: Empty prompt."
        );
    }

    LOGI(
            "Final prompt token count=%zu",
            tokens.size()
    );

    llama_context_params contextParams =
            llama_context_default_params();


    contextParams.n_ctx =
            NERFEAI_CONTEXT_SIZE;

    contextParams.n_batch =
            NERFEAI_BATCH_SIZE;

    contextParams.n_threads =
            NERFEAI_THREADS;

    contextParams.n_threads_batch =
            NERFEAI_THREADS;

    LOGI(
            "Creating llama context. n_ctx=%u, n_batch=%u, threads=%u",
            NERFEAI_CONTEXT_SIZE,
            NERFEAI_BATCH_SIZE,
            NERFEAI_THREADS
    );


    llama_context *ctx =
            llama_init_from_model(
                    g_model,
                    contextParams
            );

    if (ctx == nullptr) {

        LOGE(
                "llama_init_from_model failed."
        );

        return env->NewStringUTF(
                "Error: Could not initialize AI context."
        );
    }

    LOGI(
            "llama context created successfully."
    );

    llama_batch batch =
            llama_batch_get_one(
                    tokens.data(),
                    static_cast<int32_t>(
                            tokens.size()
                    )
            );

    LOGI(
            "Evaluating initial prompt."
    );

    const int promptResult =
            llama_decode(
                    ctx,
                    batch
            );


    if (promptResult != 0) {

        LOGE(
                "Initial prompt evaluation failed: %d",
                promptResult
        );

        llama_free(
                ctx
        );


        return env->NewStringUTF(
                "Error: Prompt evaluation failed."
        );
    }

    LOGI(
            "Initial prompt evaluation completed."
    );

    llama_sampler_chain_params samplerParams =
            llama_sampler_chain_default_params();

    llama_sampler *sampler =
            llama_sampler_chain_init(
                    samplerParams
            );

    if (sampler == nullptr) {

        LOGE(
                "Could not initialize sampler."
        );

        llama_free(
                ctx
        );

        return env->NewStringUTF(
                "Error: Could not initialize sampler."
        );
    }

    llama_sampler_chain_add(
            sampler,
            llama_sampler_init_greedy()
    );

    std::string output;

    output.reserve(
            4096
    );

    char piece[1024];

    LOGI(
            "Generation started. Maximum output tokens=%u",
            NERFEAI_MAX_GENERATION
    );

    uint32_t generatedTokens = 0;


    for (
            uint32_t i = 0;
            i < NERFEAI_MAX_GENERATION;
            ++i) {

        const llama_token newToken =
                llama_sampler_sample(
                        sampler,
                        ctx,
                        -1
                );

        if (
                llama_vocab_is_eog(
                        vocab,
                        newToken
                )
        ) {

            LOGI(
                    "End-of-generation token received at %u.",
                    i
            );

            break;
        }

        const int pieceLength =
                llama_token_to_piece(
                        vocab,

                        newToken,

                        piece,

                        sizeof(piece),

                        0,

                        true
                );

        if (pieceLength < 0) {

            LOGE(
                    "Token-to-piece failed at token %u. Error=%d",
                    i,
                    pieceLength
            );

            break;
        }

        if (pieceLength > 0) {

            output.append(
                    piece,
                    static_cast<size_t>(
                            pieceLength
                    )
            );
        }

        generatedTokens++;

        batch =
                llama_batch_get_one(
                        &newToken,
                        1
                );

        const int decodeResult =
                llama_decode(
                        ctx,
                        batch
                );

        if (decodeResult != 0) {

            LOGE(
                    "Generation decode failed at token %u: %d",
                    i,
                    decodeResult
            );

            break;
        }

        if (
                (i + 1) % 32 == 0
        ) {

            LOGI(
                    "Generated %u tokens.",
                    i + 1
            );
        }
    }

    llama_sampler_free(
            sampler
    );

    llama_free(
            ctx
    );

    LOGI(
            "Generation finished. Generated tokens=%u, output bytes=%zu",
            generatedTokens,
            output.size()
    );

    if (
            output.empty()
    ) {

        output =
                "No response generated. Please try again.";
    }

    return env->NewStringUTF(
            output.c_str()
    );
}
