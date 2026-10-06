# NerfeAI

> A lightweight, offline-first personal AI assistant for Android powered by **Qwen2.5 0.5B** and **llama.cpp**.

NerfeAI is a personal AI assistant designed to run local AI inference directly on an Android device.

The goal is simple:

**Install the APK → open NerfeAI → chat with your local AI without depending on cloud AI services.**

The application uses a native Android frontend written in Kotlin and a C++ inference backend powered by llama.cpp.

---

## Features

### 🤖 Local AI

- Runs AI inference directly on the Android device
- Uses Qwen2.5 0.5B Instruct
- GGUF quantized model
- Q4_K_M quantization
- llama.cpp inference backend
- No cloud AI API required for normal chatting
- Works without an internet connection after installation

### 💬 Chat Interface

- ChatGPT-style interface
- User and assistant message bubbles
- Animated AI responses
- New Chat
- Persistent conversations
- Conversation history
- Delete conversations
- Sidebar navigation
- Current conversation indicator
- Dark/light appearance support

### 📱 Android

- Native Kotlin Android application
- C++ JNI backend
- ARM64 support
- Android 8.0+ (`minSdk 26`)
- Android 15 compatible target configuration
- Screen rotation support
- Keyboard-aware layout
- Persistent conversation state

### ⚡ Performance

NerfeAI includes memory and prompt-size protections for long conversations:

- Limited prompt history
- Limited visible messages
- Limited stored messages per conversation
- Prompt-size protection
- Response-size limits
- Native inference context limits
- Reduced unnecessary persistence operations

These protections help reduce crashes caused by continuously growing UI and conversation memory.

### 🔄 Automatic Updates

NerfeAI can check GitHub Releases for newer versions.

The updater:

1. Checks the latest GitHub release.
2. Reads `update.json`.
3. Compares the installed `versionCode`.
4. Displays an update dialog when a newer version exists.
5. Downloads the APK.
6. Calculates the SHA-256 checksum.
7. Verifies the downloaded APK.
8. Starts the Android package installer.

The update system is designed so that NerfeAI can still function normally when the device is offline.

---

# Architecture

```text
┌─────────────────────────────────────┐
│              NerfeAI                │
│           Android Application       │
├─────────────────────────────────────┤
│          Kotlin UI Layer            │
│                                     │
│  MainActivity.kt                    │
│  ├── Chat UI                        │
│  ├── Chat History                   │
│  ├── New Chat                       │
│  ├── Theme / Appearance             │
│  ├── Persistence                    │
│  └── Update Manager                 │
├─────────────────────────────────────┤
│              JNI                    │
│       Kotlin → Native C++           │
├─────────────────────────────────────┤
│          C++ Native Backend         │
│        native-lib.cpp               │
├─────────────────────────────────────┤
│             llama.cpp               │
│        GGUF Model Runtime            │
├─────────────────────────────────────┤
│          Qwen2.5 0.5B              │
│       Q4_K_M GGUF Model             │
└─────────────────────────────────────┘
```

---

# Project Structure

```text
NerfeAI-Android/
│
├── .github/
│   └── workflows/
│       └── release.yml
│
├── app/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/
│           │   └── com/nerfeai/
│           │       └── MainActivity.kt
│           ├── cpp/
│           │   ├── CMakeLists.txt
│           │   └── native-lib.cpp
│           └── res/
│               ├── drawable/
│               ├── mipmap/
│               ├── values/
│               └── xml/
│                   └── file_paths.xml
│
├── third_party/
│   └── llama.cpp/
│
├── gradle.properties
├── settings.gradle
├── build.gradle
└── README.md
```

---

# AI Model

NerfeAI currently uses:

```text
Qwen2.5 0.5B Instruct
```

Quantized model:

```text
qwen2.5-0.5b-instruct-q4_k_m.gguf
```

The model is distributed with the application as:

```text
nerfeai-model.gguf
```

The model uses the GGUF format and is loaded by llama.cpp.

---

# Why Qwen2.5 0.5B?

NerfeAI is designed primarily for mobile devices.

A very large model would require significantly more:

- RAM
- Storage
- CPU resources
- Battery
- Inference time

The 0.5B model provides a practical starting point for a completely local Android AI assistant.

The project can later experiment with larger or different models.

---

# llama.cpp

NerfeAI uses llama.cpp as its native inference engine.

The Android application communicates with llama.cpp through JNI.

The main native functions are:

```text
nativeLoadModel()
nativeGenerate()
```

The general flow is:

```text
User sends message
        │
        ▼
   MainActivity
        │
        ▼
       JNI
        │
        ▼
 native-lib.cpp
        │
        ▼
    llama.cpp
        │
        ▼
    GGUF Model
        │
        ▼
 Generated response
        │
        ▼
       JNI
        │
        ▼
   Android UI
```

---

# Native Inference Configuration

The current native backend uses approximately:

```text
Context size:       4096
Batch size:          256
Threads:             4
Batch threads:       4
Prompt limit:       ~3584 tokens
Max generation:      512 tokens
```

Actual performance depends on the Android device.

---

# Conversation Memory

NerfeAI uses multiple protections to prevent conversations from growing indefinitely.

## Prompt History

Only a limited amount of recent conversation is included when generating a response.

```text
Old conversation
       │
       ▼
Keep recent turns
       │
       ▼
Build prompt
       │
       ▼
llama.cpp
```

## Visible Messages

Long conversations are limited to a recent set of visible messages.

This reduces:

- Android View memory usage
- layout processing
- scrolling overhead
- rendering overhead

## Stored Messages

Conversation storage is bounded so that a single conversation does not continuously increase the amount of data stored in `SharedPreferences`.

---

# Long Conversation Protection

Earlier versions could become unstable during very long conversations because several things grew continuously:

```text
Conversation history
        +
Android Views
        +
SharedPreferences data
        +
Prompt size
        =
High memory usage
```

The updated implementation limits:

```text
Visible messages
Stored messages
Message length
Prompt length
Prompt turns
Generated tokens
```

This makes long conversations significantly safer.

---

# Android Requirements

## Minimum Android Version

```text
Android 8.0
API 26
```

## Target SDK

```text
Android 15
API 35
```

## Architecture

```text
arm64-v8a
```

The application is optimized for modern 64-bit ARM Android devices.

---

# Building the Project

From the project root:

```bash
cd ~/NerfeAI-Android
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The resulting APK should be located under:

```text
app/build/outputs/apk/debug/
```

---

# Clean Build

```bash
./gradlew clean
./gradlew assembleDebug
```

---

# Installing the Debug APK

After building:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If ADB is not being used, copy the APK to the Android device and install it manually.

---

# GitHub Actions

NerfeAI uses GitHub Actions to build release APKs.

The general workflow is:

```text
Developer
   │
   ▼
Git commit
   │
   ▼
GitHub
   │
   ▼
GitHub Actions
   │
   ├── Setup Java
   ├── Setup Android SDK
   ├── Setup NDK
   ├── Build llama.cpp
   ├── Build Android APK
   ├── Sign APK
   ├── Generate SHA-256
   ├── Generate update.json
   └── Create GitHub Release
```

---

# Development Branch

The main development branch is:

```text
beta-dev
```

Push development changes with:

```bash
git add .
git commit -m "Your commit message"
git push origin beta-dev
```

Check status:

```bash
git status
```

Check recent commits:

```bash
git log --oneline --decorate -10
```

---

# Creating a Release

NerfeAI uses version tags for releases:

```text
v1.0
v1.1
v1.2
```

Update the Android version in:

```text
app/build.gradle
```

Example:

```gradle
defaultConfig {
    applicationId 'com.nerfeai'
    minSdk 26
    targetSdk 35

    versionCode 2
    versionName '1.1'
}
```

Every release must have a higher `versionCode`.

| Version | Version Code |
|---|---:|
| 1.0 | 1 |
| 1.1 | 2 |
| 1.2 | 3 |
| 2.0 | 4 |

---

# Creating a Git Tag

After committing the release version:

```bash
git add app/build.gradle
git commit -m "Prepare NerfeAI 1.1 release"
git push origin beta-dev
```

Create and push the tag:

```bash
git tag v1.1
git push origin v1.1
```

GitHub Actions should then build the release.

Verify remote tags:

```bash
git ls-remote --tags origin
```

---

# Release APK

The GitHub Actions release workflow generates an APK such as:

```text
NerfeAI-1.1.apk
```

The APK is then attached to the GitHub Release.

---

# APK Signing

Release APKs must be signed consistently.

GitHub Actions expects these repository secrets:

```text
NERFEAI_KEYSTORE_BASE64
NERFEAI_STORE_PASSWORD
NERFEAI_KEY_ALIAS
NERFEAI_KEY_PASSWORD
```

## Important: Keep the Signing Key Safe

The release keystore is extremely important.

Once users install a signed version of NerfeAI, future updates must use the same signing identity.

Do not:

- Generate a new release key for every version
- Delete the original keystore
- Change the release key accidentally
- Commit the keystore into Git
- Publish the keystore publicly

If the signing key is lost, existing installations may not be able to install future updates as normal updates.

---

# Automatic Update System

NerfeAI checks:

```text
https://github.com/<repository>/releases/latest/download/update.json
```

Example:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "apkName": "NerfeAI-1.1.apk",
  "apkUrl": "https://github.com/nerfeguno/NerfeAI/releases/download/v1.1/NerfeAI-1.1.apk",
  "sha256": "YOUR_SHA256_HASH",
  "releaseUrl": "https://github.com/nerfeguno/NerfeAI/releases/tag/v1.1"
}
```

## Update Process

```text
NerfeAI starts
      │
      ▼
Wait briefly
      │
      ▼
Check update.json
      │
      ▼
Compare versionCode
      │
      ├── Same/older
      │      │
      │      ▼
      │    Continue normally
      │
      └── New version
             │
             ▼
       Show update dialog
             │
             ▼
        Download APK
             │
             ▼
       SHA-256 verification
             │
             ▼
       Android installer
```

---

# Offline Operation

Normal AI inference does not require internet access.

The model runs locally:

```text
Android Device
     │
     ├── NerfeAI APK
     ├── Qwen GGUF Model
     └── llama.cpp
             │
             ▼
        Local inference
```

The optional update checker requires internet access.

If the update check fails because the device is offline, NerfeAI continues operating normally.

---

# Privacy

NerfeAI is designed around local AI inference.

Normal chat generation happens on the device.

The application does not need a cloud AI API to generate responses.

The updater may connect to GitHub to check for new releases and download updates.

---

# Security

The update system verifies downloaded APKs using SHA-256 before attempting installation.

The application uses Android's `FileProvider` when handing the downloaded APK to the Android package installer.

---

# Android Update Permissions

The updater requires:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

It may also use:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

The update APK is passed to Android through a secure `FileProvider`.

---

# FileProvider

The updater uses:

```text
app/src/main/res/xml/file_paths.xml
```

Example:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path
        name="update_cache"
        path="." />
</paths>
```

---

# Troubleshooting

## App crashes during a long conversation

Try:

1. Start a new chat.
2. Update to the latest version.
3. Avoid extremely long prompts.
4. Rebuild the latest APK if developing locally.

The application includes limits designed to prevent excessive memory usage.

## AI does not respond

Verify that the model loads successfully.

For development builds:

```bash
adb logcat | grep NerfeAI
```

If the model cannot be loaded, verify that the GGUF model is included correctly in the APK.

## Native library error

```bash
./gradlew clean
./gradlew assembleDebug
```

Verify llama.cpp:

```bash
ls third_party/llama.cpp
```

## Build fails after changing native code

```bash
./gradlew clean
./gradlew assembleDebug
```

## GitHub Actions fails

Check:

```text
Actions
   ↓
Workflow
   ↓
Failed step
```

Common causes:

- Incorrect versionCode
- Missing signing secrets
- Invalid keystore
- NDK configuration problems
- CMake configuration errors
- Incorrect Git tag
- Release already exists
- Missing model file

---

# Development Commands

## Clone

```bash
git clone https://github.com/nerfeguno/NerfeAI.git
cd NerfeAI
```

## Enter project

```bash
cd ~/NerfeAI-Android
```

## Check status

```bash
git status
```

## Update repository

```bash
git pull origin beta-dev
```

## Build debug APK

```bash
./gradlew assembleDebug
```

## Clean build

```bash
./gradlew clean assembleDebug
```

## Commit

```bash
git add .
git commit -m "Update NerfeAI"
```

## Push development branch

```bash
git push origin beta-dev
```

---

# Recommended Development Workflow

```text
1. Pull latest code
        │
        ▼
2. Make changes
        │
        ▼
3. Build locally
        │
        ▼
4. Install/test APK
        │
        ▼
5. Test normal chat
        │
        ▼
6. Test long conversation
        │
        ▼
7. Test rotation
        │
        ▼
8. Test New Chat
        │
        ▼
9. Test chat history
        │
        ▼
10. Commit
        │
        ▼
11. Push beta-dev
        │
        ▼
12. Verify GitHub Actions
        │
        ▼
13. Create release tag
```

---

# Testing Checklist

## Chat

- [ ] Send a message
- [ ] Receive an AI response
- [ ] Send multiple messages
- [ ] Long conversation
- [ ] Long user prompt
- [ ] Long AI response
- [ ] Empty message
- [ ] New Chat

## History

- [ ] Conversation is saved
- [ ] Conversation can be reopened
- [ ] Conversation can be deleted
- [ ] Multiple conversations work
- [ ] App restart preserves conversations

## UI

- [ ] Dark mode
- [ ] Light mode
- [ ] Sidebar
- [ ] New Chat button
- [ ] Keyboard does not cover input
- [ ] Status bar is correct
- [ ] Screen rotation works

## Model

- [ ] Model loads
- [ ] AI responds
- [ ] Multiple generations work
- [ ] Long conversation does not crash

## Updates

- [ ] Current version does not show a false update
- [ ] New version is detected
- [ ] APK downloads
- [ ] SHA-256 verification works
- [ ] Android installer opens
- [ ] Existing app updates successfully

---

# Current Version

Current development target:

```text
NerfeAI 1.1
```

Version information is controlled from:

```text
app/build.gradle
```

Example:

```gradle
versionCode 2
versionName '1.1'
```

---

# Roadmap

Future improvements may include:

- [ ] Better streaming token generation
- [ ] Faster mobile inference
- [ ] GPU acceleration where practical
- [ ] Better model management
- [ ] Multiple local models
- [ ] Model download manager
- [ ] Conversation search
- [ ] Export conversations
- [ ] Import conversations
- [ ] Markdown rendering
- [ ] Code syntax highlighting
- [ ] Improved response animation
- [ ] Stop generation button
- [ ] Regenerate response
- [ ] Edit user message
- [ ] Message copying
- [ ] More advanced memory management
- [ ] Database-based conversation storage
- [ ] Improved update system
- [ ] More Android architectures

---

# Design Philosophy

NerfeAI is built around four principles:

## 1. Local First

The AI should run on the user's device whenever possible.

## 2. Lightweight

The application should remain practical for mobile hardware.

## 3. Private

Normal AI conversations should not require sending prompts to a remote AI service.

## 4. Personal

NerfeAI is intended to become a customizable personal AI assistant rather than simply another cloud chatbot.

---

# Technology Stack

| Component | Technology |
|---|---|
| Android UI | Kotlin |
| Native backend | C++ |
| JNI | Android JNI |
| AI runtime | llama.cpp |
| Model | Qwen2.5 0.5B Instruct |
| Model format | GGUF |
| Quantization | Q4_K_M |
| Build system | Gradle |
| Native build | CMake |
| Android SDK | API 35 |
| Minimum Android | API 26 |
| Native ABI | arm64-v8a |
| CI/CD | GitHub Actions |
| Release hosting | GitHub Releases |
| Update verification | SHA-256 |

---

# Project Goal

The long-term goal of NerfeAI is to create a fully local personal AI assistant that can run directly on Android hardware without requiring a permanent cloud connection.

The project is intentionally modular so that the model, native backend, Android interface, and update system can continue evolving independently.

```text
NerfeAI
│
├── Personal AI
├── Offline inference
├── Android
├── llama.cpp
├── Local models
├── Persistent conversations
└── Automatic updates
```

---

# License

This project contains third-party software and model components.

Check the individual licenses of:

- NerfeAI source code
- llama.cpp
- Qwen model
- GGUF model distribution

before redistributing modified versions of the complete application.

---

# Author

**Nerfe**

Project:

```text
NerfeAI
```

Repository:

```text
nerfeguno/NerfeAI
```

---

# Final Goal

> **NerfeAI — Your AI. Your device. Your data.**





# 🤖 AI-Generated Documentation Disclaimer

> [!IMPORTANT]
> **This repository's documentation was generated with the assistance of artificial intelligence.** 
> While the content has been reviewed for clarity and general accuracy, users are advised to verify critical technical steps, configurations, or code blocks independently. The materials here are provided "as is" and serve as a baseline reference.

