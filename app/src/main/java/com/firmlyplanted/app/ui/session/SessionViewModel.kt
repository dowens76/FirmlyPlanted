package com.firmlyplanted.app.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
