package com.firmlyplanted.app.ui.session

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
    private val audioRepository: AudioRepository,
) : ViewModel() {

    /** Recomposes the screen as clips finish downloading, so Listen buttons appear. */
    val audioStates = audioRepository.states

    private var project: MemoryProjectEntity? = null

    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drives which script-specific font (Ezra SIL, Gentium) the verse text renders with. */
    var translation by mutableStateOf<Translation?>(null)
        private set

    var queue by mutableStateOf<List<String>>(emptyList())
        private set
    var currentIndex by mutableStateOf(0)
        private set
    var revealed by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set

    private var newIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            val project = projectRepository.observeProject(projectId).first()
            this@SessionViewModel.project = project
            translation = project?.let { translationRepository.getById(it.translationId) }
            audioRepository.ensureProjectAudio(projectId)

            val plan = projectRepository.getTodayPlan(projectId)
            newIds = plan.newVerseIds.toSet()
            // Review what's already learned before introducing today's new material, per the
            // Davis / Scripta Memoria cumulative-review approach.
            queue = plan.dueReviewIds + plan.newVerseIds
            finished = queue.isEmpty()
            loading = false
        }
    }

    fun currentVerseId(): String? = queue.getOrNull(currentIndex)

    /** The verse's Hebrew audio clip, if this is a WLC project and it's downloaded. */
    fun clipPathFor(verse: VerseEntity): String? = project?.let { audioRepository.clipPath(it, verse) }

    fun isCurrentNew(): Boolean = currentVerseId() in newIds

    fun reveal() {
        revealed = true
    }

    fun confirmLearned() {
        val id = currentVerseId() ?: return
        viewModelScope.launch {
            projectRepository.markIntroduced(id)
            advance()
        }
    }

    fun submitReview(recalledOk: Boolean) {
        val id = currentVerseId() ?: return
        viewModelScope.launch {
            projectRepository.markReviewed(id, recalledOk)
            advance()
        }
    }

    private fun advance() {
        revealed = false
        if (currentIndex + 1 >= queue.size) {
            finished = true
        } else {
            currentIndex++
        }
    }
}
