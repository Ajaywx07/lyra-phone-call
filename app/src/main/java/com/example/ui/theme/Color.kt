package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Default Primary AI Cyber Accents (kept as compatibility base constants)
val CyanNeon = Color(0xFF00E5FF)
val CyanGlow = Color(0xFF80F0FF)
val CyanMuted = Color(0xFF007A8A)

val PurpleElectric = Color(0xFF8B5CF6)
val PurpleGlow = Color(0xFFC084FC)
val PurpleDark = Color(0xFF4C1D95)

// Semantic Status & Action Colors
val EmeraldAccept = Color(0xFF10B981)
val EmeraldGlow = Color(0xFF34D399)
val CrimsonDecline = Color(0xFFEF4444)
val CrimsonGlow = Color(0xFFF87171)
val AmberPriority = Color(0xFFF59E0B)

// Default Dark Palette Constants
val DarkBackground = Color(0xFF08090E)
val DarkSurface = Color(0xFF11131C)
val DarkSurfaceElevated = Color(0xFF191C2B)
val GlassSurface = Color(0x99171A29)
val GlassBorder = Color(0x3300E5FF)
val GlassBorderPurple = Color(0x338B5CF6)

// Default Text Colors
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)

enum class AppThemeStyle(
    val key: String,
    val displayName: String,
    val subtitle: String,
    val isLight: Boolean
) {
    IOS_GLASS(
        key = "IOS_GLASS",
        displayName = "iOS Liquid Glass",
        subtitle = "SDF refraction & prism rim",
        isLight = false
    ),
    LYRA_CYBER(
        key = "LYRA_CYBER",
        displayName = "Lyra Cyber",
        subtitle = "Deep neon cyber HUD",
        isLight = false
    ),
    CLASSIC_LIGHT(
        key = "CLASSIC_LIGHT",
        displayName = "Classic Light",
        subtitle = "Crisp daylight ink & porcelain",
        isLight = true
    ),
    MODERN_POSTER(
        key = "MODERN_POSTER",
        displayName = "Modern Poster",
        subtitle = "Bold plum & coral editorial",
        isLight = false
    ),
    AURORA_PRISM(
        key = "AURORA_PRISM",
        displayName = "Aurora Prism",
        subtitle = "Multi-color cyan, violet & coral",
        isLight = false
    ),
    PURE_AMOLED(
        key = "PURE_AMOLED",
        displayName = "Pure AMOLED",
        subtitle = "True #000000 OLED dark",
        isLight = false
    ),
    SUNSET_EMBER(
        key = "SUNSET_EMBER",
        displayName = "Sunset Ember",
        subtitle = "Amber coral, crimson & warm gold",
        isLight = false
    ),
    EMERALD_MATRIX(
        key = "EMERALD_MATRIX",
        displayName = "Emerald Matrix",
        subtitle = "Neon mint, jade & teal glow",
        isLight = false
    ),
    ROSE_GOLD_LUXE(
        key = "ROSE_GOLD_LUXE",
        displayName = "Rose Gold Luxe",
        subtitle = "Blush pearl, berry & violet daylight",
        isLight = true
    ),
    OCEAN_VOLT(
        key = "OCEAN_VOLT",
        displayName = "Ocean Volt",
        subtitle = "Sapphire blue, lime & ultraviolet",
        isLight = false
    );

    companion object {
        fun fromKey(key: String?): AppThemeStyle {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: IOS_GLASS
        }
    }
}

@Immutable
data class LyraThemeColors(
    val style: AppThemeStyle,
    val isLight: Boolean,
    val isGlassStyle: Boolean = false,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val glassBorderPurple: Color,
    val primary: Color,
    val primaryGlow: Color,
    val primaryMuted: Color,
    val secondary: Color,
    val secondaryGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onPrimaryButton: Color,
    val onSecondaryButton: Color,
    val acceptColor: Color = EmeraldAccept,
    val declineColor: Color = CrimsonDecline,
    val priorityColor: Color = AmberPriority
)

val LyraCyberColors = LyraThemeColors(
    style = AppThemeStyle.LYRA_CYBER,
    isLight = false,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    glassSurface = GlassSurface,
    glassBorder = GlassBorder,
    glassBorderPurple = GlassBorderPurple,
    primary = CyanNeon,
    primaryGlow = CyanGlow,
    primaryMuted = CyanMuted,
    secondary = PurpleElectric,
    secondaryGlow = PurpleGlow,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.White
)

val IosGlassColors = LyraThemeColors(
    style = AppThemeStyle.IOS_GLASS,
    isLight = false,
    isGlassStyle = true,
    background = Color(0xFF060B17),
    surface = Color(0xCC101A2E),
    surfaceElevated = Color(0xB8182742),
    glassSurface = Color(0x6615243D),
    glassBorder = Color(0x88DCEEFF),
    glassBorderPurple = Color(0x66A78BFA),
    primary = Color(0xFF38BDF8),
    primaryGlow = Color(0xFFBAE6FD),
    primaryMuted = Color(0xFF0284C7),
    secondary = Color(0xFF818CF8),
    secondaryGlow = Color(0xFFC7D2FE),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFE2E8F0),
    textMuted = Color(0xFF94A3B8),
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.White
)

val ClassicLightColors = LyraThemeColors(
    style = AppThemeStyle.CLASSIC_LIGHT,
    isLight = true,
    background = Color(0xFFF3F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE9EFF6),
    glassSurface = Color(0xFFFFFFFF),
    glassBorder = Color(0xFFCBD5E1),
    glassBorderPurple = Color(0xFFC4B5FD),
    primary = Color(0xFF0284C7),
    primaryGlow = Color(0xFF0369A1),
    primaryMuted = Color(0xFFE0F2FE),
    secondary = Color(0xFF6D28D9),
    secondaryGlow = Color(0xFF7C3AED),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    onPrimaryButton = Color.White,
    onSecondaryButton = Color.White
)

val ModernPosterColors = LyraThemeColors(
    style = AppThemeStyle.MODERN_POSTER,
    isLight = false,
    background = Color(0xFF14091C),
    surface = Color(0xFF21102D),
    surfaceElevated = Color(0xFF2E183E),
    glassSurface = Color(0xCC281438),
    glassBorder = Color(0x55FF4D6D),
    glassBorderPurple = Color(0x55FFB703),
    primary = Color(0xFFFF4D6D),
    primaryGlow = Color(0xFFFFB703),
    primaryMuted = Color(0xFF9D174D),
    secondary = Color(0xFFFFB703),
    secondaryGlow = Color(0xFFFDE047),
    textPrimary = Color(0xFFFFF7ED),
    textSecondary = Color(0xFFE9D5FF),
    textMuted = Color(0xFFB794D6),
    onPrimaryButton = Color.White,
    onSecondaryButton = Color.Black
)

val AuroraPrismColors = LyraThemeColors(
    style = AppThemeStyle.AURORA_PRISM,
    isLight = false,
    background = Color(0xFF090B16),
    surface = Color(0xFF12162A),
    surfaceElevated = Color(0xFF1A203A),
    glassSurface = Color(0x99161D36),
    glassBorder = Color(0x5522D3EE),
    glassBorderPurple = Color(0x55E879F9),
    primary = Color(0xFF22D3EE),
    primaryGlow = Color(0xFF67E8F9),
    primaryMuted = Color(0xFF0E7490),
    secondary = Color(0xFFE879F9),
    secondaryGlow = Color(0xFFF5D0FE),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFD6E0F5),
    textMuted = Color(0xFF8B9DC3),
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.Black,
    acceptColor = Color(0xFF34D399),
    declineColor = Color(0xFFFB7185),
    priorityColor = Color(0xFFFBBF24)
)

val PureAmoledColors = LyraThemeColors(
    style = AppThemeStyle.PURE_AMOLED,
    isLight = false,
    background = Color(0xFF000000),
    surface = Color(0xFF070707),
    surfaceElevated = Color(0xFF121212),
    glassSurface = Color(0xFF0A0A0A),
    glassBorder = Color(0xFF262626),
    glassBorderPurple = Color(0x4422D3EE),
    primary = Color(0xFF22D3EE),
    primaryGlow = Color(0xFF67E8F9),
    primaryMuted = Color(0xFF0E7490),
    secondary = Color(0xFFA855F7),
    secondaryGlow = Color(0xFFD8B4FE),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA3A3A3),
    textMuted = Color(0xFF737373),
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.White
)

val SunsetEmberColors = LyraThemeColors(
    style = AppThemeStyle.SUNSET_EMBER,
    isLight = false,
    background = Color(0xFF12080A),
    surface = Color(0xFF1D0E12),
    surfaceElevated = Color(0xFF2B141A),
    glassSurface = Color(0xCC241016),
    glassBorder = Color(0x66FF7B54),
    glassBorderPurple = Color(0x66FFB26B),
    primary = Color(0xFFFF7B54),
    primaryGlow = Color(0xFFFFB26B),
    primaryMuted = Color(0xFF9A3412),
    secondary = Color(0xFFEC4899),
    secondaryGlow = Color(0xFFF9A8D4),
    textPrimary = Color(0xFFFFF8F3),
    textSecondary = Color(0xFFFED7AA),
    textMuted = Color(0xFFD69E8E),
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.White,
    acceptColor = Color(0xFF22C55E),
    declineColor = Color(0xFFF43F5E),
    priorityColor = Color(0xFFFACC15)
)

val EmeraldMatrixColors = LyraThemeColors(
    style = AppThemeStyle.EMERALD_MATRIX,
    isLight = false,
    background = Color(0xFF04110D),
    surface = Color(0xFF0A1D17),
    surfaceElevated = Color(0xFF102A22),
    glassSurface = Color(0xCC0D241C),
    glassBorder = Color(0x5510B981),
    glassBorderPurple = Color(0x5506B6D4),
    primary = Color(0xFF10B981),
    primaryGlow = Color(0xFF6EE7B7),
    primaryMuted = Color(0xFF065F46),
    secondary = Color(0xFF06B6D4),
    secondaryGlow = Color(0xFF67E8F9),
    textPrimary = Color(0xFFECFDF5),
    textSecondary = Color(0xFFA7F3D0),
    textMuted = Color(0xFF6EE7B7),
    onPrimaryButton = Color.Black,
    onSecondaryButton = Color.Black,
    acceptColor = Color(0xFF34D399),
    declineColor = Color(0xFFFB7185),
    priorityColor = Color(0xFFFBBF24)
)

val RoseGoldLuxeColors = LyraThemeColors(
    style = AppThemeStyle.ROSE_GOLD_LUXE,
    isLight = true,
    background = Color(0xFFFDF2F4),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFCE7EC),
    glassSurface = Color(0xFFFFFFFF),
    glassBorder = Color(0xFFF4B6C2),
    glassBorderPurple = Color(0xFFD8B4FE),
    primary = Color(0xFFBE185D),
    primaryGlow = Color(0xFFDB2777),
    primaryMuted = Color(0xFFFCE7F3),
    secondary = Color(0xFF7C3AED),
    secondaryGlow = Color(0xFF8B5CF6),
    textPrimary = Color(0xFF1F1218),
    textSecondary = Color(0xFF5B3746),
    textMuted = Color(0xFF8A6574),
    onPrimaryButton = Color.White,
    onSecondaryButton = Color.White,
    acceptColor = Color(0xFF059669),
    declineColor = Color(0xFFDC2626),
    priorityColor = Color(0xFFD97706)
)

val OceanVoltColors = LyraThemeColors(
    style = AppThemeStyle.OCEAN_VOLT,
    isLight = false,
    background = Color(0xFF050B1A),
    surface = Color(0xFF0B152E),
    surfaceElevated = Color(0xFF122147),
    glassSurface = Color(0xCC0D1A38),
    glassBorder = Color(0x663B82F6),
    glassBorderPurple = Color(0x66A3E635),
    primary = Color(0xFF3B82F6),
    primaryGlow = Color(0xFF93C5FD),
    primaryMuted = Color(0xFF1D4ED8),
    secondary = Color(0xFFA3E635),
    secondaryGlow = Color(0xFFD9F99D),
    textPrimary = Color(0xFFF0F9FF),
    textSecondary = Color(0xFFBAE6FD),
    textMuted = Color(0xFF7DD3FC),
    onPrimaryButton = Color.White,
    onSecondaryButton = Color.Black,
    acceptColor = Color(0xFF22C55E),
    declineColor = Color(0xFFEF4444),
    priorityColor = Color(0xFFF59E0B)
)

fun themeColorsFor(style: AppThemeStyle): LyraThemeColors = when (style) {
    AppThemeStyle.LYRA_CYBER -> LyraCyberColors
    AppThemeStyle.IOS_GLASS -> IosGlassColors
    AppThemeStyle.CLASSIC_LIGHT -> ClassicLightColors
    AppThemeStyle.MODERN_POSTER -> ModernPosterColors
    AppThemeStyle.AURORA_PRISM -> AuroraPrismColors
    AppThemeStyle.PURE_AMOLED -> PureAmoledColors
    AppThemeStyle.SUNSET_EMBER -> SunsetEmberColors
    AppThemeStyle.EMERALD_MATRIX -> EmeraldMatrixColors
    AppThemeStyle.ROSE_GOLD_LUXE -> RoseGoldLuxeColors
    AppThemeStyle.OCEAN_VOLT -> OceanVoltColors
}
