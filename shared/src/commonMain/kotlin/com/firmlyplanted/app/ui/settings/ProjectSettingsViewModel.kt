package com.firmlyplanted.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectSettingsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    val project: StateFlow<MemoryProjectEntity?> = projectRepository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updatePace(newVersesPerDay: Int, reviewVersesPerDay: Int) {
        viewModelScope.launch { projectRepository.updatePace(projectId, newVersesPerDay, reviewVersesPerDay) }
    }

    fun clearCacheNow() {
        viewModelScope.launch { projectRepository.clearCache(projectId) }
    }

    fun markComplete(onDone: () -> Unit) {
        viewModelScope.launch {
            projectRepository.completeProject(projectId)
            onDone()
        }
    }

    fun archive(onDone: () -> Unit) {
        viewModelScope.launch {
            projectRepository.archiveProject(projectId)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            onDone()
        }
    }
}
