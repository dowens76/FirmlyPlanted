package com.firmlyplanted.app.ui.newproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.data.repository.ScopeBlockedException
import com.firmlyplanted.app.domain.BookCatalog
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.domain.ScopeCheck
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.Versification
import com.firmlyplanted.app.ui.CopyrightNotice
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.resolveStringByName
import com.firmlyplanted.app.ui.simpleFactory

private val STEP_TITLES = listOf("Name", "Text", "Scope", "Daily pace", "Confirm")

@Composable
fun NewProjectScreen(onCreated: (String) -> Unit, onCancel: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: NewProjectViewModel = viewModel(
        factory = simpleFactory { NewProjectViewModel(container.projectRepository, container.translationRepository) },
    )

    LaunchedEffect(viewModel.createResult) {
        viewModel.createResult?.onSuccess { onCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Project — ${STEP_TITLES[viewModel.step]}") },
                navigationIcon = {
                    IconButton(onClick = { if (viewModel.step == 0) onCancel() else viewModel.goTo(viewModel.step - 1) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            when (viewModel.step) {
                0 -> NameStep(viewModel)
                1 -> TextStep(viewModel)
                2 -> ScopeStep(viewModel)
                3 -> PaceStep(viewModel)
                4 -> ConfirmStep(viewModel)
            }
        }
    }
}

@Composable
private fun StepNav(canProceed: Boolean, isLast: Boolean, onNext: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, enabled = canProceed, modifier = Modifier.fillMaxWidth()) {
        Text(if (isLast) "Create Project" else "Next")
    }
}

@Composable
private fun NameStep(vm: NewProjectViewModel) {
    Text("What should we call this memory project?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = vm.name,
        onValueChange = { vm.name = it },
        label = { Text("Project name") },
        placeholder = { Text("e.g. \"Philippians\" or \"John's Upper Room Discourse\"") },
        modifier = Modifier.fillMaxWidth(),
    )
    StepNav(canProceed = true, isLast = false, onNext = { vm.goTo(1) })
}

@Composable
private fun TextStep(vm: NewProjectViewModel) {
    Text("Choose a text to memorize from", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))

    DefaultTranslations.all.forEach { translation ->
        TranslationRow(translation, selected = vm.selectedTranslation?.id == translation.id) {
            vm.selectedTranslation = translation
            vm.book = ""
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { vm.showMore = !vm.showMore; if (vm.showMore) vm.loadMoreCatalog(null) }) {
        Text(if (vm.showMore) "Hide more texts" else "More texts…")
    }

    if (vm.showMore) {
        var languageFilter by remember { mutableStateOf("") }
        OutlinedTextField(
            value = languageFilter,
            onValueChange = { languageFilter = it },
            label = { Text("Filter by language") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.loadMoreCatalog(languageFilter) }, modifier = Modifier.fillMaxWidth()) {
            Text("Search fetch.bible catalog")
        }
        Spacer(Modifier.height(8.dp))
        when {
            vm.moreLoading -> CircularProgressIndicator()
            vm.moreError != null -> Text("Couldn't load catalog: ${vm.moreError}", color = MaterialTheme.colorScheme.error)
            else -> LazyColumn(Modifier.height(240.dp)) {
                items(vm.moreResults, key = { it.id }) { translation ->
                    TranslationRow(translation, selected = vm.selectedTranslation?.id == translation.id) {
                        vm.selectedTranslation = translation
                        vm.book = ""
                    }
                }
            }
        }
    }

    StepNav(canProceed = vm.selectedTranslation != null, isLast = false, onNext = { vm.goTo(2) })
}

@Composable
private fun TranslationRow(translation: Translation, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(translation.displayName) },
        supportingContent = { Text("${translation.language} · ${translation.licenseSummary}") },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScopeStep(vm: NewProjectViewModel) {
    val translation = vm.selectedTranslation
    if (translation == null) {
        Text("Pick a text first.")
        return
    }
    val books = BookCatalog.booksFor(translation.testaments)

    Text("Which passage do you want to memorize?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))

    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = vm.book,
            onValueChange = {},
            readOnly = true,
            label = { Text("Book") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            books.forEach { book ->
                DropdownMenuItem(text = { Text(book.name) }, onClick = { vm.selectBook(book.name); expanded = false })
            }
        }
    }

    if (vm.book.isNotBlank() && !Versification.hasData(vm.book)) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Verse ranges for this book aren't preloaded — pick a number and use \"Check this range\" to confirm it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val startChapterOptions = remember(vm.book) { (1..Versification.chapterCount(vm.book)).toList() }
    val startVerseOptions = remember(vm.book, vm.startChapter) { (1..Versification.lastVerse(vm.book, vm.startChapter)).toList() }
    val endChapterOptions = remember(vm.book) { (1..Versification.chapterCount(vm.book)).toList() }
    val endVerseOptions = remember(vm.book, vm.endChapter) { (1..Versification.lastVerse(vm.book, vm.endChapter)).toList() }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntDropdownField("Start ch.", vm.startChapter, startChapterOptions, Modifier.weight(1f)) { vm.selectStartChapter(it) }
        IntDropdownField("Start v.", vm.startVerse, startVerseOptions, Modifier.weight(1f)) { vm.selectStartVerse(it) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntDropdownField("End ch.", vm.endChapter, endChapterOptions, Modifier.weight(1f)) { vm.selectEndChapter(it) }
        IntDropdownField("End v. (defaults to last verse)", vm.endVerse, endVerseOptions, Modifier.weight(1f)) { vm.selectEndVerse(it) }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { vm.checkScope() }, enabled = vm.book.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Check this range")
    }

    Spacer(Modifier.height(8.dp))
    when {
        vm.previewLoading -> CircularProgressIndicator()
        vm.previewResult != null -> vm.previewResult!!.fold(
            onSuccess = { preview ->
                when (val check = preview.check) {
                    is ScopeCheck.Blocked -> Text(resolveStringByName(check.messageResName), color = MaterialTheme.colorScheme.error)
                    ScopeCheck.Ok -> Text("${preview.verseCount} verses — looks good.", color = MaterialTheme.colorScheme.primary)
                }
            },
            onFailure = { Text("Couldn't check that range: ${it.message}", color = MaterialTheme.colorScheme.error) },
        )
    }

    val canProceed = vm.previewResult?.getOrNull()?.check == ScopeCheck.Ok
    StepNav(canProceed = canProceed, isLast = false, onNext = { vm.goTo(3) })
}

/** A dropdown of valid numbers (chapters or verses) — see Versification for where the options come from. */
@Composable
private fun IntDropdownField(label: String, value: Int, options: List<Int>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.height(320.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.toString()) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun PaceStep(vm: NewProjectViewModel) {
    Text("How fast do you want to go?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))

    Text("New verses per day: ${vm.newPerDay}")
    Slider(value = vm.newPerDay.toFloat(), onValueChange = { vm.newPerDay = it.toInt() }, valueRange = 1f..10f, steps = 8)

    Spacer(Modifier.height(16.dp))
    Text("Verses reviewed per day: ${vm.reviewPerDay}")
    Slider(value = vm.reviewPerDay.toFloat(), onValueChange = { vm.reviewPerDay = it.toInt() }, valueRange = 5f..100f, steps = 18)

    StepNav(canProceed = true, isLast = false, onNext = { vm.goTo(4) })
}

@Composable
private fun ConfirmStep(vm: NewProjectViewModel) {
    val translation = vm.selectedTranslation ?: return
    val displayName = vm.name.ifBlank { "${vm.book} ${vm.startChapter}:${vm.startVerse}-${vm.endChapter}:${vm.endVerse}" }

    Column {
        Text("Ready to start memorizing", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleSmall)
                Text("${vm.book} ${vm.startChapter}:${vm.startVerse} – ${vm.endChapter}:${vm.endVerse}")
                Text("${translation.displayName}")
                Text("${vm.newPerDay} new verses/day, ${vm.reviewPerDay} reviewed/day")
            }
        }
        CopyrightNotice(translation, modifier = Modifier.fillMaxWidth())

        vm.createResult?.onFailure { error ->
            val message = if (error is ScopeBlockedException) resolveStringByName(error.messageResName) else error.message
            Spacer(Modifier.height(8.dp))
            Text(message ?: "Something went wrong creating the project.", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.createProject() }, enabled = !vm.creating, modifier = Modifier.fillMaxWidth()) {
            if (vm.creating) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("Create Project")
        }
    }
}
