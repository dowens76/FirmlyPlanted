package com.firmlyplanted.app.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.VerseEntity
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
) : ViewModel() {

    /** Every verse learned so far, in passage order; null while loading. */
    var verses by mutableStateOf<List<VerseEntity>?>(null)
        private set

    var translation by mutableStateOf<Translation?>(null)
        private set

    init {
        viewModelScope.launch {
            val project = projectRepository.observeProject(projectId).first()
            translation = project?.let { translationRepository.getById(it.translationId) }
            verses = projectRepository.loadLearnedPassage(projectId, translation, isOnline())
        }
    }
}
