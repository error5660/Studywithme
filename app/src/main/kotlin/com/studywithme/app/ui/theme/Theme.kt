package com.studywithme.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class WarmColors(
    val bgTop: Color, val bgMid: Color, val bgBottom: Color,
    val blobA: Color, val blobB: Color, val blobC: Color,
    val ink: Color, val inkSoft: Color, val inkFaint: Color,
    val accent: Color, val accentTop: Color, val onAccent: Color, val accentSoft: Color,
    val glassTint: Color, val borderHi: Color, val borderLo: Color, val highlight: Color,
    val shadow: Color, val dialog: Color,
    val grainAlpha: Float, val night: Boolean,
)

val LightWarm = WarmColors(
    bgTop = Color(0xFFFFFAF3), bgMid = Color(0xFFFFE9D6), bgBottom = Color(0xFFFFD3B0),
    blobA = Color(0x66FFB98A), blobB = Color(0x99FFE3CB), blobC = Color(0x55FF9F63),
    ink = Color(0xFF3B2A20), inkSoft = Color(0xFF8C6F5D), inkFaint = Color(0xFFC9AE9B),
    accent = Color(0xFFEA741F), accentTop = Color(0xFFF59A55), onAccent = Color.White, accentSoft = Color(0x26EA741F),
    glassTint = Color(0x59FFF8F0), borderHi = Color(0xCCFFFFFF), borderLo = Color(0x40FFFFFF), highlight = Color(0x40FFFFFF),
    shadow = Color(0x33B5651D), dialog = Color(0xFFFFF6EC),
    grainAlpha = 0.05f, night = false,
)

val NightWarm = WarmColors(
    bgTop = Color(0xFF1A1310), bgMid = Color(0xFF241912), bgBottom = Color(0xFF38251A),
    blobA = Color(0x40B5651D), blobB = Color(0x30804A2A), blobC = Color(0x30E8924F),
    ink = Color(0xFFF1E3D6), inkSoft = Color(0xFFB59B88), inkFaint = Color(0xFF6E5A4C),
    accent = Color(0xFFE08A48), accentTop = Color(0xFFEBA06A), onAccent = Color(0xFF2B1A10), accentSoft = Color(0x30E08A48),
    glassTint = Color(0x66241912), borderHi = Color(0x33FFFFFF), borderLo = Color(0x14FFFFFF), highlight = Color(0x14FFFFFF),
    shadow = Color(0x66000000), dialog = Color(0xFF2A1D15),
    grainAlpha = 0.06f, night = true,
)

/** Warm subject colors; defaults for the pre-filled subjects use the first eight. */
val SubjectPalette = listOf(
    Color(0xFFE8743B), Color(0xFFD9645A), Color(0xFFE3B04B), Color(0xFF9AA55B),
    Color(0xFF8AA68A), Color(0xFFD98E8E), Color(0xFFB07A4A), Color(0xFF9C5B5B),
    Color(0xFFF2A65A), Color(0xFFC4623A), Color(0xFFCDAA7D), Color(0xFFB8956A),
)

val LocalWarm = staticCompositionLocalOf { LightWarm }

@Composable
fun StudyTheme(night: Boolean, content: @Composable () -> Unit) {
    val w = if (night) NightWarm else LightWarm
    val scheme = if (night) {
        darkColorScheme(primary = w.accent, onPrimary = w.onAccent, background = w.bgMid, onBackground = w.ink,
            surface = w.dialog, onSurface = w.ink, onSurfaceVariant = w.inkSoft, outline = w.inkFaint)
    } else {
        lightColorScheme(primary = w.accent, onPrimary = w.onAccent, background = w.bgMid, onBackground = w.ink,
            surface = w.dialog, onSurface = w.ink, onSurfaceVariant = w.inkSoft, outline = w.inkFaint)
    }
    CompositionLocalProvider(LocalWarm provides w, LocalContentColor provides w.ink) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            ProvideTextStyle(AppText.body, content)
        }
    }
}
