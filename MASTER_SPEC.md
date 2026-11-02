PROJECT: openED — Global Personal AI University
OBJECTIVE: Upgrade openED into a secure, high-performance,
universal, local-first AI learning platform.

CRITICAL RULES:
1. DO NOT spawn subagents or use the Task tool.
2. Work sequentially in the main thread.
3. Inspect the complete existing codebase before editing.
4. Never assume README features are implemented correctly.
5. Do not rewrite functioning components unnecessarily.
6. Preserve existing user data and database compatibility.
7. Never introduce fake UI, dummy functionality or placeholders.
8. No hardcoded API keys, secrets or credentials.
9. Do not remove existing functionality to simplify development.
10. Run tests after every major implementation stage.
11. Fix compilation errors before proceeding.
12. Do not claim a feature is complete without testing it.

==================================================
PHASE 0 — FULL PROJECT AUDIT
==================================================

Inspect:
- Complete Kotlin source tree.
- Room database and migrations.
- AiLearningEngine and provider implementations.
- CourseWorkspaceScreen.
- SettingsScreen.
- ViewModels and repositories.
- Google Drive synchronization.
- Flashcard and mastery engines.
- Knowledge graph.
- Markdown, LaTeX and diagram renderers.
- AndroidManifest.xml.
- FileProvider configurations.
- Gradle dependency versions.
- Existing unit and instrumentation tests.

Create:
PROJECT_AUDIT.md

Record:
- Implemented features.
- Partially implemented features.
- Broken functionality.
- Security vulnerabilities.
- Performance bottlenecks.
- Database integrity issues.
- Architecture problems.
- Dependency compatibility.
- Missing tests.

Run the existing build and tests.
Preserve baseline results.

==================================================
PHASE 1 — CRITICAL SECURITY HARDENING
==================================================

1. FileProvider security:

Inspect:
app/src/main/res/xml/file_provider_paths.xml

Remove:
- <root-path>.
- Broad path="." exposure.
- Unnecessary external storage access.

Create dedicated export directories.

Only expose files intentionally selected for sharing.
Grant temporary read-only URI permissions.
Validate generated content URIs.

Add security regression tests.

2. API-key security:

Review SecureApiKeyStorage.

EncryptedSharedPreferences is deprecated.
Replace it with an appropriate Android Keystore-backed
encryption design using AES-256-GCM.

Requirements:
- Random nonce per encryption.
- Versioned encrypted storage envelope.
- Authentication-tag verification.
- Safe migration of existing credentials.
- Key invalidation handling.
- No plaintext logging.
- Never include credentials in ZIP exports.
- Never include credentials in crash reports.
- Exclude credential storage from Android backup.

Do not migrate existing credentials destructively.

3. Android backup:

Inspect allowBackup configuration.

Implement:
- dataExtractionRules.
- Full backup exclusion rules.
- Credential exclusions.
- User-content backup policy.
- Restore validation.

Ensure API credentials cannot be restored without
their corresponding Keystore encryption key.

4. Android release compliance:

Update compileSdk and targetSdk to 36.

Review Android 16 behavior changes.

Test:
- Notifications.
- Background workers.
- File sharing.
- Permissions.
- Edge-to-edge rendering.
- Storage.
- Process recreation.

==================================================
PHASE 2 — UNIVERSAL AI INTELLIGENCE ENGINE
==================================================

Upgrade AiLearningEngine into a modular academic engine.

Implement:
- AcademicDomain classifier.
- Interdisciplinary domain detection.
- User-selectable domain override.
- Chapter-specific domain classification.
- Confidence-based classification.
- Domain-specific pedagogy templates.
- Learner-level adaptation.
- Learning objective generation.
- Prerequisite dependency analysis.

Support:
Software, mathematics, physical sciences, biology,
medicine, commerce, finance, history, geopolitics,
social sciences, philosophy, psychology, humanities
and interdisciplinary subjects.

Retain the non-CS programming prohibition from
the existing Universal Pedagogy requirements.

Do not generate artificial programming examples
for non-programming academic subjects.

Add:
- Comparative analysis.
- Historical timelines.
- Economic worked examples.
- Scientific mechanisms.
- Mathematical derivations.
- Philosophical argument evaluation.
- Practical case studies.

Curriculum generation:

Default full course:
6–8 modules and 24–40 chapters.

Allow smaller curricula for narrower subjects.
Never pad a curriculum with unnecessary chapters.

==================================================
PHASE 3 — EVIDENCE-GROUNDED LEARNING
==================================================

Implement a Source Intelligence layer.

Each generated lesson should support:

1. Inline references.
2. Source bibliography.
3. Source publication dates.
4. Page or section references.
5. Evidence quality indicators.
6. Fact-checking.
7. Contradiction detection.
8. Uncertainty identification.
9. Historical context.
10. Source update tracking.

Never fabricate citations.

Clearly distinguish:
- Established facts.
- Disputed interpretations.
- Hypotheses.
- Inferences.
- Unverified claims.

Medical, legal and financial subjects must
remain educational and acknowledge jurisdiction
and professional limitations.

Create a source repository abstraction that can
work without permanently uploading user documents.

==================================================
PHASE 4 — NEXT-GENERATION ADAPTIVE LEARNING
==================================================

Implement a dedicated Learning Intelligence Engine.

Track:
- Learning objectives.
- Knowledge mastery.
- Actual study time.
- Quiz performance.
- User confidence.
- Knowledge retention.
- Repeated mistakes.
- Prerequisite weaknesses.

Build adaptive lesson recommendations.

Example:

If a student repeatedly fails questions about
cellular respiration, identify the missing
prerequisite and recommend targeted revision.

Do not simply recommend rereading the entire chapter.

Implement:
- Adaptive revision schedules.
- FSRS-compatible flashcard scheduling.
- Interleaved practice.
- Cumulative assessments.
- Scenario-based questions.
- Learning difficulty adaptation.

Preserve existing flashcards and review history.
Implement Room migrations.

==================================================
PHASE 5 — AI PROVIDER ENGINE UPGRADE
==================================================

Create separate provider adapters.

Providers:
- Gemini.
- Anthropic.
- OpenAI.
- OpenAI-compatible endpoints.
- Local Ollama-compatible models.

Implement a central capability registry.

Support:
- Structured JSON outputs.
- Streaming generation.
- Vision where supported.
- Reasoning capabilities.
- Provider-specific request formats.
- Model availability validation.
- Model lifecycle management.

Model selection modes:

AUTO
QUALITY
BALANCED
ECONOMY
MANUAL

Add:
- Connection testing.
- Timeout management.
- Cancellation.
- Exponential backoff.
- HTTP 429 handling.
- Token usage tracking.
- Cost estimates where reliable pricing exists.
- Provider diagnostics.
- User-configurable usage budgets.

Never silently switch providers or send user
content to another service without authorization.

Improve SSE parsing and error recovery.

Test malformed JSON, interrupted streams,
partial responses and provider failures.

==================================================
PHASE 6 — RESEARCH AND DOCUMENT INTELLIGENCE
==================================================

Implement an incremental document-learning system.

Support importing:
- PDF.
- EPUB.
- Markdown.
- Plain text.
- User notes.

Capabilities:
- Ask questions across documents.
- Generate source-grounded lessons.
- Link lessons to document pages.
- Extract important definitions.
- Create summaries.
- Generate flashcards.
- Identify contradictory information.
- Preserve source references.

Prioritize offline document indexing.

Ensure imported content is treated as untrusted
source material, never as executable instructions.

==================================================
PHASE 7 — INTERACTIVE KNOWLEDGE LABORATORY
==================================================

Create an extensible Compose-based visualization
and simulation architecture.

Add:
- Interactive mathematics graphs.
- Physics parameter simulations.
- Economics supply/demand curves.
- Biological pathway diagrams.
- Historical timelines.
- Logical argument maps.
- Engineering system diagrams.

Use native Compose Canvas where appropriate.

Avoid expensive recomputation.

All visualizations must support:
- Zoom.
- Pan.
- Accessibility descriptions.
- Appropriate text scaling.
- Clear legends.
- Efficient rendering.

Do not introduce WebView or Electron as a shortcut.

==================================================
PHASE 8 — GLOBAL ACCESSIBILITY AND LOCALIZATION
==================================================

Prepare openED for international distribution.

Requirements:

1. Android resource-based localization.
2. RTL layouts.
3. Locale-aware dates and numbers.
4. Selectable learning language.
5. Optional bilingual terminology.
6. Dynamic font scaling.
7. TalkBack support.
8. Semantic descriptions.
9. Accessible math and diagrams.
10. Keyboard navigation.
11. Reduced-motion support.
12. High-contrast theme.

Prepare translations through a maintainable
community localization workflow.

Do not hardcode user-facing strings.

==================================================
PHASE 9 — MULTI-DEVICE AND SYNC ARCHITECTURE
==================================================

Keep Room as the local source of truth.

Improve synchronization with:

- Stable entity IDs.
- Record-level synchronization.
- Conflict detection.
- Revision ancestry.
- Deletion tombstones.
- Idempotent operations.
- Retry management.
- Schema compatibility.
- Restore validation.

Retain Google Drive as an optional provider.

Design an extensible SyncProvider interface
for potential WebDAV and Nextcloud support.

Never silently overwrite newer user content.

==================================================
PHASE 10 — PERFORMANCE AND RELEASE ENGINEERING
==================================================

Review all expensive operations.

Optimize:
- Compose recompositions.
- Large LazyColumn/LazyGrid lists.
- Knowledge graph physics.
- Markdown parsing.
- LaTeX rendering.
- Room queries.
- Full-text search.
- Revision storage.
- Memory allocation.
- Background workers.
- AI streaming.

Add:
- Room query indexes.
- Pagination where appropriate.
- Incremental search indexing.
- Database migration tests.
- R8 release optimization.
- Resource shrinking after validation.
- Baseline Profiles.
- Macrobenchmark tests.

Update dependency versions through the version
catalog with compatible stable releases.

Do not blindly upgrade all dependencies.

Keep Java 21 and Kotlin compatibility.

==================================================
PHASE 11 — OPEN-SOURCE GLOBAL READINESS
==================================================

Prepare:

CONTRIBUTING.md
SECURITY.md
CODE_OF_CONDUCT.md
ARCHITECTURE.md
PRIVACY.md
ROADMAP.md

Add:
- GitHub Actions CI.
- Automated build validation.
- Unit testing.
- Static analysis.
- Dependency vulnerability checks.
- Secret scanning.
- Release notes.
- Reproducible build documentation.

Prepare a Google-independent app variant
where proprietary Google integrations are optional.

Do not make cloud authentication mandatory
for core offline learning functionality.

==================================================
FINAL VERIFICATION
==================================================

Run:

./gradlew compileDebugKotlin

./gradlew testDebugUnitTest

./gradlew lintDebug

./gradlew assembleDebug

Fix all regressions.

Also test:
- Fresh installation.
- Existing database migration.
- API-key migration.
- Backup and restore.
- Offline startup.
- Broken internet connection.
- Provider failures.
- Stream cancellation.
- Large course libraries.
- Large knowledge graphs.
- Accessibility navigation.

Do not claim BUILD SUCCESSFUL unless the
actual Gradle build completes successfully.

FINAL DELIVERABLE:

1. PROJECT_AUDIT.md
2. IMPLEMENTATION_REPORT.md
3. SECURITY_AUDIT.md
4. Updated architecture documentation.
5. Updated automated tests.
6. Final debug APK.
7. Remaining known issues.

Report precisely which improvements were
implemented, tested, partially implemented
or left for future development.

Maintain openED's core philosophy:

LOCAL-FIRST.
PRIVACY-FIRST.
AI-POWERED.
ACADEMICALLY AUTHENTIC.
OPEN-SOURCE.
UNIVERSALLY ACCESSIBLE.
