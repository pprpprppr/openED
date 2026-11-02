# Contributing to openED 🎓

Thank you for your interest in contributing to **openED** — the global personal AI university. openED is an open-source, local-first, privacy-respecting educational platform empowering anyone to master any academic discipline natively on device.

---

## 🏛️ Guiding Architectural Principles

Before writing code, please review our core architectural tenets:
1. **Local-First & Offline Priority:** All study paths, chapters, cross-reference links, flashcards, document chunks, and embeddings reside in the local Room SQLite database on device. Cloud sync is optional and never mandatory.
2. **Bring Your Own Key (BYOK) Security:** User API keys are stored exclusively in the hardware-backed **Android Keystore** (`AES-256-GCM`). Zero keys are ever transmitted to third-party tracking servers or exported in backup bundles.
3. **Multi-Discipline Pedagogy Authenticity:** openED distinguishes academic disciplines (Biology, Commerce, Economics, History, Geopolitics, Law, Philosophy, STEM, Computer Science). In non-CS subjects, fake programming scripts or code blocks are strictly forbidden in favor of real-world cases, LaTeX formulas, and domain diagrams.
4. **Single-Agent & Non-Destructive Operations:** Data migrations must be non-destructive (`Migration(X, Y)`). Never silently overwrite user content.

---

## 🛠️ Development Setup

### Prerequisites
- **JDK:** Version 21 (LTS).
- **Android SDK:** Compile SDK 36, Target SDK 36, Minimum SDK 26.
- **Build System:** Gradle with Version Catalogs (`gradle/libs.versions.toml`).
- **Android Studio:** Ladybug or newer recommended.

### Building Locally
Clone the repository and verify your build:
```bash
git clone https://github.com/pprpprppr/openED.git
cd openED

# Check compilation
./gradlew compileDebugKotlin

# Run unit test suite
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## 🧪 Testing Standards

All code contributions must be accompanied by comprehensive automated tests:
- **Domain & Pedagogy:** Test academic classification, prompt synthesis, and guardrails.
- **Database & Migrations:** Test non-destructive Room migrations (`MIGRATION_X_Y`).
- **FSRS & Learning:** Test spaced repetition intervals, stability, and difficulty calculations.
- **Security & Sandboxing:** Verify Android Keystore isolation and untrusted document sanitization.

Run the test suite prior to opening a PR:
```bash
./gradlew testDebugUnitTest
```

---

## 📐 Code Style & Conventions

- **Language:** Kotlin 2.0+ with modern idioms.
- **UI:** 100% Jetpack Compose with Material 3. Do not introduce legacy Views or WebViews.
- **Accessibility:**
  - All text must scale dynamically using `sp` units.
  - Interactive Canvas simulations and mathematical formulas must provide descriptive `semantics { contentDescription = ... }`.
  - Support high-contrast palettes (WCAG AAA) and reduced-motion preferences.
- **Localization:** Never hardcode user-facing strings; extract them into `res/values/strings.xml`.

---

## 🔀 Submitting a Pull Request

1. Fork the repository and create a feature branch (`git checkout -b feat/your-feature-name`).
2. Commit changes using Conventional Commits format:
   - `feat(...)`: New capability or feature
   - `fix(...)`: Bug fix or patch
   - `perf(...)`: Performance optimization
   - `test(...)`: Adding or updating test cases
   - `docs(...)`: Documentation improvements
3. Ensure all Gradle verification checks pass:
   ```bash
   ./gradlew compileDebugKotlin testDebugUnitTest lintDebug assembleDebug
   ```
4. Push your branch and open a Pull Request against `main`. Provide a concise description of changes and test evidence.

---

## 📜 Code of Conduct

All contributors and maintainers are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). Please report unacceptable behavior to the project maintainers.
