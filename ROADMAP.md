# openED Roadmap 🗺️

This document outlines the strategic product and engineering roadmap for **openED — Global Personal AI University**.

---

## 🚀 Released in Version 1.0.0

- ✅ **Phase 0 & 1: Security & Keystore Vault**
  - Android Keystore AES-256-GCM BYOK key storage.
  - Hardened FileProvider and auto-backup exclusions.
  - Target & Compile SDK 36.
- ✅ **Phase 2 & 5: Universal Multi-Discipline Pedagogy & Multi-Provider AI**
  - Academic domain classification (STEM, Physical Sciences, Life Sciences, Economics & Commerce, History & Geopolitics, Philosophy & Humanities, General).
  - Strict prohibition of programming code blocks in non-CS domains.
  - Multi-provider architecture (Gemini, Anthropic, OpenAI, Ollama).
  - SSE streaming recovery, token usage tracking, exponential backoff.
- ✅ **Phase 3: Evidence Grounding & Source Intelligence**
  - Inline source citations, bibliography cards, and epistemic fact/hypothesis badges.
  - Legal, medical, and financial educational disclaimers.
- ✅ **Phase 4: Next-Generation Adaptive Mastery**
  - Free Spaced Repetition Scheduler (FSRS-v4) for memory stability and retrieval probability.
  - Concept mastery tracking, repeated mistake diagnoses, and targeted 3-minute remedial lessons.
- ✅ **Phase 6: Research & Document Intelligence**
  - On-device PDF text extractor, EPUB spine parser, Markdown, and text chunking.
  - Untrusted document sanitization and prompt-injection defense.
  - Offline BM25 lexical search ranking without cloud embeddings.
- ✅ **Phase 7: Interactive Knowledge Laboratory**
  - 6 native Jetpack Compose Canvas simulations: 2D Math Function Plots, Physics Kinematics, Economics Supply/Demand Markets, Cellular Bioenergetics Pathways, Historical Geopolitical Timelines, and Epistemic Argument Maps.
- ✅ **Phase 8: Global Accessibility & Localization**
  - Android resource localization (`strings.xml` and `strings-es.xml`).
  - Dynamic font scaling, TalkBack semantics for math formulas and diagrams.
  - High-contrast WCAG AAA theme and reduced-motion preferences.
- ✅ **Phase 9: Multi-Device Sync Architecture**
  - Room local source of truth with Version 4 database schema.
  - Record-level merge conflict resolution and deletion tombstones.
  - Extensible `SyncProvider` (Local Archive, Google Drive, WebDAV/Nextcloud).
- ✅ **Phase 10 & 11: Release Engineering & Open-Source Readiness**
  - Room indexing on foreign keys and search columns.
  - `@Immutable` Compose UI state models.
  - R8 release rules and automated GitHub Actions CI pipeline.

---

## 🔮 Upcoming Milestones

### Milestone 1.1: Local Edge AI Execution
- **On-Device Inference:** Direct integration with MediaPipe GenAI / ONNX Runtime to execute lightweight models (e.g. Gemma 2B, Phi-3, Llama 3.2 1B) with zero internet connection.
- **Offline Diagnostics:** Generate diagnostic interviews and flashcards completely offline.

### Milestone 1.2: Multi-Modal Audio & Voice Tutoring
- **Bidirectional Speech:** Real-time conversational Socratic dialogue via Android SpeechRecognizer and on-device neural TTS.
- **Auditory Flashcards:** Hands-free voice review while commuting or exercising.

### Milestone 1.3: Peer-to-Peer Collaborative Sync
- **Local P2P Exchange:** Direct course and flashcard deck sharing over Local Wi-Fi / Nearby Connections without any internet server.
- **Classroom AirDrop:** Offline teacher-to-student curriculum distribution.

### Milestone 1.4: Cross-Platform Horizons
- **Desktop (Kotlin Multiplatform / Compose Desktop):** Run openED seamlessly on Linux, macOS, and Windows.
- **Wear OS Companion:** Micro-spaced repetition quick-reviews on smartwatches.
