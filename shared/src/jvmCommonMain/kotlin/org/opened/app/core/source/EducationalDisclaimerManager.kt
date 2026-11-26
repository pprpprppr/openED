package org.opened.app.core.source

import org.opened.app.core.ai.AcademicDomain

object EducationalDisclaimerManager {

    const val MEDICAL_DISCLAIMER =
        "> [!WARNING] Medical Education Disclaimer\n> This lesson is provided solely for academic and educational purposes. It does not constitute clinical, diagnosis, or treatment advice. Consult a licensed medical professional for personal healthcare decisions."

    const val LEGAL_DISCLAIMER =
        "> [!WARNING] Legal Education Disclaimer\n> This lesson is provided solely for academic study and jurisprudence education. It does not constitute professional legal counsel. Statutory provisions and constitutional interpretations vary significantly by jurisdiction."

    const val FINANCIAL_DISCLAIMER =
        "> [!WARNING] Financial & Investment Disclaimer\n> This lesson is designed solely for business and economic literacy. It does not constitute certified financial, tax, or investment advice. Historical market performance is not indicative of future returns."

    fun getDisclaimerForDomain(domain: AcademicDomain): String? {
        return when (domain) {
            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE -> MEDICAL_DISCLAIMER
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL -> LEGAL_DISCLAIMER
            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE -> FINANCIAL_DISCLAIMER
            else -> null
        }
    }

    /**
     * Injects the domain-appropriate educational disclaimer if it is not already present in the markdown.
     */
    fun injectDisclaimerIfNeeded(markdown: String, domain: AcademicDomain): String {
        val disclaimer = getDisclaimerForDomain(domain) ?: return markdown
        if (markdown.contains("Disclaimer", ignoreCase = true)) {
            return markdown
        }
        return "$disclaimer\n\n$markdown"
    }
}
