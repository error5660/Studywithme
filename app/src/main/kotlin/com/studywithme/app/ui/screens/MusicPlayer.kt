package com.studywithme.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.music.Track
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.LocalHaze
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.glass
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import java.util.Locale

private fun mss(ms: Long): String = (ms / 1000).let { String.format(Locale.ROOT, "%d:%02d", it / 60, it % 60) }

/**
 * Full player opened from the small now-playing card: big cover, title, progress and
 * previous / play-pause / next, on a blurred copy of the cover in the app's warm tones.
 */
@Composable
fun MusicPlayer(onClose: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val track by c.nowPlaying.track.collectAsStateWithLifecycle()
    // nothing playing any more: close by itself
    LaunchedEffect(track == null) { if (track == null) onClose() }
    val t = track ?: return
    val art = remember(t.art) { t.art?.asImageBitmap() }
    // a local Haze source so the glass panel blurs the cover backdrop, not the timer behind it
    val haze = remember { HazeState() }

    Box(Modifier.fillMaxSize().clickable(enabled = false) {}) {
        Box(Modifier.fillMaxSize().hazeSource(haze).background(w.bgMid)) {
            art?.let { Image(it, null, Modifier.fillMaxSize().blur(70.dp), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(w.bgTop.copy(alpha = 0.55f), w.bgBottom.copy(alpha = 0.85f)))))
        }
        CompositionLocalProvider(LocalHaze provides haze) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextAction(stringResource(R.string.common_close), onClose)
                    if (t.appName.isNotBlank()) {
                        Text(stringResource(R.string.music_playing_from, t.appName), style = AppText.caption, color = w.inkSoft, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(Modifier.size(48.dp)) // balances the close button so the label stays centred
                }
                if (layout.landscape) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(art, t.tint, Modifier.fillMaxHeight(0.9f).aspectRatio(1f))
                        Controls(t, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Cover(art, t.tint, Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(32.dp))
                        Controls(t, Modifier.widthIn(max = 520.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun Cover(art: androidx.compose.ui.graphics.ImageBitmap?, tint: Int?, modifier: Modifier) {
    val w = LocalWarm.current
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.shadow(24.dp, shape, ambientColor = w.shadow, spotColor = w.shadow).clip(shape).background(tint?.let { Color(it) } ?: w.accentSoft)) {
        art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun Controls(t: Track, modifier: Modifier) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    // re-read the clock twice a second so the progress bar moves smoothly
    val nowRt by produceState(SystemClock.elapsedRealtime(), t) { while (true) { value = SystemClock.elapsedRealtime(); delay(500) } }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val pos = t.positionAt(nowRt)
    val fraction = dragging ?: if (t.durationMs > 0) pos.toFloat() / t.durationMs else 0f

    Column(modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(t.title.ifBlank { stringResource(R.string.music_unknown_title) }, style = AppText.title, maxLines = 1, modifier = Modifier.basicMarquee())
        if (t.artist.isNotBlank()) Text(t.artist, style = AppText.body, color = w.inkSoft, maxLines = 1, modifier = Modifier.basicMarquee())
        Spacer(Modifier.height(14.dp))
        if (t.durationMs > 0) {
            Slider(
                value = fraction, onValueChange = { dragging = it }, enabled = t.canSeek,
                onValueChangeFinished = { dragging?.let { c.nowPlaying.seekTo((it * t.durationMs).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = w.accent, activeTrackColor = w.accent, inactiveTrackColor = w.inkFaint.copy(alpha = 0.5f),
                    disabledThumbColor = w.accent, disabledActiveTrackColor = w.accent, disabledInactiveTrackColor = w.inkFaint.copy(alpha = 0.5f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(mss(dragging?.let { (it * t.durationMs).toLong() } ?: pos), style = AppText.number.copy(fontSize = AppText.caption.fontSize), color = w.inkSoft, modifier = Modifier.weight(1f))
                Text(mss(t.durationMs), style = AppText.number.copy(fontSize = AppText.caption.fontSize), color = w.inkSoft)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(stringResource(R.string.music_prev), 56.dp, Color.Transparent, { c.nowPlaying.previous() }) { skip(w.ink, forward = false) }
            GlyphButton(stringResource(if (t.playing) R.string.music_pause else R.string.music_play), 76.dp, w.accent, { c.nowPlaying.playPause() }) {
                if (t.playing) pause(w.onAccent) else play(w.onAccent)
            }
            GlyphButton(stringResource(R.string.music_next), 56.dp, Color.Transparent, { c.nowPlaying.next() }) { skip(w.ink, forward = true) }
        }
    }
}

/** A round button with a hand-drawn media glyph; the label is read out by TalkBack. */
@Composable
private fun GlyphButton(label: String, size: Dp, fill: Color, onClick: () -> Unit, glyph: DrawScope.() -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(fill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(size * 0.36f), onDraw = glyph) }
}

private fun DrawScope.play(color: Color) {
    val p = Path().apply { moveTo(size.width * 0.18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.18f, size.height); close() }
    drawPath(p, color)
}

private fun DrawScope.pause(color: Color) {
    val bar = size.width * 0.3f
    val r = CornerRadius(bar / 3)
    drawRoundRect(color, Offset(size.width * 0.08f, 0f), Size(bar, size.height), r)
    drawRoundRect(color, Offset(size.width * 0.62f, 0f), Size(bar, size.height), r)
}

private fun DrawScope.skip(color: Color, forward: Boolean) {
    val bar = size.width * 0.14f
    val tri = Path().apply {
        if (forward) { moveTo(0f, 0f); lineTo(size.width - bar, size.height / 2); lineTo(0f, size.height) }
        else { moveTo(size.width, 0f); lineTo(bar, size.height / 2); lineTo(size.width, size.height) }
        close()
    }
    drawPath(tri, color)
    drawRoundRect(color, Offset(if (forward) size.width - bar else 0f, 0f), Size(bar, size.height), CornerRadius(bar / 3))
}
