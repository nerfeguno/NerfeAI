# NerfeAI Android

**An offline-first AI chatbot for Android that runs a language model locally on the device.**

NerfeAI Android is a Kotlin application that loads a GGUF language model and generates text on-device through `llama.cpp`, called from Kotlin via JNI. Ordinary text generation does not depend on a cloud AI API.

> **How to read this document**
>
> Repository: <https://github.com/nerfeguno/NerfeAI> (public, `main` branch).
>
> This README was checked against the repository root, `build.gradle`, `settings.gradle`, `gradle.properties`, `app/build.gradle`, `app/src/main/AndroidManifest.xml`, and `app/src/main/java/com/nerfeai/MainActivity.kt`.
>
> **Not inspected:** `app/src/main/cpp/native-lib.cpp`, `app/src/main/cpp/CMakeLists.txt`, `.github/workflows/android.yml`, `.gitignore`, and the local `third_party/` folder. Anything depending on those files is marked **Needs verification**.

---

## Table of Contents

1. [Project Introduction](#1-project-introduction)
2. [Features](#2-features)
3. [Technology Stack](#3-technology-stack)
4. [Repository Structure](#4-repository-structure)
5. [System Requirements](#5-system-requirements)
6. [Installation and Setup](#6-installation-and-setup)
7. [Building the APK](#7-building-the-apk)
8. [GitHub Actions](#8-github-actions)
9. [Using NerfeAI](#9-using-nerfeai)
10. [Architecture and Data Flow](#10-architecture-and-data-flow)
11. [Model Documentation](#11-model-documentation)
12. [Contributing Guide](#12-contributing-guide)
13. [Coding Standards](#13-coding-standards)
14. [Testing Guide](#14-testing-guide)
15. [Troubleshooting](#15-troubleshooting)
16. [Privacy and Security](#16-privacy-and-security)
17. [Known Limitations](#17-known-limitations)
18. [Roadmap](#18-roadmap)
19. [License and Attribution](#19-license-and-attribution)
20. [Quick Start for Contributors](#20-quick-start-for-contributors)

---

## 1. Project Introduction

### What it is

NerfeAI Android is a chat application for Android. You type a message, and a language model running **on your phone** writes the reply. The interface is inspired by popular chat apps: a message composer, a send button, controls for starting a new conversation, appearance settings, and a conversation history view.

### Purpose and motivation

Most chatbots send your text to a remote server. NerfeAI explores the opposite approach: keep the model and the conversation on the device, so the app can work without a cloud AI service once the model is in place.

### Main goals

- Run a GGUF language model locally on Android through `llama.cpp`.
- Provide a clean, familiar chat interface written in Kotlin.
- Keep the build reproducible and automated through Gradle and GitHub Actions.
- Stay small and understandable enough for new contributors to learn from.

### Offline-first design

"Offline-first" means the app is designed so that **text generation does not need the internet**. Internet access may still be needed for development and setup (downloading Gradle dependencies, the Android SDK, and the model file). The app's manifest does not request the internet permission (see [Privacy and Security](#16-privacy-and-security)); the native code has not been checked.

### Target users and use cases

| Audience | Example use |
| --- | --- |
| Privacy-minded users | Chatting with a model without sending prompts to a cloud API |
| Android developers | Learning how to embed `llama.cpp` in an app using JNI |
| Hobbyists and tinkerers | Experimenting with small quantized models on a phone |
| Contributors | Improving the UI, native backend, tests, and documentation |

### Current development status

The project is **under active development** and its implementation may change. The core pieces described by the maintainer are:

- Kotlin application with a main activity (`MainActivity.kt`).
- Native inference through `llama.cpp` and JNI.
- A GGUF model file named `nerfeai-model.gguf`.
- A GitHub Actions workflow (`.github/workflows/android.yml`) for APK builds.

The status of each individual feature is listed in the next section.

---

## 2. Features

This table is based on `MainActivity.kt`, `app/build.gradle`, and `AndroidManifest.xml`. Native-side behavior depends on `native-lib.cpp`, which was **not** inspected.

| Feature | Status | Notes |
| --- | --- | --- |
| Local AI text generation | Implemented in the app | `MainActivity` calls `nativeGenerate(prompt)` on a background thread. Output quality depends on the native code (Needs verification). |
| GGUF model loading | Implemented | Model is copied from APK assets to app storage, then loaded with `nativeLoadModel(path)` |
| Kotlin Android interface | Implemented | Built in code with the platform `Activity` and views |
| Native inference through `llama.cpp` | Wiring present | `libnerfeai` is loaded and CMake is configured. The `llama.cpp` integration itself (**Needs verification**) lives in `native-lib.cpp` / `third_party/`. |
| Chat-style messaging | Implemented | Message bubbles, composer, **Send** button, keyboard Send action |
| New conversations | Implemented | **＋** in the top bar and **New chat** in the side panel |
| Persistent chat history | Implemented | Saved as JSON in `SharedPreferences` (unencrypted) |
| Opening previous conversations | Implemented | Tap a chat in the side panel |
| Deleting individual conversations | Implemented | **×** button with a confirmation dialog |
| Clearing all history | Implemented | **Delete all chat history** with a confirmation dialog |
| Light and dark appearance | Implemented | Toggle in the side panel. The choice survives rotation but **not** an app restart (see [Known Limitations](#17-known-limitations)). |
| Context trimming | Implemented | Only the last 4 user turns are kept in the prompt history |
| Overlapping-request guard | Implemented | Send, new chat, open chat, and delete are blocked while generating |
| GitHub Actions APK generation | Workflow file exists | Whether it succeeds and uploads an artifact: **Needs verification** |
| Streaming output, stop, regenerate, copy button, export, search, rename, generation settings | Not implemented | Not present in `MainActivity.kt`. Message text is selectable. See [Roadmap](#18-roadmap). |

---

## 3. Technology Stack

| Technology | Role in the project | Why it is used |
| --- | --- | --- |
| **Kotlin** | Android UI and application logic | Modern, officially supported language for Android |
| **Android SDK** | Platform APIs, build tools, emulator | Required to build and run any Android app |
| **Gradle** (Groovy DSL) | Build system (`build.gradle`, `settings.gradle`) | Standard for Android; compiles code, packages the APK, runs the native build |
| **C / C++** | Native inference integration | `llama.cpp` is written in C/C++; native code gives direct access to it |
| **JNI** (Java Native Interface) | Bridge between Kotlin and native code | Lets Kotlin call C/C++ functions and receive results |
| **`llama.cpp`** | Local language-model inference engine | Runs quantized models efficiently on consumer hardware, including ARM CPUs |
| **GGUF** | Model file format | The format `llama.cpp` loads; stores weights, tokenizer data, and metadata in one file |
| **Git and GitHub** | Version control and collaboration | Issues, pull requests, code review |
| **GitHub Actions** | Continuous integration | Builds the APK automatically on GitHub's servers |
| **Termux and Arch Linux** | Optional development environments | Allow building or editing from an Android device (see the note in [System Requirements](#5-system-requirements)) |

The UI is built in code with the platform `Activity` and standard views. `app/build.gradle` declares no XML layouts, Jetpack Compose, or library dependencies.

---

## 4. Repository Structure

Confirmed from the GitHub root listing and a local checkout in Termux (`~/NerfeAI-Android`):

```text
NerfeAI/
├── .github/workflows/
│   └── android.yml                  # APK build workflow (contents not inspected)
├── app/
│   ├── build.gradle                 # App module config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt       # Native build (contents not inspected)
│       │   └── native-lib.cpp       # JNI + inference code (not inspected)
│       └── java/com/nerfeai/
│           └── MainActivity.kt      # Entire UI and app logic
├── third_party/                     # Exists locally; NOT in the GitHub root listing
├── .gitignore
├── build.gradle                     # Plugin versions (Groovy DSL)
├── gradle.properties
└── settings.gradle
```

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/nerfeai/MainActivity.kt` | UI, model preparation and loading, prompt building, chat persistence |
| `app/src/main/cpp/native-lib.cpp` | Native side of the two JNI methods. **Needs verification.** |
| `app/src/main/cpp/CMakeLists.txt` | Builds the native library. `app/build.gradle` loads it from this path with CMake `3.22.1`. |
| `third_party/` | Present locally but not on GitHub. Likely holds `llama.cpp`; **Needs verification.** |
| `app/src/main/assets/nerfeai-model.gguf` | Where the bundled model is expected (see below). **Not present** in the local checkout. |

**Not present:** `app/src/main/res/`, `app/src/test/`, `app/src/androidTest/`, `gradlew`, `gradle/wrapper/`, `LICENSE`, `README.md` (before this file).

**Model location:** `MainActivity.kt` calls `assets.open("nerfeai-model.gguf")`, and `app/build.gradle` has no custom source sets. The model therefore has to be at `app/src/main/assets/nerfeai-model.gguf` at build time. That folder does not exist in the local checkout, so the model is probably added at build time or kept out of Git. Check `.gitignore` and `android.yml`.

---

## 5. System Requirements

### Confirmed from the project

| Setting | Value | Source |
| --- | --- | --- |
| Android Gradle Plugin | `8.7.3` | `build.gradle` |
| Kotlin Android plugin | `2.0.21` | `build.gradle` |
| Java / Kotlin target | 17 | `app/build.gradle` |
| `compileSdk` / `targetSdk` | 35 | `app/build.gradle` |
| `minSdk` | 26 (Android 8.0) | `app/build.gradle` |
| NDK | `27.2.12479018` | `app/build.gradle` |
| CMake | `3.22.1` | `app/build.gradle` |
| Native ABI | `arm64-v8a` only | `app/build.gradle` |
| C++ standard / STL | C++17, `c++_shared` | `app/build.gradle` |
| Model files not compressed in APK | `.gguf` | `app/build.gradle` |
| Gradle daemon heap / workers | `-Xmx3g` / 2, parallel off | `gradle.properties` |
| Repository mode | `FAIL_ON_PROJECT_REPOS` | `settings.gradle` |

`FAIL_ON_PROJECT_REPOS` means repositories must be declared in `settings.gradle`, not in `app/build.gradle`.

### Requirements

| Requirement | Details |
| --- | --- |
| **JDK** | JDK 17 or newer (the project targets Java 17, and AGP 8.x requires 17+). The JDK used in CI: **Needs verification.** |
| **Android SDK** | Platform 35 and matching build-tools (install through SDK Manager). |
| **NDK and CMake** | NDK `27.2.12479018` and CMake `3.22.1`, installed through **SDK Manager → SDK Tools**. |
| **Gradle** | No wrapper in the repository. Use an installed Gradle compatible with AGP 8.7.3 (Android's compatibility table lists 8.9 as the minimum; verify). See [Building the APK](#7-building-the-apk). |
| **Git** | For cloning and contributing. |
| **Device** | An **arm64** Android 8.0+ device. A physical phone is strongly recommended. Because only `arm64-v8a` is built, x86/x86_64 emulators will not run the app. |
| **Storage** | The model is stored **twice** on the device: once inside the APK and once copied to app storage. Plan for roughly twice the model size, plus the APK and build output on your computer. |
| **RAM** | The model is loaded into memory, which limits usable model size. The build machine also needs RAM for a 3 GB Gradle heap plus the native compile. |
| **Internet access** | For the initial clone, Gradle dependencies, SDK/NDK downloads, and the model download. The app itself declares no internet permission (see [Privacy and Security](#16-privacy-and-security)). |

**Termux / Arch Linux note:** Official Android SDK and NDK binaries target specific host platforms, so a full on-device Gradle build can be difficult. Whether your Termux setup can run it **needs verification**. GitHub Actions is the alternative (see [GitHub Actions](#8-github-actions)).

---

## 6. Installation and Setup

### Step 1: Clone the repository

```bash
git clone https://github.com/nerfeguno/NerfeAI.git
```

The repository root has no `.gitmodules` file, so `llama.cpp` is not a root-level submodule. A local `third_party/` folder exists in the maintainer's checkout but is **not** in the GitHub root listing, so a fresh clone may be missing `llama.cpp` (or whatever `third_party/` holds). How it should be obtained **needs verification**. Check `.gitignore`, `CMakeLists.txt`, and `android.yml`.

### Step 2: Enter the project directory

```bash
cd NerfeAI
```

### Step 3: Open the project in Android Studio

1. Start Android Studio.
2. Choose **Open** and select the project's root folder (the one containing `settings.gradle(.kts)`).
3. Wait for the initial indexing to finish.

### Step 4: Configure the JDK and Android SDK

1. **JDK:** In Android Studio, go to **Settings → Build, Execution, Deployment → Build Tools → Gradle** and set **Gradle JDK** to JDK 17 or newer.
2. **SDK:** Go to **Settings → Languages & Frameworks → Android SDK**. Install the SDK platform and build tools the project requires.
3. **NDK and CMake:** Under the **SDK Tools** tab (enable **Show Package Details**), install NDK `27.2.12479018` and CMake `3.22.1`.
4. If building from the command line, make sure Gradle can find the SDK. Android Studio normally creates `local.properties` automatically:

```properties
sdk.dir=/path/to/Android/Sdk
```

Do not commit `local.properties`; it is machine-specific.

### Step 5: Sync Gradle

Use **File → Sync Project with Gradle Files**. Android Studio can sync without a wrapper if a compatible Gradle is configured. From a terminal (requires an installed Gradle, since there is no wrapper yet):

```bash
gradle --no-daemon tasks
```

If this prints a task list without errors, the toolchain is set up.

### Step 6: Obtain the GGUF model

The app bundles its model as an APK asset named `nerfeai-model.gguf`.

- **Exact model name, source URL, license, and checksum: Needs verification.** The app's status text says "Qwen loaded", but that string is hard-coded, so it does not prove which model is installed.
- Download the model from its official source and verify it (see [Model Documentation](#11-model-documentation)).
- Place it at:

```text
app/src/main/assets/nerfeai-model.gguf
```

Create the `assets` folder if it does not exist. Do not commit the model unless you have decided to (see [Model Documentation](#11-model-documentation)).

### Step 7: Check the filename, path, and size

Confirm:

- The filename matches `nerfeai-model.gguf` exactly (case-sensitive). `MainActivity.kt` uses this name for both the asset and the copied file.
- The file is **larger than 400,000,000 bytes (about 400 MB)**. On launch the app only reuses its copied model if that file exists *and* exceeds this size. A smaller model is re-copied from the APK on every launch.

```bash
ls -l app/src/main/assets/nerfeai-model.gguf
grep -rn "nerfeai-model" app/ .github/ 2>/dev/null
```

### Step 8: Run on an Android device

Use a physical **arm64** device with Android 8.0 or newer.

1. On the phone, enable **Developer options** and **USB debugging**.
2. Connect the phone by USB and accept the debugging prompt.
3. Verify it is detected:

```bash
adb devices
```

4. In Android Studio, select the device and press **Run**, or install from the terminal:

```bash
gradle --no-daemon installDebug
```

---

## 7. Building the APK

### Gradle wrapper status

The repository root does **not** contain `gradlew`, `gradlew.bat`, or `gradle/wrapper/`. That means `./gradlew` will fail on a fresh clone. This README therefore uses an installed `gradle` in its commands.

Recommended fix: generate the wrapper once with a Gradle version compatible with AGP 8.7.3 (Android's compatibility table lists 8.9 as the minimum; confirm against the version CI uses), then commit it:

```bash
gradle wrapper --gradle-version <compatible-version>
git add gradlew gradlew.bat gradle/wrapper
git commit -m "Add Gradle wrapper"
```

After that, replace `gradle` with `./gradlew` in the commands below. A wrapper gives every contributor and CI the same Gradle version.

### Debug APK

```bash
gradle --no-daemon assembleDebug
```

Usual output location for the `app` module (standard Android layout; **verify on your machine**):

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release APK

```bash
gradle --no-daemon assembleRelease
```

Usual output location:

```text
app/build/outputs/apk/release/
```

In the current `app/build.gradle`, the release build type uses `signingConfig signingConfigs.debug`, so the release APK is signed with the **debug key** and is installable. It is not a production signature. See [signing](#signing-and-app-updates). Minification is off for both build types.

### Before you build

- The model must be at `app/src/main/assets/nerfeai-model.gguf`, or the app will start but fail with "Could not prepare bundled model."
- The native build needs NDK `27.2.12479018` and CMake `3.22.1`, and whatever `third_party/` provides (**Needs verification**).
- The first build compiles the native code and can take a long time on a slow machine.

### Verifying the actual output path

Do not rely on assumptions. After a build, list the APKs:

```bash
find app/build/outputs -name "*.apk" -print
```

Customized Gradle configurations (flavors, renamed outputs, different module names) change this path. Use the result of `find` as the truth.

---

## 8. GitHub Actions

The workflow lives at `.github/workflows/android.yml`. Its exact steps **need verification**; open the file and confirm the details below. Because the repository has no Gradle wrapper, the workflow must install or provide Gradle itself; check how it does that.

### How the workflow operates

A typical Android build workflow does the following. Confirm each item in your file:

1. Checks out the repository (and submodules, if needed).
2. Sets up a JDK.
3. Sets up the Android SDK / NDK / CMake as needed.
4. Runs a Gradle build such as `gradle --no-daemon assembleDebug`.
5. Uploads the resulting APK as a workflow **artifact**.

### Triggering a build

The `on:` section of `android.yml` decides when it runs (for example on `push`, `pull_request`, or manual `workflow_dispatch`). If it triggers on push:

```bash
git add .
git commit -m "Describe the change"
git push
```

If a manual trigger is configured, open the **Actions** tab, select the workflow, and click **Run workflow**.

### Inspecting build logs

1. Open the repository on GitHub and click the **Actions** tab.
2. Select the workflow run.
3. Click the job, then expand each step to read its log.
4. For deeper detail, re-run with debug logging enabled from the run's **Re-run jobs** menu.

### Downloading the APK artifact

1. Open the finished workflow run.
2. Scroll to the **Artifacts** section at the bottom of the run summary.
3. Download the artifact (a `.zip`), extract it, and install the `.apk` on your device.

Artifacts expire after the retention period configured for the repository or workflow.

### Diagnosing missing artifacts

If the run succeeds but no artifact appears:

- Check that the upload step actually ran (it may be skipped by an `if:` condition or an earlier failure).
- Check the upload step's log for a warning like "No files were found with the provided path."
- Compare the **Gradle output path** with the **upload `path:`** in the workflow (next section).
- Confirm the build task you run matches the APK you expect (`assembleDebug` produces a debug APK, not a release APK).
- Check whether the artifact expired.

### Verifying the Gradle output path matches the upload path

Add a temporary diagnostic step before the upload step:

```yaml
- name: List generated APKs
  run: find . -name "*.apk" -print
```

Then make sure the upload step's `path:` matches what is printed. Example of a matching pair (adjust to your project):

```yaml
- name: Upload APK
  uses: actions/upload-artifact@v4   # verify the version your workflow uses
  with:
    name: nerfeai-debug-apk
    path: app/build/outputs/apk/debug/*.apk
    if-no-files-found: error
```

Setting `if-no-files-found: error` makes the workflow fail loudly instead of silently producing no artifact.

### What CI must provide (Needs verification)

A fresh clone of the GitHub repository does not contain two things the build needs, based on the root listing and local checkout:

- the model file at `app/src/main/assets/nerfeai-model.gguf`
- the contents of `third_party/` (the folder exists locally, not on GitHub)

Check that `android.yml` downloads or creates both before running Gradle. Otherwise, the build may fail at the native step, or the APK may build but fail at runtime because the asset is missing.

### Signing and app updates

Android only allows an app to be updated if the new APK is signed with the **same key** as the installed one.

| APK type | Signing | Effect on updates |
| --- | --- | --- |
| Debug | Signed with an auto-generated debug key. Each machine (including each CI runner) may have a **different** key. | An APK built on GitHub may not update one built on your laptop; Android reports a signature conflict. |
| Release (**current config**) | `signingConfig signingConfigs.debug`, so it uses the same per-machine debug key. | Same update behavior as debug builds. It does not give you a stable release identity. |
| Release (your own key) | Consistent key across builds. | Updates install over earlier builds signed with the same key. |

If you switch signing keys, you must uninstall the old app first, which **deletes its local data** (including saved chats). Back up anything important before doing that.

### Configuring release signing securely

Never commit a keystore (`.jks` / `.keystore`), passwords, or key aliases to the repository.

1. **Generate a keystore locally** and keep it somewhere safe and backed up:

```bash
keytool -genkeypair -v -keystore nerfeai-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias nerfeai
```

2. **Add the keystore and passwords as GitHub secrets** (Repository → Settings → Secrets and variables → Actions). Store the keystore as Base64:

```bash
base64 -w 0 nerfeai-release.jks   # copy the output into a secret, e.g. KEYSTORE_BASE64
```

3. **Decode it in the workflow** at build time and pass credentials as environment variables (secret names below are examples):

```yaml
- name: Decode keystore
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"

- name: Build release APK
  env:
    KEYSTORE_PATH: ${{ runner.temp }}/release.jks
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: gradle --no-daemon assembleRelease
```

4. **Read the values in Gradle** from environment variables rather than hard-coding them. This project uses the Groovy DSL. In `app/build.gradle`, add the `signingConfigs` block and replace the existing `signingConfig signingConfigs.debug` line in the `release` build type:

```groovy
android {
    signingConfigs {
        release {
            def path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile file(path)
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

Note that GitHub does not pass secrets to workflows triggered from forks, which protects your key but means signed release builds only run on your own branches.

---

## 9. Using NerfeAI

1. **Launch the app.** The title bar shows NerfeAI and a status line.
2. **Wait for the model.** The status moves through "Preparing offline assistant...", "Preparing offline model..." (first launch only: the model is copied out of the APK), "Loading AI model into memory...", and finally "Qwen loaded • Offline mode ready". The **Send** button stays disabled until loading succeeds. If something fails you will see "Could not prepare bundled model." or "Model loading failed."
3. **Send a message.** Type in the **Message NerfeAI** box and tap **Send** (or press the keyboard's send key).
4. **Receive a response.** A "Thinking…" placeholder appears. The full answer replaces it when generation finishes. There is no word-by-word streaming and no stop button.
5. **Start a new conversation.** Tap **＋** in the top bar, or open the ☰ menu and choose **New chat**.
6. **Open saved chats.** Open the ☰ menu. Saved chats are listed under "YOUR SPACE", titled by the first 36 characters of your first message. Tap one to continue it.
7. **Delete chats.** Tap **×** next to a chat and confirm. **Delete all chat history** at the bottom of the menu removes everything after confirmation.
8. **Change appearance.** Use **Light appearance** / **Dark appearance** in the ☰ menu.
9. **Copy text.** Long-press message text to select and copy it.

New chat, opening a chat, and deleting are blocked while a response is being generated; the app shows "Please wait for the current response to finish."

### Why is the first response slow?

- On first launch the app copies the entire model out of the APK, then loads it into RAM.
- Inference runs on the phone's CPU (assuming the native build uses no GPU backend; **Needs verification**), which is much slower than a data center.
- Longer conversations produce longer prompts (up to the last 4 user turns), which take longer to process.
- Phones may throttle performance when hot or in battery-saver mode.

---

## 10. Architecture and Data Flow

```text
User Input
    ↓
Kotlin Android UI
    ↓
Conversation Context and Prompt Formatting
    ↓
JNI Interface
    ↓
Native Inference Backend
    ↓
llama.cpp
    ↓
GGUF Language Model
    ↓
Generated Response
    ↓
Android Chat Interface
    ↓
Local Conversation Storage (SharedPreferences)
```

### Component responsibilities

| Component | Responsibility |
| --- | --- |
| **`MainActivity.kt`** | Everything on the Kotlin side: builds the UI in code, copies and loads the model, builds prompts, runs generation on a background `Thread`, renders messages, and saves chats to `SharedPreferences` |
| **Prompt formatting** | In `sendMessage()`. Uses ChatML-style markers (`<\|im_start\|>` / `<\|im_end\|>`) with the system prompt "You are NerfeAI, a helpful offline AI assistant. Answer clearly and honestly." |
| **JNI layer** | Two `external` methods in `MainActivity`: `nativeLoadModel(path: String): Boolean` and `nativeGenerate(prompt: String): String`. The C++ side (`native-lib.cpp`) should export `Java_com_nerfeai_MainActivity_nativeLoadModel` and `Java_com_nerfeai_MainActivity_nativeGenerate`. **Needs verification.** |
| **Native library** | Loaded with `System.loadLibrary("nerfeai")`, so CMake must produce `libnerfeai.so` |
| **Native inference backend / `llama.cpp`** | Loads the model, tokenizes, generates, and decides when to stop. **Needs verification** in `native-lib.cpp`. |
| **Model-loading process** | `prepareBundledModel()` reuses `filesDir/nerfeai-model.gguf` if it exists and is larger than 400,000,000 bytes. Otherwise it copies the asset there. Then `loadModel()` calls `nativeLoadModel` on a background thread. |
| **Chat storage** | `SharedPreferences` file `nerfeai_chat_storage`, keys `chats_json` and `current_chat_id` |
| **Gradle configuration** | Builds Kotlin and native code (CMake) for `arm64-v8a` and packages the model as an uncompressed asset |
| **GitHub Actions** | Builds the APK on GitHub's servers (details **need verification**) |

---

## 11. Model Documentation

### What is GGUF?

GGUF is a single-file model format used by `llama.cpp`. It stores the weights, tokenizer data, and metadata, which makes models easy to distribute and load.

### Model details for this project

| Item | Value |
| --- | --- |
| Filename | `nerfeai-model.gguf` (asset name and copied-file name) |
| Expected asset location | `app/src/main/assets/nerfeai-model.gguf` |
| Runtime location | `filesDir/nerfeai-model.gguf` (app-private storage) |
| Model family | The UI text says "Qwen" (hard-coded). **Needs verification.** |
| Exact model name, source URL, license, quantization, checksum | **Needs verification** |
| Prompt format used by the app | ChatML-style (`<\|im_start\|>role ... <\|im_end\|>`). Qwen-family chat models commonly use this format, but confirm for your exact model. |
| Stop tokens | Handled in `native-lib.cpp`. **Needs verification.** |

### How the model is obtained, stored, and loaded

1. The GGUF file is placed in `app/src/main/assets/` before building. It is stored uncompressed (`noCompress += ['gguf']`).
2. On first launch the app copies it from the APK to the app's private files directory.
3. On later launches it reuses that copy if it is larger than 400,000,000 bytes. A smaller model is copied again on every launch.
4. `nativeLoadModel(path)` loads the copy into memory.

Two copies of the model exist on the device (inside the APK and in app storage).

### Verifying a model download

1. Download only from the model publisher's official page.
2. Compare the file size with the published size.
3. Compare the checksum:

```bash
sha256sum app/src/main/assets/nerfeai-model.gguf
```

4. Check that the file starts with the ASCII characters `GGUF`:

```bash
head -c 4 app/src/main/assets/nerfeai-model.gguf; echo
```

### Why not commit large model files to Git?

- Git keeps every version of every file forever, so a multi-gigabyte model permanently bloats the repository.
- GitHub rejects files over its size limit, and clones become slow.
- Model licenses may restrict redistribution.

Add the model path to `.gitignore` and document where to download it. If you must track it, consider Git LFS or release assets, after checking the license.

### Quantization

Quantization stores model weights using fewer bits.

| Effect | Lower-bit quantization | Higher-bit quantization |
| --- | --- | --- |
| File size | Smaller | Larger |
| RAM usage | Lower | Higher |
| Speed | Often faster on phones | Often slower |
| Output quality | Can degrade | Closer to the original |

Pick one that fits your device's RAM with room to spare. Remember the 400 MB threshold described above.

### Chat templates and stop tokens

Chat models are trained with a specific format for system, user, and assistant turns, and with specific end-of-turn tokens. If `MainActivity.kt` formats prompts differently, or generation does not stop at the right token, you may see broken output, repeated text, the model talking as the user, or endless generation. If you change models, update the prompt format in `sendMessage()` and the stop handling in the native code.

---

## 12. Contributing Guide

Contributions of all kinds are welcome, including bug reports, code, tests, and documentation.

### Types of changes

| Type | Meaning | Example |
| --- | --- | --- |
| **Bug fix** | Corrects behavior that is wrong | App crashes on screen rotation |
| **Feature** | Adds new behavior | Add a copy-message button |
| **Documentation** | Improves docs only; no behavior change | Clarify model setup in this README |
| **Refactoring** | Restructures code without changing behavior | Move prompt formatting into its own class |

Keep each pull request to **one** type of change where practical.

### Workflow

**1. Check existing issues and pull requests.** Search the repository's **Issues** and **Pull requests** tabs to avoid duplicating work. For larger changes, open an issue first to discuss the approach.

**2. Fork or clone.** External contributors should fork on GitHub, then clone their fork. Collaborators with write access can clone directly.

```bash
# Forked on GitHub? Clone YOUR fork, then add the original repo as "upstream":
git clone https://github.com/<your-github-username>/NerfeAI.git
cd NerfeAI
git remote add upstream https://github.com/nerfeguno/NerfeAI.git

# Have write access? Clone the main repository directly instead:
# git clone https://github.com/nerfeguno/NerfeAI.git
```

**3. Create a feature branch.**

```bash
git switch -c feature/short-description
# Examples: fix/rotation-crash, docs/readme-update, refactor/prompt-builder
```

**4. Make focused changes.** Change only what is needed for the task. Do not mix unrelated formatting changes.

**5. Build and test.**

```bash
gradle --no-daemon assembleDebug
```

Then run through the relevant parts of the [Testing Guide](#14-testing-guide) on a device.

**6. Commit with a meaningful message.**

```bash
git status
git add path/to/changed/files
git commit -m "Describe the change"
```

Good messages are short, specific, and in the imperative mood: `Fix crash when rotating during generation`, not `fixed stuff`.

**7. Push the branch.**

```bash
git push -u origin feature/short-description
```

**8. Open a pull request** on GitHub, targeting the repository's main branch.

**9. Describe your changes and test results.** Include what changed and why, how you tested it (device, Android version, model used), screenshots for UI changes, and any known limitations.

**10. Respond to review feedback.** Push follow-up commits to the same branch; the pull request updates automatically.

```bash
git add path/to/changed/files
git commit -m "Address review feedback"
git push
```

To keep your branch current with the main branch:

```bash
git fetch upstream      # or: git fetch origin
git rebase upstream/main  # or merge; follow the maintainer's preference
```

Never commit model files, keystores, passwords, `local.properties`, or private conversation data.

---

## 13. Coding Standards

These are practical guidelines for a small project, not strict rules.

**Kotlin style and naming**
- Follow the official Kotlin coding conventions: `PascalCase` for classes, `camelCase` for functions and variables, `UPPER_SNAKE_CASE` for constants.
- Prefer small functions with clear names; avoid putting everything in `MainActivity.kt` as the app grows.

**Readable, maintainable code**
- Comment the *why*, not the *what*, especially in JNI and prompt-formatting code.
- Remove dead code instead of commenting it out.

**UI responsiveness and threading**
- Never run model loading or inference on the main thread. Use coroutines, an executor, or another background mechanism, and post results back to the UI thread.
- Show a visible loading or "generating" state.

**Avoid overlapping generation requests**
- Disable the send button, or queue/ignore requests, while a generation is running. The native context is generally not safe to use from multiple threads at once.

**JNI compatibility**
- Native function names and signatures must exactly match the Kotlin `external` declarations (package, class, method name, parameter types).
- If you rename or move a class, update the JNI side in the same change.
- Never let a native exception escape; return error codes or messages.

**Native resource management**
- Free models, contexts, and buffers when they are no longer needed. Make sure every allocation has a matching release.
- Guard against use-after-free if the activity is destroyed during generation.

**Error handling**
- Handle a missing model file, a corrupt model, a failed native library load, and an out-of-memory condition with a clear message to the user, not a crash.

**Chat history persistence**
- Store conversations in a structured local format or database, and write changes on a background thread.
- Handle corrupt or missing data gracefully.

**Accessibility**
- Provide content descriptions for icon buttons, keep touch targets large enough, and keep text readable in both light and dark appearances. Support system font scaling.

**Memory and performance**
- Keep prompt context bounded; trim or summarize old messages when the context window fills.
- Avoid holding large objects (such as full chat histories) in memory unnecessarily.
- Test with the smallest supported device you can.

**Logging**
- Do not log prompts or responses in release builds (see [Privacy and Security](#16-privacy-and-security)).

---

## 14. Testing Guide

Run these manually on a real arm64 device.

### Checklist

**Installation and model**
- [ ] The APK installs without errors on a clean device.
- [ ] The app launches without crashing.
- [ ] The model loads successfully (watch `adb logcat` for errors).
- [ ] A missing or invalid model produces a clear error rather than a crash.

**Chat**
- [ ] Sending a message produces a response.
- [ ] Empty messages are handled sensibly.
- [ ] Long messages and long responses work.
- [ ] Sending several messages in a row does not overlap generations.
- [ ] A long conversation (many turns) stays stable and does not exhaust memory.

**Conversations and history**
- [ ] New conversation starts with a fresh context.
- [ ] History persists after closing and reopening the app.
- [ ] Saved conversations can be opened and continued.
- [ ] Deleting an individual chat removes only that chat.
- [ ] Clearing all history removes everything.

**Lifecycle and robustness**
- [ ] Screen rotation while idle does not crash or lose messages. The activity is recreated on rotation and `onCreate` prepares and loads the model again; confirm the model reloads cleanly and the status line recovers.
- [ ] Rotating the screen *during* generation does not crash, duplicate replies, or allow a second overlapping generation.
- [ ] A model smaller than 400,000,000 bytes still works (it is re-copied on every launch).
- [ ] The theme choice after force-stopping and reopening the app (currently resets to dark).
- [ ] Backgrounding and returning to the app works.
- [ ] Force-stopping and restarting the app works.
- [ ] Native failures (for example, a deliberately corrupted model file) are reported gracefully.
- [ ] Light and dark appearance both render correctly.

**CI and distribution**
- [ ] GitHub Actions build completes successfully.
- [ ] The APK artifact is present, downloads, and extracts.
- [ ] The downloaded APK installs and launches.

Useful commands:

```bash
adb logcat | grep -i nerfe       # filter logs (adjust the tag as needed)
adb install -r path/to/app-debug.apk
```

There are no automated tests yet (`app/src` contains only `main`). See the [Roadmap](#18-roadmap).

---

## 15. Troubleshooting

| Problem | Likely causes | What to try |
| --- | --- | --- |
| **Gradle build errors** | Missing SDK/NDK, wrong JDK, corrupt cache, offline dependency fetch | Read the *first* error in the log. Run `gradle --no-daemon clean assembleDebug --stacktrace`. Check internet access. |
| **`./gradlew: No such file or directory`** | The repository has no Gradle wrapper | Use an installed `gradle` compatible with AGP 8.7.3, or generate and commit the wrapper (see [Building the APK](#7-building-the-apk)). |
| **`Build was configured to prefer settings repositories`** | `settings.gradle` uses `FAIL_ON_PROJECT_REPOS`, and a build file declares its own `repositories {}` | Remove the project-level repositories and declare them in `settings.gradle`. |
| **"Could not prepare bundled model."** | `nerfeai-model.gguf` is missing from the APK assets, or storage is full | Confirm `app/src/main/assets/nerfeai-model.gguf` existed at build time (`unzip -l app-debug.apk \| grep gguf`) and that the device has free space for a second copy. |
| **"Model loading failed."** | Invalid or unsupported GGUF, not enough RAM, or a native error | Check the GGUF header and checksum, try a smaller or more heavily quantized model, and read `adb logcat`. |
| **`INSTALL_FAILED_NO_MATCHING_ABIS`** | The APK contains only `arm64-v8a` and the device or emulator is not arm64 | Use a physical arm64 phone or an arm64 emulator image. |
| **NDK / CMake not found** | `27.2.12479018` or `3.22.1` is not installed | Install those exact versions via SDK Manager → SDK Tools. |
| **CMake fails about missing `llama.cpp` sources** | `third_party/` is absent from your clone | Obtain it as the project documents (**Needs verification**). |
| **JDK / Android SDK mismatch** | JDK too old or too new for the Android Gradle Plugin; missing platform/build-tools | Set the Gradle JDK in Android Studio to the version the project/CI uses. Install the required SDK platform and build tools. Check `sdk.dir` in `local.properties`. |
| **Failed model download** | Network interruption, wrong URL, gated model requiring login, insufficient storage | Retry with a resumable tool such as `curl -L -C - -O <url>`. Confirm the URL and license terms. Free up storage. |
| **Invalid GGUF file** | Incomplete download, HTML error page saved as a file, unsupported format version, wrong file | Check size and `head -c 4` shows `GGUF`; verify the checksum; ensure your `llama.cpp` version supports the model. |
| **Native library loading failures** (`UnsatisfiedLinkError`) | Library not built, wrong ABI for the device, wrong library name in `System.loadLibrary` | Confirm the native build ran, the `.so` is inside the APK (`unzip -l app-debug.apk \| grep .so`), the ABI matches your device, and the loaded name matches the CMake target. |
| **JNI errors** | Mismatched function name or signature, class/package renamed | Compare Kotlin `external` declarations with native function names exactly. Check logcat for the missing-method message. |
| **Out-of-memory errors** | Model or context too large for available RAM | Use a smaller or more heavily quantized model, reduce context size, close other apps. |
| **Slow inference** | Large model, few CPU threads, thermal throttling, emulator | Use a smaller quantization, test on a physical device, let the phone cool, plug in power, check the thread count setting. |
| **Git authentication failures** | GitHub no longer accepts account passwords for Git over HTTPS | Use a personal access token or SSH keys, or sign in with GitHub CLI (`gh auth login`). |
| **Git merge conflicts** | Both branches changed the same lines | Run `git status`, open the conflicted files, resolve the `<<<<<<<` markers, then `git add` the files and continue the merge or rebase. |
| **Missing GitHub Actions artifacts** | Upload path mismatch, skipped step, failed build, expired artifact | See [Diagnosing missing artifacts](#diagnosing-missing-artifacts). |
| **APK signature conflicts during update** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) | New APK signed with a different key than the installed app | Uninstall the existing app first (this deletes its data), or sign every build with the same key. |

---

## 16. Privacy and Security

### Local versus cloud inference

With local inference, prompts are processed on the device by the model file rather than sent to a remote AI service. That reduces exposure to third-party servers, but it does not by itself guarantee privacy.

### What the project configuration shows

| Item | Finding | Source |
| --- | --- | --- |
| Internet permission | `AndroidManifest.xml` does **not** declare `android.permission.INTERNET` | Manifest |
| Cleartext HTTP | Disabled (`usesCleartextTraffic="false"`) | Manifest |
| Backups | Disabled (`allowBackup="false"`) | Manifest |
| Network code in Kotlin | No HTTP or socket code in `MainActivity.kt` | Source |
| Libraries | `app/build.gradle` declares no dependencies | Build file |
| Logging in Kotlin | No `Log` calls in `MainActivity.kt` | Source |
| Native code | **Not inspected**: check `native-lib.cpp` for network calls and logging of prompts/outputs | n/a |

### Verifying network behavior yourself

1. Inspect the **merged** manifest in the built APK for any `INTERNET` permission:

```bash
aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
```

2. Search the code: `grep -rniE "http|socket|curl" app/src/ third_party/ | head`
3. Test in airplane mode.

### Local conversation storage

Chats are stored as plain JSON in `SharedPreferences` inside the app's private storage. They are **not encrypted** in the code. Other apps cannot normally read them, but someone with root access or an unlocked device with debugging enabled might. "Delete all chat history" removes them from the app's saved list; whether the underlying file contents are securely erased is not guaranteed.

The model is also copied into app-private storage (`filesDir`).

### Logging prompts and responses

Logs can be read through `adb logcat` and bug reports. Do not add logging of user prompts or model output (in Kotlin or in `native-lib.cpp`), and never paste logs with private conversations into public issues.

### Secrets management

- Never commit keystores, passwords, or tokens.
- Use GitHub Actions **secrets** for signing credentials.
- If a secret is ever committed, treat it as compromised and rotate it.

### GitHub Actions security

- Pin third-party actions to a version, preferably a full commit SHA for sensitive workflows.
- Give the workflow the minimum `permissions:` it needs.
- Do not expose secrets to workflows triggered by pull requests from forks.
- Never print secrets in logs.

### Model and dependency license verification

Check the license of the model, `llama.cpp`, and anything in `third_party/` before redistributing the APK. See [License and Attribution](#19-license-and-attribution).

### Keep private data out of commits and issues

Do not include personal conversations, device identifiers, or credentials in commits, screenshots, logs, or issues.

---

## 17. Known Limitations

### Confirmed from the code and configuration

| Limitation | Detail |
| --- | --- |
| arm64 only | `abiFilters 'arm64-v8a'`. No x86/x86_64 emulator support and no 32-bit ARM devices. |
| Android 8.0+ | `minSdk 26` |
| Double model storage | The model is stored in the APK and copied to app storage |
| 400 MB threshold | A copied model under 400,000,000 bytes is re-copied on every launch |
| No Gradle wrapper | Builds require an installed Gradle |
| Release signing | Release builds are signed with the debug key |
| No streaming / stop / regenerate | The answer appears all at once and cannot be cancelled from the UI |
| Short memory | Only the last 4 user turns are sent to the model |
| Theme not persisted | The light/dark choice is kept only across rotation, not across app restarts |
| Chat storage | Unencrypted JSON in `SharedPreferences`, written from the main thread |
| Hard-coded status text | The status line always says "Qwen loaded" regardless of the model file |
| No automated tests | `app/src` contains only `main` |
| Device-dependent performance, RAM limits, model quality | Inherent to on-device LLMs. Small quantized models can give incorrect answers. |

### Observed in the code, needs testing

- **Rotation:** `MainActivity` has no `configChanges`, so rotating the screen recreates it. `onCreate` calls `prepareBundledModel()` again, which loads the model again. Whether the native side handles repeated loads safely is unknown.
- **Rotation during generation:** `isGenerating` is a field of the activity instance, so a recreated activity starts with it `false`. A second generation could start while the first is still running, and the first thread updates the destroyed activity's views.
- **Context overflow:** Trimming happens after a reply is generated. A very long single message is not limited by `MainActivity`.

### Needs verification (native side)

Context size, stop-token handling, thread count, error handling, and behavior on repeated `nativeLoadModel` calls all live in `native-lib.cpp`.

---

## 18. Roadmap

> **These are proposals, not existing features.**

- Stop and regenerate response
- Streaming output
- Dedicated copy-message button
- Rename and search chat history
- Conversation export
- Persist the appearance setting
- Handle rotation safely (`configChanges` or a `ViewModel`-style holder) and avoid reloading the model
- Move chat saving off the main thread
- Add a Gradle wrapper and a `LICENSE`
- Show real model metadata instead of a hard-coded "Qwen" label
- Generation settings (temperature, max tokens, context size)
- Real release signing
- Automated unit and instrumentation tests
- Accessibility improvements (more content descriptions, larger touch targets, text scaling)
- Reproducible release builds
- Avoid storing the model twice

---

## 19. License and Attribution

**Project license:** The repository needs an explicit license before external reuse and contributions can be governed clearly. Without a license file, default copyright law applies and others generally have no permission to reuse, modify, or redistribute the code. **No `LICENSE` file was present in the repository root listing at the time of inspection, and the repository has no description or topics. This README does not choose a license and does not state that the project is open-source licensed.**

**Third-party licenses:** These must be checked independently:

| Component | What to verify |
| --- | --- |
| **Language model (GGUF)** | License terms, allowed uses, redistribution, and attribution requirements from the model publisher |
| **`llama.cpp` and `third_party/`** | The license of `llama.cpp` and anything else in `third_party/` (a local folder not on GitHub), including notice requirements when distributing the app |
| **Android libraries and Gradle dependencies** | Each dependency's license |
| **Fonts, icons, and images** | Their licenses and attributions |

Consider adding a `THIRD_PARTY_NOTICES` file once the dependency list is confirmed.

---

## 20. Quick Start for Contributors

```bash
# 1. Clone and enter the project
git clone https://github.com/nerfeguno/NerfeAI.git
cd NerfeAI

# 2. Create a branch
git switch -c docs/improve-readme

# 3. Make a documentation change (edit README.md in your editor), then review it
git status
git diff

# 4. Commit
git add README.md
git commit -m "Improve README setup instructions"

# 5. Push
git push -u origin docs/improve-readme

# 6. Open a pull request on GitHub (via the link printed by git push),
#    or with the GitHub CLI if installed:
gh pr create --fill
```

Thank you for helping improve NerfeAI Android.

!!! This content was generated by AI !!!
