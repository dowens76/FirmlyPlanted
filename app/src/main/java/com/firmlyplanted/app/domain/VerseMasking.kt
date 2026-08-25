package com.firmlyplanted.app.domain

import kotlin.math.abs

/** Utilities for progressively hiding words in a verse during the initial learning drill. */
object VerseMasking {

    private val WORD_REGEX = Regex("""\S+""")

    const val TOTAL_ROUNDS = 10

    /** Replaces each letter/digit in [word] with '-', leaving punctuation/marks as-is. */
    private fun maskAllChars(word: String): String =
        word.map { ch -> if (ch.isLetterOrDigit()) '-' else ch }.joinToString("")

    /** Keeps [word]'s first letter, masking the rest of its letters/digits. */
    private fun maskAllButFirstChar(word: String): String {
        val firstLetterIndex = word.indexOfFirst { it.isLetterOrDigit() }
        if (firstLetterIndex == -1) return word
        return word.mapIndexed { i, ch ->
            when {
                i == firstLetterIndex -> ch
                ch.isLetterOrDigit() -> '-'
                else -> ch
            }
        }.joinToString("")
    }

    /** First-letter-only view: every word keeps just its first letter, rest hyphened. */
    fun firstLetterOnly(text: String): String = WORD_REGEX.replace(text) { maskAllButFirstChar(it.value) }

    /**
     * Deterministic, evenly-spread order in which words become permanently revealed as the
     * round number increases (see [forRound]) — greedy farthest-point placement, so revealed
     * words spread across the verse rather than clustering at its start or end.
     */
    private fun evenlySpreadRevealOrder(wordCount: Int): List<Int> {
        if (wordCount <= 1) return (0 until wordCount).toList()
        val remaining = (0 until wordCount).toMutableList()
        val order = mutableListOf(remaining.removeAt(0))
        while (remaining.isNotEmpty()) {
            var bestIndex = 0
            var bestDistance = -1
            for ((ri, candidate) in remaining.withIndex()) {
                val minDistance = order.minOf { abs(it - candidate) }
                if (minDistance > bestDistance) {
                    bestDistance = minDistance
                    bestIndex = ri
                }
            }
            order.add(remaining.removeAt(bestIndex))
        }
        return order
    }

    /**
     * The text for one round (1..TOTAL_ROUNDS) of the progressive-hiding drill: round 1 shows
     * the verse in full, round TOTAL_ROUNDS hides every word, with the hidden-word count
     * stepping up evenly in between. A word hidden at an earlier round stays hidden at every
     * later round — words never flicker back into view once masked.
     */
    fun forRound(text: String, round: Int): String {
        val clampedRound = round.coerceIn(1, TOTAL_ROUNDS)
        val matches = WORD_REGEX.findAll(text).toList()
        val n = matches.size
        if (n == 0) return text

        val hiddenCount = (n * (clampedRound - 1) / (TOTAL_ROUNDS - 1).toDouble())
            .let { Math.round(it).toInt() }
            .coerceIn(0, n)
        val revealOrder = evenlySpreadRevealOrder(n)
        val stillHiddenIndices = revealOrder.takeLast(hiddenCount).toSet()

        val builder = StringBuilder()
        var lastEnd = 0
        matches.forEachIndexed { index, match ->
            builder.append(text, lastEnd, match.range.first)
            builder.append(if (index in stillHiddenIndices) maskAllChars(match.value) else match.value)
            lastEnd = match.range.last + 1
        }
        builder.append(text, lastEnd, text.length)
        return builder.toString()
    }
}
