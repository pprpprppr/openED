# openED Architecture Specification 🏛️

This document describes the high-level system architecture, software components, security mechanisms, and data flows of **openED — Global Personal AI University**.

```
+---------------------------------------------------------------------------------+
|                                 openED CLIENT                                   |
|                                                                                 |
|  +--------------------+  +--------------------+  +---------------------------+  |
|  |     Jetpack        |  |  Interactive Compose|  |     Android TalkBack      |  |
|  |  Compose Material 3|  |  Canvas Laboratory |  |  Semantics & Localization |  |
|  +---------+----------+  +---------+----------+  +-------------+-------------+  |
|            |                       |                           |                |
|  +---------v-----------------------v---------------------------v-------------+  |
|  |                            VIEWMODELS (MVI)                                |  |
|  | Home | DiagnosticWizard | CourseWorkspace | FlashcardReview | Settings     |  |
|  +---------------------------------+-----------------------------------------+  |
|                                    |                                            |
|  +---------------------------------v-----------------------------------------+  |
|  |                        CORE PEDAGOGY & ENGINES                            |  |
|  | - AcademicDomainClassifier (7 Authentic Disciplines, Non-CS Guardrails)  |  |
|  | - AdaptiveLearningEngine & FSRS-v4 Spaced Repetition                      |  |
|  | - DocumentIntelligenceEngine & Local BM25 Search Index                   |  |
|  | - SourceIntelligenceParser & Educational Disclaimer Manager               |  |
|  +---------------------------------+-----------------------------------------+  |
|                                    |                                            |
|  +---------------------------------v-----------------------------------------+  |
|  |                       MULTI-AI PROVIDER ADAPTERS                          |  |
|  | GeminiProvider | AnthropicProvider | OpenAiProvider | OllamaProvider      |  |
|  | (Hardware-Backed Keystore AES-256-GCM BYOK, BackoffRetry, SSE Streaming)   |  |
|  +---------------------------------+-----------------------------------------+  |
|                                    |                                            |
|  +---------------------------------v-----------------------------------------+  |
|  |                 LOCAL-FIRST DATA & SYNC ARCHITECTURE                      |  |
|  |  OpenEDDatabase (Room SQLite v4 Source of Truth)                          |  |
|  |  SyncManager (Record-Level Merge Conflict Resolution & Deletion Tombstones)|  |
|  |  SyncProvider: Local Device Archive | Google Drive | WebDAV / Nextcloud   |  |
|  +---------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------+
```

---

## 1. Local-First Room SQLite Database Architecture

Room serves as the absolute local source of truth on the client device.

### Database Version History
- **Version 1:** Initial schemas for `StudyPathEntity`, `ModuleEntity`, `ChapterEntity`, `ChapterRevisionEntity`, `ChapterLinkCrossRef`, `ChapterQaEntity`.
- **Version 2:** Non-destructive addition of `FlashcardEntity` (SM-2) and `MasteryQuizEntity`.
- **Version 3:** Upgraded `FlashcardEntity` with Free Spaced Repetition Scheduler (`FSRS-v4`) fields (`stability`, `difficulty`, `state`, `elapsedDays`, `lastReviewTimestamp`). Added `LearningObjectiveEntity`, `MistakeRecordEntity`, `RemedialLessonEntity`, `DocumentEntity`, and `DocumentChunkEntity`.
- **Version 4:** Non-destructive addition of `SyncTombstoneEntity` for multi-device sync conflict resolution and query indexes on status, state, and timestamp columns.

---

## 2. Multi-Discipline Academic Pedagogy Engine

openED recognizes that true educational mastery requires authentic pedagogical representations tailored to the discipline:

1. **`SOFTWARE_AND_CS`**: Programming languages, algorithms, architecture, data structures. Authentic code snippets with syntax highlighting.
2. **`MATH_AND_PHYSICAL_SCIENCES`**: Pure/applied mathematics, physics, chemistry. Authentic LaTeX ($$...$$), step-by-step mathematical proofs, and coordinate plots.
3. **`LIFE_SCIENCES_AND_MEDICINE`**: Cellular bioenergetics, genetics, physiology, biochemistry. **Zero programming code blocks**. Pathway diagrams, enzyme kinetics, and clinical disclaimers.
4. **`COMMERCE_ECONOMICS_AND_FINANCE`**: Micro/macroeconomics, accounting, finance. Worked ledgers, supply/demand curve plots, price elasticity, and financial disclaimers.
5. **`HISTORY_GEOPOLITICS_AND_SOCIAL`**: Historical turning points, geopolitical actor maps, historiography comparison tables, legal disclaimers.
6. **`PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES`**: Epistemic argument maps, thought experiments, logic trees.
7. **`GENERAL_INTERDISCIPLINARY`**: Versatile conceptual and real-world case study framework.

---

## 3. Cryptographic Security & Android Keystore

- **Key Storage:** Raw BYOK API keys are encrypted using an AES-256-GCM cipher backed by Android's hardware `MasterKey`.
- **Initialization Vector:** Every encryption generates a cryptographically random 12-byte IV using `SecureRandom`.
- **Zero Cloud Exfiltration:** API keys are never written to unencrypted storage, never sent to openED tracking servers, and never packaged into sync or backup archives.

---

## 4. Multi-Device Synchronization & Conflict Resolution

- **Stable Entity IDs:** All entities utilize client-generated UUIDs.
- **Deletion Tombstones:** `SyncTombstoneEntity` records entity deletions to prevent resurrection during sync merges.
- **Revision Ancestry:** When conflicting edits occur between local and remote versions of a chapter, the older version is automatically archived as a `ChapterRevisionEntity` with a descriptive change reason. Newer content is never silently overwritten.
- **Extensible Sync Providers:** Pluggable `SyncProvider` interface supporting Local Storage Archives, Google Drive AppData, and standard WebDAV / Nextcloud endpoints.
