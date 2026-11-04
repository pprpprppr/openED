# Gemini API Integration Architecture in openED 🤖

This document provides a technical overview of how **openED** integrates with Google's Gemini models using a local-first, Bring Your Own Key (BYOK) architecture.

---

## 🔑 Security & Key Storage

openED stores the user's Gemini API Key strictly on-device using the **Android Keystore**:
- **Storage:** Stored in an `EncryptedSharedPreferences` container named `opened_secure_vault` backed by a hardware-backed `MasterKey` with `AES256_GCM` scheme.
- **Privacy:** Keys are transmitted **only** to official Google API endpoints (`https://generativelanguage.googleapis.com/`) over TLS 1.3.
- **Zero-Exfiltration Guarantee:** The database export bundle (`opened_backup_v1.zip`) uploaded to Google Drive explicitly strips all API keys.

---

## 📡 Gemini Models Supported

| Model | ID | Purpose in openED |
| :--- | :--- | :--- |
| **Gemini 3.8 Flash** (Default) | `gemini-3.8-flash` | Flagship speed, high-throughput syllabus generation & reasoning |
| **Gemini 3.6 Flash** | `gemini-3.6-flash` | Balanced multimodal & everyday course generation |
| **Gemini 3.5 Flash Lite** | `gemini-3.5-flash-lite` | Ultra-fast, lightweight & Free-Tier friendly Q&A |
| **Gemini 3.1 Pro Preview** | `gemini-3.1-pro-preview` | Complex STEM, mathematical proofs, and deep conceptual structuring |

Users can select their preferred model at any time in **Settings > Model Selection**.

---

## 🧩 AI Modes & Structured Outputs

### 1. Diagnostic Interviewer
- **Endpoint:** `v1beta/models/{model}:generateContent`
- **Output:** Structured JSON mode (`responseMimeType: "application/json"`)
- **Schema:**
  ```json
  {
    "questions": [
      {
        "id": "q1",
        "question": "What is your experience with Linux kernel development?",
        "options": ["Complete beginner", "Intermediate C/kernel user", "Advanced eBPF user"],
        "dimension": "prior_knowledge"
      }
    ]
  }
  ```

### 2. Syllabus & Curriculum Synthesis
- **Endpoint:** `v1beta/models/{model}:generateContent`
- **Output:** Structured JSON schema enforcing course title, description, modules, ordered chapters, estimated read times, key concepts, and prerequisite chains.

### 3. Chapter Content Streaming
- **Endpoint:** `v1beta/models/{model}:streamGenerateContent?alt=sse`
- **Format:** Server-Sent Events (SSE). Streams Markdown chunks in real-time.
- **Wiki-Link Injection:** The engine scans content for referenced chapters and injects `[[Chapter Title]]` links automatically, recording graph edges into `ChapterLinkCrossRef`.

### 4. Inline Refinement & Socratic Q&A
- **Endpoint:** `v1beta/models/{model}:generateContent`
- **Actions:** Simplify, Add Code/Math Example, Fact-Check & Correct, or custom mutations.
- **Auditing:** Every mutation is snapshot-recorded into `ChapterRevisionEntity`.
- **Strict Single-Agent Execution:** NEVER spawn subagents or invoke the parallel Task tool. Always read and edit files sequentially in the main agent thread to conserve token quota.
