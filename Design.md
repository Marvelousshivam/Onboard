# OnBOARD Design System & Anti-AI-Slop Specification

> **Version:** 1.2.0  
> **Target Audience:** All engineers, designers, and AI agents contributing to OnBOARD.  
> **Primary Rule:** The user's focus and screen real-estate are sacred. Every pixel must serve student mastery, clarity, and speed. Zero decorative filler. Zero AI slop.

---

## 1. Core Philosophy: The High-Yield Student Workspace

OnBOARD is built for CBSE Class 12 board aspirants in their final sprint. Students preparing for 70-mark and 80-mark board papers are under intense time pressure and cognitive load. The UI must feel like a precision instrument:

- **Distraction-Free Focus**: High-contrast, dark-mode first (AMOLED), eliminating visual noise and gratuitous fluff.
- **Academic Grounding**: Every label, badge, formula, and rubric strictly calibrated to official CBSE 2026–27 blueprints.
- **Tactile Responsiveness (`/genjutsu`)**: Physics-driven spring animations, zero lag, smooth transitions, and instant feedback.
- **Respect for Screen Space**: The student must be able to read long question passages, formulas, and diagrams without fighting cramped viewports or bloated headers.

---

## 2. Anti-AI-Slop Code of Conduct (The Anti-Tells Framework)

AI models have distinct reflexive defaults ("tells") when designing UI. Any AI assistant or developer working on OnBOARD **must strictly avoid** these patterns:

### 🚫 Tell 1: Double Glyphing & Emoji Stacking
- **The Violation**: Mixing 3D system emojis (`🔒`, `🚀`, `🔥`, `🗝️`, `⚡`, `🚨`, `✨`) right next to flat Material vector icons, or stacking duplicate emojis (e.g., `✨ ✨ Ask AI / Hint`, `⚡ 5-MIN DRILL`, `🚨 1 to Review`).
- **The Rule**: **Purge all emojis from UI components completely.**
  - Rely exclusively on single, purposeful Material 3 vector symbols (`Icons.Default.AutoAwesome`, `Icons.Default.Bolt`, `Icons.Default.AutoFixHigh`, `Icons.Default.CheckCircle`).
  - No emojis in buttons, badges, navigation pills, headers, or drill tags.
  - Emojis are reserved strictly for celebratory confetti screens or streak milestones, never in UI chrome.

### 🚫 Tell 2: Hollow & Invented Copy
- **The Violation**: Generic marketing slogans ("*Empower your academic journey with AI-driven revolutionary mastery!*").
- **The Rule**: Every string must describe a specific, concrete feature or CBSE curricular fact:
  - *Good*: "CBSE 2026-27 Blueprint Calibrated • Code 042 • 70 Marks"
  - *Bad*: "Supercharge your physics exam preparation with smart tools!"

### 🚫 Tell 3: "The Tiny Viewport" (Header Bloat)
- **The Violation**: Stacking 5 to 7 static bars above the content (Top App Bar + Subject Tabs + Mastery Card + PDF Banner + Search Bar + Marks Filter + Status Filter), leaving only 200dp of visible vertical height for questions.
- **The Rule**:
  - At least **70% of the visible viewport** must belong to the active study content.
  - Large promotional banners and Mastery summary cards **must live inside the `LazyColumn` as scrollable `item {}` blocks**, scrolling out of the way immediately.
  - Search bars must be sleek, single-line (42dp–44dp height), with auto-hiding headers when the software keyboard (`WindowInsets.isImeVisible`) opens.
  - Consolidate horizontal filters into a single row using dropdown menus for secondary dimensions (e.g., `[All Chapters ▾] | 2M | 3M | 4M | 5M | [Status: All ▾]`).

### 🚫 Tell 4: Text Truncation & Letter Slicing
- **The Violation**: Subtitles that wrap onto two lines and crash into banners below them (slicing off descending letters like `g`, `y`, `p`, `j`), or labels cut off mid-parenthesis (e.g. `Sec A (/20)`).
- **The Rule**:
  - Always set `maxLines = 1` and `overflow = TextOverflow.Ellipsis` on toolbar headers, subtitles, and stat tiles.
  - In input fields, put secondary information (like max marks) inside the field as a muted placeholder (`/20`), keeping top labels strictly clean (`Sec A`, `Sec B`).
  - Ensure parent containers have adequate vertical padding (`PaddingValues` / `contentPadding`) so text ascenders and descenders never clip.

### 🚫 Tell 5: Raw Scrape & OCR Artifacts
- **The Violation**: Displaying scraped text with hardcoded mid-sentence `\n` line breaks, OCR spacing typos (`M . Hamel` instead of `M. Hamel`, `fir st` instead of `first`), or stray mark values.
- **The Rule**:
  - All text from CBSE PDFs must pass through sanitization before display:
    - Merge single newlines within paragraphs while preserving genuine double newlines.
    - Fix common OCR character splits.
    - Render choice questions with structured visual dividers (`[ OR (Alternative Choice) ]` pill badge) rather than dumping raw markdown text.

### 🚫 Tell 6: Asymmetric & Right-Aligned Action Buttons
- **The Violation**: Modal dialogs with unevenly sized buttons right-aligned in a tiny corner, or mismatched borders (e.g. thick dark-gray outline next to a flat green button).
- **The Rule**:
  - Mobile modals and bottom sheets must use **full-width stacked buttons** (`fillMaxWidth()`, primary filled button on top, clean text button on bottom), OR **balanced 50%/50% side-by-side rows**.
  - All primary CTA buttons must have minimum 48dp height with 14dp–16dp corner radius.

---

## 3. Design Tokens & Palette Architecture

OnBOARD uses an **AMOLED-First M3 Expressive** color system:

| Token | Hex / Value | Semantic Role |
|:---|:---|:---|
| `md_theme_dark_background` | `#08080C` | Deepest AMOLED canvas background |
| `md_theme_dark_surface` | `#10111A` | Bento tiles, cards, bottom sheets |
| `md_theme_dark_surfaceVariant` | `#181926` | Elevated cards, chip containers, input backgrounds |
| `md_theme_dark_primary` | `#6366F1` | Brand Indigo: Primary action buttons, active tabs |
| `md_theme_dark_primaryContainer`| `#2C2B54` | Active pill selection, subtle container tints |
| `AmberAccent` | `#FBBF24` | High-Yield badges, CBSE blueprint warnings |
| `EmeraldGreen` | `#10B981` / `#2E7D32` | Mastery achieved, full marks, installed updates |
| `ErrorRose` | `#EF4444` | Mistake Vault, retries needed, download failures |
| `GlassStroke` | `Color.White.copy(0.12f)` | Subtle hairline border for dark mode depth |

### Liquid Glass Mode (`LocalGlassEnabled`)
When enabled, OnBOARD applies dynamic backdrop blur and chromatic highlights using custom SkSL runtime shaders (`lightweightLiquidGlass`, `layerBackdrop`).  
**Fallback Rule**: If the device GPU or Android version does not support SkSL shaders, all surfaces must gracefully fallback to pure AMOLED `#10111A` with zero visual artifacts.

---

## 4. Typography Scale & Layout Rules

### Typography
- **Headlines (`HeadlineLarge`, `HeadlineMedium`)**: Bold, letter-spacing -0.5sp, used for subject titles and major scores.
- **Titles (`TitleMedium`, `TitleSmall`)**: Bold, clean line-height, used for chapter names and card headers.
- **Body (`BodyMedium`, `BodySmall`)**: High contrast (`#E2E8F0` on dark), minimum 18sp line-height for effortless reading of lengthy question passages.
- **Labels (`LabelSmall`, `LabelMedium`)**: Uppercase, letter-spacing +0.8sp, used for weightage tags (`2M (VSA)`, `5M (LONG)`).

### Touch Targets & Padding
- **Minimum Interactive Target**: `48.dp x 48.dp` (never smaller than `44.dp`).
- **Standard Card Padding**: `16.dp` outer padding, `12.dp` or `14.dp` inner padding.
- **Dock Clearance**: **All** scrollable lists (`LazyColumn`, `LazyVerticalGrid`, scrollable `Column`) must apply:
  ```kotlin
  contentPadding = PaddingValues(bottom = 110.dp)
  ```
  to guarantee content fully clears the floating navigation dock.

---

## 5. Motion Craft & Spring Physics (`/genjutsu`)

All animations in OnBOARD must feel tactile, physical, and weighted—never linear or robotic:

### 1. Spring Physics Specs
- **Modal Sheets & Dialogs**:
  ```kotlin
  spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
  ```
- **Navigation Dock & Elastic Pills**:
  ```kotlin
  // ClearPDF / USMusic-style rubber-band stretch & squash
  GlassSpring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
  ```
- **Download & Progress Meters**:
  ```kotlin
  val animatedProgress by animateFloatAsState(
      targetValue = progress,
      animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
      label = "progress_meter"
  )
  ```

### 2. High-Frame-Rate Discipline (60–120 FPS)
- **Zero Layout-Phase Recompositions**:
  - Never trigger recompositions on scroll by reading raw offsets inside composable bodies.
  - Use `Modifier.graphicsLayer { ... }` or lambda modifiers for translation, scale, and alpha transforms.
  - Wrap derived calculations inside `remember { derivedStateOf { ... } }`.
- **Haptic Tactility**:
  - Provide subtle haptic clicks (`HapticFeedbackType.TextHandleMove`) on tab switches, exam section transitions, and formula drawer expansions.

---

## 6. Academic Data Truth & CBSE Rules

1. **Curriculum Alignment**:
   - English Core (301): Strictly **3 Sections** (A: Reading, B: Creative Writing, C: Literature).
   - Physics (042) & Chemistry (043): Strictly **5 Sections** (A: MCQs, B: VSA 2M, C: SA 3M, D: Case 4M, E: Long 5M).
   - Mathematics (041): Strictly **5 Sections** (A: MCQs, B: 2M, C: 3M, D: 5M, E: Case 4M).
2. **Mastery Formula**:
   - Chapter mastery strictly requires all four study pillars: Theory Video, NCERT Reading, Exemplar Solutions, and PYQ Practice.
   - Partial completion shows truthfully as *"In Progress"*—never false 100% roundups.
3. **Reactive Math**:
   - Total scores in exam simulations and self-grading dialogs must always be the real-time sum of the active section inputs (`Score = A + B + C + ...`).

---

## 7. AI Socratic Assistant & Behavioral Nudge System (`/genjutsu` + `/agency-behavioral-nudge-engine`)

The AI features in OnBOARD serve as an elite, non-judgmental CBSE Class 12 Socratic Master Teacher. The UI must eliminate cognitive friction, prevent the "illusion of competence," and guide students toward deep conceptual mastery.

### 1. Liquid Glass AI Bottom Sheet (`LiquidGlassAiSheet`)
- **Visual Surface**: Uses `lightweightLiquidGlass` with specular rim highlights and dynamic backdrop blur over the active study screen.
- **Flat Elevation**: Must use `elevation = 0.dp` to prevent ugly dark drop-shadow blobbing against translucent layers.
- **Streaming Typewriter Effect**:
  - Live character/token streaming via OkHttp SSE Flow.
  - Streaming cursor: 2dp x 16dp pill with breathing alpha pulse (`animateFloat` 0.2f to 1.0f, infinite repeat).
- **Mathematical & Chemical Formula Rendering**:
  - Formulas MUST be rendered with KaTeX/LaTeX formatting ($...$ inline, $$...$$ block equations).
  - Clean font rendering matching `MathFormulaView`.
- **Grounding Citation Pills**:
  - When live Internet Search is active, citations appear as interactive squircle chips (`[🌐 CBSE Circular 2026-27]`, `[📖 NCERT Section 4.5]`).
- **Interactive Follow-Up Input Bar**:
  - Sleek 44dp height single-line input anchored to the bottom.
  - Quick action chips row above input: `[⚡ Simpler Analogy]`, `[🔁 Give Me 1 Twin Question]`, `[📊 CBSE Marking Rubric]`.
  - Haptic response on submit (`HapticFeedbackType.TextHandleMove`).

### 2. The 3-Part Diagnostic Card Architecture
When explaining DPP or Quiz questions, the AI output must NEVER be an unstructured wall of text. It must be formatted into three distinct, scannable cognitive cards:
1. **Core Concept & Proof** (Green accent tint, `M3EBentoTileShape`): Step-by-step mathematical proof or physical mechanism demonstrating why the correct option is true.
2. **Why Your Pick Was Wrong** (Amber/Rose accent tint): Identifies the exact cognitive trap, arithmetic slip, or misread premise that led to the student's chosen option.
3. **CBSE Board Exam Tip** (Indigo accent tint): High-yield examiner note on how this concept is tested in 2M/3M/5M board questions and how partial step-marks are awarded.

### 3. Pedagogical & Behavioral Rules (`/agency-psychologist`)
- **The Anti-Offloading Rule**: AI must never spoon-feed direct answers in active practice mode. Hints must guide retrieval:
  - *Tier 1 Hint*: Core physical/mathematical principle (e.g. *"Apply Lenz's law to determine induced EMF direction"*).
  - *Tier 2 Hint*: Formula setup & given variables.
  - *Tier 3 Solution*: Full step-wise marking breakdown (only after attempt).
- **Dual Mode Architecture**:
  - **Practice Mode**: On-demand Socratic hints available before answering; instant 3-part diagnostic available upon selecting an option.
  - **Timed Quiz Mode**: AI strictly disabled while the timer is running to maintain real exam simulation pressure. Unlocked fully on the Post-Quiz Review Screen.
- **Error Vault Integration**:
  - Missed questions auto-ingested into Error Vault.
  - AI tags the error type: `[Conceptual Gap]`, `[Formula Slip]`, `[Calculation / Unit Error]`, or `[Question Misinterpretation]`.
  - Generates 1 "Twin Question" to verify mastery before resolving the error.

---

## 8. Material 3 Expressive Tonal Discipline & Screen Upgrades

### 1. Tonal Palette & Color Discipline
- **Zero-Border Discipline**: Eliminate 90% of `BorderStroke(1.dp, ...)` on standard cards and surfaces. Let contrast between deep background and `surfaceContainerLow` define boundaries. Reserve subtle 0.5dp borders exclusively for liquid glass mode.
- **Harmonized Action Buttons**: Banish clashing yellow/amber primary buttons against lavender themes. All primary hero buttons (e.g. "Start 3-Hour Exam Simulation") must use theme `primary` (deep violet/indigo) with `onPrimary` text, or `primaryContainer`/`onPrimaryContainer`.
- **Unified Academic Cards**: Eliminate washed-out pastel backgrounds (`#E8F5E9`, etc.). Subject cards across Home and Library unify to `surfaceContainerLow`. Unique subject hues are restricted to the 40–44dp icon squircle.
- **Single-Pill Question Badges**: In CBSE Question Bank cards, replace fragmented multi-colored badges with a single unified `secondaryContainer` pill (`VSA • 2 Marks`), leaving the chapter title as clean `labelMedium` (`onSurfaceVariant`) text without an enclosing box.

### 2. Screen-by-Screen Expressive Polish
- **Home / Dashboard**:
  - *Countdown Hero*: Subtle vertical gradient from `primaryContainer` to `surfaceContainerLow`. Display countdown styled in massive tight-tracked Google Sans Display (`FontWeight.Black`, `56.sp`). "Continue Today's Study" rendered as an elevated tactile surface pill.
  - *Academic Subjects*: De-clutter metadata. Remove repeated "Core Track". Show clean subtitle: `"19 Chapters • 80M"`. Replace thin hair-line progress with a 6dp rounded M3 linear progress indicator with visible track gap.
- **CBSE Question Bank & Sample Papers**:
  - *Integrated Subject Tabs*: Top subject selector transformed into clean M3 `PrimaryScrollableTabRow` or edge-to-edge underlined tabs integrated into the TopAppBar.
  - *Clean Secondary Filters*: Secondary chips (All Marks, 2M, 3M, etc.) use `FilterChip` with 0dp border when unselected and `surfaceContainerHigh` fill.
  - *Single Clean AutoAwesome*: Ask AI button uses single Material vector `Icons.Default.AutoAwesome` (no emoji sparkle).
  - *Full-Width Rubric Accordion*: "Model Answer & Rubric" rendered as a clean full-width expandable accordion positioned directly above the self-grading bar.
  - *Tonal Self-Grading Dock*: Group "✓ Full Marks", "½ Partial", and "↻ Retry" with distinct responsive tonal fills when selected.
  - *Text Wrapping*: Header subtitle strictly `"Official Subjective Studio • 2027"` with `maxLines = 1` and `TextOverflow.Ellipsis`.
- **Library Hub Screen**:
  - *Grammar Fix*: Singular/plural agreement: `if (mistakeCount == 1) "1 question to fix" else "$mistakeCount questions to fix"`.
  - *Handbooks*: Unified `surfaceContainerLow` container fill.
- **AI Engine & Settings**:
  - *Banner*: Remove 1dp purple border, fill with `secondaryContainer`, style button as clean filled `primary` pill.
  - *Segmentation*: Remove Android 9 list dividers inside "Provider & Model Configuration" in favor of clean card segmentation or 12dp spacing.
  - *Monospace Tokens*: Latency (`676 ms`), marks (`70M`), and API keys (`••••••••••vGwz`) rendered in `FontFamily.Monospace`.

---

## 9. Anti-AI-Slop Review Checklist
Before any PR or feature branch is considered complete, run this mental audit:
- [ ] Are all icons pure Material 3 vectors with zero emoji mixing?
- [ ] Does the screen viewport dedicate >= 70% space to actual questions/content?
- [ ] Do headers scroll out of the way naturally?
- [ ] Does the search bar auto-hide top banners when the keyboard opens?
- [ ] Are question passages sanitized of raw `\n` line wraps and OCR typos?
- [ ] Are action buttons balanced, full-width, or symmetrically aligned?
- [ ] Is there 110dp bottom scroll clearance above the floating dock?
- [ ] Do animations use spring physics instead of linear tweens?
- [ ] Is every claim and label grounded in official CBSE syllabus reality?
- [ ] Does the AI bottom sheet use 0.dp elevation and KaTeX math formatting?
- [ ] Does the AI respect the Dual Mode rule (disabled during timed exams, active in review)?
- [ ] Are monospace fonts used for latencies, API keys, and token benchmarks?
- [ ] Are cards zero-border M3 Expressive surfaces without washed-out pastel fills?

