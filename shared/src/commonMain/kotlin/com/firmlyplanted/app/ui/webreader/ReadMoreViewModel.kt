package com.firmlyplanted.app.ui.webreader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.TranslationRepository
import com.firmlyplanted.app.domain.ReadMoreUrlBuilder
import com.firmlyplanted.app.domain.Translation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReadMoreViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    var translation by mutableStateOf<Translation?>(null)
        private set
    var url by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            val project = projectRepository.observeProject(projectId).filterNotNull().first()
            val t = translationRepository.getById(project.translationId)
            translation = t
            url = t?.let { ReadMoreUrlBuilder.build(it, project.book, project.startChapter) }
            loading = false
        }
    }
}
