package com.studywithme.app.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exists only so Android lets this app read the active media sessions (Spotify and other players).
 * It never reads, stores or changes notifications.
 */
class MediaListener : NotificationListenerService()

data class Track(val title: String, val artist: String, val art: Bitmap?, val tint: Int?, val playing: Boolean)

const val SPOTIFY = "com.spotify.music"

/** Picks the session to show: one that is playing first, Spotify first among equals. */
fun <T> pickSession(items: List<T>, pkg: (T) -> String, playing: (T) -> Boolean): T? =
    items.sortedWith(compareByDescending<T> { playing(it) }.thenByDescending { pkg(it) == SPOTIFY }).firstOrNull()

/** Follows whatever music app is playing and exposes its title, artist and album art. Needs notification access. */
class NowPlayingWatcher(private val context: Context) {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()
    private var users = 0

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    fun hasAccess(): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Start following sessions; call [stop] when the screen that shows the card goes away. */
    fun start() {
        if (users++ > 0 || !hasAccess()) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            watch(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            users = 0 // access was revoked in the meantime
        }
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        _track.value = null
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, handler) }
        refresh()
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun refresh() {
        val live = controllers.filter { it.metadata != null && it.playbackState?.state.let { s -> s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED } }
        val c = pickSession(live, { it.packageName }, { it.isPlaying() })
        val m = c?.metadata
        if (c == null || m == null) { _track.value = null; return }
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val previous = _track.value
        _track.value = Track(
            title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = art,
            tint = if (art != null && art == previous?.art) previous.tint else art?.let(::averageColor),
            playing = c.isPlaying(),
        )
    }

    /** The cover's average color, used to tint the glass card the way Spotify tints its player. */
    private fun averageColor(bmp: Bitmap): Int? = runCatching {
        val soft = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
        Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)
    }.getOrNull()
}
