package com.boardsprep.onboard.core.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Material You Expressive Color Palette (Android 14/15 M3 Expressive)
// High-vibrancy pastel containers, deep OLED velvet surfaces, and distinct tonal accents
// ==========================================

// Primary (Expressive Lavender / Royal Violet)
val MdPrimaryLight = Color(0xFF6750A4)
val MdOnPrimaryLight = Color(0xFFFFFFFF)
val MdPrimaryContainerLight = Color(0xFFEADDFF)
val MdOnPrimaryContainerLight = Color(0xFF21005D)

val MdPrimaryDark = Color(0xFFA78BFA)
val MdOnPrimaryDark = Color(0xFF1E103A)
val MdPrimaryContainerDark = Color(0xFF2A244D)
val MdOnPrimaryContainerDark = Color(0xFFEDE9FE)

// Secondary (Expressive Teal / Mint)
val MdSecondaryLight = Color(0xFF4B6358)
val MdOnSecondaryLight = Color(0xFFFFFFFF)
val MdSecondaryContainerLight = Color(0xFFCDE8DA)
val MdOnSecondaryContainerLight = Color(0xFF072017)

val MdSecondaryDark = Color(0xFF2DD4BF)
val MdOnSecondaryDark = Color(0xFF003830)
val MdSecondaryContainerDark = Color(0xFF133E38)
val MdOnSecondaryContainerDark = Color(0xFF99F6E4)

// Tertiary (Expressive Sunset Peach / Coral)
val MdTertiaryLight = Color(0xFF7D5260)
val MdOnTertiaryLight = Color(0xFFFFFFFF)
val MdTertiaryContainerLight = Color(0xFFFFD8E4)
val MdOnTertiaryContainerLight = Color(0xFF31111D)

val MdTertiaryDark = Color(0xFFFB923C)
val MdOnTertiaryDark = Color(0xFF431B00)
val MdTertiaryContainerDark = Color(0xFF4E2612)
val MdOnTertiaryContainerDark = Color(0xFFFFEDD5)

// Error Tokens
val MdErrorLight = Color(0xFFBA1A1A)
val MdOnErrorLight = Color(0xFFFFFFFF)
val MdErrorContainerLight = Color(0xFFFFDAD6)
val MdOnErrorContainerLight = Color(0xFF410002)

val MdErrorDark = Color(0xFFF87171)
val MdOnErrorDark = Color(0xFF450A0A)
val MdErrorContainerDark = Color(0xFF7F1D1D)
val MdOnErrorContainerDark = Color(0xFFFEE2E2)

// Surfaces & Backgrounds (Velvet Obsidian Dark & Clean Light)
val MdBackgroundLight = Color(0xFFFEF7FF)
val MdOnBackgroundLight = Color(0xFF1D1B20)
val MdSurfaceLight = Color(0xFFFEF7FF)
val MdOnSurfaceLight = Color(0xFF1D1B20)
val MdSurfaceVariantLight = Color(0xFFE7E0EC)
val MdOnSurfaceVariantLight = Color(0xFF49454F)
val MdOutlineLight = Color(0xFF79747E)
val MdOutlineVariantLight = Color(0xFFCAC4D0)

val MdBackgroundDark = Color(0xFF090A0F)
val MdOnBackgroundDark = Color(0xFFF3F4F6)
val MdSurfaceDark = Color(0xFF12141C)
val MdOnSurfaceDark = Color(0xFFF3F4F6)
val MdSurfaceVariantDark = Color(0xFF1A1D26)
val MdOnSurfaceVariantDark = Color(0xFF9CA3AF)
val MdOutlineDark = Color(0xFF373C4D)
val MdOutlineVariantDark = Color(0xFF242735)

// Subject Distinctive Expressive Accent Tones
val PhysicsLavender = Color(0xFFA78BFA)
val PhysicsLavenderContainer = Color(0xFF1A172B)
val PhysicsLightBg = Color(0xFFF3EDFF)        // 10% Lavender tint container
val PhysicsLightOnBg = Color(0xFF5B2288)      // Deep high-contrast violet ink (WCAG 7:1)
val PhysicsAccent = Color(0xFF7C3AED)         // Vibrant icon accent

val ChemistryMint = Color(0xFF34D399)
val ChemistryMintContainer = Color(0xFF0F241E)
val ChemistryLightBg = Color(0xFFE8F8F0)      // 10% Mint tint container
val ChemistryLightOnBg = Color(0xFF0F5B38)    // Deep emerald ink (WCAG 7:1)
val ChemistryAccent = Color(0xFF059669)

val MathsPeach = Color(0xFFFB923C)
val MathsPeachContainer = Color(0xFF261912)
val MathsLightBg = Color(0xFFFFF3EB)          // 10% Peach tint container
val MathsLightOnBg = Color(0xFF8A3B0F)        // Deep amber/rust ink (WCAG 6.5:1)
val MathsAccent = Color(0xFFEA580C)

val BiologySky = Color(0xFF38BDF8)
val BiologySkyContainer = Color(0xFF0E222F)
val BiologyLightBg = Color(0xFFE6F7FF)        // 10% Sky tint container
val BiologyLightOnBg = Color(0xFF0C5274)      // Deep cerulean ink (WCAG 7:1)
val BiologyAccent = Color(0xFF0284C7)

val EnglishRose = Color(0xFFF472B6)
val EnglishRoseContainer = Color(0xFF271321)
val EnglishLightBg = Color(0xFFFDF2F8)        // 10% Rose tint container
val EnglishLightOnBg = Color(0xFF831843)      // Deep garnet ink (WCAG 7.5:1)
val EnglishAccent = Color(0xFFBE185D)         // Soothing cranberry rose (replaces alarming neon red)

// High-Yield Accent Tokens
val AccentAmber = Color(0xFFFFC043)
val AccentAmberLight = Color(0xFFFFF3D6)
val AccentAmberDark = Color(0xFFFBBF24)
val AccentAmberOnBg = Color(0xFF7C4A00)       // WCAG AA compliant dark amber text

val SuccessGreen = Color(0xFF48BB78)
val SuccessGreenLight = Color(0xFFDCFCE7)
val SuccessGreenDark = Color(0xFF34D399)

val ErrorRed = Color(0xFFF56565)
val PrimaryBlue = Color(0xFFA78BFA)

/**
 * Semantic container and accent tokens for academic subjects
 * Supports true OLED dark mode containers and high-contrast light mode pastels
 */
data class SubjectThemeTokens(
    val containerColor: Color,
    val onContainerColor: Color,
    val accentColor: Color
)

fun getAdaptiveSubjectTokens(subjectId: String, isDark: Boolean): SubjectThemeTokens {
    return when (subjectId.lowercase()) {
        "physics" -> if (isDark) SubjectThemeTokens(PhysicsLavenderContainer, Color(0xFFE9D5FF), PhysicsLavender)
                     else SubjectThemeTokens(PhysicsLightBg, PhysicsLightOnBg, PhysicsAccent)
        "chemistry" -> if (isDark) SubjectThemeTokens(ChemistryMintContainer, Color(0xFFA7F3D0), ChemistryMint)
                       else SubjectThemeTokens(ChemistryLightBg, ChemistryLightOnBg, ChemistryAccent)
        "maths" -> if (isDark) SubjectThemeTokens(MathsPeachContainer, Color(0xFFFED7AA), MathsPeach)
                   else SubjectThemeTokens(MathsLightBg, MathsLightOnBg, MathsAccent)
        "biology" -> if (isDark) SubjectThemeTokens(BiologySkyContainer, Color(0xFFBAE6FD), BiologySky)
                     else SubjectThemeTokens(BiologyLightBg, BiologyLightOnBg, BiologyAccent)
        "english" -> if (isDark) SubjectThemeTokens(EnglishRoseContainer, Color(0xFFFBCFE8), EnglishRose)
                     else SubjectThemeTokens(EnglishLightBg, EnglishLightOnBg, EnglishAccent)
        else -> if (isDark) SubjectThemeTokens(MdSurfaceVariantDark, MdOnSurfaceDark, MdPrimaryDark)
                else SubjectThemeTokens(MdSurfaceVariantLight, MdOnSurfaceVariantLight, MdPrimaryLight)
    }
}
