package com.firmlyplanted.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.repository.AudioDownloadState
import com.firmlyplanted.app.data.repository.AudioRepository
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.domain.AudioSpeed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectSettingsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val audioRepository: AudioRepository,
) : ViewModel() {

    val project: StateFlow<MemoryProjectEntity?> = projectRepository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pasted projects keep their full text (it's the only copy), so there's no cache to clear. */
    var isPasted by mutableStateOf(false)
        private set

    /** This project's Hebrew audio download progress; null until it has been checked. */
    val audioState: StateFlow<AudioDownloadState?> = audioRepository.states
        .map { it[projectId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { isPasted = projectRepository.isPasted(projectId) }
        audioRepository.ensureProjectAudio(projectId)
    }

    fun supportsAudio(project: MemoryProjectEntity?): Boolean = audioRepository.supportsAudio(project)

    fun setAudioSpeed(speed: AudioSpeed) {
        viewModelScope.launch { audioRepository.setSpeed(projectId, speed) }
    }

    fun retryAudio() = audioRepository.ensureProjectAudio(projectId)

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
