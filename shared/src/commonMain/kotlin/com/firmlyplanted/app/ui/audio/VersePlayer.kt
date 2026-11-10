package com.firmlyplanted.app.ui.audio

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.firmlyplanted.app.data.audio.VerseAudio
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays one verse at a time: each of its segments is a time range of an unaltered chapter
 * recording, played from its start to its end with sample-accurate positioning (AVAudioEngine on
 * iOS, Media3 ExoPlayer with an exact MP3 index on Android).
 */
interface VersePlayer {
    /** [VerseAudio.key] of the verse playing now, or null. */
    val playing: StateFlow<String?>

    /** Stops whatever is playing and plays [audio]. */
    fun play(audio: VerseAudio)

    fun stop()
}

/** A player that stops and releases its resources when the composable leaves the screen. */
@Composable
expect fun rememberVersePlayer(): VersePlayer

/** "Listen" / "Stop" for one verse. */
@Composable
fun ListenButton(audio: VerseAudio, player: VersePlayer, modifier: Modifier = Modifier) {
    val playing by player.playing.collectAsState()
    val isThis = playing == audio.key
    OutlinedButton(onClick = { if (isThis) player.stop() else player.play(audio) }, modifier = modifier) {
        Icon(
            if (isThis) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isThis) "Stop" else "Listen")
    }
}
