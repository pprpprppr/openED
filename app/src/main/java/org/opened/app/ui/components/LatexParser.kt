package org.opened.app.ui.components

import java.util.regex.Pattern

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

    private val SUPERSCRIPTS = mapOf(
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
        var text = raw.trim()
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("$").removeSuffix("$")
            .replace("$", "")
            .trim()

        if (text.isBlank()) return ""

        // 1. Protect escaped underscores
        val placeholderUnderscore = "\u0000PROTECTEDUNDERSCORE\u0000"
        text = text.replace("\\_", placeholderUnderscore)

        // 2. Inline matrices \begin{bmatrix} a \\ b \end{bmatrix} -> [a, b]ᵀ
        text = replaceInlineMatrices(text)

        // 3. Hat and vector accents
        text = text.replace(Regex("""\\hat\{i\}|\\hat\{ı\}|\\hat\s+i"""), "î")
            .replace(Regex("""\\hat\{j\}|\\hat\s+j"""), "ĵ")
            .replace(Regex("""\\hat\{k\}|\\hat\s+k"""), "k̂")
            .replace(Regex("""\\hat\{x\}|\\hat\s+x"""), "x̂")
            .replace(Regex("""\\hat\{y\}|\\hat\s+y"""), "ŷ")
            .replace(Regex("""\\hat\{z\}|\\hat\s+z"""), "ẑ")
            .replace(Regex("""\\hat\{w\}|\\hat\s+w"""), "ŵ")
            .replace(Regex("""\\hat\{theta\}|\\hat\\theta"""), "θ̂")
            .replace(Regex("""\\hat\{([^}]+)\}""")) { "${it.groupValues[1]}̂" }
            .replace(Regex("""\\vec\{v\}|\\vec\s+v"""), "v⃗")
            .replace(Regex("""\\vec\{u\}|\\vec\s+u"""), "u⃗")
            .replace(Regex("""\\vec\{w\}|\\vec\s+w"""), "w⃗")
            .replace(Regex("""\\vec\{x\}|\\vec\s+x"""), "x⃗")
            .replace(Regex("""\\vec\{y\}|\\vec\s+y"""), "y⃗")
            .replace(Regex("""\\vec\{i\}|\\vec\s+i"""), "i⃗")
            .replace(Regex("""\\vec\{j\}|\\vec\s+j"""), "j⃗")
            .replace(Regex("""\\vec\{([^}]+)\}""")) { "${it.groupValues[1]}⃗" }

        // 4. Blackboard bold sets
        text = text.replace("\\mathbb{R}", "ℝ")
            .replace("\\mathbb{N}", "ℕ")
            .replace("\\mathbb{Z}", "ℤ")
            .replace("\\mathbb{Q}", "ℚ")
            .replace("\\mathbb{C}", "ℂ")
            .replace("\\mathbb{P}", "ℙ")
            .replace("\\mathbb{E}", "𝔼")
            .replace("\\mathbb{H}", "ℍ")
            .replace(Regex("""\\mathbb\{([A-Za-z])\}""")) { it.groupValues[1] }

        // 5. Formats: \mathbf, \boldsymbol, \mathrm, \text, \mathit
        text = text.replace(Regex("""\\(mathbf|boldsymbol|mathrm|text|textbf|textit|mathit)\{([^{}]+)\}""")) { it.groupValues[2] }

        // 6. Non-letter spacing commands
        text = text.replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\:", " ")
            .replace("\\!", "")
            .replace("\\ ", " ")

        // 7. Whole-word regex matching for LaTeX commands: \\([a-zA-Z]+)
        text = Regex("""\\([a-zA-Z]+)""").replace(text) { match ->
            val cmd = match.groupValues[1]
            LATEX_COMMANDS[cmd] ?: cmd
        }

        // Clean spaces around brackets, floors, and ceilings
        text = text.replace(Regex("""⌊\s+"""), "⌊").replace(Regex("""\s+⌋"""), "⌋")
        text = text.replace(Regex("""⌈\s+"""), "⌈").replace(Regex("""\s+⌉"""), "⌉")

        // 8. Fractions: \frac{a}{b} -> a/b or (a)/(b)
        text = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""").replace(text) { match ->
            val num = cleanInlineLatex(match.groupValues[1].trim())
            val den = cleanInlineLatex(match.groupValues[2].trim())
            if (num.length <= 3 && den.length <= 3 && !num.contains(" ") && !den.contains(" ")) {
                "$num/$den"
            } else {
                "($num)/($den)"
            }
        }

        // 9. Square roots: \sqrt{...} -> √( ... )
        text = Regex("""\\sqrt\{([^{}]+)\}""").replace(text) { match ->
            "√(${cleanInlineLatex(match.groupValues[1].trim())})"
        }

        // 10. Transpose ^T or ^\top or ^\intercal
        text = text.replace(Regex("""\^\{\\top\}|\^\\top|\^T|\^\{T\}|\^\{\\intercal\}"""), "ᵀ")

        // 11. Superscripts: ^{x} or ^x (resolve inner macros before mapping)
        text = Regex("""\^\{([^{}]+)\}""").replace(text) { match ->
            val inner = cleanInlineLatex(match.groupValues[1])
            val allMapped = inner.all { SUPERSCRIPTS.containsKey(it) }
            if (allMapped && inner.isNotBlank()) {
                inner.map { SUPERSCRIPTS[it] ?: it }.joinToString("")
            } else {
                "^($inner)"
            }
        }
        text = Regex("""\^([0-9a-zA-Z])""").replace(text) { match ->
            val c = match.groupValues[1].first()
            (SUPERSCRIPTS[c] ?: "^$c").toString()
        }

        // 12. Subscripts: _{x} or _x
        text = Regex("""_\{([^{}]+)\}""").replace(text) { match ->
            val inner = cleanInlineLatex(match.groupValues[1])
            val allMapped = inner.all { SUBSCRIPTS.containsKey(it) }
            if (allMapped && inner.isNotBlank()) {
                inner.map { SUBSCRIPTS[it] ?: it }.joinToString("")
            } else {
                "_($inner)"
            }
        }
        text = Regex("""_([0-9a-zA-Z])""").replace(text) { match ->
            val c = match.groupValues[1].first()
            (SUBSCRIPTS[c] ?: "_$c").toString()
        }

        // 13. Strip leftover formatting commands and braces
        text = text.replace(Regex("""\\[a-zA-Z]+"""), "") // remove remaining backslash commands
        text = text.replace("{", "").replace("}", "") // strip ALL remaining raw braces {X} -> X

        // 14. Restore protected escaped underscores
        text = text.replace(placeholderUnderscore, "_")

        // Normalize multiple spaces
        text = text.replace(Regex("""\s{2,}"""), " ").trim()

        return text
    }

    private fun replaceInlineMatrices(input: String): String {
        val matrixRegex = Regex("""\\begin\{(bmatrix|pmatrix|matrix)\}(.*?)\\end\{\1\}""", RegexOption.DOT_MATCHES_ALL)
        return matrixRegex.replace(input) { match ->
            val content = match.groupValues[2].trim()
            val rows = content.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
            if (rows.size == 1) {
                val cols = rows[0].split("&").map { cleanInlineLatex(it.trim()) }
                "[${cols.joinToString(", ")}]"
            } else {
                // Check if it is a single column vector (each row has no '&')
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
        var clean = raw.trim()
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("$").removeSuffix("$")
            .trim()

        if (clean.isBlank()) return Math2DNode.Text("")

        // Check for standalone or embedded matrix
        val matrixRegex = Regex("""\\begin\{(bmatrix|pmatrix|vmatrix|matrix)\}(.*?)\\end\{\1\}""", RegexOption.DOT_MATCHES_ALL)
        val matrixMatch = matrixRegex.find(clean)

        if (matrixMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, matrixMatch.range.first).trim()
            if (prefix.isNotEmpty()) {
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
            if (suffix.isNotEmpty()) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for stacked fraction \frac{numerator}{denominator}
        val fracRegex = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
        val fracMatch = fracRegex.find(clean)
        if (fracMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, fracMatch.range.first).trim()
            if (prefix.isNotEmpty()) {
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
            if (suffix.isNotEmpty()) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for square root \sqrt{inner}
        val sqrtRegex = Regex("""\\sqrt\{([^{}]+)\}""")
        val sqrtMatch = sqrtRegex.find(clean)
        if (sqrtMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, sqrtMatch.range.first).trim()
            if (prefix.isNotEmpty()) {
                nodes.add(parseDisplayMath(prefix))
            }

            val inner = sqrtMatch.groupValues[1]
            nodes.add(Math2DNode.Sqrt(inner = parseDisplayMath(inner)))

            val suffix = clean.substring(sqrtMatch.range.last + 1).trim()
            if (suffix.isNotEmpty()) {
                nodes.add(parseDisplayMath(suffix))
            }

            return if (nodes.size == 1) nodes.first() else Math2DNode.Row(nodes)
        }

        // Check for Summations, Products, Integrals \sum_{lower}^{upper} or \int_{lower}^{upper}
        val opRegex = Regex("""\\(sum|prod|int|lim)(?:_\{?([^{}\n^]+)\}?)?(?:\^\{?([^{}\n_]+)\}?)?""")
        val opMatch = opRegex.find(clean)
        if (opMatch != null) {
            val nodes = mutableListOf<Math2DNode>()
            val prefix = clean.substring(0, opMatch.range.first).trim()
            if (prefix.isNotEmpty()) {
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
            val exprNode = if (suffix.isNotEmpty()) parseDisplayMath(suffix) else null

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

        // Otherwise plain text, clean with cleanInlineLatex
        return Math2DNode.Text(cleanInlineLatex(clean))
    }
}
