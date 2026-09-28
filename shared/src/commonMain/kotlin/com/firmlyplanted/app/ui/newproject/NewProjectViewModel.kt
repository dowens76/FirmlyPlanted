package com.firmlyplanted.app.ui.newproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.ScopePreview
import com.firmlyplanted.app.data.repository.TranslationRepository
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.Versification
import kotlinx.coroutines.launch

class NewProjectViewModel(
    private val projectRepository: ProjectRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    var step by mutableStateOf(0)
        private set

    var name by mutableStateOf("")

    var selectedTranslation by mutableStateOf<Translation?>(DefaultTranslations.esv)
    var moreResults by mutableStateOf<List<Translation>>(emptyList())
        private set
    var moreLoading by mutableStateOf(false)
        private set
    var moreError by mutableStateOf<String?>(null)
        private set
    var showMore by mutableStateOf(false)

    var book by mutableStateOf("")
    var startChapter by mutableStateOf(1)
    var startVerse by mutableStateOf(1)
    var endChapter by mutableStateOf(1)
    var endVerse by mutableStateOf(1)

    /** Picking a new book resets the range — the old chapter/verse numbers rarely make sense in it. */
    fun selectBook(name: String) {
        book = name
        startChapter = 1
        startVerse = 1
        endChapter = 1
        endVerse = Versification.lastVerse(name, 1)
        previewResult = null
    }

    fun selectStartChapter(chapter: Int) {
        startChapter = chapter
        startVerse = 1
        previewResult = null
    }

    fun selectStartVerse(verse: Int) {
        startVerse = verse
        previewResult = null
    }

    /** Defaults the end verse to the last verse of the newly picked chapter — usually what's wanted. */
    fun selectEndChapter(chapter: Int) {
        endChapter = chapter
        endVerse = Versification.lastVerse(book, chapter)
        previewResult = null
    }

    fun selectEndVerse(verse: Int) {
        endVerse = verse
        previewResult = null
    }

    var newPerDay by mutableStateOf(2)
    var reviewPerDay by mutableStateOf(20)

    var previewResult by mutableStateOf<Result<ScopePreview>?>(null)
        private set
    var previewLoading by mutableStateOf(false)
        private set

    var createResult by mutableStateOf<Result<String>?>(null)
        private set
    var creating by mutableStateOf(false)
        private set

    fun goTo(newStep: Int) {
        step = newStep
    }

    fun loadMoreCatalog(languageFilter: String?) {
        moreLoading = true
        moreError = null
        viewModelScope.launch {
            val result = runCatching { translationRepository.fetchMoreCatalog(languageFilter) }
            moreResults = result.getOrDefault(emptyList())
            moreError = result.exceptionOrNull()?.message
            moreLoading = false
        }
    }

    fun checkScope() {
        val translation = selectedTranslation ?: return
        if (book.isBlank()) return
        previewLoading = true
        previewResult = null
        viewModelScope.launch {
            previewResult = projectRepository.previewScope(
                translation = translation,
                bookName = book,
                startChapter = startChapter,
                startVerse = startVerse,
                endChapter = endChapter,
                endVerse = endVerse,
            )
            previewLoading = false
        }
    }

    fun createProject() {
        val translation = selectedTranslation ?: return
        creating = true
        viewModelScope.launch {
            if (!translation.isDefault) translationRepository.cacheTranslation(translation)
            createResult = projectRepository.createProject(
                name = name.ifBlank { "$book $startChapter:$startVerse-$endChapter:$endVerse" },
                translation = translation,
                bookName = book,
                startChapter = startChapter,
                startVerse = startVerse,
                endChapter = endChapter,
                endVerse = endVerse,
                newVersesPerDay = newPerDay,
                reviewVersesPerDay = reviewPerDay,
            )
            creating = false
        }
    }
}
