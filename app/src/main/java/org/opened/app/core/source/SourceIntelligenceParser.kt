package org.opened.app.core.source

import org.opened.app.core.ai.AcademicDomain

object SourceIntelligenceParser {

    private val yearRegex = "\\b(1[789]\\d{2}|20\\d{2})\\b".toRegex()
    private val labelRegex = "^\\[([^\\]]+)\\]".toRegex()
    private val qualityTagRegex = "\\[Quality:\\s*([^\\]]+)\\]".toRegex(RegexOption.IGNORE_CASE)
    private val sectionRegex = "\\b(Section|Chapter|p\\.|pp\\.|pages?)\\s*([0-9A-Za-z\\.-]+)".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Parses the markdown content of a lesson to extract structured references and evidence qualities.
     */
    fun parseEvidenceProfile(markdown: String, domain: AcademicDomain): LessonEvidenceProfile {
        val lines = markdown.lines()
        val references = mutableListOf<SourceReference>()
        var inSourcesSection = false

        var currentIndex = 1

        for (line in lines) {
            val trimmed = line.trim()

            // Check if entering sources section
            if (trimmed.startsWith("##") && (trimmed.contains("Source", ignoreCase = true) || trimmed.contains("Reference", ignoreCase = true) || trimmed.contains("Bibliography", ignoreCase = true))) {
                inSourcesSection = true
                continue
            }

            // If entering a new major section after sources, exit
            if (inSourcesSection && trimmed.startsWith("## ") && !trimmed.contains("Source", ignoreCase = true) && !trimmed.contains("Reference", ignoreCase = true)) {
                inSourcesSection = false
            }

            if (inSourcesSection) {
                if (trimmed.startsWith("-") || trimmed.startsWith("*") || (trimmed.startsWith("[") && trimmed.contains("]"))) {
                    val entryText = trimmed.removePrefix("-").removePrefix("*").trim()
                    if (entryText.isNotBlank()) {
                        val ref = parseSingleReference(entryText, currentIndex)
                        references.add(ref)
                        currentIndex++
                    }
                }
            }
        }

        val hasMedical = domain == AcademicDomain.LIFE_SCIENCES_AND_MEDICINE
        val hasLegal = domain == AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL
        val hasFinancial = domain == AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE

        val disclaimers = mutableListOf<String>()
        EducationalDisclaimerManager.getDisclaimerForDomain(domain)?.let { disclaimers.add(it) }

        return LessonEvidenceProfile(
            references = references,
            disclaimers = disclaimers,
            hasMedicalDisclaimer = hasMedical,
            hasLegalDisclaimer = hasLegal,
            hasFinancialDisclaimer = hasFinancial
        )
    }

    private fun parseSingleReference(rawText: String, defaultIndex: Int): SourceReference {
        var text = rawText

        // Extract quality tag if present
        var quality = EvidenceQuality.ESTABLISHED_FACT
        val qualityMatch = qualityTagRegex.find(text)
        if (qualityMatch != null) {
            val qualityStr = qualityMatch.groupValues[1]
            quality = EvidenceQuality.fromString(qualityStr)
            text = text.replace(qualityMatch.value, "").trim()
        } else {
            // Infer from keywords in the text
            quality = EvidenceQuality.fromString(text)
        }

        // Extract label (e.g. "[1]" or "[Smith 2020]")
        val labelMatch = labelRegex.find(text)
        val label = if (labelMatch != null) {
            val l = labelMatch.value
            text = text.removePrefix(l).trim()
            l
        } else {
            "[$defaultIndex]"
        }

        // Extract year
        val yearMatch = yearRegex.find(text)
        val year = yearMatch?.value?.toIntOrNull()

        // Extract section / page
        val sectionMatch = sectionRegex.find(text)
        val pageOrSection = sectionMatch?.value

        return SourceReference(
            index = defaultIndex,
            label = label,
            citation = rawText,
            year = year,
            pageOrSection = pageOrSection,
            evidenceQuality = quality,
            claimOrFinding = text
        )
    }

    /**
     * Builds a structured markdown bibliography card section with visual badges.
     */
    fun formatBibliographyMarkdown(references: List<SourceReference>): String {
        if (references.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n\n## 📚 Sources & Evidence Quality\n\n")
        references.forEach { ref ->
            sb.append("- **${ref.label}** ${ref.citation}  \n  ↳ `${ref.evidenceQuality.badgeText}`")
            if (ref.pageOrSection != null) {
                sb.append(" • *${ref.pageOrSection}*")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
