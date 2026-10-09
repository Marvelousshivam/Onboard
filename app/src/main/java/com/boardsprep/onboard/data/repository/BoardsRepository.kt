package com.boardsprep.onboard.data.repository

import android.content.Context
import com.boardsprep.onboard.core.playback.LocalLectureScanner
import com.boardsprep.onboard.data.local.OnboardDatabase
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.boardsprep.onboard.data.models.*
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class BoardsRepository(private val context: Context) {

    companion object {
        const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com/Marvelousshivam/OnBoard-files/main"
    }

    private val db = OnboardDatabase.getInstance(context)
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    val manifestManager = CurriculumManifestManager(context)

    fun getSubjects(): List<Subject> {
        val manifest = manifestManager.getManifest()
        if (manifest != null && manifest.subjects.isNotEmpty()) {
            return manifest.subjects.map { remoteSub ->
                Subject(
                    id = remoteSub.id,
                    name = remoteSub.name,
                    code = remoteSub.code,
                    stream = remoteSub.stream,
                    totalTheoryMarks = remoteSub.totalTheoryMarks,
                    totalPracticalMarks = remoteSub.totalPracticalMarks,
                    iconName = remoteSub.iconName,
                    chapters = remoteSub.chapters.map { remoteCh ->
                        Chapter(
                            id = remoteCh.id,
                            subjectId = remoteSub.id,
                            number = remoteCh.number,
                            name = remoteCh.name,
                            weightageMarks = remoteCh.weightageMarks,
                            ncertPdfUrl = remoteCh.ncertPdfUrl,
                            exemplarPdfUrl = "",
                            notesPdfUrl = remoteCh.notes.firstOrNull()?.url ?: "",
                            dppPdfUrl = remoteCh.dpps.firstOrNull()?.url ?: "",
                            keyTopics = remoteCh.keyTopics,
                            totalLecturesCount = remoteCh.videos.size,
                            totalDppsCount = remoteCh.dpps.size,
                            notesList = remoteCh.notes.map { ResourceItem(title = it.title, url = it.url, filename = it.filename) },
                            dppsList = remoteCh.dpps.map { ResourceItem(title = it.title, url = it.url, filename = it.filename) },
                            quizzesList = remoteCh.quizzes.map { QuizItem(id = it.id, title = it.title.trim(), url = it.url, filename = it.filename, totalQuestions = it.totalQuestions) },
                            videosList = remoteCh.videos
                                .filter { it.title.isNotBlank() && it.youtubeUrl.isNotBlank() }
                                .mapIndexed { idx, v ->
                                    val safeTitle = if (v.title.isNotBlank()) v.title.trim() else "${remoteCh.name} — Lecture ${idx + 1}"
                                    Lecture(
                                        id = v.id.ifBlank { "vid_${remoteCh.id}_${idx + 1}" },
                                        chapterId = remoteCh.id,
                                        title = safeTitle,
                                        youtubeUrl = v.youtubeUrl,
                                        durationText = v.duration.ifBlank { "One Shot" },
                                        author = v.author.ifBlank { "Curated" }
                                    )
                                }
                        )
                    }
                )
            }
        }
        return emptyList()
    }

    fun getSamplePapers(): List<SamplePaper> {
        val manifest = manifestManager.getManifest()
        val rawList = manifest?.samplePapers ?: emptyList()
        return rawList.map { remote ->
            val mappedSubjectId = when (remote.subject.trim().lowercase()) {
                "english", "englishcore", "english core" -> "english"
                "physics" -> "physics"
                "chemistry" -> "chemistry"
                "maths", "mathematics" -> "maths"
                "biology" -> "biology"
                "physicaleducation", "physical education", "pe" -> "physical_education"
                else -> remote.subject.lowercase().replace(" ", "_")
            }
            val cleanTitle = if (remote.title.isNotBlank()) {
                remote.title
            } else {
                val typeName = if (remote.type.equals("sqp", ignoreCase = true)) "Sample Question Paper" else "Marking Scheme"
                "CBSE Class 12 2026-27 ${remote.subject} $typeName"
            }
            SamplePaper(
                title = cleanTitle,
                filename = remote.filename,
                subject = remote.subject,
                subjectId = mappedSubjectId,
                type = remote.type.lowercase(),
                url = remote.url
            )
        }
    }

    fun getSamplePapersForSubject(subjectId: String): List<SamplePaper> {
        return getSamplePapers().filter { it.subjectId.equals(subjectId, ignoreCase = true) }
    }

    fun getBlueprintForSubject(subjectId: String): BoardBlueprint {
        return when (subjectId) {
            "english" -> BoardBlueprint(
                subject = "English Core",
                subjectCode = "301",
                totalMarks = 80,
                totalTimeMinutes = 180,
                totalQuestions = 13,
                competencyBreakdown = "Reading: 22 Marks (28%) | Creative Writing: 18 Marks (22%) | Literature: 40 Marks (50%)",
                notes = "Section A: 2 Comprehension passages. Section B: Official CBSE box formats for Notice/Invitation. Section C: Flamingo & Vistas extracts and critical thinking answers.",
                sections = listOf(
                    BlueprintSection("Section A: Reading", "Unseen Passage (12m) + Case-based Factual Passage (10m)", 2, 11, 22),
                    BlueprintSection("Section B: Writing", "Notice (4m), Invitation (4m), Letter (5m), Article/Report (5m)", 4, 4, 18),
                    BlueprintSection("Section C: Literature", "Flamingo Poetry & Prose Extracts, Short & Long Answer Questions", 7, 6, 40)
                )
            )
            "physics" -> BoardBlueprint(
                subject = "Physics",
                subjectCode = "042",
                totalMarks = 70,
                totalTimeMinutes = 180,
                totalQuestions = 33,
                competencyBreakdown = "Remembering & Understanding: 38% (27m) | Applying: 32% (22m) | Analysing/Evaluating: 30% (21m)",
                notes = "33% internal choice across all sections. Step-marking applies: formula (0.5m), substitution (0.5m), final answer with proper SI unit (1m).",
                sections = listOf(
                    BlueprintSection("Section A", "12 Multiple Choice Questions + 4 Assertion-Reasoning", 16, 1, 16),
                    BlueprintSection("Section B", "Short Answer Type-I Questions (2 Marks each)", 5, 2, 10),
                    BlueprintSection("Section C", "Short Answer Type-II Questions (3 Marks each)", 7, 3, 21),
                    BlueprintSection("Section D", "Case-Based / Data-Based Questions (4 Marks each)", 2, 4, 8),
                    BlueprintSection("Section E", "Long Answer Type Questions with Derivations (5 Marks each)", 3, 5, 15)
                )
            )
            "chemistry" -> BoardBlueprint(
                subject = "Chemistry",
                subjectCode = "043",
                totalMarks = 70,
                totalTimeMinutes = 180,
                totalQuestions = 33,
                competencyBreakdown = "Remembering & Understanding: 40% (28m) | Applying: 30% (21m) | Analysing/Evaluating: 30% (21m)",
                notes = "Physical Chemistry (23m), Inorganic Chemistry (14m), Organic Chemistry (33m). 33% internal choices provided in all sections.",
                sections = listOf(
                    BlueprintSection("Section A", "12 Multiple Choice Questions + 4 Assertion-Reasoning", 16, 1, 16),
                    BlueprintSection("Section B", "Short Answer Type-I Questions (2 Marks each)", 5, 2, 10),
                    BlueprintSection("Section C", "Short Answer Type-II Questions (3 Marks each)", 7, 3, 21),
                    BlueprintSection("Section D", "Case-Based Integrated Assessment Questions (4 Marks each)", 2, 4, 8),
                    BlueprintSection("Section E", "Long Answer Questions (Organic Mechanisms & Conversions)", 3, 5, 15)
                )
            )
            "maths" -> BoardBlueprint(
                subject = "Mathematics",
                subjectCode = "041",
                totalMarks = 80,
                totalTimeMinutes = 180,
                totalQuestions = 38,
                competencyBreakdown = "Remembering & Understanding: 55% (44m) | Applying: 25% (20m) | Analysing/Evaluating: 20% (16m)",
                notes = "Calculus alone carries 35 marks! 33% internal choice across all sections. Lab Manual activity assessment: 10 marks.",
                sections = listOf(
                    BlueprintSection("Section A", "18 Multiple Choice Questions + 2 Assertion-Reasoning", 20, 1, 20),
                    BlueprintSection("Section B", "Very Short Answer Questions (2 Marks each)", 5, 2, 10),
                    BlueprintSection("Section C", "Short Answer Questions (3 Marks each)", 6, 3, 18),
                    BlueprintSection("Section D", "Long Answer Questions (5 Marks each)", 4, 5, 20),
                    BlueprintSection("Section E", "Source-Based / Case-Based Integrated Units (4 Marks each)", 3, 4, 12)
                )
            )
            "biology" -> BoardBlueprint(
                subject = "Biology",
                subjectCode = "044",
                totalMarks = 70,
                totalTimeMinutes = 180,
                totalQuestions = 33,
                competencyBreakdown = "Knowledge & Understanding: 50% (35m) | Application: 30% (21m) | Analyze/Evaluate: 20% (14m)",
                notes = "Genetics & Evolution (20m) + Reproduction (16m) constitute over 51% of theory marks! Well-labeled diagrams carry dedicated marks.",
                sections = listOf(
                    BlueprintSection("Section A", "12 Multiple Choice Questions + 4 Assertion-Reasoning", 16, 1, 16),
                    BlueprintSection("Section B", "Short Answer Questions (2 Marks each)", 5, 2, 10),
                    BlueprintSection("Section C", "Short Answer Questions (3 Marks each)", 7, 3, 21),
                    BlueprintSection("Section D", "Case-Based / Data-Based Questions (4 Marks each)", 2, 4, 8),
                    BlueprintSection("Section E", "Long Answer Questions with Detailed Crosses / Cycles", 3, 5, 15)
                )
            )
            else -> BoardBlueprint(
                subject = "Physical Education",
                subjectCode = "048",
                totalMarks = 70,
                totalTimeMinutes = 180,
                totalQuestions = 34,
                competencyBreakdown = "Objective MCQs: 18 Marks | Short & Long Questions: 40 Marks | Case Studies: 12 Marks",
                notes = "Practical carries 30 marks (SAI Khelo India Fitness Test 6m, Sports Skill 7m, Yogic Practices 7m, Record File 5m, Viva 5m).",
                sections = listOf(
                    BlueprintSection("Section A", "18 Multiple Choice Questions", 18, 1, 18),
                    BlueprintSection("Section B", "Very Short Answer Questions (2 Marks each)", 5, 2, 10),
                    BlueprintSection("Section C", "Short Answer Questions (3 Marks each)", 5, 3, 15),
                    BlueprintSection("Section D", "Case-Based / Picture-Based Questions (4 Marks each)", 3, 4, 12),
                    BlueprintSection("Section E", "Long Answer Questions (5 Marks each)", 3, 5, 15)
                )
            )
        }
    }

    fun getLecturesForChapter(chapter: Chapter): List<Lecture> {
        val curated = mutableListOf<Lecture>()

        // 1. Curated real lectures from manifest for this chapter
        curated.addAll(chapter.videosList.filter { it.title.isNotBlank() })

        // 2. Dynamically add user-added local lectures from the lectures/ directory!
        val localLectures = LocalLectureScanner.scanLocalLectures(context, chapter.name)
            .filter { it.title.isNotBlank() }
        curated.addAll(localLectures)

        // 3. Guarantee strictly non-empty, clean titles and unique IDs
        return curated
            .distinctBy { it.id.ifBlank { it.title } }
            .mapIndexed { index, lec ->
                if (lec.title.isBlank()) {
                    lec.copy(title = "${chapter.name} — Lecture ${index + 1}")
                } else {
                    lec
                }
            }
            .filter { it.title.isNotBlank() }
    }

    suspend fun getQuizForFile(filename: String): Quiz? = withContext(Dispatchers.IO) {
        val remoteQuiz = manifestManager.fetchRemoteQuiz(filename)
        if (remoteQuiz != null) {
            return@withContext remoteQuiz
        }

        val cleanName = filename.substringAfterLast("/").substringAfterLast("\\")
        val possibleFolders = listOf("english", "biology", "physics", "chemistry", "maths", "physical_education")
        for (folder in possibleFolders) {
            try {
                val assetPath = "quizzes/$folder/$cleanName"
                context.assets.open(assetPath).use { stream ->
                    InputStreamReader(stream, "UTF-8").use { reader ->
                        val parsed = gson.fromJson(reader, Quiz::class.java)
                        if (parsed != null) {
                            val computedTotal = if (parsed.totalQuestions > 0) parsed.totalQuestions else parsed.questions.size
                            val quizId = if (parsed.id.isNotBlank()) parsed.id else cleanName.removeSuffix(".json")
                            return@withContext parsed.copy(id = quizId, totalQuestions = computedTotal)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext generateFallbackQuiz(cleanName)
    }

    private fun generateFallbackQuiz(cleanName: String): Quiz {
        val chapterId = cleanName.substringBefore("_dpp").ifEmpty { "general" }
        val allChapters = getSubjects().flatMap { it.chapters }
        val chapter = allChapters.find { it.id.equals(chapterId, ignoreCase = true) }
        val chapterName = chapter?.name ?: "CBSE High-Yield Revision"
        val subjectName = chapter?.subjectId?.replaceFirstChar { it.uppercase() } ?: "CBSE Board"

        val questions = when {
            chapterId.startsWith("phy") -> listOf(
                Question(
                    id = "q1", qNo = 1,
                    question = "In $chapterName, what is the fundamental SI unit and physical dimension associated with the primary field quantity?",
                    options = listOf(
                        "Volt per metre (V/m)",
                        "Newton second (N·s)",
                        "Coulomb per square metre (C/m²)",
                        "Weber per square metre (Wb/m²)"
                    ),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "In CBSE Class 12 Physics, electric field intensity has SI units of V/m (or N/C), derived directly from E = -dV/dr."
                ),
                Question(
                    id = "q2", qNo = 2,
                    question = "Assertion (A): Gauss's law is valid only for symmetric charge distributions.\nReason (R): The electric flux through any closed surface depends only on the net charge enclosed.",
                    options = listOf(
                        "Both (A) and (R) are true and (R) is the correct explanation of (A)",
                        "Both (A) and (R) are true but (R) is not the correct explanation of (A)",
                        "(A) is false but (R) is true",
                        "(A) is true but (R) is false"
                    ),
                    correctOptionIndex = 2,
                    correctAnswerLetter = "C",
                    explanation = "Gauss's law is mathematically valid for all closed surfaces regardless of shape or symmetry; however, symmetry is required to conveniently compute E."
                ),
                Question(
                    id = "q3", qNo = 3,
                    question = "A particle with charge q moves with velocity v in a region containing uniform magnetic field B. The work done by the magnetic force on the charge is:",
                    options = listOf("qvB", "Zero", "1/2 q v² B", "Infinite"),
                    correctOptionIndex = 1,
                    correctAnswerLetter = "B",
                    explanation = "The magnetic Lorentz force F = q(v x B) is always perpendicular to velocity v. Power P = F · v = 0, so work done is strictly zero."
                ),
                Question(
                    id = "q4", qNo = 4,
                    question = "According to the official CBSE Class 12 blueprint for $chapterName, which derivation holds the highest probability rating in 3-mark / 5-mark sections?",
                    options = listOf(
                        "Derivation of the governing formula using vector calculus and boundary conditions",
                        "Dimensional analysis only",
                        "Empirical graphical estimation",
                        "None of the above"
                    ),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "CBSE board marking schemes award step-marks for stating the initial vector relation, showing intermediate integration steps, and writing the final boxed result."
                ),
                Question(
                    id = "q5", qNo = 5,
                    question = "Two identical components from $chapterName are connected first in series and then in parallel. The equivalent ratio depends strictly on:",
                    options = listOf("Square of the count (n²)", "Linear sum (2n)", "Inverse root (1/√n)", "Independent of configuration"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "For n identical components (capacitors or resistors), the ratio of equivalent series to parallel configuration scales as n²."
                )
            )
            chapterId.startsWith("chem") -> listOf(
                Question(
                    id = "q1", qNo = 1,
                    question = "For $chapterName, which of the following expressions correctly represents the temperature dependency of reaction kinetics?",
                    options = listOf(
                        "k = A · e^(-Ea / RT) (Arrhenius Equation)",
                        "P = K_H · x (Henry's Law)",
                        "ΔG° = -nFE°_cell (Nernst Relation)",
                        "λ_m = λ°_m - A√C (Kohlrausch Law)"
                    ),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "The Arrhenius equation quantitatively relates the rate constant k to activation energy Ea and absolute temperature T."
                ),
                Question(
                    id = "q2", qNo = 2,
                    question = "Assertion (A): Boiling point of an azeotropic mixture remains constant during distillation.\nReason (R): The liquid and vapour phases have the identical composition at the azeotropic temperature.",
                    options = listOf(
                        "Both (A) and (R) are true and (R) is correct explanation of (A)",
                        "Both (A) and (R) are true but (R) is not correct explanation",
                        "(A) is false but (R) is true",
                        "(A) is true but (R) is false"
                    ),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Azeotropes are binary mixtures having the same composition in liquid and vapour phase and boil at a constant temperature without fractional separation."
                ),
                Question(
                    id = "q3", qNo = 3,
                    question = "In organic transformations of $chapterName, which reagent is universally preferred for nucleophilic substitution / conversion without rearrangement?",
                    options = listOf("Thionyl Chloride (SOCl₂) with Pyridine", "Concentrated H₂SO₄ at 443 K", "Aqueous KMnO₄", "Alkaline K₂Cr₂O₇"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "SOCl₂ yields gaseous byproducts (SO₂ and HCl) that escape into the atmosphere, leaving pure alkyl halide (Darzens procedure)."
                ),
                Question(
                    id = "q4", qNo = 4,
                    question = "According to Crystal Field Theory in $chapterName, the splitting energy Δ_o is greater than pairing energy P when the ligand is:",
                    options = listOf("Strong field ligand (e.g., CN⁻, CO)", "Weak field ligand (e.g., I⁻, Br⁻)", "Zero field inert solvent", "Independent of ligand nature"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Strong field ligands produce large crystal field splitting (Δ_o > P), causing electrons to pair up in t_2g orbitals (low-spin complex)."
                ),
                Question(
                    id = "q5", qNo = 5,
                    question = "Which unit operation / rule governs the direction of addition of unsymmetrical HX to an unsymmetrical alkene?",
                    options = listOf("Markovnikov's Rule", "Hund's Multiplicity Rule", "Le Chatelier's Principle", "Graham's Law of Diffusion"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Markovnikov's rule states that the negative part of the addendum attaches to the carbon possessing fewer hydrogen atoms via the more stable carbocation intermediate."
                )
            )
            chapterId.startsWith("math") -> listOf(
                Question(
                    id = "q1", qNo = 1,
                    question = "In $chapterName, if a function f: A → B is both one-one (injective) and onto (surjective), then f is guaranteed to be:",
                    options = listOf("Bijective and Invertible", "Strictly decreasing everywhere", "Discontinuous at origin", "Constant throughout B"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "A function is invertible if and only if it is a bijection (both injective and surjective)."
                ),
                Question(
                    id = "q2", qNo = 2,
                    question = "The value of determinant |A| for an orthogonal square matrix A of order 3 satisfies:",
                    options = listOf("|A| = ±1", "|A| = 0", "|A| = 3", "|A| = ∞"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "For an orthogonal matrix, A · A^T = I. Taking determinants, |A| · |A^T| = |A|² = |I| = 1, which implies |A| = ±1."
                ),
                Question(
                    id = "q3", qNo = 3,
                    question = "Evaluation of definite integral ∫_{-a}^{a} f(x) dx when f(x) is an odd function f(-x) = -f(x) yields:",
                    options = listOf("0", "2 ∫_0^a f(x) dx", "a · f(a)", "1"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "By standard CBSE Board property P7 of definite integrals, the integral of any odd function over symmetric limits [-a, a] is identically 0."
                ),
                Question(
                    id = "q4", qNo = 4,
                    question = "Shortest distance between two skew lines r = a1 + λ b1 and r = a2 + μ b2 is given by:",
                    options = listOf(
                        "| (a2 - a1) · (b1 x b2) | / | b1 x b2 |",
                        "| (a2 + a1) x (b1 · b2) |",
                        "| a2 - a1 | · | b1 x b2 |",
                        "| b1 x b2 | / | a2 - a1 |"
                    ),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "The shortest distance between non-parallel skew lines is the projection of (a2 - a1) along the unit normal (b1 x b2) / |b1 x b2|."
                ),
                Question(
                    id = "q5", qNo = 5,
                    question = "If events A and B are independent with P(A) > 0 and P(B) > 0, then P(A ∩ B) equals:",
                    options = listOf("P(A) · P(B)", "P(A) + P(B)", "P(A) / P(B)", "P(A) - P(B)"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "By the multiplication theorem for independent events in CBSE Class 12 Probability, P(A ∩ B) = P(A) · P(B)."
                )
            )
            chapterId.startsWith("bio") -> listOf(
                Question(
                    id = "q1", qNo = 1,
                    question = "In $chapterName, double fertilization is a unique phenomenon characteristic of:",
                    options = listOf("Angiosperms (Flowering plants)", "Gymnosperms", "Bryophytes", "Pteridophytes"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Double fertilization involving syngamy (producing 2n zygote) and triple fusion (producing 3n PEN) occurs exclusively in angiosperms."
                ),
                Question(
                    id = "q2", qNo = 2,
                    question = "According to the Central Dogma of Molecular Biology in $chapterName, genetic flow proceeds as:",
                    options = listOf("DNA → mRNA → Protein", "Protein → RNA → DNA", "mRNA → DNA → tRNA", "Lipid → Protein → DNA"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Francis Crick proposed the Central Dogma: DNA replicates, transcribes to mRNA, and translates into functional polypeptide chains."
                ),
                Question(
                    id = "q3", qNo = 3,
                    question = "The enzyme responsible for cutting DNA at specific palindromic recognition sequences in genetic engineering is:",
                    options = listOf("Restriction Endonuclease", "DNA Ligase", "RNA Polymerase", "Alkaline Phosphatase"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Restriction endonucleases (molecular scissors) identify specific palindrome sequences and create sticky or blunt ends for recombinant DNA technology."
                ),
                Question(
                    id = "q4", qNo = 4,
                    question = "In the human immune response, cell-mediated immunity (CMI) is primarily coordinated by:",
                    options = listOf("T-lymphocytes", "B-lymphocytes", "Erythrocytes", "Platelets"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "T-cells (thymus-derived) mediate cell-mediated immunity and are responsible for graft rejection and destruction of intracellular pathogens."
                ),
                Question(
                    id = "q5", qNo = 5,
                    question = "Which ecological principle states that two closely related species competing for the same limiting resources cannot coexist indefinitely?",
                    options = listOf("Gause's Competitive Exclusion Principle", "Allen's Rule", "Bergmann's Rule", "Hardy-Weinberg Equilibrium"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Gause's competitive exclusion principle states that competitively inferior species are eventually eliminated if resources are limiting."
                )
            )
            else -> listOf(
                Question(
                    id = "q1", qNo = 1,
                    question = "In CBSE Class 12 board preparations for $chapterName, what is the maximum recommended weightage of NCERT back-exercise questions?",
                    options = listOf("Over 70% direct and concept-linked questions", "Less than 10%", "Only 25%", "None"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Official CBSE examination guidelines consistently formulate over 70% of board paper questions directly or concept-analogously from NCERT textbooks."
                ),
                Question(
                    id = "q2", qNo = 2,
                    question = "Which revision cadence best protects against memory decay leading into CBSE Board Exams 2027?",
                    options = listOf("Spaced review on Day 1, Day 3, and Day 7", "Cramming the night before", "Reading once and not reviewing", "Only watching videos without solving MCQs"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "Spaced repetition intervals consolidate synaptic pathways and ensure permanent long-term memory retrieval during 3-hour board exams."
                ),
                Question(
                    id = "q3", qNo = 3,
                    question = "What strategy guarantees maximum step marks in CBSE Class 12 theory papers?",
                    options = listOf("Writing the relevant formula, defining variables, substituting with units, and boxing the answer", "Writing only the final numerical value", "Skipping step descriptions", "Using non-standard abbreviations"),
                    correctOptionIndex = 0,
                    correctAnswerLetter = "A",
                    explanation = "CBSE evaluators follow strict step-marking schemes allocating 0.5 to 1 mark for each clearly stated formula and unit."
                )
            )
        }

        return Quiz(
            id = cleanName.removeSuffix(".json"),
            subject = subjectName,
            book = "CBSE Class 12 Boards 2027",
            chapter = chapterName,
            title = "$chapterName — Board DPP (CBSE 2027)",
            sourcePdf = "Official NCERT 2027 Curriculum",
            totalQuestions = questions.size,
            questions = questions
        )
    }

    fun getPhysicsDerivations(): List<DerivationItem> {
        val remote = manifestManager.getDerivations()
        if (remote.isNotEmpty()) {
            return remote.map {
                DerivationItem(
                    id = it.id,
                    chapterId = it.chapterId,
                    chapterName = it.chapterName,
                    title = it.title,
                    formulaStatement = it.formula,
                    keySteps = it.steps,
                    probabilityRating = it.importance
                )
            }
        }
        return getBaselinePhysicsDerivations()
    }

    fun getBaselinePhysicsDerivations(): List<DerivationItem> {
        return listOf(
            DerivationItem("d1", "phy_01", "Electric Charges & Fields", "Electric Field on Axial Line of an Electric Dipole", "E_{axial} = \\frac{1}{4\\pi\\varepsilon_0} \\frac{2pr}{(r^2 - a^2)^2} \\approx \\frac{2p}{4\\pi\\varepsilon_0 r^3}", listOf("Place dipole -q and +q separated by 2a", "Calculate field due to -q and +q at point P distance r from centre", "Take vector difference: E = E_+q - E_-q", "Apply binomial approximation for short dipole r >> a"), "Very High"),
            DerivationItem("d2", "phy_01", "Electric Charges & Fields", "Electric Field on Equatorial Line of Dipole", "E_{eq} = \\frac{1}{4\\pi\\varepsilon_0} \\frac{p}{(r^2 + a^2)^{3/2}} \\approx \\frac{p}{4\\pi\\varepsilon_0 r^3}", listOf("Draw symmetric triangle with charges at -a and +a, P at height r", "Decompose fields into cos theta (axial) and sin theta (normal) components", "Notice sin theta components cancel completely", "Sum cos theta components: E = 2 E_1 \\cos\\theta"), "Very High"),
            DerivationItem("d3", "phy_01", "Electric Charges & Fields", "Gauss's Theorem: Field due to Infinitely Long Straight Wire", "E = \\frac{\\lambda}{2\\pi\\varepsilon_0 r}", listOf("Choose coaxial cylindrical Gaussian surface of radius r and length L", "Flux through flat circular ends is zero (E perpendicular to area vector)", "Curved surface flux = E \\times (2\\pi r L)", "By Gauss's Law: E (2\\pi r L) = \\frac{\\lambda L}{\\varepsilon_0} \\Rightarrow E = \\frac{\\lambda}{2\\pi\\varepsilon_0 r}"), "Very High"),
            DerivationItem("d4", "phy_01", "Electric Charges & Fields", "Gauss's Theorem: Field due to Uniformly Charged Infinite Plane Sheet", "E = \\frac{\\sigma}{2\\varepsilon_0}", listOf("Construct cylindrical pillbox perpendicular to sheet of cross-section A", "Curved surface contributes zero flux (E parallel to surface)", "Total flux through two circular ends = 2 E A", "Charge enclosed q = \\sigma A \\Rightarrow 2 E A = \\frac{\\sigma A}{\\varepsilon_0} \\Rightarrow E = \\frac{\\sigma}{2\\varepsilon_0}"), "Very High"),
            DerivationItem("d5", "phy_02", "Electrostatic Potential & Capacitance", "Capacitance of Parallel Plate Capacitor with Dielectric Slab", "C = \\frac{\\varepsilon_0 A}{d - t + \\frac{t}{K}}", listOf("Original uniform field in air E0 = sigma / epsilon0", "Dielectric slab of thickness t reduces field inside to E = E0 / K", "Potential diff V = E0 (d - t) + E t = E0 [d - t + t/K]", "Use C = Q / V to obtain final formula"), "High"),
            DerivationItem("d6", "phy_03", "Current Electricity", "Relation between Current and Drift Velocity & Ohm's Law Deduction", "I = n e A v_d \\quad \\text{and} \\quad \\rho = \\frac{m}{n e^2 \\tau}", listOf("Volume of conductor of length l is V = A l", "Total mobile charge in conductor Q = n e A l", "Time taken by electrons to drift through t = l / v_d", "Current I = Q / t = n e A v_d", "Substitute v_d = \\frac{e E \\tau}{m} to deduce Ohm's law V = I R"), "Very High"),
            DerivationItem("d7", "phy_04", "Moving Charges & Magnetism", "Biot-Savart Law: Magnetic Field on Axis of Circular Current Loop", "B_{axis} = \\frac{\\mu_0 I R^2}{2(R^2 + x^2)^{3/2}}", listOf("Take current element dl on ring perimeter of radius R", "dB = \\frac{\\mu_0}{4\\pi} \\frac{I dl}{r^2} where r = \\sqrt{R^2 + x^2}", "Resolve dB into dB_x (along axis) and dB_perp (perpendicular to axis)", "dB_perp cancels by symmetry; integrate dB_x = dB \\sin\\theta over full circle"), "Very High"),
            DerivationItem("d8", "phy_04", "Moving Charges & Magnetism", "Force Between Two Parallel Current-Carrying Conductors", "\\frac{F}{L} = \\frac{\\mu_0 I_1 I_2}{2\\pi d}", listOf("Field due to conductor 1 at conductor 2: B1 = \\frac{\\mu_0 I_1}{2\\pi d}", "Force on length L of conductor 2: F2 = I_2 L B_1 \\sin(90^\\circ)", "F2 = \\frac{\\mu_0 I_1 I_2 L}{2\\pi d}", "Like currents attract; opposite currents repel (Official CBSE 1A definition)"), "Very High"),
            DerivationItem("d9", "phy_06", "Electromagnetic Induction", "Motional EMF Induced in a Rod Moving in Magnetic Field", "\\mathcal{E} = B v l", listOf("Consider conducting rod PQ of length l moving with velocity v in field B", "Magnetic Lorentz force on charge q is F = q (v \\times B)", "Work done moving charge along rod W = F l = q v B l", "Induced EMF \\mathcal{E} = W / q = B v l"), "High"),
            DerivationItem("d10", "phy_06", "Electromagnetic Induction", "Mutual Inductance of Two Co-axial Long Solenoids", "M = \\mu_0 n_1 n_2 \\pi r_1^2 l", listOf("Inner solenoid 1 has radius r1, turns n1; outer solenoid 2 has n2", "Current I2 in outer solenoid produces uniform field B2 = \\mu_0 n_2 I_2", "Magnetic flux linked with solenoid 1: \\Phi_1 = N_1 (B_2 A_1) = (n_1 l)(\\mu_0 n_2 I_2)(\\pi r_1^2)", "By definition \\Phi_1 = M I_2 \\Rightarrow M = \\mu_0 n_1 n_2 \\pi r_1^2 l"), "High"),
            DerivationItem("d11", "phy_07", "Alternating Current", "Series LCR Circuit: Impedance & Resonant Frequency", "Z = \\sqrt{R^2 + (X_L - X_C)^2} \\quad \\text{and} \\quad f_r = \\frac{1}{2\\pi\\sqrt{LC}}", listOf("Draw phasor diagram with current phasor I_0 along x-axis", "V_R is in phase with I, V_L leads by 90°, V_C lags by 90°", "Resultant voltage V_0^2 = V_R^2 + (V_L - V_C)^2 = I_0^2 [R^2 + (X_L - X_C)^2]", "At resonance X_L = X_C \\Rightarrow \\omega L = \\frac{1}{\\omega C} \\Rightarrow \\omega_r = \\frac{1}{\\sqrt{LC}}"), "Very High"),
            DerivationItem("d12", "phy_09", "Ray Optics", "Lens Maker's Formula for a Thin Convex Lens", "\\frac{1}{f} = (\\mu - 1)\\left(\\frac{1}{R_1} - \\frac{1}{R_2}\\right)", listOf("Refraction at first surface (radius R1): \\frac{\\mu}{v_1} - \\frac{1}{u} = \\frac{\\mu - 1}{R_1}", "Refraction at second surface (radius R2): \\frac{1}{v} - \\frac{\\mu}{v_1} = \\frac{1 - \\mu}{R_2}", "Add the two equations: \\frac{1}{v} - \\frac{1}{u} = (\\mu - 1)\\left(\\frac{1}{R_1} - \\frac{1}{R_2}\\right)", "For u = \\infty, v = f, yielding the Lens Maker's formula"), "Very High"),
            DerivationItem("d13", "phy_09", "Ray Optics", "Refraction Through a Triangular Prism & Minimum Deviation", "\\mu = \\frac{\\sin\\left(\\frac{A + D_m}{2}\\right)}{\\sin\\left(\\frac{A}{2}\\right)}", listOf("For prism: A = r1 + r2 and Deviation D = (i + e) - A", "At minimum deviation: i = e and r1 = r2 = r", "Then r = A / 2 and D_m = 2i - A \\Rightarrow i = \\frac{A + D_m}{2}", "Apply Snell's law \\mu = \\frac{\\sin i}{\\sin r} to get the prism equation"), "Very High"),
            DerivationItem("d14", "phy_10", "Wave Optics", "Huygens' Principle: Proof of Laws of Reflection & Refraction", "i = r \\quad \\text{and} \\quad \\frac{\\sin i}{\\sin r} = \\frac{v_1}{v_2} = \\mu", listOf("Incident plane wavefront AB hits reflecting/refracting boundary at angle i", "Secondary wavelet from A travels to C in time t (distance = v t)", "Draw envelope from B to contact point D", "Use congruent/similar right triangles to prove laws geometrically"), "Very High"),
            DerivationItem("d15", "phy_10", "Wave Optics", "Young's Double Slit Experiment: Fringe Width Expression", "\\beta = \\frac{\\lambda D}{d}", listOf("Path difference between waves from slits S1 and S2: \\Delta x = S_2 P - S_1 P", "Using geometry: (S_2 P)^2 - (S_1 P)^2 = 2 y d \\Rightarrow \\Delta x \\approx \\frac{y d}{D}", "Condition for bright fringes: \\Delta x = n \\lambda \\Rightarrow y_n = \\frac{n \\lambda D}{d}", "Fringe width \\beta = y_{n+1} - y_n = \\frac{\\lambda D}{d}"), "Very High"),
            DerivationItem("d16", "phy_12", "Atoms", "Bohr's Postulate: Radius and Energy of Orbit in Hydrogen Atom", "r_n = \\frac{n^2 h^2 \\varepsilon_0}{\\pi m e^2} \\quad \\text{and} \\quad E_n = -\\frac{13.6}{n^2} \\text{ eV}", listOf("Centripetal force = Electrostatic attraction: \\frac{m v^2}{r} = \\frac{1}{4\\pi\\varepsilon_0} \\frac{e^2}{r^2}", "Bohr's quantization postulate: m v r = \\frac{n h}{2\\pi} \\Rightarrow v = \\frac{n h}{2\\pi m r}", "Substitute v to solve for radius r_n \\propto n^2", "Total energy E = K + U = \\frac{1}{2} m v^2 - \\frac{e^2}{4\\pi\\varepsilon_0 r} = -\\frac{e^2}{8\\pi\\varepsilon_0 r} = -\\frac{13.6}{n^2} \\text{ eV}"), "Very High")
        )
    }

    fun getChemistryNamedReactions(): List<NamedReactionItem> {
        val remote = manifestManager.getNamedReactions()
        if (remote.isNotEmpty()) {
            return remote.map {
                NamedReactionItem(
                    id = it.id,
                    chapterName = it.chapter,
                    reactionName = it.title,
                    equation = it.equation,
                    reagents = it.reagents,
                    keyApplication = it.significance
                )
            }
        }
        return getBaselineChemistryNamedReactions()
    }

    fun getBaselineChemistryNamedReactions(): List<NamedReactionItem> {
        return listOf(
            NamedReactionItem("r1", "Haloalkanes & Haloarenes", "Sandmeyer Reaction", "Ar-N_2^+ X^- + Cu_2Cl_2 / HCl \\rightarrow Ar-Cl + N_2", "Cu2Cl2/HCl or Cu2Br2/HBr", "Key method to synthesize chlorobenzene and bromobenzene from aniline diazonium salts with high yield"),
            NamedReactionItem("r2", "Haloalkanes & Haloarenes", "Finkelstein Reaction", "R-X + NaI \\xrightarrow{dry\\,acetone} R-I + NaX \\downarrow", "NaI in dry acetone (acts as nucleophilic halide exchange)", "Halogen exchange specifically designed for synthesizing alkyl iodides; NaCl/NaBr precipitate drives equilibrium forward"),
            NamedReactionItem("r3", "Haloalkanes & Haloarenes", "Swarts Reaction", "R-Cl / R-Br + AgF \\rightarrow R-F + AgCl \\downarrow", "Metallic fluorides (AgF, Hg2F2, CoF2, SbF3)", "Best laboratory synthesis method for alkyl fluorides via metal fluoride exchange"),
            NamedReactionItem("r4", "Haloalkanes & Haloarenes", "Wurtz-Fittig & Fittig Reactions", "Ar-X + 2 Na + R-X \\xrightarrow{dry\\,ether} Ar-R + 2 NaX", "Sodium metal in dry ether", "Coupling of aryl halide with alkyl halide (Wurtz-Fittig) or two aryl halides (Fittig) to form biphenyl compounds"),
            NamedReactionItem("r5", "Alcohols, Phenols & Ethers", "Kolbe's Reaction", "Phenol + NaOH + CO_2 \\xrightarrow{400K, 4-7 atm} \\xrightarrow{H^+} Salicylic\\,Acid", "1. NaOH 2. CO2 gas (weak electrophile) 3. Dilute H2SO4/HCl", "Industrial conversion of phenol to 2-hydroxybenzoic acid (salicylic acid), precursor for Aspirin manufacture"),
            NamedReactionItem("r6", "Alcohols, Phenols & Ethers", "Reimer-Tiemann Reaction", "Phenol + CHCl_3 + aq\\,NaOH \\xrightarrow{\\Delta} \\xrightarrow{H^+} Salicylaldehyde", "Chloroform (CHCl3) + 3 NaOH (generates :CCl2 dichlorocarbene)", "Electrophilic formylation yielding ortho-salicylaldehyde as the major board product"),
            NamedReactionItem("r7", "Alcohols, Phenols & Ethers", "Williamson Ether Synthesis", "R-X + R'-O^- Na^+ \\rightarrow R-O-R' + NaX", "Primary alkyl halide + Sodium alkoxide (SN2 mechanism)", "Preparation of symmetrical and unsymmetrical ethers. Board rule: Alkyl halide MUST be 1° to prevent elimination"),
            NamedReactionItem("r8", "Aldehydes, Ketones & Carboxylic Acids", "Rosenmund Reduction", "R-COCl + H_2 \\xrightarrow{Pd / BaSO_4, \\, S \\, or \\, quinoline} R-CHO + HCl", "Pd poisoned with BaSO4 and sulfur/quinoline", "Selective catalytic reduction of acyl chlorides to aldehydes without over-reducing to alcohols"),
            NamedReactionItem("r9", "Aldehydes, Ketones & Carboxylic Acids", "Stephen Reaction", "R-CN + SnCl_2 + HCl \\rightarrow R-CH=NH \\cdot HCl \\xrightarrow{H_3O^+} R-CHO", "SnCl2 + conc. HCl followed by steam hydrolysis", "Reduction of nitriles to imines followed by acidic hydrolysis to yield aldehydes"),
            NamedReactionItem("r10", "Aldehydes, Ketones & Carboxylic Acids", "Etard Reaction", "Toluene + CrO_2Cl_2 \\xrightarrow{CS_2} \\text{Chromium Complex} \\xrightarrow{H_3O^+} Benzaldehyde", "Chromyl chloride (CrO2Cl2) in CS2 or CCl4 solvent", "Direct controlled mild oxidation of methylbenzene (toluene) to benzaldehyde"),
            NamedReactionItem("r11", "Aldehydes, Ketones & Carboxylic Acids", "Gattermann-Koch Reaction", "Benzene + CO + HCl \\xrightarrow{anhyd.\\,AlCl_3 / CuCl} Benzaldehyde + HCl", "Carbon monoxide + HCl in presence of anhydrous AlCl3", "Industrial formylation of benzene ring directly to benzaldehyde"),
            NamedReactionItem("r12", "Aldehydes, Ketones & Carboxylic Acids", "Clemmensen Reduction", "\\text{>C=O} + 4 [H] \\xrightarrow{Zn-Hg \\, / \\, conc.\\,HCl} \\text{>CH}_2 + H_2O", "Zinc amalgam (Zn-Hg) and concentrated hydrochloric acid", "Reduction of carbonyl group of aldehydes and ketones into methylene group (-CH2-) under acidic conditions"),
            NamedReactionItem("r13", "Aldehydes, Ketones & Carboxylic Acids", "Wolff-Kishner Reduction", "\\text{>C=O} \\xrightarrow{NH_2NH_2} \\text{>C=N-NH}_2 \\xrightarrow{KOH / ethylene\\,glycol, \\Delta} \\text{>CH}_2 + N_2", "Hydrazine (NH2NH2) followed by heating with KOH in ethylene glycol", "Reduction of carbonyl group into methylene under strongly basic conditions (ideal for acid-sensitive substrates)"),
            NamedReactionItem("r14", "Aldehydes, Ketones & Carboxylic Acids", "Aldol Condensation & Cross Aldol", "2 CH_3CHO \\xrightarrow{dil.\\,NaOH} CH_3-CH(OH)-CH_2-CHO \\xrightarrow{\\Delta, -H_2O} CH_3-CH=CH-CHO", "Dilute NaOH or Ba(OH)2 + Heat", "Self or cross condensation of aldehydes/ketones possessing at least one alpha-hydrogen to form alpha,beta-unsaturated carbonyls"),
            NamedReactionItem("r15", "Aldehydes, Ketones & Carboxylic Acids", "Cannizzaro Reaction", "2 HCHO + conc.\\,KOH (50\\%) \\rightarrow CH_3OH + HCOOK", "Concentrated alkali (50% NaOH or KOH)", "Self oxidation-reduction (disproportionation) of aldehydes lacking alpha-hydrogens (e.g. Formaldehyde, Benzaldehyde)"),
            NamedReactionItem("r16", "Aldehydes, Ketones & Carboxylic Acids", "Hell-Volhard-Zelinsky (HVZ) Reaction", "R-CH_2-COOH + X_2 \\xrightarrow{red\\,P} \\xrightarrow{H_2O} R-CH(X)-COOH", "Halogen (Cl2 or Br2) in presence of small amount of red phosphorus", "Alpha-halogenation of aliphatic carboxylic acids containing an alpha-hydrogen"),
            NamedReactionItem("r17", "Amines", "Gabriel Phthalimide Synthesis", "\\text{Phthalimide} \\xrightarrow{KOH} \\xrightarrow{R-X} \\xrightarrow{aq.\\,NaOH} R-NH_2 \\text{ (pure 1°)}", "Phthalimide, ethanolic KOH, primary alkyl halide, aq. NaOH", "Best method for synthesizing pure primary aliphatic amines without secondary or tertiary contamination. Aryl halides do not react"),
            NamedReactionItem("r18", "Amines", "Hoffmann Bromamide Degradation", "R-CONH_2 + Br_2 + 4 NaOH \\rightarrow R-NH_2 + Na_2CO_3 + 2 NaBr + 2 H_2O", "Bromine in aqueous or ethanolic NaOH", "Step-down reaction: converts an acid amide into a primary amine with ONE LESS carbon atom"),
            NamedReactionItem("r19", "Amines", "Carbylamine Test (Isocyanide Test)", "R-NH_2 + CHCl_3 + 3 alc.\\,KOH \\xrightarrow{\\Delta} R-NC + 3 KCl + 3 H_2O", "Chloroform and alcoholic KOH with heat", "Diagnostic chemical test for primary aliphatic and aromatic amines; releases an intolerable foul odor (isocyanide)"),
            NamedReactionItem("r20", "Amines", "Hinsberg Test for 1°, 2°, 3° Amines", "C_6H_5SO_2Cl + R-NH_2 \\rightarrow C_6H_5SO_2NHR \\xrightarrow{NaOH} \\text{Soluble salt}", "Benzenesulfonyl chloride (Hinsberg's reagent) + alkali", "Distinguishes 1° amine (ppt soluble in alkali), 2° amine (ppt insoluble in alkali), 3° amine (does not react)")
        )
    }

    // Room DB helper methods
    fun getMastery(chapterId: String): Flow<ChapterMasteryEntity?> = db.masteryDao().getMastery(chapterId)
    fun getAllMastery(): Flow<List<ChapterMasteryEntity>> = db.masteryDao().getAllMastery()

    suspend fun saveMastery(entity: ChapterMasteryEntity) = withContext(Dispatchers.IO) {
        db.masteryDao().saveMastery(entity)
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncChapterMastery(entity)
        } catch (_: Exception) {}
    }

    fun getVideoProgress(videoId: String): Flow<VideoProgressEntity?> = db.videoProgressDao().getProgress(videoId)

    suspend fun saveVideoProgress(entity: VideoProgressEntity) = withContext(Dispatchers.IO) {
        db.videoProgressDao().saveProgress(entity)
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncVideoProgress(entity)
        } catch (_: Exception) {}
    }

    suspend fun recordQuizAttempt(quizId: String, score: Int, total: Int, timeSecs: Long) = withContext(Dispatchers.IO) {
        val entity = QuizAttemptEntity(
            quizId = quizId,
            score = score,
            totalQuestions = total,
            timeTakenSeconds = timeSecs
        )
        db.quizDao().saveAttempt(entity)
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncQuizAttempt(entity)
        } catch (_: Exception) {}
    }

    fun getAllMastered(): Flow<List<com.boardsprep.onboard.data.local.entities.MasteredItemEntity>> = db.handbookDao().getAllMastered()

    suspend fun setMastered(itemId: String, category: String, isMastered: Boolean) = withContext(Dispatchers.IO) {
        val entity = com.boardsprep.onboard.data.local.entities.MasteredItemEntity(itemId = itemId, category = category, isMastered = isMastered)
        if (isMastered) {
            db.handbookDao().setMastered(entity)
        } else {
            db.handbookDao().removeMastered(itemId)
        }
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncMasteredItem(entity, isMastered)
        } catch (_: Exception) {}
    }

    fun getAllDownloads(): Flow<List<com.boardsprep.onboard.data.local.entities.DownloadedFileEntity>> = db.downloadsDao().getAllDownloads()

    suspend fun deleteDownload(id: String, localPath: String) = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(localPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        db.downloadsDao().deleteDownload(id)
    }

    fun getLectureContext(lectureId: String, title: String): LectureContext? {
        val subjects = getSubjects()
        for (subj in subjects) {
            val chapters = subj.chapters
            for (i in chapters.indices) {
                val chap = chapters[i]
                val vIndex = chap.videosList.indexOfFirst {
                    it.id == lectureId || it.title.equals(title, ignoreCase = true)
                }
                val isMatch = vIndex != -1 ||
                        chap.name.contains(title, ignoreCase = true) ||
                        title.contains(chap.name, ignoreCase = true)
                if (isMatch) {
                    val currLecture = if (vIndex != -1) chap.videosList[vIndex] else chap.videosList.firstOrNull()
                    val nextLecture = if (vIndex != -1 && vIndex + 1 < chap.videosList.size) chap.videosList[vIndex + 1] else null
                    val nextChapter = if (i + 1 < chapters.size) chapters[i + 1] else null
                    val prevChapter = if (i > 0) chapters[i - 1] else null
                    return LectureContext(
                        currentSubject = subj,
                        currentChapter = chap,
                        currentLecture = currLecture,
                        nextLecture = nextLecture,
                        nextChapter = nextChapter,
                        prevChapter = prevChapter
                    )
                }
            }
        }
        return null
    }

    // ==========================================
    // Error Vault ("Mistake Notebook") Operations
    // ==========================================
    val errorVaultDao get() = db.errorVaultDao()

    fun getUnresolvedErrors(): Flow<List<com.boardsprep.onboard.data.local.entities.ErrorVaultEntity>> =
        db.errorVaultDao().getUnresolvedErrors()

    fun getAllErrors(): Flow<List<com.boardsprep.onboard.data.local.entities.ErrorVaultEntity>> =
        db.errorVaultDao().getAllErrors()

    fun getUnresolvedErrorCount(): Flow<Int> =
        db.errorVaultDao().getUnresolvedCount()

    fun getResolvedErrorCount(): Flow<Int> =
        db.errorVaultDao().getResolvedCount()

    suspend fun saveQuizMistake(
        questionId: String,
        chapterId: String = "",
        subjectId: String = "",
        questionText: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        correctOptionIndex: Int,
        userSelectedOptionIndex: Int,
        explanation: String = "",
        mistakeCategory: String = "conceptual"
    ) = withContext(Dispatchers.IO) {
        val entity = com.boardsprep.onboard.data.local.entities.ErrorVaultEntity(
            questionId = questionId,
            chapterId = chapterId,
            subjectId = subjectId,
            questionText = questionText,
            optionA = optionA,
            optionB = optionB,
            optionC = optionC,
            optionD = optionD,
            correctOptionIndex = correctOptionIndex,
            userSelectedOptionIndex = userSelectedOptionIndex,
            explanation = explanation,
            mistakeCategory = mistakeCategory,
            isResolved = false,
            lastAttemptedAt = System.currentTimeMillis()
        )
        db.errorVaultDao().insertError(entity)
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncQuizMistake(entity)
        } catch (_: Exception) {}
    }

    suspend fun resolveMistake(id: Long) = withContext(Dispatchers.IO) {
        val mistake = db.errorVaultDao().getAllErrorsList().firstOrNull { it.id == id }
        db.errorVaultDao().markResolved(id)
        if (mistake != null) {
            try {
                com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncMistakeResolved(mistake.questionId)
            } catch (_: Exception) {}
        }
    }

    suspend fun reattemptMistakeFailed(id: Long) = withContext(Dispatchers.IO) {
        db.errorVaultDao().recordReattemptFailure(id)
    }

    suspend fun deleteMistake(id: Long) = withContext(Dispatchers.IO) {
        db.errorVaultDao().deleteError(id)
    }

    // ==========================================
    // Spaced Repetition Blitz (Ebbinghaus SM-2) Operations
    // ==========================================
    val spacedReviewDao get() = db.spacedReviewDao()

    fun getDueReviews(limit: Int = 5): Flow<List<com.boardsprep.onboard.data.local.entities.SpacedReviewEntity>> =
        db.spacedReviewDao().getDueReviews(System.currentTimeMillis() + 86400000L, limit)

    suspend fun updateSpacedReview(entity: com.boardsprep.onboard.data.local.entities.SpacedReviewEntity, knewIt: Boolean) = withContext(Dispatchers.IO) {
        val newRepetitions = if (knewIt) entity.repetitions + 1 else 0
        val newEaseFactor = if (knewIt) {
            (entity.easeFactor + 0.1f).coerceIn(1.3f, 2.8f)
        } else {
            (entity.easeFactor - 0.2f).coerceAtLeast(1.3f)
        }
        val nextIntervalDays = when {
            !knewIt -> 1
            newRepetitions == 1 -> 1
            newRepetitions == 2 -> 3
            newRepetitions == 3 -> 6
            else -> (entity.intervalDays * newEaseFactor).toInt().coerceAtLeast(7)
        }
        val nextDate = System.currentTimeMillis() + nextIntervalDays * 86400000L
        val updated = entity.copy(
            repetitions = newRepetitions,
            easeFactor = newEaseFactor,
            intervalDays = nextIntervalDays,
            nextReviewDate = nextDate,
            lastReviewedAt = System.currentTimeMillis()
        )
        db.spacedReviewDao().updateReview(updated)
        try {
            com.boardsprep.onboard.core.sync.FirebaseSyncManager.getInstance(context).syncSpacedReview(updated)
        } catch (_: Exception) {}
    }

    suspend fun seedSpacedReviewsIfEmpty() = withContext(Dispatchers.IO) {
        if (db.spacedReviewDao().getCount() == 0) {
            val defaultDeck = listOf(
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "phy_lens_maker",
                    subjectId = "physics",
                    title = "Lens Maker's Formula",
                    prompt = "State the Lens Maker's Formula for a thin convex lens in terms of refractive index and radii of curvature.",
                    answer = "1/f = (μ - 1) * [ (1/R₁) - (1/R₂) ]\nNote: For equiconvex lens of index μ=1.5, f = R.",
                    category = "formula"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "chem_reimer_tiemann",
                    subjectId = "chemistry",
                    title = "Reimer-Tiemann Reaction",
                    prompt = "What is the product and electrophile when Phenol reacts with CHCl₃ + aq. NaOH?",
                    answer = "Product: Salicylaldehyde (o-hydroxybenzaldehyde).\nActive Electrophile: Dichlorocarbene (:CCl₂).",
                    category = "name_reaction"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "phy_ampere_maxwell",
                    subjectId = "physics",
                    title = "Ampere-Maxwell Law",
                    prompt = "Write the generalized Ampere-Maxwell circulating loop equation including displacement current.",
                    answer = "∮ B · dl = μ₀(Ic + Id) = μ₀[Ic + ε₀(dΦ_E/dt)]\nWhere Id = ε₀(dΦ_E/dt) is the displacement current.",
                    category = "formula"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "math_std_integral",
                    subjectId = "maths",
                    title = "Special Integral: √(a² - x²)",
                    prompt = "Evaluate ∫ √(a² - x²) dx without limits.",
                    answer = "(x/2)√(a² - x²) + (a²/2)sin⁻¹(x/a) + C",
                    category = "formula"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "bio_dihybrid_cross",
                    subjectId = "biology",
                    title = "Mendelian Dihybrid Ratio",
                    prompt = "State the phenotypic and genotypic ratios of Mendel's F2 dihybrid cross (Yellow Round x Green Wrinkled).",
                    answer = "Phenotypic: 9:3:3:1\nGenotypic: 1:2:2:4:1:2:1:2:1 (9 distinct genotypes).",
                    category = "definition"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "chem_aldol",
                    subjectId = "chemistry",
                    title = "Aldol Condensation Criteria",
                    prompt = "What structural feature is strictly required in aldehydes/ketones to undergo self-Aldol condensation?",
                    answer = "Must contain at least one α-hydrogen atom (e.g., Acetaldehyde, Acetone). Formaldehyde and Benzaldehyde lack α-H and undergo Cannizzaro reaction instead.",
                    category = "name_reaction"
                ),
                com.boardsprep.onboard.data.local.entities.SpacedReviewEntity(
                    itemId = "phy_bohr_radii",
                    subjectId = "physics",
                    title = "Bohr Radius of nth Orbit",
                    prompt = "State how the radius r_n of an electron in a hydrogen-like atom scales with principal quantum number n and atomic number Z.",
                    answer = "r_n ∝ (n² / Z)\nr_n = 0.529 Å * (n² / Z). Radius scales with n squared!",
                    category = "formula"
                )
            )
            db.spacedReviewDao().insertReviews(defaultDeck)
        }
    }

    // ==========================================
    // Focus Session Chamber Operations
    // ==========================================
    val focusSessionDao get() = db.focusSessionDao()

    fun getRecentFocusSessions(): Flow<List<com.boardsprep.onboard.data.local.entities.FocusSessionEntity>> =
        db.focusSessionDao().getRecentSessions()

    suspend fun recordFocusSession(
        sessionType: String,
        targetSubjectId: String,
        durationMinutes: Int,
        completedMinutes: Int,
        wasInterrupted: Boolean
    ) = withContext(Dispatchers.IO) {
        val session = com.boardsprep.onboard.data.local.entities.FocusSessionEntity(
            sessionType = sessionType,
            targetSubjectId = targetSubjectId,
            durationMinutes = durationMinutes,
            completedMinutes = completedMinutes,
            wasInterrupted = wasInterrupted,
            timestamp = System.currentTimeMillis()
        )
        db.focusSessionDao().recordSession(session)
    }
}


data class LectureContext(
    val currentSubject: Subject,
    val currentChapter: Chapter,
    val currentLecture: Lecture?,
    val nextLecture: Lecture?,
    val nextChapter: Chapter?,
    val prevChapter: Chapter?
)

