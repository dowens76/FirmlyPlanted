package com.firmlyplanted.app.ui.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class VersePlayer actual constructor() {
    private var player: AVAudioPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)
    actual val playing: StateFlow<String?> = _playing.asStateFlow()

    // AVAudioPlayer holds its delegate weakly, so keep it here.
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (this@VersePlayer.player === player) stop()
        }
    }

    actual fun play(path: String) {
        stop()
        // Playback category: audible even with the ring/silent switch on, like other audio apps.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
        val audioPlayer = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
        audioPlayer.delegate = delegate
        if (!audioPlayer.prepareToPlay() || !audioPlayer.play()) return
        player = audioPlayer
        _playing.value = path
    }

    actual fun stop() {
        player?.stop()
        player = null
        _playing.value = null
    }
}
