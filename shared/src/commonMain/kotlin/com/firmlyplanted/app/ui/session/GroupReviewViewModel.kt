package com.firmlyplanted.app.ui.session

import com.firmlyplanted.app.data.audio.VerseAudio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.repository.AudioRepository
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.TranslationRepository
import com.firmlyplanted.app.domain.Translation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GroupReviewViewModel(
    projectId: String,
    projectRepository: ProjectRepository,
    translationRepository: TranslationRepository,
    isOnline: () -> Boolean,
    private val audioRepository: AudioRepository,
) : ViewModel() {

    private var project: MemoryProjectEntity? = null

    /** Recomposes the screen as clips finish downloading, so Listen buttons appear. */
    val audioStates = audioRepository.states

    /** Every verse learned so far, in passage order; null while loading. */
    var verses by mutableStateOf<List<VerseEntity>?>(null)
        private set

    var translation by mutableStateOf<Translation?>(null)
        private set

    init {
        viewModelScope.launch {
            val project = projectRepository.observeProject(projectId).first()
            this@GroupReviewViewModel.project = project
            translation = project?.let { translationRepository.getById(it.translationId) }
            verses = projectRepository.loadLearnedPassage(projectId, translation, isOnline())
        }
    }

    /** The verse's Hebrew audio clip, if this is a WLC project and it's downloaded. */
    fun audioFor(verse: VerseEntity): VerseAudio? = project?.let { audioRepository.verseAudio(it, verse) }
}
