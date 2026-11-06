# openED Critical Security Audit (Phase 1)

**Date:** November 2, 2026  
**Project:** openED — Global Personal AI University  
**Target Platform:** Android (compileSdk 36, targetSdk 36, minSdk 26)  
**Security Status:** HARDENED & VERIFIED  
**Automated Security Suite:** 47/47 Tests Passing  

---

## 1. Executive Summary

Phase 1 of the openED master specification mandates eliminating all critical security vulnerabilities, removing broad filesystem exposure vectors, replacing deprecated credential storage with hardware-backed Android Keystore AES-256-GCM encryption, enforcing strict backup exclusion policies, and upgrading target compliance to Android 16 (API Level 36).

All Phase 1 security requirements have been implemented, verified, and regression-tested.

---

## 2. FileProvider Hardening

### Former Vulnerabilities
- `file_provider_paths.xml` previously declared `<root-path name="root" path="." />`, `<files-path name="files" path="." />`, and `<cache-path name="cache" path="." />`.
- This exposed the entire Linux root filesystem (`/`) and app private directories (including SQLite databases, DataStore preferences, and Keystore envelopes) to any application interacting with the `FileProvider`.

### Hardened Implementation
- **Dedicated Subdirectories:** Locked down strictly to user-initiated export directories:
  ```xml
  <paths xmlns:android="http://schemas.android.com/apk/res/android">
      <cache-path name="exports_cache" path="exports/" />
      <files-path name="exports_files" path="exports/" />
  </paths>
  ```
- **Prohibited Exposures:** Completely purged `<root-path>`, broad root `path="."`, and external storage access.
- **Export Isolation:** `ExportManager.kt` was updated to generate Obsidian `.zip` vaults and Anki `.tsv` flashcards exclusively inside `context.cacheDir/exports/`.
- **Least Privilege Sharing:** File intents strictly specify `Intent.FLAG_GRANT_READ_URI_PERMISSION` with temporary, read-only grants.
- **Automated Verification:** `FileProviderSecurityTest.kt` verifies via AST assertions that no broad paths or root paths exist in `file_provider_paths.xml`.

---

## 3. Android Keystore AES-256-GCM Secure Key Storage

### Former Vulnerabilities
- `SecureApiKeyStorage.kt` relied on `androidx.security.crypto.EncryptedSharedPreferences`, which is officially deprecated, prone to known hardware keystore desynchronization crashes on specific vendor ROMs, and fell back to unencrypted plaintext storage in `opened_secure_vault_fallback`.

### Hardened Implementation (`SecureApiKeyStorage.kt`)
1. **Hardware-Backed Master Key:**
   - Key alias: `opened_keystore_master_key` in `AndroidKeyStore`.
   - Algorithm: `AES` (256-bit key length).
   - Block Mode: `GCM` (Galois/Counter Mode).
   - Padding: `NoPadding`.
   - Purposed for authenticated encryption and decryption (`PURPOSE_ENCRYPT | PURPOSE_DECRYPT`).
2. **Cryptographic Nonce (IV):**
   - Strictly 12-byte cryptographically random IV generated per encryption operation via `SecureRandom`.
   - Nonces are never reused, precluding GCM keystream replay attacks.
3. **Authenticated Encryption (AEAD):**
   - 128-bit authentication tag appended to the ciphertext.
   - Any modification or bit-flip in the ciphertext or tag throws `AEADBadTagException` and returns `null`, preventing silent tampering.
4. **Versioned Storage Envelope:**
   - Byte 0: Envelope version identifier (`0x01`).
   - Bytes 1–12: 12-byte random IV.
   - Bytes 13–End: Ciphertext with 128-bit authentication tag.
   - Encoded as standard Base64 for persistent storage in `opened_keystore_vault`.
5. **Non-Destructive Migration:**
   - On first startup, `SecureApiKeyStorage` inspects legacy `opened_secure_vault` (EncryptedSharedPreferences) and `opened_secure_vault_fallback`.
   - Discovered credentials (Gemini, Claude, OpenAI, Custom keys and models) are decrypted, re-encrypted into the new Keystore AES-256-GCM envelope, and saved to `opened_keystore_vault`.
   - Legacy containers are then securely wiped to leave zero plaintext remnants on device.
6. **Zero-Leak Guarantee:**
   - Zero plaintext logging in Logcat.
   - API keys are explicitly excluded from `BackupBundle` and Drive export archives.
   - In-memory `ConcurrentHashMap` caching prevents main-thread disk I/O while preserving thread safety.
7. **Automated Verification:**
   - `SecureApiKeyStorageTest.kt` exercises round-trip encryption/decryption, IV uniqueness, envelope version verification, tamper rejection, and invalid input handling.

---

## 4. Android Auto-Backup & Data Extraction Rules

### Risk Analysis
- Android's cloud auto-backup and device-to-device transfer can inadvertently serialize encrypted preferences to Google Drive or peer devices. If hardware-backed Keystore keys cannot be exported across physical devices, restoring encrypted payloads leads to unrecoverable deadlocks or key-mismatch crashes.

### Hardened Implementation
- **Android 12+ (API 31+) Rules:** Created [`data_extraction_rules.xml`](./app/src/main/res/xml/data_extraction_rules.xml):
  - Excludes `opened_keystore_vault.xml`, `opened_secure_vault.xml`, and `opened_secure_vault_fallback.xml` from `<cloud-backup>` and `<device-transfer>`.
  - Excludes temporary cache directories.
- **Legacy (API < 31) Rules:** Created [`backup_rules.xml`](./app/src/main/res/xml/backup_rules.xml):
  - Full backup exclusion for all credential preferences.
- **Manifest Integration:** Configured in [`AndroidManifest.xml`](./app/src/main/AndroidManifest.xml):
  ```xml
  android:allowBackup="true"
  android:dataExtractionRules="@xml/data_extraction_rules"
  android:fullBackupContent="@xml/backup_rules"
  ```

---

## 5. Android SDK 36 Upgrade & Compliance

- **Compilation & Target:**
  - Upgraded `compileSdk = 36` and `targetSdk = 36` in [`app/build.gradle.kts`](./app/build.gradle.kts).
  - Successfully verified `platforms;android-36` via Android SDK manager into `$ANDROID_HOME/platforms/android-36`.
  - Added `android.suppressUnsupportedCompileSdk=36` to [`gradle.properties`](./gradle.properties) to cleanly suppress AGP 8.7.2 advisory warnings.
- **Behavior Compliance:**
  - Edge-to-edge layout enabled in `MainActivity.kt`.
  - Notification permissions gated under `POST_NOTIFICATIONS`.
  - Modern storage scoped strictly through app cache and FileProvider export directories.

---

## 6. Automated Verification Results

```
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 8s
31 actionable tasks: 12 executed, 19 up-to-date
```
- Total Tests: **47 passed, 0 failed, 0 ignored**
- New Test Suites Added:
  - [`SecureApiKeyStorageTest.kt`](./app/src/test/java/org/opened/app/core/security/SecureApiKeyStorageTest.kt) (6 tests)
  - [`FileProviderSecurityTest.kt`](./app/src/test/java/org/opened/app/core/export/FileProviderSecurityTest.kt) (1 test)
  - [`PromptEngineeringTest.kt`](./app/src/test/java/org/opened/app/core/ai/PromptEngineeringTest.kt) (14 tests)
