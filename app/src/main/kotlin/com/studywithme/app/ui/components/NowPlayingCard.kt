package com.studywithme.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.music.Track
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm

/** Shows the song playing in Spotify (or another player) as a small glass card; hidden when nothing plays. */
@Composable
fun NowPlaying(modifier: Modifier = Modifier, artSize: Dp = 52.dp, onOpen: () -> Unit = {}) {
    val c = LocalContainer.current
    val track by c.nowPlaying.track.collectAsStateWithLifecycle()
    // keep the last song on screen while the card fades out
    val last = remember { arrayOfNulls<Track>(1) }
    track?.let { last[0] = it }
    AnimatedVisibility(track != null, modifier, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
        last[0]?.let { NowPlayingCard(it, artSize, onOpen) }
    }
}

@Composable
private fun NowPlayingCard(t: Track, artSize: Dp, onOpen: () -> Unit) {
    val w = LocalWarm.current
    val shape = RoundedCornerShape(18.dp)
    val tint = t.tint?.let { Color(it) }
    val art = remember(t.art) { t.art?.asImageBitmap() }
    Row(
        Modifier.glass(shape).background(tint?.copy(alpha = 0.22f) ?: Color.Transparent)
            .clickable(onClickLabel = stringResource(R.string.music_open_player), onClick = onOpen).padding(10.dp).alpha(if (t.playing) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(artSize).clip(RoundedCornerShape(10.dp)).background(tint ?: w.accentSoft)) {
            art?.let { Image(it, contentDescription = null, modifier = Modifier.size(artSize), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false).padding(end = 6.dp)) {
            Text(t.title.ifBlank { stringResource(R.string.music_unknown_title) }, style = AppText.bodyMedium, maxLines = 1, modifier = Modifier.basicMarquee())
            if (t.artist.isNotBlank()) Text(t.artist, style = AppText.caption, color = w.inkSoft, maxLines = 1, modifier = Modifier.basicMarquee())
        }
    }
}
