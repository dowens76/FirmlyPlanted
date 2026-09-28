package com.firmlyplanted.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY isDefault DESC, displayName ASC")
    fun observeAll(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE id = :id")
    suspend fun getById(id: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(translations: List<TranslationEntity>)
}

@Dao
interface MemoryProjectDao {
    @Query("SELECT * FROM memory_projects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryProjectEntity>>

    @Query("SELECT * FROM memory_projects WHERE id = :id")
    fun observeById(id: String): Flow<MemoryProjectEntity?>

    @Query("SELECT * FROM memory_projects WHERE id = :id")
    suspend fun getById(id: String): MemoryProjectEntity?

    @Insert
    suspend fun insert(project: MemoryProjectEntity)

    @Update
    suspend fun update(project: MemoryProjectEntity)

    @Delete
    suspend fun delete(project: MemoryProjectEntity)
}

@Dao
interface VerseDao {
    @Query("SELECT * FROM verses WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun observeForProject(projectId: String): Flow<List<VerseEntity>>

    @Query("SELECT * FROM verses WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getForProject(projectId: String): List<VerseEntity>

    @Query("SELECT * FROM verses WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<VerseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(verses: List<VerseEntity>)

    @Update
    suspend fun update(verse: VerseEntity)

    @Update
    suspend fun updateAll(verses: List<VerseEntity>)

    /** Rolling-window cache eviction: blanks cached text for anything not in the keep-list. */
    @Query("UPDATE verses SET text = NULL, textCachedAt = NULL WHERE projectId = :projectId AND id NOT IN (:keepIds)")
    suspend fun evictTextOutside(projectId: String, keepIds: List<String>)

    /** Used on project completion: clears all cached text for the project, keeps progress rows. */
    @Query("UPDATE verses SET text = NULL, textCachedAt = NULL WHERE projectId = :projectId")
    suspend fun clearAllText(projectId: String)

    @Query("SELECT COUNT(*) FROM verses WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int
}
