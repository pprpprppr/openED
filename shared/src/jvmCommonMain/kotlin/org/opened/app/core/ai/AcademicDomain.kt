package org.opened.app.core.ai

enum class AcademicDomain(val displayName: String) {
    SOFTWARE_AND_CS("Software & Computer Science"),
    MATH_AND_PHYSICAL_SCIENCES("Mathematics & Physical Sciences"),
    LIFE_SCIENCES_AND_MEDICINE("Life Sciences & Medicine"),
    COMMERCE_ECONOMICS_AND_FINANCE("Commerce, Economics & Finance"),
    HISTORY_GEOPOLITICS_AND_SOCIAL("History, Geopolitics & Law"),
    PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES("Philosophy, Psychology & Humanities"),
    GENERAL_INTERDISCIPLINARY("General & Interdisciplinary")
}

data class DomainClassificationResult(
    val primaryDomain: AcademicDomain,
    val confidence: Float,
    val isInterdisciplinary: Boolean,
    val secondaryDomains: List<AcademicDomain> = emptyList(),
    val domainScores: Map<AcademicDomain, Float> = emptyMap(),
    val matchedKeywords: List<String> = emptyList()
)

data class CurriculumSizeSpec(
    val minModules: Int,
    val maxModules: Int,
    val minChaptersPerModule: Int,
    val maxChaptersPerModule: Int,
    val totalChaptersRange: IntRange,
    val isNarrowTopic: Boolean,
    val rationale: String
)

data class PrerequisiteDependency(
    val concept: String,
    val requiredForChapterTitle: String,
    val prerequisiteChapterTitle: String? = null,
    val isMissing: Boolean = false
)

data class PrerequisiteMapResult(
    val dependencies: List<PrerequisiteDependency> = emptyList(),
    val hasCircularDependency: Boolean = false,
    val missingPrerequisites: List<String> = emptyList()
)

object AcademicDomainClassifier {

    private fun matchesAny(text: String, keywords: List<String>): List<String> {
        val matched = mutableListOf<String>()
        for (kw in keywords) {
            val trimmed = kw.trim()
            if (trimmed.any { !it.isLetterOrDigit() }) {
                if (text.contains(trimmed)) matched.add(trimmed)
            } else {
                val regex = "\\b${Regex.escape(trimmed)}\\b".toRegex()
                if (regex.containsMatchIn(text)) matched.add(trimmed)
            }
        }
        return matched
    }

    private val csKeywords = listOf(
        "programming", "software", "coding", "python", "javascript", "typescript", "kotlin",
        "java ", "c++", "c#", "rust lang", "rust ", "golang", "go lang", "swift ", "sql",
        "web dev", "frontend", "backend", "fullstack", "devops", "docker", "kubernetes", "linux",
        "kernel", "ebpf", "operating system", "cybersecurity", "git ", "github", "api", "rest api",
        "graphql", "microservice", "compiler", "runtime", "jvm", "bytecode", "assembly", "wasm",
        "webassembly", "data structures", "algorithm", "bioinformatics",
        "database", "postgres", "mongodb", "redis", "qiskit", "quantum computing",
        "machine learning engineering", "deep learning", "pytorch", "tensorflow", "transformer model",
        "llm", "prompt engineering", "neural network", "computer science", "html", "css", "react", "android dev"
    )

    private val lifeScienceKeywords = listOf(
        "biology", "biological", "cellular", "cell biology", "molecular biology", "genetics", "gene",
        "dna", "rna", "genomics", "genomic", "genome", "bioinformatics", "bioenergetics", "mitochondria", "cellular respiration", "atp",
        "biochemistry", "neuroscience", "neuron", "synapse", "brain", "anatomy", "anatomical",
        "physiology", "physiological", "medicine", "medical", "clinical", "pharmacology", "drug",
        "immunology", "virology", "microbiology", "pathology", "disease", "human body",
        "ecology", "ecosystem", "evolution", "evolutionary", "zoology", "botany", "plant",
        "agriculture", "nutrition", "nutritional", "metabolism", "metabolic", "protein", "enzyme",
        "hormone", "endocrine", "cardiovascular"
    )

    private val commerceKeywords = listOf(
        "economics", "microeconomics", "macroeconomics", "econometric", "fiscal", "monetary",
        "inflation", "gdp", "interest rate", "central bank", "finance", "corporate finance",
        "accounting", "accountancy", "balance sheet", "income statement", "cash flow", "bookkeeping",
        "stock market", "equities", "bonds", "derivatives", "option trading", "futures", "valuation",
        "dcf", "portfolio", "banking", "investment", "venture capital", "private equity", "fintech",
        "business", "business strategy", "marketing", "supply chain", "logistics", "trade",
        "commerce", "taxation", "taxes", "entrepreneurship", "startup", "management", "mba",
        "market equilibrium", "price elasticity"
    )

    private val historyGeopoliticsKeywords = listOf(
        "history", "historical", "ancient", "medieval", "renaissance", "revolution", "empire",
        "world war", "cold war", "civilization", "dynasty", "treaty", "geopolitics", "geopolitical",
        "international relations", "foreign policy", "diplomacy", "diplomatic", "alliance", "nato",
        "united nations", "political science", "politics", "government", "democracy", "authoritarian",
        "regime", "public policy", "sociology", "sociological", "anthropology", "anthropological",
        "geography", "demography", "law", "legal", "jurisprudence", "constitution", "constitutional",
        "supreme court", "criminal law", "civil law", "tort", "contract law", "judiciary", "civics",
        "human rights"
    )

    private val humanitiesKeywords = listOf(
        "philosophy", "philosophical", "philosopher", "ethics", "ethical", "morality", "moral",
        "epistemology", "metaphysics", "logic", "existentialism", "stoicism", "utilitarianism",
        "kantian", "socrates", "plato", "aristotle", "nietzsche", "descartes", "reasoning",
        "psychology", "psychological", "cognitive science", "cognitive", "behavioral", "behavior",
        "psychoanalysis", "psychiatry", "mental health", "perception", "consciousness",
        "developmental psychology", "social psychology", "bias", "fallacy", "literature", "literary",
        "poetry", "novel", "prose", "linguistics", "semantics", "art history", "music theory",
        "musicology", "religious studies", "theology", "mythology"
    )

    private val mathPhysicsKeywords = listOf(
        "mathematics", "math", "calculus", "linear algebra", "discrete math", "statistics",
        "statistical", "probability", "differential equation", "topology", "number theory",
        "geometry", "trigonometry", "algebra", "matrix", "vector", "eigenvalue", "physics",
        "quantum mechanics", "quantum physics", "relativity", "thermodynamics", "electromagnetism",
        "optics", "astrophysics", "astronomy", "mechanics", "fluid dynamics", "electronics",
        "electrical engineering", "mechanical engineering", "chemical engineering", "chemistry",
        "inorganic", "organic chemistry", "physical chemistry"
    )

    /**
     * Comprehensive multi-discipline classification with confidence scoring,
     * interdisciplinary detection, and support for user or chapter-level overrides.
     */
    fun classify(
        topic: String,
        courseTitle: String = "",
        chapterTitle: String = "",
        userOverride: AcademicDomain? = null
    ): DomainClassificationResult {
        if (userOverride != null && userOverride != AcademicDomain.GENERAL_INTERDISCIPLINARY) {
            return DomainClassificationResult(
                primaryDomain = userOverride,
                confidence = 1.0f,
                isInterdisciplinary = false,
                secondaryDomains = emptyList(),
                domainScores = mapOf(userOverride to 10.0f)
            )
        }

        val combined = "$topic $courseTitle $chapterTitle".lowercase()

        val csMatches = matchesAny(combined, csKeywords)
        val lifeMatches = matchesAny(combined, lifeScienceKeywords)
        val commerceMatches = matchesAny(combined, commerceKeywords)
        val historyMatches = matchesAny(combined, historyGeopoliticsKeywords)
        val humanitiesMatches = matchesAny(combined, humanitiesKeywords)
        val mathMatches = matchesAny(combined, mathPhysicsKeywords)

        fun calculateScore(matches: List<String>): Float {
            return matches.sumOf { kw ->
                if (kw.contains(" ") || kw.length > 8) 3.0 else 2.0
            }.toFloat()
        }

        val scoreMap = mutableMapOf<AcademicDomain, Float>()
        scoreMap[AcademicDomain.SOFTWARE_AND_CS] = calculateScore(csMatches)
        scoreMap[AcademicDomain.LIFE_SCIENCES_AND_MEDICINE] = calculateScore(lifeMatches)
        scoreMap[AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE] = calculateScore(commerceMatches)
        scoreMap[AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL] = calculateScore(historyMatches)
        scoreMap[AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES] = calculateScore(humanitiesMatches)
        scoreMap[AcademicDomain.MATH_AND_PHYSICAL_SCIENCES] = calculateScore(mathMatches)

        val totalScore = scoreMap.values.sum()
        if (totalScore <= 0f) {
            return DomainClassificationResult(
                primaryDomain = AcademicDomain.GENERAL_INTERDISCIPLINARY,
                confidence = 0.5f,
                isInterdisciplinary = false,
                secondaryDomains = emptyList(),
                domainScores = scoreMap
            )
        }

        val sortedScores = scoreMap.entries.sortedByDescending { it.value }
        val primary = sortedScores[0].key
        val primaryScore = sortedScores[0].value

        val confidence = (primaryScore / totalScore).coerceIn(0.5f, 1.0f)
        val secondary = sortedScores.drop(1).filter { it.value >= 2.0f && it.value >= (0.35f * primaryScore) }.map { it.key }
        val isInterdisciplinary = secondary.isNotEmpty()

        val allMatched = (csMatches + lifeMatches + commerceMatches + historyMatches + humanitiesMatches + mathMatches).distinct()

        return DomainClassificationResult(
            primaryDomain = primary,
            confidence = confidence,
            isInterdisciplinary = isInterdisciplinary,
            secondaryDomains = secondary,
            domainScores = scoreMap,
            matchedKeywords = allMatched
        )
    }

    /**
     * Backward-compatible domain detector.
     */
    fun detectAcademicDomain(
        topic: String,
        courseTitle: String = "",
        chapterTitle: String = ""
    ): AcademicDomain {
        return classify(topic, courseTitle, chapterTitle).primaryDomain
    }

    /**
     * Adaptive curriculum sizing: determines whether the topic is narrow/focused
     * (e.g. "Derivation of Black-Scholes", "Mitochondrial Complex I", "Git Rebase vs Merge")
     * vs. a broad discipline course, returning appropriate module and chapter bounds.
     */
    fun determineCurriculumSize(topic: String, userLevel: String = "BEGINNER"): CurriculumSizeSpec {
        val lower = topic.trim().lowercase()
        val words = lower.split("\\s+".toRegex()).filter { it.isNotBlank() }

        val narrowIndicators = listOf(
            "derivation", "proof of", "mechanism of", "vs", "versus", "comparison of",
            "algorithm for", "crash course", "mini course", "quick guide", "walkthrough"
        )

        val isNarrowPhrase = narrowIndicators.any { lower.contains(it) }
        val isShortSpecific = words.size in 1..3 && (lower.endsWith("equation") || lower.endsWith("cycle") || lower.endsWith("theorem") || lower.endsWith("paradox") || lower.endsWith("formula"))

        val isNarrow = isNarrowPhrase || isShortSpecific

        return if (isNarrow) {
            CurriculumSizeSpec(
                minModules = 3,
                maxModules = 4,
                minChaptersPerModule = 3,
                maxChaptersPerModule = 4,
                totalChaptersRange = 9..16,
                isNarrowTopic = true,
                rationale = "Targeted subject: concise, single-concept curriculum preventing artificial padding"
            )
        } else {
            CurriculumSizeSpec(
                minModules = 6,
                maxModules = 8,
                minChaptersPerModule = 4,
                maxChaptersPerModule = 6,
                totalChaptersRange = 24..40,
                isNarrowTopic = false,
                rationale = "Comprehensive discipline curriculum: 6-8 modules with 24-40 bite-sized chapters"
            )
        }
    }

    /**
     * Analyzes prerequisite dependency links between modules and chapters.
     */
    fun analyzePrerequisites(modules: List<SyllabusModuleItem>): PrerequisiteMapResult {
        val chapterTitles = modules.flatMap { it.chapters }.map { it.title }
        val dependencies = mutableListOf<PrerequisiteDependency>()
        val missing = mutableListOf<String>()

        modules.forEachIndexed { modIdx, module ->
            module.chapters.forEachIndexed { chapIdx, chapter ->
                chapter.keyConcepts.forEach { concept ->
                    // Check if concept is introduced in an earlier chapter
                    val foundEarlier = modules.take(modIdx + 1).flatMap {
                        if (it == module) it.chapters.take(chapIdx) else it.chapters
                    }.any { it.keyConcepts.any { c -> c.equals(concept, ignoreCase = true) } }

                    if (!foundEarlier && (modIdx > 0 || chapIdx > 0)) {
                        dependencies.add(
                            PrerequisiteDependency(
                                concept = concept,
                                requiredForChapterTitle = chapter.title,
                                prerequisiteChapterTitle = null,
                                isMissing = true
                            )
                        )
                        if (!missing.contains(concept)) {
                            missing.add(concept)
                        }
                    }
                }
            }
        }

        return PrerequisiteMapResult(
            dependencies = dependencies,
            hasCircularDependency = false,
            missingPrerequisites = missing
        )
    }
}
