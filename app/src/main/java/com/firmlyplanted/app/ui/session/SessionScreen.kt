package com.firmlyplanted.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.domain.VerseMasking
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory
import com.firmlyplanted.app.ui.theme.fontFamilyForLanguage
import com.firmlyplanted.app.ui.theme.scriptureTextStyle
import kotlin.math.abs

@Composable
fun SessionScreen(projectId: String, onDone: (List<String>) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SessionViewModel = viewModel(
        factory = simpleFactory { SessionViewModel(projectId, container.projectRepository, container.translationRepository) },
    )
    val verses by viewModel.verses.collectAsStateWithLifecycle()
    val verseFontFamily = fontFamilyForLanguage(viewModel.translation?.language)

    Scaffold(topBar = { TopAppBar(title = { Text("Today's Session") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                viewModel.loading -> CircularProgressIndicator()
                viewModel.finished -> SessionComplete(onDone = { onDone(viewModel.queue) })
                else -> {
                    val verse = verses.find { it.id == viewModel.currentVerseId() }
                    if (verse == null) {
                        CircularProgressIndicator()
                    } else {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            LinearProgressIndicator(
                                progress = { (viewModel.currentIndex + 1).toFloat() / viewModel.queue.size.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "${verse.book} ${verse.chapter}:${verse.verseNumber}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (viewModel.isCurrentNew()) {
                                NewVerseCard(text = verse.text, fontFamily = verseFontFamily, onLearned = { viewModel.confirmLearned() })
                            } else {
                                ReviewVerseCard(
                                    text = verse.text,
                                    fontFamily = verseFontFamily,
                                    revealed = viewModel.revealed,
                                    onReveal = { viewModel.reveal() },
                                    onResult = { ok -> viewModel.submitReview(ok) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LearningMode { READ, CYCLE, FIRST_LETTER }

@Composable
private fun NewVerseCard(text: String?, fontFamily: FontFamily, onLearned: () -> Unit) {
    var mode by remember(text) { mutableStateOf(LearningMode.READ) }
    var round by remember(text) { mutableStateOf(1) }
    // Re-created (and so reset to false) whenever the mode or round changes, so a peek never
    // carries over into the next round or mode.
    var peeking by remember(text, mode, round) { mutableStateOf(false) }

    val displayText = when {
        text == null -> null
        peeking -> text
        mode == LearningMode.CYCLE -> VerseMasking.forRound(text, round)
        mode == LearningMode.FIRST_LETTER -> VerseMasking.firstLetterOnly(text)
        else -> text
    }
    val caption = when (mode) {
        LearningMode.READ -> "New verse — read it over a few times."
        LearningMode.CYCLE -> "Practice round $round of ${VerseMasking.TOTAL_ROUNDS} — tap the verse to peek."
        LearningMode.FIRST_LETTER -> "First letters only — tap the verse to peek."
    }

    Card(onClick = { if (text != null) peeking = !peeking }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(caption, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                displayText ?: "Not cached yet — connect to the internet and reopen Today.",
                style = scriptureTextStyle(),
                fontFamily = fontFamily,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { mode = LearningMode.CYCLE; round = 1 },
            enabled = text != null,
            modifier = Modifier.weight(1f),
        ) { Text("Practice cycle") }
        OutlinedButton(
            onClick = { mode = LearningMode.FIRST_LETTER },
            enabled = text != null,
            modifier = Modifier.weight(1f),
        ) { Text("First letters") }
    }

    if (mode == LearningMode.CYCLE) {
        Spacer(Modifier.height(12.dp))
        val onLastRound = round >= VerseMasking.TOTAL_ROUNDS
        Button(
            onClick = { if (onLastRound) mode = LearningMode.READ else round++ },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (onLastRound) "Done practicing" else "Next round ($round/${VerseMasking.TOTAL_ROUNDS})")
        }
    }

    Spacer(Modifier.height(20.dp))
    Button(onClick = onLearned, enabled = text != null, modifier = Modifier.fillMaxWidth()) {
        Text("I've got it — start reviewing")
    }
}

@Composable
private fun ReviewVerseCard(
    text: String?,
    fontFamily: FontFamily,
    revealed: Boolean,
    onReveal: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    // A shorter, swipe-driven version of the initial learning cycle, offered as an alternative
    // to jumping straight to Reveal, and also used to reinforce a verse right after a missed
    // recall — all reset whenever a new verse comes up.
    var cycling by remember(text) { mutableStateOf(false) }
    var round by remember(text) { mutableStateOf(1) }
    var peeking by remember(text, round) { mutableStateOf(false) }
    // True when the cycle was launched from "Not quite" rather than from the pre-reveal button,
    // so completing it should submit the (already-known) failed result instead of revealing.
    var reinforcing by remember(text) { mutableStateOf(false) }
    var hintShown by remember(text) { mutableStateOf(false) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }

    val cycleText = text?.let { VerseMasking.forRound(it, round, VerseMasking.REVIEW_ROUNDS) }
    val hintText = text?.let { VerseMasking.firstWords(it) }

    Card(
        onClick = { if (cycling) peeking = !peeking },
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (cycling) {
                    Modifier.pointerInput(round) {
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                dragTotal += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(dragTotal) > swipeThresholdPx) {
                                    peeking = false
                                    if (round >= VerseMasking.REVIEW_ROUNDS) {
                                        cycling = false
                                        if (reinforcing) {
                                            reinforcing = false
                                            onResult(false)
                                        } else {
                                            onReveal()
                                        }
                                    } else {
                                        round++
                                    }
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                when {
                    cycling -> "Review round $round of ${VerseMasking.REVIEW_ROUNDS} — swipe to continue, tap to peek."
                    else -> "Try to recall this verse, then reveal it."
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(12.dp))
            if (cycling) {
                Text(
                    (if (peeking) text else cycleText) ?: "Not cached yet — connect to the internet and reopen Today.",
                    style = scriptureTextStyle(),
                    fontFamily = fontFamily,
                )
            } else if (revealed) {
                Text(
                    text ?: "Not cached yet — connect to the internet and reopen Today.",
                    style = scriptureTextStyle(),
                    fontFamily = fontFamily,
                )
            } else if (hintShown) {
                Text(
                    hintText ?: "Not cached yet — connect to the internet and reopen Today.",
                    style = scriptureTextStyle(),
                    fontFamily = fontFamily,
                )
            }
        }
    }
    if (cycling) {
        Spacer(Modifier.height(12.dp))
        PageIndicator(pageCount = VerseMasking.REVIEW_ROUNDS, currentPage = round - 1, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
    when {
        cycling -> {}
        !revealed -> {
            TextButton(onClick = { hintShown = !hintShown }, enabled = text != null) {
                Text(if (hintShown) "Hide hint" else "Hint")
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { cycling = true; round = 1 },
                    enabled = text != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Practice cycle (${VerseMasking.REVIEW_ROUNDS}x)") }
                Button(onClick = onReveal, enabled = text != null, modifier = Modifier.weight(1f)) { Text("Reveal") }
            }
        }
        else -> {
            Text("Did you recall it correctly?", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { cycling = true; round = 1; reinforcing = true },
                    enabled = text != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Not quite") }
                Button(onClick = { onResult(true) }, modifier = Modifier.weight(1f)) { Text("Got it") }
            }
        }
    }
}

/** Dot-style progress indicator for a swipe-through-pages flow; the current page is lighter. */
@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        repeat(pageCount) { page ->
            val color = if (page == currentPage) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.primary
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun SessionComplete(onDone: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Session complete!", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Come back tomorrow for more.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}
