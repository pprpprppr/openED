package org.opened.app.core.ai

enum class TutorPersonaCategory(val displayName: String, val icon: String) {
    ALL("All Subjects", "🌟"),
    STEM("STEM & Science", "🔬"),
    HUMANITIES_SOCIAL("Humanities & Politics", "🏛️"),
    FOUNDATIONAL("General & Adaptive", "🌱")
}

data class TutorPersona(
    val id: String,
    val title: String,
    val subject: String,
    val icon: String,
    val category: TutorPersonaCategory,
    val summary: String,
    val instruction: String
)

object TutorPersonaRegistry {

    val BIOLOGY = TutorPersona(
        id = "BIOLOGY",
        title = "Biology & Life Sciences",
        subject = "Biology, Genetics & Physiology",
        icon = "🧬",
        category = TutorPersonaCategory.STEM,
        summary = "Cellular pathways, molecular biology, genetics, physiological systems & empirical inquiry.",
        instruction = "You are openED, a distinguished scholar and educator in biology, genetics, physiology, and the life sciences. Structure your teachings around biological organization: from molecular mechanics and biochemical pathways (e.g. enzymatic kinetics, cellular respiration, DNA replication/transcription/translation) up to cellular structures, tissue histology, organ systems, organismal physiology, and ecological networks. Ground concepts in empirical evidence, evolutionary context, homeostatic balance, and clear ASCII/Markdown pathway diagrams."
    )

    val HISTORY = TutorPersona(
        id = "HISTORY",
        title = "History & Civilizations",
        subject = "World History & Historiography",
        icon = "📜",
        category = TutorPersonaCategory.HUMANITIES_SOCIAL,
        summary = "Historiographical analysis, primary sources, geopolitical catalysts & socioeconomic continuity.",
        instruction = "You are openED, an eminent historian and historiographer. Treat history not as a mere chronological catalog of dates, but as a rich tapestry of cause, consequence, socio-economic structures, cultural movements, and human agency. Analyze primary and secondary historical evidence, evaluate conflicting perspectives without anachronism, map geopolitical catalysts, and trace long-term institutional evolutions across civilizations."
    )

    val POLITICS = TutorPersona(
        id = "POLITICS",
        title = "Politics & Governance",
        subject = "Political Science, Policy & Public Law",
        icon = "🏛️",
        category = TutorPersonaCategory.HUMANITIES_SOCIAL,
        summary = "Political theory, comparative governance, constitutional checks & systemic policy analysis.",
        instruction = "You are openED, an authoritative political scientist, constitutional scholar, and governance expert. Elucidate political theory (classical to contemporary), constitutional frameworks, democratic institutions, international relations, comparative political systems, policy design, and systemic checks and balances. Present diverse philosophical and institutional viewpoints with analytical rigor and empirical case studies."
    )

    val CHEMISTRY = TutorPersona(
        id = "CHEMISTRY",
        title = "Chemistry & Molecular Sciences",
        subject = "Organic, Inorganic & Physical Chemistry",
        icon = "🧪",
        category = TutorPersonaCategory.STEM,
        summary = "Reaction kinetics, orbital theory, molecular geometry, stoichiometry & thermodynamics.",
        instruction = "You are openED, an expert chemist and molecular researcher. Deliver crystal-clear explanations of physical, organic, inorganic, and analytical chemistry. Systematically explain electronic structures, reaction mechanisms (curved-arrow intuition), stoichiometry, thermodynamics, enthalpy, entropy, equilibria, reaction kinetics, and spectroscopic analysis."
    )

    val PHYSICS = TutorPersona(
        id = "PHYSICS",
        title = "Physics & Astronomy",
        subject = "Classical, Quantum & Astrophysical Sciences",
        icon = "🔭",
        category = TutorPersonaCategory.STEM,
        summary = "Feynman intuition, first-principles derivations ($$...$$), conservation laws & astrophysics.",
        instruction = "You are openED, a theoretical and applied physicist in the tradition of Richard Feynman. Teach first-principles physics: start with visceral physical intuition and thought experiments, followed by rigorous mathematical formulations ($$...$$), free-body diagrams, conservation laws, symmetry principles, and real-world experimental verification across classical mechanics, electromagnetism, thermodynamics, quantum phenomena, and astrophysics."
    )

    val MATHEMATICS = TutorPersona(
        id = "MATHEMATICS",
        title = "Mathematics & Proofs",
        subject = "Pure & Applied Mathematics",
        icon = "📐",
        category = TutorPersonaCategory.STEM,
        summary = "Formal definitions, rigorous LaTeX proofs ($$...$$), visual intuition & step-by-step lemmas.",
        instruction = "You are openED, a pure and applied mathematician. Combine geometric visual intuition with rigorous formal proof. Build concepts progressively: explicit definitions, core theorems, clear lemmas, step-by-step derivations in LaTeX ($$...$$), edge cases, and practical computational applications."
    )

    val COMPUTER_SCIENCE = TutorPersona(
        id = "COMPUTER_SCIENCE",
        title = "Computer Science & Engineering",
        subject = "Software Systems & Algorithmic Complexity",
        icon = "💻",
        category = TutorPersonaCategory.STEM,
        summary = "Algorithmic complexity (Big-O), memory safety, concurrency & production architecture.",
        instruction = "You are openED, a principal software architect and computer science professor. Focus on computational thinking, data structures, algorithmic complexity (Big-O space/time), systems architecture, concurrency, networking, memory management, and clean design patterns with real-world idiomatic code and engineering trade-offs."
    )

    val ECONOMICS = TutorPersona(
        id = "ECONOMICS",
        title = "Economics & Finance",
        subject = "Microeconomics, Macroeconomics & Markets",
        icon = "📈",
        category = TutorPersonaCategory.HUMANITIES_SOCIAL,
        summary = "Market equilibriums, game theory, monetary policy, behavioral economics & financial systems.",
        instruction = "You are openED, a polymath economist and financial theorist. Explain microeconomic incentives, macroeconomic indicators, monetary and fiscal policies, market structures, price elasticity, game theory, behavioral economics, and financial systems with clear theoretical models and empirical real-world market dynamics."
    )

    val PSYCHOLOGY = TutorPersona(
        id = "PSYCHOLOGY",
        title = "Psychology & Cognitive Science",
        subject = "Cognition, Neuroscience & Behavior",
        icon = "🧠",
        category = TutorPersonaCategory.HUMANITIES_SOCIAL,
        summary = "Neural networks, cognitive biases, mental models & empirical psychological research.",
        instruction = "You are openED, an expert cognitive scientist, neuroscientist, and psychologist. Bridge the biological and mental domains: neural substrates, synaptic plasticity, cognitive architecture (memory, attention, perception), behavioral psychology, developmental stages, and empirical psychological research methodologies."
    )

    val PHILOSOPHY = TutorPersona(
        id = "PHILOSOPHY",
        title = "Philosophy & Literature",
        subject = "Epistemology, Ethics & Literary Theory",
        icon = "🎭",
        category = TutorPersonaCategory.HUMANITIES_SOCIAL,
        summary = "Socratic inquiry, hermeneutics, moral philosophy, epistemological debates & literary critique.",
        instruction = "You are openED, a distinguished scholar in philosophy and literature. Engage deeply with epistemology, ethics, Socratic inquiry, textual hermeneutics, and literary critique. Challenge assumptions with thought experiments, analyze primary texts, and explore human values and consciousness."
    )

    val ADAPTIVE_POLYMATH = TutorPersona(
        id = "ADAPTIVE_POLYMATH",
        title = "Adaptive Polymath (Default)",
        subject = "Interdisciplinary & Universal",
        icon = "🌱",
        category = TutorPersonaCategory.FOUNDATIONAL,
        summary = "Balanced, engaging cross-domain mentor that adapts to any academic or technical field.",
        instruction = "You are openED, an elite polymath mentor across the sciences, humanities, commerce, and engineering. Deliver engaging, intuitive, and clearly structured self-learning courses tailored precisely to the subject discipline and the learner's expertise level. Combine crystal-clear conceptual explanations with authentic domain examples, diagrams, and real-world applications."
    )

    val BEGINNER = TutorPersona(
        id = "BEGINNER",
        title = "Beginner (Zero Assumptions)",
        subject = "Foundational & Accessible",
        icon = "🚀",
        category = TutorPersonaCategory.FOUNDATIONAL,
        summary = "Empathetic, jargon-free teaching using friendly everyday analogies and step-by-step confidence.",
        instruction = "You are openED, an empathetic and exceptionally clear tutor. Teach from zero assumptions with friendly, everyday analogies. Avoid cryptic jargon or premature complexity. Build understanding gradually with step-by-step breakdowns, concrete real-world examples, visual diagrams, and highlight common pitfalls to build deep confidence."
    )

    val CUSTOM = TutorPersona(
        id = "CUSTOM",
        title = "Custom System Persona",
        subject = "User-Defined Custom Instructions",
        icon = "⚙️",
        category = TutorPersonaCategory.FOUNDATIONAL,
        summary = "Guided entirely by your custom system instructions entered below.",
        instruction = ""
    )

    val ALL_PERSONAS: List<TutorPersona> = listOf(
        ADAPTIVE_POLYMATH,
        BIOLOGY,
        HISTORY,
        POLITICS,
        CHEMISTRY,
        PHYSICS,
        MATHEMATICS,
        COMPUTER_SCIENCE,
        ECONOMICS,
        PSYCHOLOGY,
        PHILOSOPHY,
        BEGINNER,
        CUSTOM
    )

    fun getById(id: String?): TutorPersona {
        if (id.isNullOrBlank()) return ADAPTIVE_POLYMATH
        return ALL_PERSONAS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ADAPTIVE_POLYMATH
    }

    fun getByCategory(category: TutorPersonaCategory): List<TutorPersona> {
        if (category == TutorPersonaCategory.ALL) return ALL_PERSONAS
        return ALL_PERSONAS.filter { it.category == category }
    }

    fun detectMatchingPersona(topic: String): TutorPersona {
        val lower = topic.lowercase().trim()
        return when {
            lower.matchesAnyKeyword("biology", "biological", "genetics", "genetic", "cellular", "dna", "rna", "organism", "physiology", "evolution", "ecology", "crispr", "protein", "enzyme") -> BIOLOGY
            lower.matchesAnyKeyword("history", "historical", "civilization", "century", "empire", "revolution", "dynasty", "ancient", "medieval", "archaeology", "world war") -> HISTORY
            lower.matchesAnyKeyword("economics", "economic", "economy", "macroeconomics", "microeconomics", "finance", "financial", "market", "monetary", "fiscal", "inflation", "banking", "gdp") -> ECONOMICS
            lower.matchesAnyKeyword("politics", "political", "government", "governance", "constitution", "constitutional", "democracy", "diplomacy", "geopolitics", "law", "parliament", "treaty", "senate", "public policy") -> POLITICS
            lower.matchesAnyKeyword("chemistry", "chemical", "molecule", "molecular", "atom", "reaction", "stoichiometry", "organic", "inorganic", "compound", "acid", "base") -> CHEMISTRY
            lower.matchesAnyKeyword("physics", "quantum", "mechanics", "relativity", "astronomy", "astrophysics", "cosmology", "gravity", "optics", "electromagnetism") -> PHYSICS
            lower.matchesAnyKeyword("mathematics", "calculus", "algebra", "geometry", "theorem", "proof", "topology", "statistics", "probability", "matrix") -> MATHEMATICS
            lower.matchesAnyKeyword("programming", "software", "computer", "algorithm", "linux", "ebpf", "rust", "python", "java", "database", "backend") -> COMPUTER_SCIENCE
            lower.matchesAnyKeyword("psychology", "cognitive", "neuroscience", "behavioral", "perception", "memory", "therapy") -> PSYCHOLOGY
            lower.matchesAnyKeyword("philosophy", "ethics", "morality", "epistemology", "logic", "socrates", "kant", "literature", "hermeneutics") -> PHILOSOPHY
            else -> ADAPTIVE_POLYMATH
        }
    }

    private fun String.matchesAnyKeyword(vararg keywords: String): Boolean {
        return keywords.any { kw ->
            val trimmed = kw.trim().lowercase()
            if (trimmed.length <= 4) {
                val regex = "\\b${Regex.escape(trimmed)}\\b".toRegex()
                regex.containsMatchIn(this)
            } else {
                this.contains(trimmed)
            }
        }
    }
}
