# Security Policy 🔒

The openED project team takes security, user privacy, and on-device data sovereignty seriously. This document outlines our security architecture, supported versions, and procedure for reporting vulnerabilities.

---

## 🛡️ Security Guarantees & Architecture

openED is engineered with a **zero-trust, local-first** threat model:

### 1. Hardware-Backed Android Keystore Storage
- **Cryptographic Scheme:** AES-256-GCM authenticated encryption (`MasterKey` backed by Android KeyStore).
- **Initialization Vectors:** A fresh, cryptographically secure 12-byte IV is generated per encryption operation via `SecureRandom`.
- **Versioned Ciphertext Envelopes:** Stored securely in on-device storage; raw API keys are never written to unencrypted shared preferences, logs, or crash dumps.
- **Auto-Backup Exclusion:** Keys and sensitive credentials are explicitly excluded from Android cloud/device-to-device auto-backup (`data_extraction_rules.xml` and `backup_rules.xml`).

### 2. Zero-Exfiltration Privacy Guarantee
- User credentials (Gemini, Anthropic, OpenAI, or Ollama endpoint keys) are transmitted **strictly** to official provider endpoints over TLS 1.3.
- Knowledge base exports (`opened_backup_v1.zip` or `opened_sync_bundle.zip`) explicitly strip all API credentials and sensitive tokens.

### 3. FileProvider Sandboxing
- `app/src/main/res/xml/file_provider_paths.xml` is strictly confined to dedicated internal subdirectories (`exports/` and `documents/`).
- External root paths (`<root-path>`) and dangerous broad path shares are strictly forbidden.

### 4. Untrusted Document Ingestion Isolation
- Imported user documents (PDF, EPUB, Markdown, Plain Text) are treated strictly as passive, untrusted data.
- Text is sanitized via `UntrustedDocumentSanitizer` to strip delimiter-injection attacks (`[SYSTEM PROMPT]`, `Ignore previous instructions`, `DAN mode`, etc.) before being passed to LLM context windows.

---

## 📦 Supported Versions

Security updates and patches are actively maintained for the following versions:

| Version | Supported |
|:---|:---|
| `1.0.x` | ✅ Yes |
| `< 1.0.0` | ❌ No |

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in openED, please report it responsibly:

1. **Do NOT open a public GitHub issue.**
2. Send an email to the security team at **security@opened.org** (or contact the maintainers via private GitHub Security Advisory).
3. Include detailed reproduction steps, proof of concept, affected components, and potential impact.

We will acknowledge receipt within 48 hours and work with you to patch and publicly credit the discovery once resolved.
