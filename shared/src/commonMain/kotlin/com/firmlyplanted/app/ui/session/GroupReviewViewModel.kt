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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Verses shown together at once at the end of a session, capped at this many, in canonical order. */
const val GROUP_REVIEW_MAX_VERSES = 10

class GroupReviewViewModel(
    projectId: String,
    verseIds: List<String>,
    projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    private val verseIdSet = verseIds.toSet()

    /** The session's verses, restricted to canonical (book/chapter/verse) order, per the app's review convention. */
    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .map { all ->
            all.filter { it.id in verseIdSet }
                .sortedBy { it.orderIndex }
                .take(GROUP_REVIEW_MAX_VERSES)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var translation by mutableStateOf<Translation?>(null)
        private set

    init {
        viewModelScope.launch {
            val project = projectRepository.observeProject(projectId).first()
            translation = project?.let { translationRepository.getById(it.translationId) }
        }
    }
}
