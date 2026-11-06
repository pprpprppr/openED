# openED Comprehensive Project Audit (Phase 0)

**Date:** November 2, 2026  
**Project:** openED — Global Personal AI University  
**Architecture:** Android (Kotlin, Jetpack Compose, Room, Hilt, Coroutines & Flow, Local-First, Bring Your Own Key)  
**Baseline Test Status:** 40/40 Unit Tests Passing (`BUILD SUCCESSFUL in 936ms`)

---

## 1. Executive Summary

openED is a local-first, privacy-respecting educational platform providing on-demand, adaptive AI curricula across academic disciplines. This audit reviews the entire codebase, Room persistence layer, AI engine, rendering pipeline, Google Drive backup, file sharing, security posture, and dependency graph against the requirements in `MASTER_SPEC.md`.

---

## 2. Implemented Features

### A. Academic Pedagogy & AI Engine
- **Universal Multi-Discipline Classification:** `AcademicDomainClassifier` categorizes study topics across 7 academic domains:
  - `SOFTWARE_AND_CS`
  - `MATH_AND_PHYSICAL_SCIENCES`
  - `LIFE_SCIENCES_AND_MEDICINE`
  - `COMMERCE_ECONOMICS_AND_FINANCE`
  - `HISTORY_GEOPOLITICS_AND_SOCIAL`
  - `PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES`
  - `GENERAL_INTERDISCIPLINARY`
- **Domain-Specific Pedagogical Blueprints:** Structured blueprints in `Prompts.kt` enforcing discipline-authentic section headers, visual diagrams, and strict zero-programming constraints for non-CS fields.
- **Granular Curriculum Synthesis:** Generates 6–8 modules with 4–6 chapters per module (24–40 bite-sized single-concept chapters) with `maxOutputTokens = 8192`.
- **Level-Adaptive Teaching:** Tailors explanations based on learner level (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`).
- **SSE Streaming Generation:** Live markdown streaming with cancellation safety and automatic dead-state recovery in `AiLearningEngine.kt`.
- **Multi-Provider AI Client:** Supports Google Gemini (default), Anthropic Claude, OpenAI, and Custom local LLMs (Ollama / vLLM).
- **In-Chapter AI Editing & Refinement:** In-line depth adjustments (`Simplify`, `Add Code/Math`, `Fact-Check`, `Custom Prompt`).
- **Socratic Q&A & Revision Tracking:** Integrated conversational assistant and full revision snapshot history in Room.

### B. UI, Workspaces & Renderers
- **Compose Multiplatform UI Architecture:** Edge-to-edge Material 3 interface with dark/light theming.
- **Rich Markdown Engine:** Custom AST chunker supporting headers, tables, callouts, lists, blockquotes, code blocks, and wikilinks.
- **LaTeX Math Rendering:** `LatexParser` parsing inline `$..$` and block `$$..$$` equations, rendered natively with fractions, superscripts, subscripts, Greek characters, and square roots.
- **Interactive Diagrams & Charts:** Canvas-based interactive tree/flowchart rendering with pan-zoom and XY charts.
- **Wiki-Link Knowledge Graph:** Bidirectional `[[Chapter Title]]` wikilink extraction, edge tracking in `ChapterLinkCrossRef`, and interactive force-directed graph view.
- **Mastery Engine & Spaced Repetition:** SM-2/FSRS flashcard scheduling with review workflows, adaptive multiple-choice quizzes, and Feynman technique self-explanation scoring.
- **Text-to-Speech (TTS):** On-device Android TTS mini-player for listening to chapter lessons.

---

## 3. Partially Implemented Features

1. **Source Intelligence & Evidence Layer (Phase 3):**
   - The app currently relies on the LLM's parametric knowledge and ground-truth chapter context. Explicit citation extraction, bibliography schemas, and fact-checking against external primary repositories are not yet implemented.
2. **Advanced Cross-Session Analytics (Phase 4):**
   - Flashcards track intervals, repetitions, and ease factors; mastery quizzes record score history. However, cumulative prerequisite failure tracking and automated remedial lesson generation require further expansion.
3. **Multi-Database Coexistence:**
   - The project contains both `OpenEDDatabase` (`opened_database.db`, active) and `LearningDatabase` (`opened_learning_paths.db`, block-based prototype in `org.opened.app.learning`). These run in parallel without unified migration.

---

## 4. Broken / Fragile Functionality

1. **FileProvider Over-Exposure (Critical Security Flaw):**
   - `app/src/main/res/xml/file_provider_paths.xml` previously included `<root-path name="root" path="." />` and `<files-path name="files" path="." />`. This granted access to arbitrary filesystem paths including private databases and keystore storage.
2. **Deprecated EncryptedSharedPreferences:**
   - `SecureApiKeyStorage.kt` used `androidx.security.crypto.EncryptedSharedPreferences`, which is deprecated, prone to initialization crashes on specific OEM ROMs, and falls back to insecure plaintext shared preferences if Keystore initialization fails.
3. **Unprotected Android Auto-Backup:**
   - `AndroidManifest.xml` had `android:allowBackup="true"` without `dataExtractionRules` or `fullBackupContent` rules, risking credential leakage during cloud or device-to-device transfers.

---

## 5. Security Vulnerabilities Identified

| Vulnerability | Severity | Impact | Mitigation Plan |
|---|---|---|---|
| Overly Broad FileProvider Paths | **CRITICAL** | `<root-path>` and `path="."` allow unauthorized access to app private data | Restrict to dedicated subdirectories (`cache/exports`, `files/exports`) |
| Insecure Fallback in API Key Storage | **HIGH** | If Keystore fails, keys were written to unencrypted `opened_secure_vault_fallback` | Implement native Android Keystore AES-256-GCM with versioned envelopes & fallback encryption |
| Cloud Backup of Credentials | **HIGH** | Auto-backup could sync Keystore-bound data or sensitive preferences to Google Drive / cloud | Add `data_extraction_rules.xml` & `backup_rules.xml` excluding credential containers |
| Google Drive App Data Scope | **MEDIUM** | Drive backup writes `.zip` archive to user Google Drive | Kept strictly credential-free; backup bundle verified to contain zero API keys |

---

## 6. Performance Bottlenecks & Optimization Areas

1. **Diagram & Knowledge Graph Rendering:**
   - `DiagramBlockView.kt` and `KnowledgeGraphScreen.kt` perform iterative spring/tree layouts. Large trees (>100 nodes) should throttle layout updates to prevent Compose recomposition churn.
2. **Keystore IO Operations:**
   - Keystore crypto operations are expensive (~5–15ms). In-memory caching in `SecureApiKeyStorage` mitigates main-thread blocking, which must be preserved during Keystore migration.
3. **Markdown Token Streaming Recomposition:**
   - High-throughput SSE token streaming can trigger frequent layout passes. Chunked buffering prevents per-token recomposition drops.

---

## 7. Database Integrity & Schema Analysis

- **Primary Database:** `OpenEDDatabase` (`version = 2`, `exportSchema = true`)
  - `study_paths`: Courses metadata, topic, level, progress.
  - `modules`: Module title, order index, foreign-keyed to `study_paths` with `CASCADE` delete.
  - `chapters`: Chapter title, summary, markdown content, status, foreign-keyed to `modules`.
  - `chapter_revisions`: Snapshot history of edits with timestamp and change reason.
  - `chapter_link_cross_ref`: Graph edges for bidirectional wikilinks.
  - `chapter_qa`: Socratic Q&A conversation logs.
  - `flashcards`: Spaced-repetition cards with foreign keys to `chapters` and `study_paths`.
  - `mastery_quizzes`: Quiz attempts, session JSON, scores.
- **Migration 1 -> 2:** Fully verified; correctly creates `flashcards` and `mastery_quizzes` tables with indexes.

---

## 8. Dependency & Android Platform Compatibility

- **Gradle & Toolchain:**
  - AGP: `8.7.2`
  - Kotlin: `2.0.21` (K2 compiler, Compose compiler plugin enabled)
  - Java / JVM Target: `21`
  - Room: `2.6.1`
  - Hilt: `2.52`
  - compileSdk / targetSdk: `35` -> Upgrade to `36` (Android 16 compatibility)
- **Local Platform SDK:**
  - `android-35` and `android-36` both present in `$ANDROID_HOME/platforms`.

---

## 9. Baseline Test Results

All existing unit tests in `app/src/test` were executed prior to code modifications:
- `FlashcardSchedulerTest`: Passed
- `LearningDataModelsTest`: Passed
- `LatexParserTest`: Passed
- `MarkdownLinkRegexTest`: Passed
- `WikiLinkExtractorTest`: Passed
- `BackupBundleSerializationTest`: Passed
- `PromptEngineeringTest` (14 multi-disciplinary pedagogy tests): Passed
- **Total Tests:** 40 / 40 passing (0 failures, 0 ignored).
