package com.studywithme.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Slowly drifting warm gradient with a faint grain. This is also the Haze source the glass cards blur. */
@Composable
fun WarmBackground(hazeState: HazeState, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    // Updated ~8x per second: smooth enough for a 90 s drift, and cheap for the blur behind every card.
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            phase = ((System.nanoTime() - start) / 1e9 / DRIFT_SECONDS % 1.0).toFloat()
            delay(120)
        }
    }
    val grain = remember { grainBrush() }

    Canvas(modifier.fillMaxSize().hazeSource(hazeState)) {
        drawRect(Brush.verticalGradient(listOf(w.bgTop, w.bgMid, w.bgBottom)))
        val a = phase * 2 * PI.toFloat()
        val big = maxOf(size.width, size.height)
        blob(w.blobA, size.width * (0.18f + 0.10f * sin(a)), size.height * (0.22f + 0.08f * cos(a)), big * 0.55f)
        blob(w.blobB, size.width * (0.80f + 0.08f * cos(a * 2)), size.height * (0.40f + 0.10f * sin(a)), big * 0.50f)
        blob(w.blobC, size.width * (0.45f + 0.12f * sin(a + 2f)), size.height * (0.92f + 0.05f * cos(a)), big * 0.60f)
        drawRect(grain, alpha = w.grainAlpha)
    }
}

private const val DRIFT_SECONDS = 90.0

private fun DrawScope.blob(color: Color, cx: Float, cy: Float, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), Offset(cx, cy), radius), radius, Offset(cx, cy))
}

private fun grainBrush(): ShaderBrush {
    val n = 192
    val rnd = Random(7)
    val px = IntArray(n * n) { val g = rnd.nextInt(256); (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
