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

object AcademicDomainClassifier {

    private fun matchesAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { kw ->
            val trimmed = kw.trim()
            if (trimmed.any { !it.isLetterOrDigit() }) {
                text.contains(trimmed)
            } else {
                val regex = "\\b${Regex.escape(trimmed)}\\b".toRegex()
                regex.containsMatchIn(text)
            }
        }
    }

    fun detectAcademicDomain(
        topic: String,
        courseTitle: String = "",
        chapterTitle: String = ""
    ): AcademicDomain {
        val combined = "$topic $courseTitle $chapterTitle".lowercase()

        // 1. SOFTWARE & COMPUTER SCIENCE
        val csKeywords = listOf(
            "programming", "software", "coding", "python", "javascript", "typescript", "kotlin",
            "java ", "c++", "c#", "rust lang", "rust ", "golang", "go lang", "swift ", "sql",
            "web dev", "frontend", "backend", "fullstack", "devops", "docker", "kubernetes", "linux",
            "kernel", "ebpf", "operating system", "cybersecurity", "git ", "github", "api", "rest api",
            "graphql", "microservice", "compiler", "runtime", "jvm", "bytecode", "assembly", "wasm",
            "webassembly", "data structures", "algorithm",
            "database", "postgres", "mongodb", "redis", "qiskit", "quantum computing",
            "machine learning engineering", "deep learning", "pytorch", "tensorflow", "transformer model",
            "llm", "prompt engineering", "neural network", "computer science", "html", "css", "react", "android dev"
        )
        if (matchesAny(combined, csKeywords)) {
            return AcademicDomain.SOFTWARE_AND_CS
        }

        // 2. LIFE SCIENCES & MEDICINE
        val lifeScienceKeywords = listOf(
            "biology", "biological", "cellular", "cell biology", "molecular biology", "genetics", "gene",
            "dna", "rna", "genomics", "bioenergetics", "mitochondria", "cellular respiration", "atp",
            "biochemistry", "neuroscience", "neuron", "synapse", "brain", "anatomy", "anatomical",
            "physiology", "physiological", "medicine", "medical", "clinical", "pharmacology", "drug",
            "immunology", "virology", "microbiology", "pathology", "disease", "human body",
            "ecology", "ecosystem", "evolution", "evolutionary", "zoology", "botany", "plant",
            "agriculture", "nutrition", "nutritional", "metabolism", "metabolic", "protein", "enzyme",
            "hormone", "endocrine", "cardiovascular"
        )
        if (matchesAny(combined, lifeScienceKeywords)) {
            return AcademicDomain.LIFE_SCIENCES_AND_MEDICINE
        }

        // 3. COMMERCE, ECONOMICS & FINANCE
        val commerceKeywords = listOf(
            "economics", "microeconomics", "macroeconomics", "econometric", "fiscal", "monetary",
            "inflation", "gdp", "interest rate", "central bank", "finance", "corporate finance",
            "accounting", "accountancy", "balance sheet", "income statement", "cash flow", "bookkeeping",
            "stock market", "equities", "bonds", "derivatives", "option trading", "futures", "valuation",
            "dcf", "portfolio", "banking", "investment", "venture capital", "private equity", "fintech",
            "business", "business strategy", "marketing", "supply chain", "logistics", "trade",
            "commerce", "taxation", "taxes", "entrepreneurship", "startup", "management", "mba",
            "market equilibrium", "price elasticity"
        )
        if (matchesAny(combined, commerceKeywords)) {
            return AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE
        }

        // 4. HISTORY, GEOPOLITICS & SOCIAL SCIENCES / LAW
        val historyGeopoliticsKeywords = listOf(
            "history", "historical", "ancient", "medieval", "renaissance", "revolution", "empire",
            "world war", "cold war", "civilization", "dynasty", "treaty", "geopolitics", "geopolitical",
            "international relations", "foreign policy", "diplomacy", "diplomatic", "alliance", "nato",
            "united nations", "political science", "politics", "government", "democracy", "authoritarian",
            "regime", "public policy", "sociology", "sociological", "anthropology", "anthropological",
            "geography", "demography", "law", "legal", "jurisprudence", "constitution", "constitutional",
            "supreme court", "criminal law", "civil law", "tort", "contract law", "judiciary", "civics",
            "human rights"
        )
        if (matchesAny(combined, historyGeopoliticsKeywords)) {
            return AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL
        }

        // 5. PHILOSOPHY, PSYCHOLOGY & HUMANITIES
        val humanitiesKeywords = listOf(
            "philosophy", "philosophical", "philosopher", "ethics", "ethical", "morality", "moral",
            "epistemology", "metaphysics", "logic", "existentialism", "stoicism", "utilitarianism",
            "kantian", "socrates", "plato", "aristotle", "nietzsche", "descartes", "reasoning",
            "psychology", "psychological", "cognitive science", "cognitive", "behavioral", "behavior",
            "psychoanalysis", "psychiatry", "mental health", "perception", "consciousness",
            "developmental psychology", "social psychology", "bias", "fallacy", "literature", "literary",
            "poetry", "novel", "prose", "linguistics", "semantics", "art history", "music theory",
            "musicology", "religious studies", "theology", "mythology"
        )
        if (matchesAny(combined, humanitiesKeywords)) {
            return AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES
        }

        // 6. MATHEMATICS & PHYSICAL SCIENCES
        val mathPhysicsKeywords = listOf(
            "mathematics", "math", "calculus", "linear algebra", "discrete math", "statistics",
            "statistical", "probability", "differential equation", "topology", "number theory",
            "geometry", "trigonometry", "algebra", "matrix", "vector", "eigenvalue", "physics",
            "quantum mechanics", "quantum physics", "relativity", "thermodynamics", "electromagnetism",
            "optics", "astrophysics", "astronomy", "mechanics", "fluid dynamics", "electronics",
            "electrical engineering", "mechanical engineering", "chemical engineering", "chemistry",
            "inorganic", "organic chemistry", "physical chemistry"
        )
        if (matchesAny(combined, mathPhysicsKeywords)) {
            return AcademicDomain.MATH_AND_PHYSICAL_SCIENCES
        }

        return AcademicDomain.GENERAL_INTERDISCIPLINARY
    }
}
