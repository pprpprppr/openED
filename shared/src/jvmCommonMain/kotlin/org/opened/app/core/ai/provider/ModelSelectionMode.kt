package org.opened.app.core.ai.provider

/**
 * Model selection modes supported across openED providers:
 * - AUTO: Intelligently switches based on task (Curriculum synthesis -> Quality, Streaming lesson -> Balanced, Flashcards -> Economy)
 * - QUALITY: Maximum reasoning and mathematical/analytical depth
 * - BALANCED: High-throughput, responsive streaming with strong coherence
 * - ECONOMY: Low token consumption, lightweight generation
 * - MANUAL: Fixed model explicitly selected by the user
 */
enum class ModelSelectionMode(val id: String, val displayName: String, val description: String) {
    AUTO(
        id = "auto",
        displayName = "Auto (Smart Selection)",
        description = "Intelligently chooses Quality for curriculum and Balanced for lessons"
    ),
    QUALITY(
        id = "quality",
        displayName = "Quality (Deep Reasoning)",
        description = "Prioritizes rigorous mathematical proofs, complex systems, and capstone curricula"
    ),
    BALANCED(
        id = "balanced",
        displayName = "Balanced (Default)",
        description = "Optimized for high-speed streaming, practical lessons, and interactive Q&A"
    ),
    ECONOMY(
        id = "economy",
        displayName = "Economy (Low Latency)",
        description = "Lightweight, ultra-fast generation with minimal token consumption"
    ),
    MANUAL(
        id = "manual",
        displayName = "Manual (User Choice)",
        description = "Uses the exact model chosen in settings without automatic adjustment"
    );

    companion object {
        fun fromId(id: String?): ModelSelectionMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: BALANCED
        }
    }
}
