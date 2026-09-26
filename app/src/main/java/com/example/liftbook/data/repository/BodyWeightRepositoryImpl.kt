package com.example.liftbook.data.repository

import androidx.room.withTransaction
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.BodyWeightDao
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.repository.BodyWeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodyWeightRepositoryImpl @Inject constructor(
    private val database: LiftBookDatabase,
    private val bodyWeightDao: BodyWeightDao,
) : BodyWeightRepository {

    override fun observeEntries(): Flow<List<BodyWeightEntry>> =
        bodyWeightDao.observeAll().map { entries -> entries.map(BodyWeightEntryEntity::toDomain) }

    override suspend fun log(date: LocalDate, weightKg: Double) {
        require(weightKg > 0.0 && weightKg.isFinite()) { "A body weight is more than nothing" }
        // A day keeps its row, and its id, when it's logged again: the unique date allows one.
        database.withTransaction {
            val existing = bodyWeightDao.getOn(date)
            if (existing != null) {
                bodyWeightDao.update(existing.copy(weightKg = weightKg))
            } else {
                bodyWeightDao.insert(
                    BodyWeightEntryEntity(id = UUID.randomUUID().toString(), weightKg = weightKg, recordedOn = date, note = null),
                )
            }
        }
    }

    override suspend fun delete(id: String) = bodyWeightDao.delete(id)
}
