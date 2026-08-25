package com.firmlyplanted.app.data.local

import androidx.room.TypeConverter
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.TranslationSource
import com.firmlyplanted.app.domain.VersePhase
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromVersePhase(value: VersePhase): String = value.name

    @TypeConverter
    fun toVersePhase(value: String): VersePhase = VersePhase.valueOf(value)

    @TypeConverter
    fun fromProjectStatus(value: ProjectStatus): String = value.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus = ProjectStatus.valueOf(value)

    @TypeConverter
    fun fromTranslationSource(value: TranslationSource): String = value.name

    @TypeConverter
    fun toTranslationSource(value: String): TranslationSource = TranslationSource.valueOf(value)
}
