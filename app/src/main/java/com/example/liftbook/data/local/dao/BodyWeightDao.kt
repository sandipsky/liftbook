package com.example.liftbook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BodyWeightDao {

    /** Every weigh-in, oldest first (FR-5.4). */
    @Query("SELECT * FROM body_weight_entries ORDER BY recordedOn")
    fun observeAll(): Flow<List<BodyWeightEntryEntity>>

    @Query("SELECT * FROM body_weight_entries WHERE recordedOn = :date")
    suspend fun getOn(date: LocalDate): BodyWeightEntryEntity?

    @Insert
    suspend fun insert(entry: BodyWeightEntryEntity)

    @Update
    suspend fun update(entry: BodyWeightEntryEntity)

    @Query("DELETE FROM body_weight_entries WHERE id = :id")
    suspend fun delete(id: String)
}
