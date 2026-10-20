package com.boardsprep.onboard.core.ai

object AiSanitizer {

    private val superscriptMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 't' to 'ᵗ'
    )

    private val subscriptMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'i' to 'ᵢ',
        'j' to 'ⱼ', 'k' to 'ₖ', 'm' to 'ₘ', 'n' to 'ₙ', 'p' to 'ₚ',
        's' to 'ₛ', 't' to 'ₜ'
    )

    fun toSuperscript(raw: String): String {
        return buildString {
            for (ch in raw) {
                append(superscriptMap[ch] ?: ch)
            }
        }
    }

    fun toSubscript(raw: String): String {
        return buildString {
            for (ch in raw) {
                append(subscriptMap[ch] ?: ch)
            }
        }
    }

    /**
     * Sanitizes raw LaTeX strings, stripping delimiters like \( ... \), \[ ... \],
     * and converting common mathematical symbols, fractions, and superscripts/subscripts
     * into clean, natively readable Unicode characters.
     */
    fun sanitizeLatexToUnicode(input: String): String {
        if (input.isBlank()) return input

        var text = input

        // 1. Remove LaTeX delimiters
        text = text.replace(Regex("""\\\(|\\\)"""), "")
        text = text.replace(Regex("""\\\[|\\\]"""), "")
        text = text.replace("$$", "")

        // 2. Unescape LaTeX spaces and punctuation
        text = text.replace(Regex("""\\[!,;: ]"""), " ")

        // 3. Text formatting commands: \text{...}, \mathrm{...}, \mathbf{...}, \mathit{...}
        text = text.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|textnormal)\{([^}]*)\}""")) {
            it.groupValues[1]
        }

        // 4. Fractions: \frac{num}{den} -> (num)/(den)
        text = text.replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")) {
            val num = it.groupValues[1].trim()
            val den = it.groupValues[2].trim()
            if (num.length <= 4 && den.length <= 4 && !num.contains(" ") && !den.contains(" ")) {
                "$num/$den"
            } else {
                "($num)/($den)"
            }
        }

        // 5. Square Roots: \sqrt[n]{x} -> ⁿ√(x), \sqrt{x} -> √(x), \sqrt -> √
        text = text.replace(Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")) {
            "${toSuperscript(it.groupValues[1])}√(${it.groupValues[2]})"
        }
        text = text.replace(Regex("""\\sqrt\{([^}]+)\}""")) {
            "√(${it.groupValues[1]})"
        }
        text = text.replace(Regex("""\\sqrt\b"""), "√")

        // 6. Common Physics / Math Constants & Greek Letters
        text = text.replace(Regex("""\\varepsilon_0|\\epsilon_0"""), "ε₀")
        text = text.replace(Regex("""\\varepsilon|\\epsilon"""), "ε")
        text = text.replace(Regex("""\\Delta"""), "Δ")
        text = text.replace(Regex("""\\delta"""), "δ")
        text = text.replace(Regex("""\\theta|\\vartheta"""), "θ")
        text = text.replace(Regex("""\\lambda"""), "λ")
        text = text.replace(Regex("""\\mu"""), "μ")
        text = text.replace(Regex("""\\pi"""), "π")
        text = text.replace(Regex("""\\phi|\\varphi"""), "ϕ")
        text = text.replace(Regex("""\\omega"""), "ω")
        text = text.replace(Regex("""\\Omega"""), "Ω")
        text = text.replace(Regex("""\\alpha"""), "α")
        text = text.replace(Regex("""\\beta"""), "β")
        text = text.replace(Regex("""\\gamma"""), "γ")
        text = text.replace(Regex("""\\Gamma"""), "Γ")
        text = text.replace(Regex("""\\sigma"""), "σ")
        text = text.replace(Regex("""\\Sigma"""), "Σ")
        text = text.replace(Regex("""\\tau"""), "τ")
        text = text.replace(Regex("""\\rho"""), "ρ")
        text = text.replace(Regex("""\\nu"""), "ν")
        text = text.replace(Regex("""\\eta"""), "η")

        // 7. Math Operators & Relations
        text = text.replace(Regex("""\\times\b"""), "×")
        text = text.replace(Regex("""\\cdot\b"""), "·")
        text = text.replace(Regex("""\\pm\b"""), "±")
        text = text.replace(Regex("""\\mp\b"""), "∓")
        text = text.replace(Regex("""\\div\b"""), "÷")
        text = text.replace(Regex("""\\approx\b"""), "≈")
        text = text.replace(Regex("""\\neq\b"""), "≠")
        text = text.replace(Regex("""\\leq\b|\\le\b"""), "≤")
        text = text.replace(Regex("""\\geq\b|\\ge\b"""), "≥")
        text = text.replace(Regex("""\\infty\b"""), "∞")
        text = text.replace(Regex("""\\circ\b"""), "°")
        text = text.replace(Regex("""\\rightarrow\b|\\to\b"""), "→")
        text = text.replace(Regex("""\\leftarrow\b"""), "←")
        text = text.replace(Regex("""\\leftrightarrow\b"""), "↔")
        text = text.replace(Regex("""\\rightleftharpoons\b"""), "⇌")

        // 8. Vector & Hat notation: \vec{p} or \vec p -> p, \hat{r} or \hat r -> r
        text = text.replace(Regex("""\\vec\s*\{?([a-zA-Z0-9]+)\}?""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\hat\s*\{?([a-zA-Z0-9]+)\}?""")) { it.groupValues[1] }
        text = text.replace(Regex("""\\bar\s*\{?([a-zA-Z0-9]+)\}?""")) { "${it.groupValues[1]}̄" }

        // 9. Standard Trig & Math functions: \cos -> cos, \sin -> sin, etc.
        text = text.replace(Regex("""\\(cos|sin|tan|sec|csc|cot|ln|log|exp|det|max|min)\b""")) {
            it.groupValues[1]
        }

        // 10. Superscripts: ^{...} or ^\text{...} or ^12
        text = text.replace(Regex("""\^\{([^}]+)\}""")) {
            toSuperscript(it.groupValues[1])
        }
        text = text.replace(Regex("""\^([0-9a-zA-Z+-])""")) {
            toSuperscript(it.groupValues[1])
        }

        // 11. Subscripts: _{...} or _1
        text = text.replace(Regex("""_\{([^}]+)\}""")) {
            toSubscript(it.groupValues[1])
        }
        text = text.replace(Regex("""_([0-9a-zA-Z+-])""")) {
            toSubscript(it.groupValues[1])
        }

        // 12. Clean single dollar delimiters ($...$) without eating markdown
        // If text is wrapped in $ ... $, strip outer dollars
        text = text.replace(Regex("""\$([^$\n]+)\$""")) {
            it.groupValues[1]
        }

        // 13. Strip any stray single backslashes before letters
        text = text.replace(Regex("""\\([a-zA-Z])""")) { it.groupValues[1] }

        return text
    }
}
