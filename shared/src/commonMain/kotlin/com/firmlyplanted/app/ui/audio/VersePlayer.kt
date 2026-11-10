package com.firmlyplanted.app.ui.audio

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/** Plays one saved verse clip at a time (MediaPlayer on Android, AVAudioPlayer on iOS). */
expect class VersePlayer() {
    /** Path of the clip playing now, or null. */
    val playing: StateFlow<String?>

    /** Stops whatever is playing and plays the MP3 at [path]. */
    fun play(path: String)

    fun stop()
}

/** A player that stops when the composable leaves the screen. */
@Composable
fun rememberVersePlayer(): VersePlayer {
    val player = remember { VersePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

/** "Listen" / "Stop" for one verse's clip. */
@Composable
fun ListenButton(path: String, player: VersePlayer, modifier: Modifier = Modifier) {
    val playing by player.playing.collectAsState()
    val isThis = playing == path
    OutlinedButton(onClick = { if (isThis) player.stop() else player.play(path) }, modifier = modifier) {
        Icon(
            if (isThis) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(if (isThis) "Stop" else "Listen")
    }
}
