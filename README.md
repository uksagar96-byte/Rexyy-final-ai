# REXYY - Native Android AI Assistant

REXYY is a modern, native Android AI assistant application built with Kotlin and Jetpack Compose. It provides real-time intelligent conversations, conversation history persistence with Room database, and hardware-backed secure API key management via Android KeyStore (AES-256 GCM).

---

## 1. What REXYY Is

REXYY is an intelligent mobile companion for Android that connects to OpenAI-compatible AI APIs (including GPT-4o, GPT-4o-mini, and compatible completions endpoints).

### Key Features
- **Conversational Chat Interface:** Clean, dark-mode-first Material 3 UI with responsive scrolling, timestamped message bubbles, and smooth typing indicators.
- **Hardware-Backed KeyStore Security:** User API keys are encrypted using AES-256 GCM with keys generated and stored in the Android KeyStore system. Complete keys are never displayed in logs or sent anywhere other than the authenticated API endpoints.
- **First-Launch Onboarding:** When launched without an API key, REXYY displays an onboarding setup screen guiding the user to enter and securely save their key.
- **Offline Conversation History:** All conversation messages are persisted locally using SQLite via Android Jetpack Room with full offline access and a clear conversation option.
- **Settings & Model Selection:** Switch between models (`gpt-4o-mini`, `gpt-4o`, `gpt-3.5-turbo`), update the API key, or clear stored credentials at any time.
- **Resilient Network Layer:** Retrofit and OkHttp client architecture featuring error handling for offline status, rate limits (HTTP 429), authentication errors (HTTP 401), and server errors (HTTP 5xx).

---

## 2. Project Structure

The project follows the standard native Android modular package architecture:

```text
REXYY/
├── .github/
│   └── workflows/
│       └── build-apk.yml               # Automated GitHub Actions CI workflow
├── app/
│   ├── build.gradle.kts                # Application-level Gradle build script
│   ├── proguard-rules.pro              # ProGuard / R8 code shrinking rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Application manifest & permissions
│       │   ├── java/com/rexyy/app/
│       │   │   ├── RexyyApplication.kt  # Android Application entry point
│       │   │   ├── MainActivity.kt      # Main Activity hosting Compose UI
│       │   │   ├── data/
│       │   │   │   └── local/           # Room DB (Entity, DAO, Database) & SecureStorage
│       │   │   ├── model/               # ChatMessage, MessageSender, AiConfig domain models
│       │   │   ├── network/             # Retrofit API, DTOs, ApiClientFactory & NetworkResult
│       │   │   ├── repository/          # AssistantRepository (orchestration & persistence)
│       │   │   ├── ui/                  # Compose UI screens, components, theme & navigation
│       │   │   │   ├── chat/            # ChatScreen, ChatViewModel, ChatUiState
│       │   │   │   ├── components/      # RexyyTopBar, MessageBubble, ChatInputBar, TypingIndicator
│       │   │   │   ├── navigation/      # Screen routes & navigation state
│       │   │   │   ├── settings/        # SettingsScreen (key & model configuration)
│       │   │   │   ├── setup/           # ApiKeySetupScreen (first-launch flow)
│       │   │   │   └── theme/           # Color, Theme, Typography (Material 3)
│       │   │   └── utils/               # SecurityUtils (masking) & NetworkUtils (connectivity)
│       │   └── res/
│       │       ├── drawable/            # Vector graphics & launch drawables
│       │       ├── mipmap-*/            # Adaptive launcher icons
│       │       ├── values/              # strings.xml, colors.xml, themes.xml
│       │       └── xml/                 # Backup rules & data extraction configuration
│       └── test/java/com/rexyy/app/     # Unit & Robolectric JVM tests
├── gradle/
│   ├── libs.versions.toml              # Centralized Gradle version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar          # Gradle wrapper runtime
│       └── gradle-wrapper.properties   # Gradle 9.3.1 distribution
├── build.gradle.kts                    # Root project build configuration
├── settings.gradle.kts                 # Project repositories and module definitions
├── gradle.properties                   # JVM args, parallel build & Android configuration
├── gradlew                             # Linux/macOS Gradle wrapper script
├── gradlew.bat                         # Windows Gradle wrapper script
└── README.md                           # Documentation
```

---

## 3. Requirements

- **JDK:** Java Development Kit **17** (e.g., Eclipse Temurin 17 or OpenJDK 17)
- **Minimum Android Version (minSdk):** Android 8.0 (API Level **26**)
- **Compile & Target SDK (compileSdk, targetSdk):** Android 16 / VanillaIceCream (API Level **36**)
- **Android Gradle Plugin (AGP):** 9.1.1
- **Gradle:** 9.3.1 (provided via Gradle wrapper)

---

## 4. How to Build Locally

### Prerequisites
Make sure `JAVA_HOME` points to a valid **JDK 17** installation:
```bash
java -version
```

### Build Debug APK
From the repository root, run:
```bash
./gradlew assembleDebug
```

On Windows:
```cmd
gradlew.bat assembleDebug
```

### Run Tests
To execute all local unit and Robolectric tests:
```bash
./gradlew test
```

### List Available Gradle Tasks
```bash
./gradlew tasks
```

---

## 5. Where the Generated APK is Located

After running `./gradlew assembleDebug`, the generated debug APK is located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 6. How to Build Using GitHub Actions

The repository includes a ready-to-use GitHub Actions workflow located at `.github/workflows/build-apk.yml`.

### How It Works
1. Every `push` or `pull_request` to `main` or `master` (as well as manual `workflow_dispatch` triggers) starts the build.
2. The workflow checks out the code, installs JDK 17, grants execute permissions to `./gradlew`, and executes `./gradlew assembleDebug`.
3. Once the build succeeds, GitHub Actions uploads the generated APK as an artifact named `REXYY-debug-apk`.

### Downloading the APK from GitHub Actions
1. Navigate to the **Actions** tab in your GitHub repository.
2. Select the latest workflow run.
3. Under the **Artifacts** section at the bottom, click **REXYY-debug-apk** to download the ZIP containing `app-debug.apk`.

---

## 7. How First-Launch API Key Setup Works

REXYY implements a zero-hardcoding security architecture:

1. **First Launch (No Key Present):**
   - The app detects that no encrypted key exists in `SecureStorage`.
   - The user is presented with the **API Key Setup Screen**.
   - The user enters their API key and taps **Save Key & Launch REXYY**.
   - The key is encrypted using the Android KeyStore (`AES/GCM/NoPadding`) and saved to private preferences.
   - The app navigates directly into the chat interface.

2. **Subsequent Launches (Key Exists):**
   - The app verifies the key exists in encrypted storage and loads the **Main Chat Screen** immediately.

3. **Updating or Removing the Key:**
   - At any time, tap the **Settings** icon in the top app bar.
   - You can view the masked key (`sk-••••XXXX`), enter a new key, or completely remove the stored key.

---

## 8. Security Warning

> **CRITICAL SECURITY ADVISORY:**
> - **NEVER commit a real API key to GitHub or any public source repository.**
> - **NEVER place API keys directly inside Kotlin source files or resource XMLs.**
> - REXYY does not include or require any hardcoded keys; each user enters their own key securely on device.
> - The `.gitignore` file is configured to exclude sensitive files such as `.env`, `*.keystore`, and build artifacts.
