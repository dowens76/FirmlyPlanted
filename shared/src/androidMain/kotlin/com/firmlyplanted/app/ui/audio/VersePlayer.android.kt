package com.firmlyplanted.app.ui.audio

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.firmlyplanted.app.data.audio.VerseAudio
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
actual fun rememberVersePlayer(): VersePlayer {
    val context = LocalContext.current.applicationContext
    val player = remember { ExoVersePlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/**
 * Plays each segment as a clipped MediaItem of the unaltered chapter MP3. The MP3s are VBR, and
 * their built-in seek table is too coarse (seconds off); FLAG_ENABLE_INDEX_SEEKING makes the
 * extractor index every frame, so clipping starts and ends exactly where the ePub's SMIL says.
 */
private class ExoVersePlayer(context: Context) : VersePlayer {
    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()

    private val exo: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultMediaSourceFactory(
            context,
            DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING),
        ),
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .build()
        .apply {
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) stop()
                    }

                    override fun onPlayerError(error: PlaybackException) = stop()
                },
            )
        }

    override fun play(audio: VerseAudio) {
        stop()
        exo.setMediaItems(
            audio.segments.map { segment ->
                MediaItem.Builder()
                    .setUri(Uri.fromFile(File(segment.path)))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((segment.start * 1000).toLong())
                            .setEndPositionMs((segment.end * 1000).toLong())
                            .build(),
                    )
                    .build()
            },
        )
        exo.prepare()
        exo.play()
        _playing.value = audio.key
    }

    override fun stop() {
        exo.stop()
        exo.clearMediaItems()
        _playing.value = null
    }

    fun release() {
        stop()
        exo.release()
    }
}
