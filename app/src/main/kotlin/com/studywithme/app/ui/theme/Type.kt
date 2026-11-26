package com.studywithme.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.studywithme.app.R

/** Pretendard (SIL OFL 1.1) bundled in res/font. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
)

private fun style(weight: FontWeight, size: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = Pretendard, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = tracking)

private const val TABULAR = "tnum"

/** Weight hierarchy: thin for big numbers, light for secondary numbers, regular for body, medium for labels, semibold for titles. */
object AppText {
    val timer = style(FontWeight.Thin, 96.sp, tracking = (-0.02).em).copy(fontFeatureSettings = TABULAR)
    val hero = style(FontWeight.Thin, 72.sp, tracking = (-0.02).em).copy(fontFeatureSettings = TABULAR)
    val number = style(FontWeight.Light, 16.sp).copy(fontFeatureSettings = TABULAR)
    val title = style(FontWeight.SemiBold, 24.sp, 32.sp, (-0.01).em)
    val section = style(FontWeight.Medium, 14.sp, 20.sp, 0.02.em)
    val body = style(FontWeight.Normal, 16.sp, 24.sp)
    val bodyMedium = style(FontWeight.Medium, 16.sp, 24.sp)
    val label = style(FontWeight.Medium, 14.sp, 20.sp)
    val caption = style(FontWeight.Normal, 13.sp, 18.sp)
}

private fun TextStyle.pretendard() = copy(fontFamily = Pretendard)

val AppTypography = with(Typography()) {
    copy(
        displayLarge = displayLarge.pretendard(), displayMedium = displayMedium.pretendard(), displaySmall = displaySmall.pretendard(),
        headlineLarge = headlineLarge.pretendard(), headlineMedium = headlineMedium.pretendard(), headlineSmall = headlineSmall.pretendard(),
        titleLarge = titleLarge.pretendard(), titleMedium = titleMedium.pretendard(), titleSmall = titleSmall.pretendard(),
        bodyLarge = bodyLarge.pretendard(), bodyMedium = bodyMedium.pretendard(), bodySmall = bodySmall.pretendard(),
        labelLarge = labelLarge.pretendard(), labelMedium = labelMedium.pretendard(), labelSmall = labelSmall.pretendard(),
    )
}
