package org.opened.app.core.source

data class SourceReference(
    val index: Int,
    val label: String, // e.g. "[1]" or "[Smith 2021]"
    val citation: String, // Full bibliographical citation
    val authors: String = "",
    val year: Int? = null,
    val publicationVenue: String = "",
    val pageOrSection: String? = null,
    val evidenceQuality: EvidenceQuality = EvidenceQuality.ESTABLISHED_FACT,
    val claimOrFinding: String = ""
)

data class LessonEvidenceProfile(
    val references: List<SourceReference> = emptyList(),
    val disclaimers: List<String> = emptyList(),
    val hasMedicalDisclaimer: Boolean = false,
    val hasLegalDisclaimer: Boolean = false,
    val hasFinancialDisclaimer: Boolean = false
)
