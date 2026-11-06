package org.opened.app.ui.components

sealed class Math2DNode {
    data class Text(val content: String) : Math2DNode()
    data class Matrix(
        val rows: List<List<Math2DNode>>,
        val bracketType: BracketType = BracketType.SQUARE
    ) : Math2DNode()
    data class Fraction(
        val numerator: Math2DNode,
        val denominator: Math2DNode
    ) : Math2DNode()
    data class BigOp(
        val opSymbol: String,
        val lowerLimit: String?,
        val upperLimit: String?,
        val expression: Math2DNode? = null
    ) : Math2DNode()
    data class Sqrt(
        val inner: Math2DNode
    ) : Math2DNode()
    data class Row(
        val children: List<Math2DNode>
    ) : Math2DNode()
}

enum class BracketType {
    SQUARE, ROUND, DETERMINANT
}

object LatexParser {

    private const val MAX_REPLACE_ITERATIONS = 25

    // Pre-compiled Regex constants to eliminate repeated compilation on Main/Worker threads
    private val CE_REGEX = Regex("""\\ce\{([^{}]+)\}""")
    private val OPERATOR_NAME_REGEX = Regex("""\\operatorname\*?\{([^{}]+)\}""")
    private val BOXED_REGEX = Regex("""\\boxed\{([^{}]+)\}""")
    private val TRANSPOSE_REGEX = Regex("""\^\{\\?(?:mathsf|mathrm)?\{?T\}?\}|\^\\?(?:mathsf|mathrm)?\{?T\}?|\^\{\\top\}|\^\\top|\^T|\^\{T\}|\^\{\\intercal\}""")
    private val BULLET_ACCENT_REGEX = Regex("""\^\{\\bullet\}|\^\\bullet|\^•|\^\{•\}""")
    private val HAT_ACCENTS_REGEX = Regex("""\\hat\{([^}]+)\}""")
    private val VEC_ACCENTS_REGEX = Regex("""\\vec\{([^}]+)\}""")
    private val MATH_BB_GENERIC_REGEX = Regex("""\\mathbb\{([A-Za-z])\}""")
    private val FORMATTING_COMMANDS_REGEX = Regex("""\\(mathbf|boldsymbol|mathrm|text|textbf|textit|mathit|mathsf|mathtt|mathcal|mathscr|mathbb|mathfrak|textnormal)\{([^{}]+)\}""")
    private val LATEX_COMMAND_WORD_REGEX = Regex("""\\([a-zA-Z]+)""")
    private val FLOOR_SPACES_REGEX = Regex("""⌊\s+""")
    private val FLOOR_CLOSE_SPACES_REGEX = Regex("""\s+⌋""")
    private val CEIL_SPACES_REGEX = Regex("""⌈\s+""")
    private val CEIL_CLOSE_SPACES_REGEX = Regex("""\s+⌉""")
    private val FRAC_REGEX = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
    private val SQRT_REGEX = Regex("""\\sqrt\{([^{}]+)\}""")
    private val SUPERSCRIPT_BRACED_REGEX = Regex("""\^\{([^{}]+)\}""")
    private val SUPERSCRIPT_SINGLE_REGEX = Regex("""\^([0-9a-zA-Z•●·])""")
    private val SUBSCRIPT_BRACED_REGEX = Regex("""_\{([^{}]+)\}""")
    private val SUBSCRIPT_SINGLE_REGEX = Regex("""_([0-9a-zA-Z])""")
    private val LEFTOVER_COMMANDS_REGEX = Regex("""\\[a-zA-Z]+""")
    private val MULTIPLE_SPACES_REGEX = Regex("""[ \t]{2,}""")

    private val CHEM_BULLET_REGEX = Regex("""\^\\bullet|\^\{\\bullet\}|\^•|\^\{•\}""")
    private val CHEM_SUPERSCRIPT_BRACED_REGEX = Regex("""\^\{([^{}]+)\}""")
    private val CHEM_SUPERSCRIPT_SINGLE_REGEX = Regex("""\^([0-9+\-•])""")
    private val CHEM_SUBSCRIPT_BRACED_REGEX = Regex("""_\{([^{}]+)\}""")
    private val CHEM_SUBSCRIPT_SINGLE_REGEX = Regex("""_([0-9a-zA-Z])""")
    private val CHEM_DIGITS_REGEX = Regex("""([A-Za-z\)])([0-9]+)""")

    private val INLINE_MATRIX_REGEX = Regex("""\\begin\{(bmatrix|pmatrix|matrix)\}(.*?)\\end\{\1\}""", RegexOption.DOT_MATCHES_ALL)
    private val DISPLAY_MATRIX_REGEX = Regex("""\\begin\{(bmatrix|pmatrix|vmatrix|matrix)\}(.*?)\\end\{\1\}""", RegexOption.DOT_MATCHES_ALL)
    private val BIG_OP_REGEX = Regex("""\\(sum|prod|int|lim)(?:_\{?([^{}\n^]+)\}?)?(?:\^\{?([^{}\n_]+)\}?)?""")

    private val SPOKEN_FRAC_REGEX = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
    private val SPOKEN_SQRT_REGEX = Regex("""\\sqrt\{([^}]+)\}""")
    private val SPOKEN_SUM_REGEX = Regex("""\\sum_\{([^}]+)\}\^\{([^}]+)\}""")
    private val SPOKEN_INT_REGEX = Regex("""\\int_\{([^}]+)\}\^\{([^}]+)\}""")
    private val SPOKEN_POW2_REGEX = Regex("""\^2\b""")
    private val SPOKEN_POW3_REGEX = Regex("""\^3\b""")
    private val SPOKEN_POWN_REGEX = Regex("""\^\{([^}]+)\}""")
    private val SPOKEN_SUBN_REGEX = Regex("""_\{([^}]+)\}""")
    private val SPOKEN_COMMANDS_REGEX = Regex("""\\[a-zA-Z]+""")

    private val SUPERSCRIPTS = mapOf(
        '•' to '•', '●' to '●', '·' to '·',
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
        'A' to 'ᴬ', 'B' to 'ᴮ', 'D' to 'ᴰ', 'E' to 'ᴱ', 'G' to 'ᴳ',
        'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᶪ', 'K' to 'ᴷ', 'L' to 'ᴸ',
        'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ', 'P' to 'ᴾ', 'R' to 'ᴿ',
        'T' to 'ᵀ', 'U' to 'ᵁ', 'V' to 'ⱽ', 'W' to 'ᵂ',
        '×' to 'ˣ', '*' to '*'
    )

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    private val LATEX_COMMANDS = mapOf(
        // Arrows
        "leftarrow" to "←", "gets" to "←", "rightarrow" to "→", "to" to "→",
        "leftrightarrow" to "↔", "Rightarrow" to "⇒", "Leftarrow" to "⇐",
        "Leftrightarrow" to "⇔", "iff" to "⇔", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓",
        "longrightarrow" to "⟶", "longleftarrow" to "⟵",
        "Longrightarrow" to "⟹", "Longleftarrow" to "⟺",
        "longleftrightarrow" to "⟷", "Longleftrightarrow" to "⟺",
        "implies" to "⇒", "impliedby" to "⇐",
        "rightleftharpoons" to "⇌", "leftrightharpoons" to "⇋",
        "harr" to "↔", "rarr" to "→", "larr" to "←",

        // Bullets & Symbols
        "bullet" to "•",

        // Relations & Comparison
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
        "sim" to "∼", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
        "gg" to "≫", "ll" to "≪",

        // Arithmetic & Operators
        "times" to "×", "cdot" to "·", "circ" to "∘", "pm" to "±", "mp" to "∓",
        "div" to "÷", "star" to "⋆", "ast" to "*",

        // Brackets & Delimiters
        "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
        "langle" to "⟨", "rangle" to "⟩", "vert" to "|", "Vert" to "‖",

        // Set & Logic
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
        "cup" to "∪", "cap" to "∩", "setminus" to "∖", "emptyset" to "∅", "varnothing" to "∅",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬",
        "land" to "∧", "lor" to "∨",

        // Calculus & Big Operators
        "partial" to "∂", "nabla" to "∇", "infty" to "∞",
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "oint" to "∮",
        "ldots" to "…", "cdots" to "…", "vdots" to "⋮", "ddots" to "⋱",

        // Functions
        "log" to "log", "ln" to "ln", "exp" to "exp",
        "min" to "min", "max" to "max", "arg" to "arg",
        "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "sec" to "sec", "csc" to "csc", "cot" to "cot",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "det" to "det", "dim" to "dim", "ker" to "ker",
        "gcd" to "gcd", "deg" to "deg", "mod" to " mod ", "bmod" to " mod ", "pmod" to " mod ",

        // Spacing
        "quad" to "  ", "qquad" to "    ",

        // Greek Lowercase
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",

        // Greek Uppercase
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω"
    )

    /**
     * Cleans inline LaTeX expressions for text display, replacing LaTeX macros with Unicode symbols,
     * stripping stray braces, converting vectors/sets, and never letting raw LaTeX leaks occur.
     */
    fun cleanInlineLatex(raw: String): String {
        val leadingSpace = raw.takeWhile { it.isWhitespace() }
        val trailingSpace = raw.takeLastWhile { it.isWhitespace() }

        var text = raw.trim()
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("\\(").removeSuffix("\\)")
            .removePrefix("$").removeSuffix("$")
            .replace("$", "")
            .trim()

        if (text.isBlank()) return ""

        // 1. Protect escaped underscores
        val placeholderUnderscore = "\u0000PROTECTEDUNDERSCORE\u0000"
        text = text.replace("\\_", placeholderUnderscore)

        // 2. Chemistry: \ce{...} (e.g. \ce{BH4}, \ce{NO^\bullet}, \ce{H2O})
        var iter = 0
        while (CE_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = text.replace(CE_REGEX) { match ->
                cleanChemistryFormula(match.groupValues[1])
            }
            if (next == text) break
            text = next
        }

        // 3. \operatorname{...} (e.g. \operatorname{rank} -> rank, \operatorname{diag} -> diag)
        iter = 0
        while (OPERATOR_NAME_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = text.replace(OPERATOR_NAME_REGEX) { match ->
                " ${match.groupValues[1].trim()} "
            }
            if (next == text) break
            text = next
        }

        // 4. \boxed{...} (e.g. \boxed{A} -> A)
        iter = 0
        while (BOXED_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = text.replace(BOXED_REGEX) { match ->
                match.groupValues[1].trim()
            }
            if (next == text) break
            text = next
        }

        // 5. Transpose: V^\mathsf{T}, V^{\mathsf{T}}, V^T, V^\top, etc.
        text = text.replace(TRANSPOSE_REGEX, "ᵀ")

        // 6. Radicals/bullets: NO^\bullet, ^\bullet -> •
        text = text.replace(BULLET_ACCENT_REGEX, "•")

        // 7. Inline matrices \begin{bmatrix} a \\ b \end{bmatrix} -> [a, b]ᵀ
        text = replaceInlineMatrices(text)

        // 8. Hat and vector accents
        text = text.replace(Regex("""\\hat\{i\}|\\hat\{ı\}|\\hat\s+i"""), "î")
            .replace(Regex("""\\hat\{j\}|\\hat\s+j"""), "ĵ")
            .replace(Regex("""\\hat\{k\}|\\hat\s+k"""), "k̂")
            .replace(Regex("""\\hat\{x\}|\\hat\s+x"""), "x̂")
            .replace(Regex("""\\hat\{y\}|\\hat\s+y"""), "ŷ")
            .replace(Regex("""\\hat\{z\}|\\hat\s+z"""), "ẑ")
            .replace(Regex("""\\hat\{w\}|\\hat\s+w"""), "ŵ")
            .replace(Regex("""\\hat\{theta\}|\\hat\\theta"""), "θ̂")
            .replace(HAT_ACCENTS_REGEX) { "${it.groupValues[1]}̂" }
            .replace(Regex("""\\vec\{v\}|\\vec\s+v"""), "v⃗")
            .replace(Regex("""\\vec\{u\}|\\vec\s+u"""), "u⃗")
            .replace(Regex("""\\vec\{w\}|\\vec\s+w"""), "w⃗")
            .replace(Regex("""\\vec\{x\}|\\vec\s+x"""), "x⃗")
            .replace(Regex("""\\vec\{y\}|\\vec\s+y"""), "y⃗")
            .replace(Regex("""\\vec\{i\}|\\vec\s+i"""), "i⃗")
            .replace(Regex("""\\vec\{j\}|\\vec\s+j"""), "j⃗")
            .replace(VEC_ACCENTS_REGEX) { "${it.groupValues[1]}⃗" }

        // 9. Blackboard bold sets
        text = text.replace("\\mathbb{R}", "ℝ")
            .replace("\\mathbb{N}", "ℕ")
            .replace("\\mathbb{Z}", "ℤ")
            .replace("\\mathbb{Q}", "ℚ")
            .replace("\\mathbb{C}", "ℂ")
            .replace("\\mathbb{P}", "ℙ")
            .replace("\\mathbb{E}", "𝔼")
            .replace("\\mathbb{H}", "ℍ")
            .replace(MATH_BB_GENERIC_REGEX) { it.groupValues[1] }

        // 10. Formats: \mathbf, \boldsymbol, \mathrm, \text, \mathit, \mathsf, \mathtt, \mathcal, \mathscr, \mathbb, \mathfrak
        iter = 0
        while (FORMATTING_COMMANDS_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = text.replace(FORMATTING_COMMANDS_REGEX) { it.groupValues[2] }
            if (next == text) break
            text = next
        }

        // 11. Non-letter spacing commands
        text = text.replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\:", " ")
            .replace("\\!", "")
            .replace("\\ ", " ")

        // 12. Whole-word regex matching for LaTeX commands: \\([a-zA-Z]+)
        text = LATEX_COMMAND_WORD_REGEX.replace(text) { match ->
            val cmd = match.groupValues[1]
            LATEX_COMMANDS[cmd] ?: cmd
        }

        // Clean spaces around brackets, floors, and ceilings
        text = text.replace(FLOOR_SPACES_REGEX, "⌊").replace(FLOOR_CLOSE_SPACES_REGEX, "⌋")
        text = text.replace(CEIL_SPACES_REGEX, "⌈").replace(CEIL_CLOSE_SPACES_REGEX, "⌉")

        // 13. Fractions: \frac{a}{b} -> a/b or (a)/(b)
        iter = 0
        while (FRAC_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = FRAC_REGEX.replace(text) { match ->
                val num = match.groupValues[1].trim()
                val den = match.groupValues[2].trim()
                if (num.length <= 3 && den.length <= 3 && !num.contains(" ") && !den.contains(" ")) {
                    "$num/$den"
                } else {
                    "($num)/($den)"
                }
            }
            if (next == text) break
            text = next
        }

        // 14. Square roots: \sqrt{...} -> √( ... )
        iter = 0
        while (SQRT_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = SQRT_REGEX.replace(text) { match ->
                "√(${match.groupValues[1].trim()})"
            }
            if (next == text) break
            text = next
        }

        // 15. Transpose ^T or ^\top or ^\intercal
        text = text.replace(TRANSPOSE_REGEX, "ᵀ")

        // 16. Superscripts: ^{x} or ^x
        iter = 0
        while (SUPERSCRIPT_BRACED_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = SUPERSCRIPT_BRACED_REGEX.replace(text) { match ->
                val inner = match.groupValues[1]
                val allMapped = inner.all { SUPERSCRIPTS.containsKey(it) }
                if (allMapped && inner.isNotBlank()) {
                    inner.map { SUPERSCRIPTS[it] ?: it }.joinToString("")
                } else {
                    "^($inner)"
                }
            }
            if (next == text) break
            text = next
        }
        text = SUPERSCRIPT_SINGLE_REGEX.replace(text) { match ->
            val c = match.groupValues[1].first()
            (SUPERSCRIPTS[c] ?: "^$c").toString()
        }

        // 17. Subscripts: _{x} or _x
        iter = 0
        while (SUBSCRIPT_BRACED_REGEX.containsMatchIn(text) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = SUBSCRIPT_BRACED_REGEX.replace(text) { match ->
                val inner = match.groupValues[1]
                val allMapped = inner.all { SUBSCRIPTS.containsKey(it) }
                if (allMapped && inner.isNotBlank()) {
                    inner.map { SUBSCRIPTS[it] ?: it }.joinToString("")
                } else {
                    "_($inner)"
                }
            }
            if (next == text) break
            text = next
        }
        text = SUBSCRIPT_SINGLE_REGEX.replace(text) { match ->
            val c = match.groupValues[1].first()
            (SUBSCRIPTS[c] ?: "_$c").toString()
        }

        // 18. Clean stray delimiters
        text = text.replace("\\(", "(").replace("\\)", ")")
            .replace("\\[", "[").replace("\\]", "]")

        // 19. Strip leftover formatting commands and braces
        text = text.replace(LEFTOVER_COMMANDS_REGEX, "")
        text = text.replace("{", "").replace("}", "")

        // 20. Restore protected escaped underscores
        text = text.replace(placeholderUnderscore, "_")

        // Normalize multiple spaces
        text = text.replace(MULTIPLE_SPACES_REGEX, " ").trim()

        return if (raw.isBlank()) "" else "$leadingSpace$text$trailingSpace"
    }

    private fun cleanChemistryFormula(formula: String): String {
        var s = formula.trim()
        s = s.replace("->", "→").replace("<=>", "⇌")
        s = s.replace(CHEM_BULLET_REGEX, "•")

        var iter = 0
        while (CHEM_SUPERSCRIPT_BRACED_REGEX.containsMatchIn(s) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = s.replace(CHEM_SUPERSCRIPT_BRACED_REGEX) { m ->
                m.groupValues[1].map { SUPERSCRIPTS[it] ?: it }.joinToString("")
            }
            if (next == s) break
            s = next
        }

        s = s.replace(CHEM_SUPERSCRIPT_SINGLE_REGEX) { m ->
            (SUPERSCRIPTS[m.groupValues[1].first()] ?: "^${m.groupValues[1]}").toString()
        }

        iter = 0
        while (CHEM_SUBSCRIPT_BRACED_REGEX.containsMatchIn(s) && iter++ < MAX_REPLACE_ITERATIONS) {
            val next = s.replace(CHEM_SUBSCRIPT_BRACED_REGEX) { m ->
                m.groupValues[1].map { SUBSCRIPTS[it] ?: it }.joinToString("")
            }
            if (next == s) break
            s = next
        }

        s = s.replace(CHEM_SUBSCRIPT_SINGLE_REGEX) { m ->
            (SUBSCRIPTS[m.groupValues[1].first()] ?: "_${m.groupValues[1]}").toString()
        }

        s = s.replace(CHEM_DIGITS_REGEX) { m ->
            val element = m.groupValues[1]
            val digits = m.groupValues[2].map { SUBSCRIPTS[it] ?: it }.joinToString("")
            "$element$digits"
        }
        return s
    }

    private fun replaceInlineMatrices(input: String): String {
        return INLINE_MATRIX_REGEX.replace(input) { match ->
            val content = match.groupValues[2].trim()
            val rows = content.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
            if (rows.size == 1) {
                val cols = rows[0].split("&").map { cleanInlineLatex(it.trim()) }
                "[${cols.joinToString(", ")}]"
            } else {
                val isColVector = rows.all { !it.contains("&") }
                if (isColVector) {
                    val elements = rows.map { cleanInlineLatex(it) }
                    "[${elements.joinToString(", ")}]ᵀ"
                } else {
                    val formattedRows = rows.map { row ->
                        val cols = row.split("&").map { cleanInlineLatex(it.trim()) }
                        cols.joinToString(", ")
                    }
                    "[${formattedRows.joinToString("; ")}]"
                }
            }
        }
    }

    /**
     * Parses a display math LaTeX string into a 2D structured AST for Compose rendering.
     */
    fun parseDisplayMath(raw: String): Math2DNode {
        val clean = raw.trim()
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("$").removeSuffix("$")
            .trim()

        if (clean.isBlank()) return Math2DNode.Text("")

        // Check for standalone or embedded matrix
        val matrixMatch = DISPLAY_MATRIX_REGEX.find(clean)
        if (matrixMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, matrixMatch.range.first).trim()
            if (prefix.isNotEmpty() && prefix != clean) {
                nodes.add(parseDisplayMath(prefix))
            }

            val typeStr = matrixMatch.groupValues[1]
            val bracketType = when (typeStr) {
                "bmatrix" -> BracketType.SQUARE
                "pmatrix" -> BracketType.ROUND
                "vmatrix" -> BracketType.DETERMINANT
                else -> BracketType.SQUARE
            }

            val matrixContent = matrixMatch.groupValues[2].trim()
            val rawRows = matrixContent.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
            val rows = rawRows.map { row ->
                val cols = row.split("&").map { col ->
                    parseDisplayMath(col.trim())
                }
                cols
            }
            nodes.add(Math2DNode.Matrix(rows = rows, bracketType = bracketType))

            val suffix = clean.substring(matrixMatch.range.last + 1).trim()
            if (suffix.isNotEmpty() && suffix != clean) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for stacked fraction \frac{numerator}{denominator}
        val fracMatch = FRAC_REGEX.find(clean)
        if (fracMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, fracMatch.range.first).trim()
            if (prefix.isNotEmpty() && prefix != clean) {
                nodes.add(parseDisplayMath(prefix))
            }

            val num = fracMatch.groupValues[1]
            val den = fracMatch.groupValues[2]
            nodes.add(
                Math2DNode.Fraction(
                    numerator = parseDisplayMath(num),
                    denominator = parseDisplayMath(den)
                )
            )

            val suffix = clean.substring(fracMatch.range.last + 1).trim()
            if (suffix.isNotEmpty() && suffix != clean) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for square root \sqrt{inner}
        val sqrtMatch = SQRT_REGEX.find(clean)
        if (sqrtMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, sqrtMatch.range.first).trim()
            if (prefix.isNotEmpty() && prefix != clean) {
                nodes.add(parseDisplayMath(prefix))
            }

            val inner = sqrtMatch.groupValues[1]
            nodes.add(Math2DNode.Sqrt(inner = parseDisplayMath(inner)))

            val suffix = clean.substring(sqrtMatch.range.last + 1).trim()
            if (suffix.isNotEmpty() && suffix != clean) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for Summations, Products, Integrals \sum_{lower}^{upper} or \int_{lower}^{upper}
        val opMatch = BIG_OP_REGEX.find(clean)
        if (opMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, opMatch.range.first).trim()
            if (prefix.isNotEmpty() && prefix != clean) {
                nodes.add(parseDisplayMath(prefix))
            }

            val opType = opMatch.groupValues[1]
            val opSymbol = when (opType) {
                "sum" -> "∑"
                "prod" -> "∏"
                "int" -> "∫"
                "lim" -> "lim"
                else -> opType
            }
            val lower = opMatch.groupValues.getOrNull(2)?.trim()?.ifBlank { null }?.let { cleanInlineLatex(it) }
            val upper = opMatch.groupValues.getOrNull(3)?.trim()?.ifBlank { null }?.let { cleanInlineLatex(it) }

            val suffix = clean.substring(opMatch.range.last + 1).trim()
            val exprNode = if (suffix.isNotEmpty() && suffix != clean) parseDisplayMath(suffix) else null

            nodes.add(
                Math2DNode.BigOp(
                    opSymbol = opSymbol,
                    lowerLimit = lower,
                    upperLimit = upper,
                    expression = exprNode
                )
            )

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        return Math2DNode.Text(cleanInlineLatex(clean))
    }

    /**
     * Converts a LaTeX mathematical formula into an accessible natural language string
     * for TalkBack / Screen Readers (WCAG Accessibility compliance).
     */
    fun toSpokenDescription(latex: String): String {
        var spoken = latex.trim()
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("$").removeSuffix("$")
            .trim()

        spoken = SPOKEN_FRAC_REGEX.replace(spoken) { "fraction with numerator ${it.groupValues[1]} over denominator ${it.groupValues[2]}" }
        spoken = SPOKEN_SQRT_REGEX.replace(spoken) { "square root of ${it.groupValues[1]}" }
        spoken = SPOKEN_SUM_REGEX.replace(spoken) { "summation from ${it.groupValues[1]} to ${it.groupValues[2]} of " }
        spoken = SPOKEN_INT_REGEX.replace(spoken) { "integral from ${it.groupValues[1]} to ${it.groupValues[2]} of " }
        spoken = SPOKEN_POW2_REGEX.replace(spoken, " squared")
        spoken = SPOKEN_POW3_REGEX.replace(spoken, " cubed")
        spoken = SPOKEN_POWN_REGEX.replace(spoken) { " to the power of ${it.groupValues[1]}" }
        spoken = SPOKEN_SUBN_REGEX.replace(spoken) { " subscript ${it.groupValues[1]}" }
        spoken = SPOKEN_COMMANDS_REGEX.replace(spoken) { match ->
            when (match.value) {
                "\\alpha" -> "alpha"
                "\\beta" -> "beta"
                "\\gamma" -> "gamma"
                "\\theta" -> "theta"
                "\\pi" -> "pi"
                "\\lambda" -> "lambda"
                "\\sigma" -> "sigma"
                "\\infty" -> "infinity"
                "\\approx" -> "approximately equals"
                "\\neq" -> "not equals"
                "\\le", "\\leq" -> "less than or equal to"
                "\\ge", "\\geq" -> "greater than or equal to"
                "\\times" -> "times"
                "\\div" -> "divided by"
                "\\pm" -> "plus or minus"
                "\\partial" -> "partial derivative"
                "\\Delta" -> "delta"
                else -> match.value.removePrefix("\\")
            }
        }
        return spoken.replace("{", "").replace("}", "").trim()
    }
}
