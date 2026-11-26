package com.studywithme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.studywithme.app.ui.theme.LocalWarm
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalHaze = staticCompositionLocalOf<HazeState> { error("HazeState was not provided") }

val GlassShape = RoundedCornerShape(20.dp)

/** Frosted glass: real backdrop blur, a warm tint, a thin white border, an inner top highlight and a very soft shadow. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glass(shape: Shape = GlassShape): Modifier {
    val w = LocalWarm.current
    val haze = LocalHaze.current
    return this
        .shadow(14.dp, shape, ambientColor = w.shadow, spotColor = w.shadow)
        .clip(shape)
        .hazeEffect(haze) {
            blurRadius = 24.dp
            // Blur at 1/3 resolution: far cheaper on the GPU and visually identical for a soft frosted look.
            inputScale = HazeInputScale.Auto
            tints = listOf(HazeTint(w.glassTint))
            noiseFactor = 0.04f
        }
        .background(Brush.verticalGradient(0f to w.highlight, 0.55f to Color.Transparent))
        .border(1.dp, Brush.linearGradient(listOf(w.borderHi, w.borderLo)), shape)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}
