package com.firmlyplanted.app.ui.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class VersePlayer actual constructor() {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)
    actual val playing: StateFlow<String?> = _playing.asStateFlow()

    actual fun play(path: String) {
        stop()
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mediaPlayer.setDataSource(path)
            mediaPlayer.setOnCompletionListener { if (player === it) stop() }
            mediaPlayer.setOnErrorListener { mp, _, _ ->
                if (player === mp) stop()
                true
            }
            mediaPlayer.prepare() // A local file of a few KB: fast enough to prepare synchronously.
            mediaPlayer.start()
        } catch (e: Exception) {
            mediaPlayer.release()
            return
        }
        player = mediaPlayer
        _playing.value = path
    }

    actual fun stop() {
        player?.release()
        player = null
        _playing.value = null
    }
}
