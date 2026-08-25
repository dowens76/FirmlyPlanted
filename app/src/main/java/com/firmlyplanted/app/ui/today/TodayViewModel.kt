package com.firmlyplanted.app.ui.today

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.remote.NetworkModule
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.TranslationRepository
import com.firmlyplanted.app.data.repository.toProgress
import com.firmlyplanted.app.domain.ReviewScheduler
import com.firmlyplanted.app.domain.TodayPlan
import com.firmlyplanted.app.domain.Translation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
    private val appContext: Context,
) : ViewModel() {

    val project: StateFlow<MemoryProjectEntity?> = projectRepository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val verses: StateFlow<List<VerseEntity>> = projectRepository.observeVerses(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayPlan: StateFlow<TodayPlan> = combine(project.filterNotNull(), verses) { proj, vs ->
        ReviewScheduler.planToday(vs.map { it.toProgress() }, proj.newVersesPerDay, proj.reviewVersesPerDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayPlan(emptyList(), emptyList()))

    var translation by mutableStateOf<Translation?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var offline by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val proj = project.filterNotNull().first()
            translation = translationRepository.getById(proj.translationId)
            refresh()
        }
    }

    fun refresh() {
        val t = translation ?: return
        viewModelScope.launch {
            refreshing = true
            val online = NetworkModule.isOnline(appContext)
            offline = !online
            projectRepository.ensureWindowCached(projectId, t, online)
            refreshing = false
        }
    }
}
