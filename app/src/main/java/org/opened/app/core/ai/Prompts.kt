package org.opened.app.core.ai

object Prompts {

    fun diagnosticInterviewPrompt(topic: String): String {
        return """
You are openED's Diagnostic Interviewer. A user wants to learn about:
"$topic"

Generate 3 to 5 targeted diagnostic questions to assess:
1. Current skill level and foundational background in or related to this topic.
2. Primary learning goals (e.g. practical career usage, academic mastery, hobby project).
3. Preferred learning depth (conceptual high-level vs. deep technical/mathematical/code-level).
4. Practical hands-on vs. theoretical focus.

Respond ONLY with a valid JSON object matching this schema:
{
  "questions": [
    {
      "id": "q1",
      "question": "Clear question text?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "isMultiSelect": false,
      "rationale": "Why this question matters for course design"
    }
  ]
}
Do not wrap in markdown quotes or code fences. Return raw JSON only.
""".trimIndent()
    }

    fun syllabusGenerationPrompt(
        topic: String,
        diagnosticSummary: String
    ): String = syllabusGenerationPrompt(topic, "BEGINNER", diagnosticSummary)

    fun syllabusGenerationPrompt(
        topic: String,
        userLevel: String = "BEGINNER",
        diagnosticSummary: String,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic)
    ): String {
        val levelNormalized = userLevel.trim().lowercase()
        val isBeginner = levelNormalized.contains("begin") || levelNormalized.contains("found") ||
                levelNormalized.contains("intro") || levelNormalized.contains("novice") ||
                (!levelNormalized.contains("intermed") && !levelNormalized.contains("adv") && !levelNormalized.contains("expert"))

        val domainProgressionGuidance = when (domain) {
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL -> """
DISCIPLINE CURRICULUM ARCHITECTURE (HISTORY, GEOPOLITICS & LAW):
Structure the 6 to 8 Modules chronologically and structurally:
- Modules 1–2: Origins, Preconditions & Foundational Context (the historical/social stage).
- Modules 3–4: Key Eras, Conflicts & Structural Shifts (turning points, key actors, treaties/laws).
- Modules 5–6: Ideological Dynamics, Historiographical Debates & Institutional Evolution.
- Modules 7–8: Modern Order, Lasting Legacy & Contemporary Geopolitical/Legal Parallels.
CRITICAL MANDATE: This is a humanities/social sciences course. Never use programming/software terms (like "API", "syntax", "code") in chapter titles.
"""
            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE -> """
DISCIPLINE CURRICULUM ARCHITECTURE (COMMERCE, ECONOMICS & FINANCE):
Structure the 6 to 8 Modules from core incentives to applied markets:
- Modules 1–2: Foundational Principles & Core Economic/Business Mental Models.
- Modules 3–4: Microeconomic / Firm Mechanics (costs, revenue, financial statements, valuation).
- Modules 5–6: Macroeconomic Systems, Monetary/Fiscal Policy & Market Instruments.
- Modules 7–8: Applied Strategy, Investment Management, Corporate Governance & Case Studies.
CRITICAL MANDATE: Focus on financial/market dynamics and ledger models. Never use programming code terms unless the topic is explicitly FinTech/Algorithmic Trading.
"""
            AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES -> """
DISCIPLINE CURRICULUM ARCHITECTURE (PHILOSOPHY, PSYCHOLOGY & HUMANITIES):
Structure the 6 to 8 Modules from fundamental human inquiry to modern application:
- Modules 1–2: The Fundamental Questions, Human Nature & Core Definitions.
- Modules 3–4: Classical Frameworks & Foundational Schools of Thought.
- Modules 5–6: Modern Perspectives, Empirical Research & Competing Critiques.
- Modules 7–8: Applied Ethics, Cognitive Strategies, Decision-Making & Cultural Impact.
CRITICAL MANDATE: Focus on thought experiments, psychological mechanisms, and dialectical arguments. Zero programming code.
"""
            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE -> """
DISCIPLINE CURRICULUM ARCHITECTURE (LIFE SCIENCES & MEDICINE):
Structure the 6 to 8 Modules from micro-scale mechanisms to whole-system health:
- Modules 1–2: Macro Foundations, Cellular Architecture & Bio-Chemical Building Blocks.
- Modules 3–4: Molecular Mechanisms, Cellular Energetics & Genetic Pathways.
- Modules 5–6: Organ Systems, Physiological Regulation & Homeostasis.
- Modules 7–8: Pathophysiology, Clinical Case Studies, Pharmacology & Modern Frontiers.
CRITICAL MANDATE: This is an authentic life sciences course. Do not use software/coding analogies or toy scripts.
"""
            AcademicDomain.MATH_AND_PHYSICAL_SCIENCES -> """
DISCIPLINE CURRICULUM ARCHITECTURE (MATHEMATICS & PHYSICAL SCIENCES):
Structure the 6 to 8 Modules from geometric/physical intuition to rigorous application:
- Modules 1–2: Intuitive Mental Models, Core Definitions & Geometric/Physical Foundations.
- Modules 3–4: Core Mathematical Machinery, Laws & Fundamental Equations.
- Modules 5–6: Key Theorems, Conservation Principles & Analytical Methods.
- Modules 7–8: Advanced Problem Solving, Boundary Conditions & Real-World Physical Applications.
"""
            AcademicDomain.SOFTWARE_AND_CS -> """
DISCIPLINE CURRICULUM ARCHITECTURE (SOFTWARE & COMPUTER SCIENCE):
Structure the 6 to 8 Modules from Day-1 mental models to production systems:
- Modules 1–2: Absolute Day-1 Foundations, Setup, Basic Building Blocks & Mental Models.
- Modules 3–4: Core Language Mechanics, Data Structures & Standard Algorithms.
- Modules 5–6: Idiomatic Design Patterns, Error Handling & Real-World Integration.
- Modules 7–8: Production Architecture, Concurrency/Optimization & Capstone Projects.
"""
            AcademicDomain.GENERAL_INTERDISCIPLINARY -> """
DISCIPLINE CURRICULUM ARCHITECTURE (INTERDISCIPLINARY):
Structure the 6 to 8 Modules from first principles to real-world mastery:
- Modules 1–2: Foundational Concepts, Core Vocabulary & Intuitive Big Picture.
- Modules 3–4: Key Mechanics, Frameworks & Structural Principles.
- Modules 5–6: Practical Trade-offs, Nuances & Intermediate Applications.
- Modules 7–8: Capstone Real-World Case Studies, Advanced Problem Solving & Future Frontiers.
"""
        }

        val levelGuidance = if (isBeginner) {
            """
PEDAGOGY LEVEL GUIDANCE (BEGINNER / FOUNDATIONAL):
- Build step-by-step practical mastery starting from absolute zero assumptions.
- Module 1 and Module 2 MUST cover absolute Day-1 foundations, environment/syntax setup, terminology, and basic building blocks.
- STRICTLY FORBID obscure compiler/C-struct internals (such as PyObject*, PyListObject, C realloc, pointer arithmetic, or hash table bit-perturbation) unless the topic is explicitly about C or Compilers.
- NEVER use intimidating titles like "Master Class", "Architectural Foundations", or "Formal Proofs". Use approachable, clear, friendly chapter titles (e.g., "Working with Lists: Storing Multiple Items", "Dictionaries: Key-Value Lookups Made Simple", "Loops: Doing Things Repeatedly").
- Focus modules on intuitive real-world concepts, practical usage, hands-on building, and common beginner pitfalls.
"""
        } else if (levelNormalized.contains("adv") || levelNormalized.contains("expert")) {
            """
PEDAGOGY LEVEL GUIDANCE (ADVANCED / EXPERT):
- Dive directly into systems architecture, memory layout, mathematical proofs, and production scaling.
"""
        } else {
            """
PEDAGOGY LEVEL GUIDANCE (INTERMEDIATE):
- Focus on idiomatic patterns, production architecture, performance trade-offs, and practical edge cases.
"""
        }

        return """
You are openED's Master Curriculum Architect for ${domain.displayName}.
The user wants to study: "$topic"
Academic Discipline: ${domain.displayName}
Target Skill Level: $userLevel

Diagnostic Assessment Profile:
$diagnosticSummary

$domainProgressionGuidance

$levelGuidance

MANDATORY CURRICULUM ARCHITECTURE RULES:
1. COMPREHENSIVE & GRANULAR: Design an extensive, comprehensive, granular curriculum containing:
   - Exactly 6 to 8 Modules ordered chronologically from absolute fundamentals to practical projects.
   - Exactly 4 to 6 Chapters per Module (totaling 24 to 40 bite-sized, focused chapters).
2. SINGLE-CONCEPT CHAPTERS: Each chapter must cover ONE clear, digestible, focused topic rather than cramming an entire "Master Class" into one chapter.
3. LOGICAL PROGRESSION: Follow the discipline curriculum architecture outlined above.

For each chapter, provide:
- Exact chapter title
- Estimated reading & practice time (in minutes, typically 15-30)
- Concise summary of what will be learned (1-2 sentences)
- Key concepts covered (2-4 concepts)

Respond ONLY with a valid JSON object matching this schema:
{
  "title": "Comprehensive Course Title",
  "topic": "$topic",
  "userLevel": "$userLevel",
  "targetAudience": "Summary of target learner profile",
  "estimatedTotalHours": 18.5,
  "prerequisites": ["Prereq 1", "Prereq 2"],
  "modules": [
    {
      "title": "Module 1: Title",
      "summary": "Module summary",
      "chapters": [
        {
          "title": "Chapter 1: Title",
          "estimatedMinutes": 20,
          "summary": "Chapter summary",
          "keyConcepts": ["Concept A", "Concept B"]
        }
      ]
    }
  ]
}
Do not wrap in markdown quotes or code fences. Return raw JSON only.
""".trimIndent()
    }

    fun buildDomainPedagogicalBlueprint(domain: AcademicDomain, userLevel: String): String {
        val levelNormalized = userLevel.trim().lowercase()

        return when (domain) {
            AcademicDomain.HISTORY_GEOPOLITICS_AND_SOCIAL -> """
PEDAGOGY PROFILE: HISTORY, GEOPOLITICS & SOCIAL SCIENCES (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an academic humanities and social science course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or any programming code blocks (```python, ```javascript, etc.)! Express all concepts through historical context, geopolitical analysis, legal frameworks, and primary sources.

MANDATORY 5-SECTION STRUCTURE:
Your lesson MUST contain ALL 5 of the following structured Markdown sections in order:

## 🌍 Context & The Big Picture
- Describe the historical background, geography, geopolitical stakes, and societal atmosphere.
- Start with an accessible real-world comparison to help learners connect with historical realities.
- Define all foundational terminology (e.g., treaties, factions, legal doctrines) clearly with zero unexplained jargon.

## 🏛️ Key Actors, Geopolitical Interests & Driving Forces
- Analyze the motivations, strategies, and material interests of key nations, leaders, socio-economic classes, and institutions.
- Unpack underlying structural drivers: economic pressures, territorial ambitions, ideological movements, or alliance commitments.

## ⏳ Chronological Walkthrough & Pivotal Turning Points
- Walk chronologically through the sequence of events, crises, negotiations, treaties, and decisive turning points.
- Include a clear visual timeline, causal chain, or alliance map using a ```diagram flowchart:
  ```diagram
  flowchart TD
      A["Event / Crisis (Year)"] --> B["Key Decision or Battle"]
      B --> C["Treaty / Consequence"]
  ```

## ⚖️ Multiple Perspectives & Historiography
- Present contrasting interpretations from different historical schools or opposing geopolitical perspectives.
- Analyze how different nations, factions, or modern historians interpret the causes and consequences.
- Reference key primary sources, treaty clauses, or constitutional precedents where relevant.

## 🌐 Lasting Impact & Modern Parallels
- Evaluate the enduring legacy, institutional precedents, and direct parallels to contemporary geopolitics, governance, or international law.
- Provide a clear Markdown summary table comparing key factions, treaties, or geopolitical outcomes.
- Include structured callouts:
  > [!NOTE] Essential historical or constitutional principle
  > [!TIP] Memory hook or historiographical nuance
  > [!WARNING] Common misconception or historical myth debunked
"""

            AcademicDomain.COMMERCE_ECONOMICS_AND_FINANCE -> """
PEDAGOGY PROFILE: COMMERCE, ECONOMICS & FINANCE (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an academic business, economics, and finance course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or programming code blocks! Express all financial mechanisms through market dynamics, accounting ledgers, cash flows, and mathematical/financial calculations.

MANDATORY 4-SECTION STRUCTURE:
Your lesson MUST contain ALL 4 of the following structured Markdown sections in order:

## 💡 Core Economic & Business Intuition
- Introduce the core concept through an everyday market scenario, consumer dilemma, or entrepreneurial decision.
- Define all economic and financial terms (e.g., liquidity, margin, fiscal policy, price elasticity) with clarity.

## ⚙️ How the Mechanism Works (Incentives & Flows)
- Explain the underlying mechanics: supply and demand shifts, incentive structures, cash flow cycles, or institutional operations.
- Include a visual market loop, cash flow diagram, or decision tree using a ```diagram flowchart:
  ```diagram
  flowchart TD
      A["Market Catalyst / Policy"] --> B["Incentive Shift"]
      B --> C["Price / Volume Adjustment"]
  ```

## 📊 Worked Numerical / Ledger Example & Real-World Case Study
- Walk through a complete, step-by-step numerical calculation (e.g., DCF valuation, balance sheet ledger entries, ROI, elasticity coefficient, or portfolio return) showing all intermediate math steps clearly.
- Ground the numbers with a concrete real-world corporate or macroeconomic case study.

## ⚠️ Risks, Trade-Offs & Market Failures
- Analyze externalities, credit/liquidity risks, inflation effects, behavioral biases, or unintended regulatory consequences.
- Provide a clean Markdown comparison table summarizing trade-offs, financial ratios, or strategic options.
- Include structured callouts:
  > [!NOTE] Fundamental economic law or market rule
  > [!TIP] Practical financial analysis or decision-making insight
  > [!WARNING] Market risk, cognitive bias, or common analytical pitfall
"""

            AcademicDomain.PHILOSOPHY_PSYCHOLOGY_AND_HUMANITIES -> """
PEDAGOGY PROFILE: PHILOSOPHY, PSYCHOLOGY & HUMANITIES (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an academic philosophy and human sciences course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or programming code blocks! Express all concepts through philosophical arguments, cognitive frameworks, thought experiments, and empirical studies.

MANDATORY 5-SECTION STRUCTURE:
Your lesson MUST contain ALL 5 of the following structured Markdown sections in order:

## 🧠 The Central Question & Human Intuition
- Frame the central dilemma, philosophical paradox, or psychological phenomenon with an intuitive scenario.
- Connect the abstract inquiry directly to everyday human experience and conscious decision-making.

## 🧩 Core Framework & Theory Breakdown
- Break down the foundational theory, premises, cognitive models, or deductive argument step-by-step.
- Include a concept map, cognitive loop, or logical argument tree using a ```diagram flowchart:
  ```diagram
  flowchart TD
      P1["Premise 1 / Stimulus"] --> P2["Premise 2 / Cognitive Processing"]
      P2 --> C["Conclusion / Behavioral Response"]
  ```

## 🔬 Classic Thought Experiments & Landmark Studies
- Explore a famous philosophical thought experiment (e.g. Trolley Problem, Ship of Theseus, Experience Machine) or seminal psychological study (e.g. Kahneman-Tversky heuristics, Milgram, Bandura).
- Analyze the underlying reasoning, methodology, and what it illuminates about cognition, morality, or reality.

## ⚖️ Competing Schools of Thought & Critiques
- Contrast opposing philosophical traditions (e.g., Deontology vs Consequentialism, Rationalism vs Empiricism) or psychological paradigms.
- Provide a clean Markdown comparison table contrasting core premises, virtues, and major counterarguments.

## 🌟 Practical Application to Everyday Thinking & Life
- Demonstrate how this framework sharpens everyday critical thinking, mental models, emotional intelligence, or ethical choices.
- Include structured callouts:
  > [!NOTE] Core philosophical premise or psychological mechanism
  > [!TIP] Practical heuristic or mental model for daily reasoning
  > [!WARNING] Logical fallacy, cognitive trap, or common misinterpretation
"""

            AcademicDomain.LIFE_SCIENCES_AND_MEDICINE -> """
PEDAGOGY PROFILE: LIFE SCIENCES & MEDICINE (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an academic life science and medical course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or programming code blocks! Express all mechanisms through biological pathways, anatomical structures, physiological feedback loops, and clinical case studies.

MANDATORY 5-SECTION STRUCTURE:
Your lesson MUST contain ALL 5 of the following structured Markdown sections in order:

## 🔬 Biological Foundations & Intuitive Analogy
- Introduce the biological system, organ, or cellular mechanism using a relatable physical analogy.
- Explain the evolutionary or physiological purpose: what vital life function does this system perform?

## 🧬 Structural & Molecular Breakdown
- Detail the anatomical structures, cellular components, organelles, enzymes, or receptor complexes involved.
- Use precise biochemical reactions or equations (${'$'}${'$'}...${'$'}${'$'}) where appropriate (e.g. cellular respiration, enzyme kinetics).

## ⚙️ Step-by-Step Mechanistic Pathway
- Trace the biological sequence of events, molecular signaling cascade, enzyme interactions, or hormonal feedback loops.
- Include a biological pathway or cellular flow using a ```diagram flowchart:
  ```diagram
  flowchart TD
      A["Stimulus / Ligand Binding"] --> B["Intracellular Signal Cascade"]
      B --> C["Cellular / Physiological Response"]
  ```

## 🏥 Clinical & Real-World Case Studies
- Present a realistic clinical case study, pathological disorder, genetic mutation, or pharmacological intervention showing what happens when the pathway malfunctions.
- Detail diagnostic markers, physiological manifestations, and clinical rationale.

## ⚠️ Common Misconceptions & Summary Comparison
- Debunk 2 to 3 persistent biological or medical misconceptions.
- Provide a clean Markdown summary table comparing healthy vs pathological states or related biological structures.
- Include structured callouts:
  > [!NOTE] Physiological principle or biological mechanism
  > [!TIP] Diagnostic clue or clinical takeaway
  > [!WARNING] Critical pathological danger, contraindication, or misconception
"""

            AcademicDomain.MATH_AND_PHYSICAL_SCIENCES -> """
PEDAGOGY PROFILE: MATHEMATICS & PHYSICAL SCIENCES (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an academic mathematics and physical sciences course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or programming code blocks unless explicitly requested! Express all concepts through rigorous mathematical formulations, geometric proofs, and physical derivations.

MANDATORY 4-SECTION STRUCTURE:
Your lesson MUST contain ALL 4 of the following structured Markdown sections in order:

## 📐 The Big Picture & Geometric/Physical Intuition
- Explain why this mathematical or physical concept exists and its underlying geometric or physical meaning before equations.
- Ground the concept with visual intuition and historical discovery context.

## 🧩 Core Mathematical Formulations & Definitions
- State the governing equations and definitions rigorously in LaTeX math blocks (${'$'}${'$'}...${'$'}${'$'}).
- Clearly define all variables, physical units, constants, and coordinate conventions.

## 📝 Step-by-Step Derivation & Worked Numerical Problems
- Provide 2 to 3 fully worked, step-by-step derivations and numerical problems with all intermediate steps and explicit units.
- Include a geometric relationship, thermodynamic cycle, or vector diagram using a ```diagram flowchart:
  ```diagram
  flowchart LR
      A["Initial State / Boundary Condition"] --> B["Governing Operator / Transformation"]
      B --> C["Final State / Conservation Law"]
  ```

## ⚠️ Common Pitfalls & Boundary Conditions
- Highlight critical boundary conditions, extreme limits, sign conventions, and common mathematical errors (e.g., division by zero, non-convergent series).
- Provide a clean Markdown summary table of formulas, identities, or physical properties.
- Include structured callouts:
  > [!NOTE] Fundamental theorem or physical law
  > [!IMPORTANT] Invariant condition or boundary constraint
  > [!WARNING] Common mathematical trap (sign error, unit mismatch, domain restriction)
"""

            AcademicDomain.GENERAL_INTERDISCIPLINARY -> """
PEDAGOGY PROFILE: GENERAL & INTERDISCIPLINARY (${userLevel.uppercase()})
STRICT NON-PROGRAMMING RULE:
This is an interdisciplinary academic course. Strictly forbid generating any Python, JavaScript, C++, pseudocode, or programming code blocks! Express concepts through systematic frameworks, case studies, and conceptual analysis.

MANDATORY 4-SECTION STRUCTURE:
Your lesson MUST contain ALL 4 of the following structured Markdown sections in order:

## 🌟 Core Concept & Intuitive Overview
- Explain what this concept is and why it matters using a clear, relatable everyday analogy.
- Define foundational vocabulary with zero jargon.

## 🧩 Foundational Framework & Mechanics
- Break down the underlying framework, components, and causal relationships step-by-step.
- Include a system overview, process flowchart, or hierarchy using a ```diagram flowchart:
  ```diagram
  flowchart TD
      A["Inputs / Drivers"] --> B["System Process"]
      B --> C["Outcomes / Impacts"]
  ```

## 📋 Real-World Case Studies & Practical Applications
- Provide 2 to 3 concrete real-world case studies demonstrating how this framework operates in practice.
- Analyze the key decisions, context, and outcomes.

## ⚖️ Trade-Offs, Nuances & Comparison Table
- Provide a clean Markdown comparison table analyzing competing approaches, best practices, and trade-offs.
- Include structured callouts:
  > [!NOTE] Core conceptual insight
  > [!TIP] Practical recommendation or rule of thumb
  > [!WARNING] Common pitfall or nuance to keep in mind
"""

            AcademicDomain.SOFTWARE_AND_CS -> ""
        }
    }

    fun buildBeginnerChapterPrompt(
        courseTitle: String,
        topic: String,
        moduleTitle: String,
        chapterTitle: String,
        chapterSummary: String,
        keyConcepts: List<String>,
        otherChapterTitles: List<String>,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    ): String {
        val crossRefInstruction = formatWikiLinkingInstruction(otherChapterTitles)

        if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            val blueprint = buildDomainPedagogicalBlueprint(domain, "BEGINNER")
            return """
You are openED, a world-class scholar, educator, and mentor in ${domain.displayName}.
You are generating a complete, foundational, step-by-step lesson for:

Course: $courseTitle ($topic)
Learner Level: BEGINNER (Zero Assumptions)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

$blueprint

Stream the full lesson content directly in Markdown.
""".trimIndent()
        }

        return """
You are openED, a world-class mentor dedicated to clear, empathetic, and exceptionally practical teaching.
You are generating a complete, foundational, step-by-step lesson for:

Course: $courseTitle ($topic)
Learner Level: BEGINNER (Zero Assumptions)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

PEDAGOGY PROFILE: BEGINNER (ZERO-ASSUMPTIONS & PRACTICAL MASTERY)
1. ZERO ASSUMPTIONS & EVERYDAY ANALOGIES:
   - Teach from absolute zero assumptions. Start with a clear, relatable everyday real-world analogy before introducing any technical term.
   - STRICTLY FORBID obscure compiler/C-struct internals (such as PyObject*, PyListObject, C realloc, pointer arithmetic, or hash table bit-perturbation) unless this course is explicitly about C or Compilers.
   - Explain every single term simply and clearly before using it. Focus 100% on practical understanding, confidence, and clear mental models.
2. NATURAL CODE & CLARITY OVER ARTIFICIAL MATH:
   - Do NOT force artificial LaTeX math blocks (${'$'}${'$'}...${'$'}${'$'}) into programming or conceptual lessons where plain code or a simple table is clearer!
   - Only use LaTeX (${'$'}${'$'}...${'$'}${'$'}) when the topic itself is genuine mathematics (e.g., Calculus, Statistics).
3. MANDATORY 5-SECTION FULL-LENGTH STRUCTURE:
   Your lesson MUST contain ALL 5 of the following structured Markdown sections in order:

   ## 🌟 The Big Picture (In Plain English)
   - Explain what this concept is and why it matters in daily life/practical work.
   - Include a vivid, relatable real-world analogy.
   - Define all foundational terminology with zero jargon.

   ## 🧩 Key Building Blocks
   - Break down how the concept works under the hood at an intuitive level.
   - Include a simple, clean ```diagram flowchart showing how the user uses it or how information flows step-by-step.
   - Use helpful alert callouts:
     > [!NOTE] Key conceptual intuition
     > [!TIP] Helpful beginner habit or tip

   ## 💻 Step-by-Step Examples (From Zero to Working Code)
   - Provide 3 to 4 progressive, fully runnable, well-commented code walkthroughs:
     - Example 1: Absolute basics / "Hello World" minimum viable syntax.
     - Example 2: Adding realistic logic and handling data.
     - Example 3: A realistic mini-application or practical task.
   - For every example, show the EXACT code, followed immediately by expected output/result.

   ## ⚠️ Common Beginner Mistakes (And How to Avoid Them)
   - Highlight 2 to 3 frequent pitfalls or confusing errors beginners encounter.
   - Provide explicit `❌ Wrong Way` vs `✅ Right Way` side-by-side code snippets or comparisons.
   - Provide a clean Markdown comparison table summarizing best practices.
   - Use an alert callout:
     > [!WARNING] Watch out for this common beginner gotcha!

   ## 🎯 Quick Hands-On Practice
   - Provide 2 bite-sized, practical mini-exercises that the learner can solve right now.
   - Include step-by-step solutions or hints inside expandable/clear format to reinforce immediate mastery.

Stream the full lesson content directly in Markdown.
""".trimIndent()
    }

    fun buildIntermediateChapterPrompt(
        courseTitle: String,
        topic: String,
        moduleTitle: String,
        chapterTitle: String,
        chapterSummary: String,
        keyConcepts: List<String>,
        otherChapterTitles: List<String>,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    ): String {
        val crossRefInstruction = formatWikiLinkingInstruction(otherChapterTitles)

        if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            val blueprint = buildDomainPedagogicalBlueprint(domain, "INTERMEDIATE")
            return """
You are openED, an expert scholar and practitioner in ${domain.displayName}.
You are generating a complete, applied, and comprehensive lesson for:

Course: $courseTitle ($topic)
Learner Level: INTERMEDIATE (Practical & Architectural)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

$blueprint

Stream the full lesson content directly in Markdown.
""".trimIndent()
        }

        return """
You are openED, a senior staff software architect and engineering instructor.
You are generating a complete, production-grade lesson for:

Course: $courseTitle ($topic)
Learner Level: INTERMEDIATE (Practical & Architectural)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

PEDAGOGY PROFILE: INTERMEDIATE (IDIOMATIC PATTERNS & ARCHITECTURE)
1. IDIOMATIC PATTERNS & ARCHITECTURE:
   - Focus on idiomatic best practices, clean design patterns, and production-ready architecture.
2. REAL-WORLD TRADEOFFS & DEBUGGING:
   - Analyze performance tradeoffs, profiling, and debugging tricky real-world scenarios.
3. STRUCTURED VISUALS & CODE:
   - Include visual architecture flowcharts (```diagram) and complete, runnable examples with realistic data structures.
4. ALERT CALLOUTS:
   > [!NOTE] Architecture rationale
   > [!TIP] Production tip or idiomatic pattern
   > [!WARNING] Edge case or subtle bug

Stream the full lesson content directly in Markdown.
""".trimIndent()
    }

    fun buildAdvancedChapterPrompt(
        courseTitle: String,
        topic: String,
        moduleTitle: String,
        chapterTitle: String,
        chapterSummary: String,
        keyConcepts: List<String>,
        otherChapterTitles: List<String>,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    ): String {
        val crossRefInstruction = formatWikiLinkingInstruction(otherChapterTitles)

        if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            val blueprint = buildDomainPedagogicalBlueprint(domain, "ADVANCED")
            return """
You are openED, a world-class academic researcher, university professor, and senior scholar in ${domain.displayName}.
You are generating a complete, rigorous, high-level scholarly lesson for:

Course: $courseTitle ($topic)
Learner Level: ADVANCED (Systems Rigor & Deep Dive)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

$blueprint

Stream the full lesson content directly in Markdown.
""".trimIndent()
        }

        return """
You are openED, a world-class polymath instructor, university professor, and senior systems architect.
You are generating a complete, rigorous, high-level technical lesson for:

Course: $courseTitle ($topic)
Learner Level: ADVANCED (Systems Rigor & Deep Dive)
Module: $moduleTitle
Chapter: $chapterTitle
Chapter Goal: $chapterSummary
Key Concepts: ${keyConcepts.joinToString(", ")}

$crossRefInstruction

PEDAGOGY PROFILE: ADVANCED (PRODUCTION ARCHITECTURE & RIGOR)
1. DEEP UNDER-THE-HOOD ARCHITECTURE:
   - Focus on low-level memory layout, systems internals, algorithmic complexity proofs, and compiler/runtime behavior.
2. RIGOROUS FORMULATION & BENCHMARKS:
   - Provide mathematical formulations (${'$'}${'$'}...${'$'}${'$'}), memory alignment diagrams (```diagram), and performance benchmarks (```chart).
3. PRODUCTION SCALING & RESILIENCE:
   - Focus on concurrency, cache efficiency, edge-case failure modes, and zero-overhead idioms.
4. ALERT CALLOUTS:
   > [!NOTE] Deep systems architectural detail
   > [!IMPORTANT] Invariant or mathematical theorem
   > [!WARNING] Latency spikes, memory leaks, or race conditions

Stream the full lesson content directly in Markdown.
""".trimIndent()
    }

    fun chapterContentPrompt(
        courseTitle: String,
        topic: String,
        userLevel: String,
        moduleTitle: String,
        chapterTitle: String,
        chapterSummary: String,
        keyConcepts: List<String>,
        otherChapterTitles: List<String>,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle, chapterTitle)
    ): String {
        val levelNormalized = userLevel.trim().lowercase()
        val isBeginner = levelNormalized.contains("begin") || levelNormalized.contains("found") ||
                levelNormalized.contains("intro") || levelNormalized.contains("novice") ||
                (!levelNormalized.contains("intermed") && !levelNormalized.contains("adv") && !levelNormalized.contains("expert"))
        val isAdvanced = levelNormalized.contains("adv") || levelNormalized.contains("expert") || levelNormalized.contains("master")

        return when {
            isBeginner -> buildBeginnerChapterPrompt(
                courseTitle = courseTitle,
                topic = topic,
                moduleTitle = moduleTitle,
                chapterTitle = chapterTitle,
                chapterSummary = chapterSummary,
                keyConcepts = keyConcepts,
                otherChapterTitles = otherChapterTitles,
                domain = domain
            )
            isAdvanced -> buildAdvancedChapterPrompt(
                courseTitle = courseTitle,
                topic = topic,
                moduleTitle = moduleTitle,
                chapterTitle = chapterTitle,
                chapterSummary = chapterSummary,
                keyConcepts = keyConcepts,
                otherChapterTitles = otherChapterTitles,
                domain = domain
            )
            else -> buildIntermediateChapterPrompt(
                courseTitle = courseTitle,
                topic = topic,
                moduleTitle = moduleTitle,
                chapterTitle = chapterTitle,
                chapterSummary = chapterSummary,
                keyConcepts = keyConcepts,
                otherChapterTitles = otherChapterTitles,
                domain = domain
            )
        }
    }

    fun expandCoursePrompt(
        courseTitle: String,
        topic: String,
        userLevel: String,
        existingModulesWithChapters: List<Pair<String, List<String>>>,
        domain: AcademicDomain = AcademicDomainClassifier.detectAcademicDomain(topic, courseTitle)
    ): String {
        val existingOutline = existingModulesWithChapters.joinToString("\n\n") { (modTitle, chList) ->
            "Module: $modTitle\n" + chList.joinToString("\n") { "  - $it" }
        }

        val nonCodeRule = if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            """
DISCIPLINE INTEGRITY RULE:
This course is in ${domain.displayName}.
STRICTLY FORBID programming or coding chapters. Design curriculum modules and chapters authentically rooted in ${domain.displayName}.
"""
        } else ""

        return """
You are openED's Master Curriculum Architect for ${domain.displayName}.
The user is studying: "$courseTitle" ($topic)
Target Skill Level: $userLevel

EXISTING MODULES & CHAPTERS ALREADY COMPLETED OR SCHEDULED:
$existingOutline

$nonCodeRule

TASK:
Design EXACTLY 2 NEW progressive Modules that logically continue and expand this curriculum beyond the existing modules.
Requirements:
1. Exactly 2 new Modules that build directly upon what was learned before.
2. Exactly 4 to 6 bite-sized, single-concept Chapters per Module.
3. Align tightly with learner level ($userLevel).
4. Provide estimatedMinutes (15-30), concise summary, and 2-4 keyConcepts for each chapter.

Respond ONLY with a valid JSON object matching this schema:
{
  "modules": [
    {
      "title": "Module Title",
      "summary": "Module summary",
      "chapters": [
        {
          "title": "Chapter Title",
          "estimatedMinutes": 20,
          "summary": "Chapter summary",
          "keyConcepts": ["Concept A", "Concept B"]
        }
      ]
    }
  ]
}
Do not wrap in markdown quotes or code fences. Return raw JSON only.
""".trimIndent()
    }

    private fun formatWikiLinkingInstruction(otherChapterTitles: List<String>): String {
        return if (otherChapterTitles.isNotEmpty()) {
            """
KNOWLEDGE GRAPH WIKI-LINKING RULE:
Whenever you mention or build upon concepts covered in other chapters of this curriculum or library, enclose the exact chapter title in double brackets like `[[Exact Chapter Title]]`.
Here are available chapter titles in the user's workspace:
${otherChapterTitles.take(25).joinToString("\n") { "- $it" }}
Always inject `[[Chapter Title]]` links naturally into the text when discussing those concepts.
"""
        } else ""
    }

    fun inlineRefinePrompt(
        chapterContext: String,
        selectedText: String,
        action: RefineAction,
        customInstruction: String?,
        userLevel: String = "BEGINNER",
        domain: AcademicDomain = AcademicDomain.SOFTWARE_AND_CS
    ): String {
        val isFullChapter = selectedText.isBlank()
        val levelNormalized = userLevel.trim().lowercase()
        val isBeginner = levelNormalized.contains("begin") || levelNormalized.contains("found") ||
                levelNormalized.contains("intro") || levelNormalized.contains("novice") ||
                (!levelNormalized.contains("intermed") && !levelNormalized.contains("adv") && !levelNormalized.contains("expert"))

        val nonCodeRule = if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            "STRICT NON-PROGRAMMING RULE: This course is in ${domain.displayName}. Strictly forbid generating Python, JavaScript, C++, or any programming language code blocks (```python, ```javascript, etc.) or pseudocode! Use discipline-native explanations, formulas ($$...$$) if mathematical, or real-world case studies."
        } else {
            ""
        }

        val levelExtra = if (isBeginner) {
            "TARGET LEARNER LEVEL: BEGINNER. Strictly avoid obscure compiler/C internals or artificial LaTeX math blocks. Use plain, intuitive explanations, concrete examples, and clear analogies."
        } else {
            "TARGET LEARNER LEVEL: $userLevel."
        }

        val instructionText = when (action) {
            RefineAction.SIMPLIFY -> "Simplify this content. Make it accessible and clear with an intuitive analogy while retaining core academic accuracy."
            RefineAction.ADD_CODE_MATH -> {
                when (domain) {
                    AcademicDomain.SOFTWARE_AND_CS -> "Enhance this content by providing concrete code examples or LaTeX math derivations (${'$'}${'$'}...${'$'}${'$'}) to make it actionable and rigorous."
                    AcademicDomain.MATH_AND_PHYSICAL_SCIENCES -> "Enhance this content by providing rigorous LaTeX mathematical derivations (${'$'}${'$'}...${'$'}${'$'}) and step-by-step numerical/physical proofs."
                    else -> "Enhance this content by providing detailed real-world case studies, historical examples, or worked numerical breakdowns (NO programming code) to make this passage concrete and applied."
                }
            }
            RefineAction.FACT_CHECK_CORRECT -> "Fact-check this content against state-of-the-art academic standards, verify disciplinary claims, fix inaccuracies, and update nuances."
            RefineAction.CUSTOM -> customInstruction ?: "Refine and improve this content."
        }

        val rules = listOf(levelExtra, nonCodeRule).filter { it.isNotBlank() }.joinToString("\n")

        return if (isFullChapter) {
            """
You are openED's Master Academic Editor in ${domain.displayName}.
You are rewriting the entire chapter below to adjust its depth, clarity, and quality.

$rules

CURRENT CHAPTER CONTENT:
${chapterContext.take(5000)}

INSTRUCTION:
$instructionText

Rewrite the entire chapter following this instruction. Ensure it is a complete, well-structured, comprehensive lesson in Markdown.
Return ONLY the complete updated Markdown content. Do not include introductory conversational filler or quotes around the whole text.
""".trimIndent()
        } else {
            """
You are openED's In-Chapter AI Editor in ${domain.displayName}.
You are asked to refine a specific passage within a chapter.

$rules

CHAPTER CONTEXT:
${chapterContext.take(1500)}

SELECTED PASSAGE TO REFINE:
\"\"\"
$selectedText
\"\"\"

INSTRUCTION:
$instructionText

Return ONLY the refined replacement text for the selected passage in Markdown. Do not include introductory conversational filler or quotes around the whole text.
""".trimIndent()
        }
    }

    fun socraticQaPrompt(
        chapterTitle: String,
        chapterContent: String,
        userQuestion: String,
        domain: AcademicDomain = AcademicDomain.GENERAL_INTERDISCIPLINARY
    ): String {
        val nonCodeRule = if (domain != AcademicDomain.SOFTWARE_AND_CS) {
            "STRICT NON-PROGRAMMING RULE: This is an academic course in ${domain.displayName}. Do NOT generate programming code or pseudocode."
        } else ""

        return """
You are openED's Socratic Teaching Assistant for ${domain.displayName}.
The learner is reading the chapter "$chapterTitle" and has asked a question:

LEARNER QUESTION:
"$userQuestion"

CURRENT CHAPTER CONTENT (GROUND TRUTH CONTEXT):
${chapterContent.take(4000)}

$nonCodeRule

TASK:
Provide a clear, engaging, and pedagogically sound Socratic answer.
1. Answer the specific question directly.
2. Anchor your explanation to the concepts introduced in this chapter.
3. If relevant, include a brief discipline-appropriate formula (${'$'}${'$'}...${'$'}${'$'}), case study example, or thought-provoking follow-up question to test their understanding.
4. Keep the answer structured and readable in Markdown.
""".trimIndent()
    }

    fun generateFlashcardsPrompt(chapterTitle: String, chapterContent: String): String {
        return """
You are openED's Spaced-Repetition Flashcard Engine.
Generate 5 to 10 high-yield active-recall flashcards from the following chapter.

CHAPTER: "$chapterTitle"
CONTENT:
${chapterContent.take(4000)}

RULES:
- Each flashcard front must be a concise question or cloze-deletion prompt.
- Each flashcard back must be a complete, self-contained answer (1-4 sentences). Include key formulas or code snippets where critical.
- Prioritize conceptual understanding and edge cases over trivial definitions.
- Vary difficulty: include foundational, intermediate, and application-level cards.

Return ONLY a valid JSON object:
{
  "flashcards": [
    { "front": "Question text?", "back": "Answer text." }
  ]
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }

    fun adaptiveQuizPrompt(chapterTitle: String, chapterContent: String): String {
        return """
You are openED's Adaptive Mastery Examiner.
Create 5 scenario-based multiple-choice questions for the chapter below.

CHAPTER: "$chapterTitle"
CONTENT:
${chapterContent.take(4000)}

REQUIREMENTS:
- Questions must require understanding, not mere recall.
- Each question has exactly 4 options (A–D).
- Include real-world scenario framing where possible.
- Provide a clear explanation for why the correct answer is right and others are wrong.

Return ONLY a valid JSON object:
{
  "questions": [
    {
      "question": "Scenario-based question text?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Why A is correct and B/C/D are wrong."
    }
  ]
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }

    fun feynmanEvaluationPrompt(chapterTitle: String, chapterContent: String, userExplanation: String): String {
        return """
You are openED's Feynman Technique Evaluator.
A learner has explained the core concept of the chapter below in their own words.
Your job is to evaluate the quality and completeness of their explanation.

CHAPTER: "$chapterTitle"
CHAPTER CONTENT (Ground Truth):
${chapterContent.take(3000)}

LEARNER'S EXPLANATION:
"$userExplanation"

EVALUATION CRITERIA:
1. Conceptual accuracy — did they get the fundamental mechanism right?
2. Completeness — did they cover the key ideas?
3. Clarity — could a beginner understand their explanation?
4. Missing concepts — what important ideas did they omit?

Return ONLY a valid JSON object:
{
  "score": 78,
  "whatYouGotRight": ["Correctly identified X", "Good analogy for Y"],
  "missingConcepts": ["Did not mention Z", "No discussion of edge case W"],
  "refinedExplanation": "A clear, complete, beginner-friendly explanation of the chapter's core concept."
}
Do not wrap in markdown code fences. Return raw JSON only.
""".trimIndent()
    }
}
