package com.firmlyplanted.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val projectRepository: ProjectRepository) : ViewModel() {
    val projects: StateFlow<List<MemoryProjectEntity>> = projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun progressFor(projectId: String): Pair<Int, Int> = projectRepository.progressSummary(projectId)
}
