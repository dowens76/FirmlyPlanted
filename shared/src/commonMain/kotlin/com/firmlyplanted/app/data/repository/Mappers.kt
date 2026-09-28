package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.local.TranslationEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.domain.Testament
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.VerseProgress

fun Translation.toEntity(): TranslationEntity = TranslationEntity(
    id = id,
    displayName = displayName,
    language = language,
    source = source,
    sourceId = sourceId,
    copyrightNoticeResName = copyrightNoticeResName,
    licenseSummary = licenseSummary,
    maxCachedVerses = maxCachedVerses,
    approvedWebReaderUrlTemplate = approvedWebReaderUrlTemplate,
    isDefault = isDefault,
    testamentsCsv = testaments.joinToString(",") { it.name },
)

fun TranslationEntity.toDomain(): Translation = Translation(
    id = id,
    displayName = displayName,
    language = language,
    source = source,
    sourceId = sourceId,
    copyrightNoticeResName = copyrightNoticeResName,
    licenseSummary = licenseSummary,
    maxCachedVerses = maxCachedVerses,
    approvedWebReaderUrlTemplate = approvedWebReaderUrlTemplate,
    isDefault = isDefault,
    testaments = testamentsCsv.split(",").filter { it.isNotBlank() }.map { Testament.valueOf(it) }.toSet(),
)

fun VerseEntity.toProgress(): VerseProgress = VerseProgress(
    id = id,
    orderIndex = orderIndex,
    phase = phase,
    addedDate = addedDate,
    lastReviewedDate = lastReviewedDate,
    nextReviewDate = nextReviewDate,
    consecutiveSuccesses = consecutiveSuccesses,
)

fun VerseEntity.withProgress(progress: VerseProgress): VerseEntity = copy(
    phase = progress.phase,
    addedDate = progress.addedDate,
    lastReviewedDate = progress.lastReviewedDate,
    nextReviewDate = progress.nextReviewDate,
    consecutiveSuccesses = progress.consecutiveSuccesses,
)
