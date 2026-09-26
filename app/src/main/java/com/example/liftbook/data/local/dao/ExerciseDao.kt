package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.liftbook.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeLibrary(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE isArchived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    /**
     * The exercise's own rest time (FR-3.5). A preference about the exercise rather than part of
     * its definition, so it can be set on built-in exercises too.
     */
    @Query("UPDATE exercises SET defaultRestSeconds = :seconds WHERE id = :id")
    suspend fun setDefaultRestSeconds(id: String, seconds: Int?)
}
