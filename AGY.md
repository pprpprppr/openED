# Project Rules & Architecture Guidelines
- **Platform:** Native Android (Kotlin 2.x, Jetpack Compose, Material 3).
- **Architecture:** Clean Architecture with MVVM / MVI (Unidirectional Data Flow), single-activity Compose Navigation.
- **Database:** Room SQLite with Foreign Keys, FTS (Full-Text Search), and cross-reference tables for bi-directional chapter linking (`[[WikiLinks]]`).
- **AI Integration:** BYOK (Bring Your Own Key) architecture supporting Google Gemini (`gemini-3.5-flash` / `gemini-2.5-flash` via REST/Ktor or Google AI SDK) using Structured Outputs (`responseSchema` in JSON) for syllabus generation and streaming for content/chat.
- **Security:** Store user API keys strictly inside `EncryptedSharedPreferences` / Android Keystore. Never hardcode keys.
- **Cloud Sync:** Google Drive API v3 (`appDataFolder` or dedicated user folder) using `CredentialManager` + Google Identity Services and `WorkManager` for background snapshot backups.
- **Open Source Readiness:** Include a clean `LICENSE` (MIT/Apache 2.0), `CONTRIBUTING.md`, and deterministic Gradle Version Catalog (`libs.versions.toml`).
- **Execution Verification:** After generating each module or layer, run `./gradlew compileDebugKotlin` to catch and fix unresolved references or type mismatches autonomously.
- **Java & Gradle Toolchain:** Host environment runs OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`). Configure `compileOptions` and `kotlinOptions` (`jvmTarget`) to `JavaVersion.VERSION_21` (or `jvmToolchain(21)`), and use Gradle Wrapper `8.11.1`+ with AGP `8.7.2`+.

