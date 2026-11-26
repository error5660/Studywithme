package com.studywithme.app.noise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays one looping white-noise track. [apply] is called whenever the kind, volume or "should play" changes. */
class NoiseController(private val scope: CoroutineScope) {
    private var track: AudioTrack? = null
    private var kind = 0
    private var token = 0
    private val cache = HashMap<Int, ShortArray>()

    fun apply(kind: Int, volume: Int, play: Boolean) {
        val wanted = if (play) kind else 0
        track?.setVolume(volume / 100f)
        if (wanted == this.kind) return
        this.kind = wanted
        val my = ++token
        track?.run { stop(); release() }
        track = null
        if (wanted == 0) return
        scope.launch {
            val pcm = withContext(Dispatchers.Default) { cache.getOrPut(wanted) { NoiseSynth.generate(wanted) } }
            if (my != token) return@launch // a newer request replaced this one while it was being generated
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(NoiseSynth.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build().apply {
                    write(pcm, 0, pcm.size)
                    setLoopPoints(0, pcm.size, -1)
                    setVolume(volume / 100f)
                    play()
                }
        }
    }
}
