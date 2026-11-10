package com.firmlyplanted.app.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.firmlyplanted.app.data.audio.VerseAudio
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeCompletionDataPlayedBack
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberVersePlayer(): VersePlayer {
    val player = remember { EngineVersePlayer() }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

/**
 * Plays frame ranges of the chapter MP3s with AVAudioPlayerNode.scheduleSegment. AVAudioFile
 * positions MP3s exactly (it reads the frame headers rather than estimating from the file's
 * coarse VBR seek table), so each verse starts and stops where the ePub's SMIL says.
 */
@OptIn(ExperimentalForeignApi::class)
private class EngineVersePlayer : VersePlayer {
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private var connectedFormat: AVAudioFormat? = null

    /** Bumped on every play/stop, so completions from an earlier play are ignored. */
    private var generation = 0

    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()

    init {
        engine.attachNode(node)
    }

    override fun play(audio: VerseAudio) {
        stop()
        val files = audio.segments.map { segment ->
            AVAudioFile(forReading = NSURL.fileURLWithPath(segment.path), error = null) ?: return
        }
        val format = files.first().processingFormat
        if (connectedFormat?.isEqual(format) != true) {
            engine.connect(node, to = engine.mainMixerNode, format = format)
            connectedFormat = format
        }

        // Playback category: audible even with the ring/silent switch on, like other audio apps.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        val gen = ++generation
        audio.segments.forEachIndexed { index, segment ->
            val file = files[index]
            val rate = file.processingFormat.sampleRate
            val first = (segment.start * rate).toLong().coerceIn(0L, file.length)
            val last = (segment.end * rate).toLong().coerceIn(first, file.length)
            val isLast = index == audio.segments.lastIndex
            node.scheduleSegment(
                file,
                startingFrame = first,
                frameCount = (last - first).toUInt(),
                atTime = null,
                completionCallbackType = AVAudioPlayerNodeCompletionDataPlayedBack,
            ) { _ ->
                if (isLast) dispatch_async(dispatch_get_main_queue()) { if (generation == gen) stop() }
            }
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            node.stop()
            return
        }
        node.play()
        _playing.value = audio.key
    }

    override fun stop() {
        generation++
        node.stop()
        if (engine.running) engine.stop()
        _playing.value = null
    }
}
