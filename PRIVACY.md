# Privacy Policy & Data Sovereignty 🛡️

openED is founded upon an unwavering commitment to **data sovereignty**, **local-first storage**, and **zero non-consensual telemetry**.

---

## 1. Local-First Storage Guarantee
- **Where Your Data Lives:** 100% of your created courses, diagnostic profiles, generated chapters, revision histories, flashcard reviews, quiz results, and research documents reside on your local physical device inside the app's sandboxed SQLite database.
- **Zero Cloud Mandate:** openED requires zero cloud accounts, zero social logins, and zero registrations to function as a full-featured university.
- **No Third-Party Analytics:** openED contains 0 Google Analytics, 0 Firebase Crashlytics, 0 Mixpanel, 0 Facebook SDKs, and 0 user-tracking beacons.

---

## 2. Bring Your Own Key (BYOK) AI Architecture
- **Hardware-Backed Keystore:** Your AI API keys (Google Gemini, Anthropic Claude, OpenAI, or Ollama endpoint URLs) are encrypted with hardware-backed `AES-256-GCM` via Android Keystore.
- **Direct TLS Endpoints:** When an AI request (e.g., chapter generation, Socratic mentoring) is initiated, prompts travel directly from your device to the official API endpoint over TLS 1.3. There is no openED intermediary proxy or middleman logging your conversations.

---

## 3. Untrusted Document RAG & Local Parsing
- When importing PDFs, EPUBs, or notes into your Research Library, text parsing and chunking occur entirely on your CPU.
- Local indexing uses an offline BM25 lexical search index without sending document text to external vector embedding services.

---

## 4. Multi-Device Sync & Backups
- Sync is strictly opt-in and controlled by the user.
- Users may choose between **Local Device Storage**, **Google Drive (App Data folder)**, or **WebDAV / Nextcloud**.
- Backups never include your API keys.

---

## 5. User Control & Deletion
- Deleting a course or document permanently removes its database records, cached text, and associated flashcards.
- Deletion tombstones ensure deleted entities remain removed across multi-device synchronizations.
