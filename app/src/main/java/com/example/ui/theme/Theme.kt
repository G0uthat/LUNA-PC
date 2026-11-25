package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ReadingTheme

@Immutable
data class FolioPalette(
    val theme: ReadingTheme,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentShadow: Color,
    val accentSoft: Color,
    val border: Color,
    val borderShadow: Color,
    val onAccent: Color = Color.White
)

fun paletteFor(theme: ReadingTheme): FolioPalette = when (theme) {
    ReadingTheme.LIGHT -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LightBg,
        surface = LightSurface,
        surfaceAlt = LightSurfaceAlt,
        ink = LightInk,
        inkMuted = LightInkMuted,
        accent = CobaltPrimary,
        accentShadow = CobaltShadow,
        accentSoft = CobaltSoftLight,
        border = LightBorder,
        borderShadow = LightBorderShadow
    )
    ReadingTheme.SEPIA -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SepiaBg,
        surface = SepiaSurface,
        surfaceAlt = SepiaSurfaceAlt,
        ink = SepiaInk,
        inkMuted = SepiaInkMuted,
        accent = SepiaAccent,
        accentShadow = SepiaAccentShadow,
        accentSoft = Color(0xFFE7DCCB),
        border = SepiaBorder,
        borderShadow = SepiaBorderShadow
    )
    ReadingTheme.SAGE -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SageBg,
        surface = SageSurface,
        surfaceAlt = SageSurfaceAlt,
        ink = SageInk,
        inkMuted = SageInkMuted,
        accent = SageAccent,
        accentShadow = SageAccentShadow,
        accentSoft = Color(0xFFD9EBE1),
        border = SageBorder,
        borderShadow = SageBorderShadow
    )
    ReadingTheme.LAVENDER -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LavenderBg,
        surface = LavenderSurface,
        surfaceAlt = LavenderSurfaceAlt,
        ink = LavenderInk,
        inkMuted = LavenderInkMuted,
        accent = LavenderAccent,
        accentShadow = LavenderAccentShadow,
        accentSoft = Color(0xFFE5DDF8),
        border = LavenderBorder,
        borderShadow = LavenderBorderShadow
    )
    ReadingTheme.PEACH -> FolioPalette(
        theme = theme,
        isDark = false,
        background = PeachBg,
        surface = PeachSurface,
        surfaceAlt = PeachSurfaceAlt,
        ink = PeachInk,
        inkMuted = PeachInkMuted,
        accent = PeachAccent,
        accentShadow = PeachAccentShadow,
        accentSoft = Color(0xFFF8E1D5),
        border = PeachBorder,
        borderShadow = PeachBorderShadow
    )
    ReadingTheme.SKY -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SkyBg,
        surface = SkySurface,
        surfaceAlt = SkySurfaceAlt,
        ink = SkyInk,
        inkMuted = SkyInkMuted,
        accent = SkyAccent,
        accentShadow = SkyAccentShadow,
        accentSoft = Color(0xFFD9EBFA),
        border = SkyBorder,
        borderShadow = SkyBorderShadow
    )
    ReadingTheme.ROSE -> FolioPalette(
        theme = theme,
        isDark = false,
        background = RoseBg,
        surface = RoseSurface,
        surfaceAlt = RoseSurfaceAlt,
        ink = RoseInk,
        inkMuted = RoseInkMuted,
        accent = RoseAccent,
        accentShadow = RoseAccentShadow,
        accentSoft = Color(0xFFF7DCE6),
        border = RoseBorder,
        borderShadow = RoseBorderShadow
    )
    ReadingTheme.LEMON -> FolioPalette(
        theme = theme,
        isDark = false,
        background = LemonBg,
        surface = LemonSurface,
        surfaceAlt = LemonSurfaceAlt,
        ink = LemonInk,
        inkMuted = LemonInkMuted,
        accent = LemonAccent,
        accentShadow = LemonAccentShadow,
        accentSoft = Color(0xFFF5ECC8),
        border = LemonBorder,
        borderShadow = LemonBorderShadow
    )
    ReadingTheme.NAVY_MID -> FolioPalette(
        theme = theme,
        isDark = true,
        background = NavyMidBg,
        surface = NavyMidSurface,
        surfaceAlt = NavyMidSurfaceAlt,
        ink = NavyMidInk,
        inkMuted = NavyMidInkMuted,
        accent = NavyMidAccent,
        accentShadow = NavyMidAccentShadow,
        accentSoft = Color(0xFF233B66),
        border = NavyMidBorder,
        borderShadow = NavyMidBorderShadow
    )
    ReadingTheme.MIDNIGHT -> FolioPalette(
        theme = theme,
        isDark = true,
        background = MidnightBg,
        surface = MidnightSurface,
        surfaceAlt = MidnightSurfaceAlt,
        ink = MidnightInk,
        inkMuted = MidnightInkMuted,
        accent = MidnightAccent,
        accentShadow = MidnightAccentShadow,
        accentSoft = Color(0xFF1A2B4D),
        border = MidnightBorder,
        borderShadow = MidnightBorderShadow
    )
    ReadingTheme.DARK -> FolioPalette(
        theme = theme,
        isDark = true,
        background = DarkBg,
        surface = DarkSurface,
        surfaceAlt = DarkSurfaceAlt,
        ink = DarkInk,
        inkMuted = DarkInkMuted,
        accent = CobaltBright,
        accentShadow = CobaltShadow,
        accentSoft = CobaltSoftDark,
        border = DarkBorder,
        borderShadow = DarkBorderShadow
    )
    ReadingTheme.WARM_CREAM -> FolioPalette(
        theme = theme,
        isDark = false,
        background = WarmCreamBg,
        surface = WarmCreamSurface,
        surfaceAlt = WarmCreamSurfaceAlt,
        ink = WarmCreamInk,
        inkMuted = WarmCreamInkMuted,
        accent = WarmCreamAccent,
        accentShadow = WarmCreamAccentShadow,
        accentSoft = Color(0xFFF4E1D2),
        border = WarmCreamBorder,
        borderShadow = WarmCreamBorderShadow
    )
    ReadingTheme.SAND -> FolioPalette(
        theme = theme,
        isDark = false,
        background = SandBg,
        surface = SandSurface,
        surfaceAlt = SandSurfaceAlt,
        ink = SandInk,
        inkMuted = SandInkMuted,
        accent = SandAccent,
        accentShadow = SandAccentShadow,
        accentSoft = Color(0xFFEBDDD0),
        border = SandBorder,
        borderShadow = SandBorderShadow
    )
    ReadingTheme.AMBER_SOFT -> FolioPalette(
        theme = theme,
        isDark = false,
        background = AmberSoftBg,
        surface = AmberSoftSurface,
        surfaceAlt = AmberSoftSurfaceAlt,
        ink = AmberSoftInk,
        inkMuted = AmberSoftInkMuted,
        accent = AmberSoftAccent,
        accentShadow = AmberSoftAccentShadow,
        accentSoft = Color(0xFFF1DCBE),
        border = AmberSoftBorder,
        borderShadow = AmberSoftBorderShadow
    )
    ReadingTheme.WARM_NIGHT -> FolioPalette(
        theme = theme,
        isDark = true,
        background = WarmNightBg,
        surface = WarmNightSurface,
        surfaceAlt = WarmNightSurfaceAlt,
        ink = WarmNightInk,
        inkMuted = WarmNightInkMuted,
        accent = WarmNightAccent,
        accentShadow = WarmNightAccentShadow,
        accentSoft = Color(0xFF3A2A1B),
        border = WarmNightBorder,
        borderShadow = WarmNightBorderShadow,
        onAccent = Color(0xFF1F160E)
    )
    ReadingTheme.FOREST_NIGHT -> FolioPalette(
        theme = theme,
        isDark = true,
        background = ForestNightBg,
        surface = ForestNightSurface,
        surfaceAlt = ForestNightSurfaceAlt,
        ink = ForestNightInk,
        inkMuted = ForestNightInkMuted,
        accent = ForestNightAccent,
        accentShadow = ForestNightAccentShadow,
        accentSoft = Color(0xFF1C352B),
        border = ForestNightBorder,
        borderShadow = ForestNightBorderShadow,
        onAccent = Color(0xFF0D1D17)
    )
    ReadingTheme.AMOLED -> FolioPalette(
        theme = theme,
        isDark = true,
        background = AmoledBg,
        surface = AmoledSurface,
        surfaceAlt = AmoledSurfaceAlt,
        ink = AmoledInk,
        inkMuted = AmoledInkMuted,
        accent = CobaltBright,
        accentShadow = CobaltShadow,
        accentSoft = Color(0xFF102042),
        border = AmoledBorder,
        borderShadow = AmoledBorderShadow
    )
}

/**
 * Gently softens ink luminance on dark themes to eliminate text halation / glare in dark rooms.
 */
fun FolioPalette.withSoftNightDimming(dimming: Float): FolioPalette {
    val clamped = dimming.coerceIn(0f, 0.40f)
    if (!isDark || clamped <= 0.01f) return this
    return copy(
        ink = androidx.compose.ui.graphics.lerp(ink, background, clamped * 0.45f),
        inkMuted = androidx.compose.ui.graphics.lerp(inkMuted, background, clamped * 0.35f)
    )
}

/**
 * Resolves whether the current clock hour falls inside the configured night reading window.
 */
fun isHourInNightWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
    val h = currentHour.coerceIn(0, 23)
    val start = startHour.coerceIn(0, 23)
    val end = endHour.coerceIn(0, 23)
    return when {
        start == end -> false
        start > end -> h >= start || h < end
        else -> h in start until end
    }
}

/**
 * Resolves the active visual theme respecting manual night mode or automatic sunset-to-sunrise schedule.
 */
fun resolveEffectiveTheme(
    prefs: com.example.data.model.ReaderPreferences,
    currentHour: Int
): ReadingTheme {
    if (prefs.nightAutoSchedule) {
        val shouldBeNight = isHourInNightWindow(currentHour, prefs.nightStartHour, prefs.nightEndHour)
        return if (shouldBeNight) {
            if (prefs.theme.isDark) prefs.theme else prefs.preferredNightTheme.takeIf { it.isDark } ?: ReadingTheme.WARM_NIGHT
        } else {
            if (!prefs.theme.isDark) prefs.theme else prefs.preferredDayTheme.takeIf { !it.isDark } ?: ReadingTheme.WARM_CREAM
        }
    }
    return prefs.theme
}

val LocalFolioPalette = staticCompositionLocalOf { paletteFor(ReadingTheme.WARM_CREAM) }

val FolioShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun FolioTheme(
    readingTheme: ReadingTheme = ReadingTheme.WARM_CREAM,
    useSystemFont: Boolean = false,
    softNightDimming: Float = 0f,
    effectiveRefreshRateHz: Float = 60f,
    isLowRamDevice: Boolean = com.example.engine.MemoryPressureManager.isLowRamDevice,
    content: @Composable () -> Unit
) {
    val palette = remember(readingTheme, softNightDimming) {
        paletteFor(readingTheme).withSoftNightDimming(softNightDimming)
    }
    val motion = remember(effectiveRefreshRateHz, isLowRamDevice) {
        motionProfileFor(effectiveRefreshRateHz, isLowRam = isLowRamDevice)
    }
    val typography = remember(useSystemFont) { buildFolioTypography(useSystemFont) }
    val colorScheme = remember(palette) {
        if (palette.isDark) {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = palette.accentSoft,
                onPrimaryContainer = palette.ink,
                secondary = EmeraldAccent,
                onSecondary = Color.White,
                background = palette.background,
                onBackground = palette.ink,
                surface = palette.surface,
                onSurface = palette.ink,
                surfaceVariant = palette.surfaceAlt,
                onSurfaceVariant = palette.inkMuted,
                outline = palette.border,
                error = CoralAccent
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = palette.accentSoft,
                onPrimaryContainer = palette.ink,
                secondary = EmeraldAccent,
                onSecondary = Color.White,
                background = palette.background,
                onBackground = palette.ink,
                surface = palette.surface,
                onSurface = palette.ink,
                surfaceVariant = palette.surfaceAlt,
                onSurfaceVariant = palette.inkMuted,
                outline = palette.border,
                error = CoralAccent
            )
        }
    }

    CompositionLocalProvider(
        LocalFolioPalette provides palette,
        LocalLumaMotion provides motion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = FolioShapes,
            content = content
        )
    }
}
