package com.boardsprep.onboard.core.ai

object CbsePromptBuilder {

    fun buildSystemPrompt(subject: String, chapter: String, webSearchEnabled: Boolean = false): String {
        val searchDirective = if (webSearchEnabled) {
            """
            INTERNET SEARCH DIRECTIVE:
            - Ground explanations against official CBSE 2026-27 circulars, rationalized NCERT syllabi, and official marking schemes.
            - If citing specific circulars or notifications, provide concise references.
            """.trimIndent()
        } else ""

        return """
            You are 'OnBOARD AI', an elite CBSE Class 12 Master Teacher and Academic Mentor for $subject ($chapter).

            STRICT FORMATTING & MATHEMATICAL RULES:
            1. NO RAW LATEX: Strictly FORBIDDEN to use LaTeX delimiters (\(, \), \[, \], $, $$, \text{}, \frac{}{}, \vec{}, \cdot, \times).
            2. UNICODE MATH ONLY: Always format formulas using clean Unicode math symbols, superscripts, and subscripts:
               - Use: ·, ×, ±, ∓, ÷, ≈, ≠, ≤, ≥, √, π, θ, Δ, δ, λ, μ, ε₀, Ω, ω, °, ⇌, →
               - Superscripts: ⁰, ¹, ², ³, ⁴, ⁵, ⁶, ⁷, ⁸, ⁹, ⁺, ⁻, ⁿ, ˣ (e.g. 4 × 10⁻⁹ C·m, r², xⁿ)
               - Subscripts: ₀, ₁, ₂, ₃, ₄, ₅, ₆, ₇, ₈, ₉, ₊, ₋, ₐ, ₑ (e.g. ε₀, U_initial, v_avg)
               - Example: U = -p·E = -pE cos θ; θ = 0°; p = 4 × 10⁻⁹ C·m; ΔU = U_final - U_initial.
               - NEVER output backslashes (\) or raw code strings.
            3. CBSE Reality: Strictly adhere to the official CBSE 2026–27 Class 12 Rationalized NCERT syllabus.
            4. Brevity & High Density: Be concise, incisive, and scannable. Zero corporate or conversational AI fluff.

            $searchDirective
        """.trimIndent()
    }

    fun buildDppDiagnosticPrompt(context: DppQuestionContext): String {
        val optionsFormatted = context.options.mapIndexed { idx, opt ->
            val letter = listOf("A", "B", "C", "D").getOrElse(idx) { "$idx" }
            "($letter) $opt"
        }.joinToString("\n")

        val studentPickInfo = if (context.selectedOptionIndex != null) {
            "Student selected: Option (${context.selectedOptionLetter}) \"${context.options.getOrNull(context.selectedOptionIndex) ?: ""}\""
        } else {
            "Student skipped this question without selecting an option."
        }

        return """
            Diagnose this CBSE Class 12 ${context.subject} question from chapter '${context.chapter}'.

            QUESTION:
            ${context.questionText}

            OPTIONS:
            $optionsFormatted

            OFFICIAL CORRECT ANSWER:
            Option (${context.correctOptionLetter}) "${context.options.getOrNull(context.correctOptionIndex) ?: ""}"

            $studentPickInfo

            TEXTBOOK / OFFICIAL SOLUTION:
            ${context.officialExplanation.ifBlank { "Apply standard CBSE NCERT methodology." }}

            REQUIRED OUTPUT FORMAT:
            Provide EXACTLY three concise sections. Remember: NO LaTeX backslashes or delimiters. Use Unicode math symbols only.

            ### 1. Core Concept & Proof
            Concise 2-3 step derivation or principle proving Option (${context.correctOptionLetter}).

            ### 2. Why Your Pick Was Wrong
            Pinpoint the exact trap, arithmetic mistake, or sign slip in the chosen or distractor option.

            ### 3. CBSE Board Exam Tip
            1-sentence exam tip on step marks (½ mark formula, 1 mark substitution with SI unit).
        """.trimIndent()
    }

    fun buildSocraticHintPrompt(context: DppQuestionContext): String {
        val optionsFormatted = context.options.mapIndexed { idx, opt ->
            val letter = listOf("A", "B", "C", "D").getOrElse(idx) { "$idx" }
            "($letter) $opt"
        }.joinToString("\n")

        return """
            The student is solving this CBSE Class 12 ${context.subject} question in Practice Mode:

            QUESTION:
            ${context.questionText}

            OPTIONS:
            $optionsFormatted

            CRITICAL SOCRATIC RULES:
            - Never reveal the correct option (A, B, C, D).
            - Never solve the question or compute the final numeric value.
            - Provide a strict 1-2 sentence first-step nudge:
              1. State the key formula or physical principle (in Unicode math, NO LaTeX).
              2. Point to the critical condition or given value to examine first.
            - Total length: under 40 words.
        """.trimIndent()
    }

    // ─── CBSE Question Bank Prompts ─────────────────────────────────────────────

    fun buildQbHintPrompt(context: QuestionBankAiContext): String {
        return """
            The student needs a high-yield hint for this CBSE ${context.marks}-Mark Subjective question (${context.subject} - ${context.chapterName}):

            QUESTION:
            ${context.questionText}

            RULES:
            - Provide a strict 1-line conceptual clue in under 25 words.
            - DO NOT write out the full answer or derivation.
            - Point them to the starting law or key relationship in clean Unicode math.
        """.trimIndent()
    }

    fun buildQbKeyTermsPrompt(context: QuestionBankAiContext): String {
        val officialTerms = if (context.keyTerms.isNotEmpty()) {
            "Official syllabus key terms: ${context.keyTerms.joinToString(", ")}"
        } else ""

        return """
            Identify the mandatory CBSE Keywords for this ${context.marks}-Mark question (${context.subject} - ${context.chapterName}):

            QUESTION:
            ${context.questionText}

            $officialTerms

            OUTPUT FORMAT:
            List exactly 4–5 essential NCERT technical terms or formula conditions that the CBSE evaluator requires to award full marks. Provide 1 short phrase per bullet. Under 50 words total.
        """.trimIndent()
    }

    fun buildQbGradeDraftPrompt(context: QuestionBankAiContext, draftAnswer: String): String {
        return """
            You are a strict CBSE Board Exam Head Evaluator. Grade the student's draft answer against the official ${context.marks}-mark rubric for ${context.subject} (${context.chapterName}).

            QUESTION (${context.marks} Marks):
            ${context.questionText}

            OFFICIAL CBSE MODEL ANSWER:
            ${context.modelAnswer}

            OFFICIAL STEP RUBRIC:
            ${context.stepMarkingSummary}

            STUDENT DRAFT ANSWER:
            "$draftAnswer"

            EVALUATION OUTPUT FORMAT:
            Provide a strict, constructive evaluation:
            - **Estimated Marks:** [X / ${context.marks} Marks]
            - **Awarded Steps:** What they got right (½m / 1m each).
            - **Missing Value Points:** Exact keywords or formulas missed.
            - **Examiner Advice:** 1 sentence on how to present it for a full 100/100 script.
        """.trimIndent()
    }

    fun buildPdfExplainPrompt(selectedText: String, subject: String, chapter: String, mode: String = "EXPLAIN"): String {
        return when (mode) {
            "ELI5" -> """
                Explain this excerpt from NCERT $subject ($chapter) using a clear, real-world intuitive analogy with zero academic jargon:
                
                "$selectedText"
                
                Keep it concise (2-3 sentences), followed by a 1-sentence formal CBSE textbook connection. Unicode math only, no LaTeX.
            """.trimIndent()

            "CBSE_ANGLE" -> """
                Analyze this excerpt from NCERT $subject ($chapter) from the perspective of a CBSE Class 12 Board Exam paper setter:
                
                "$selectedText"
                
                1. What types of questions does CBSE extract from this paragraph (1-mark Assertion-Reason, 2-mark Reasoning, 3-mark Derivation, or Case-Based)?
                2. What specific keyword or condition must be included in the answer script to secure full marks?
            """.trimIndent()

            else -> """
                Explain this concept from CBSE Class 12 $subject ($chapter) clearly, rigorously, and concisely with Unicode math (NO LaTeX delimiters):
                
                "$selectedText"
            """.trimIndent()
        }
    }
}
