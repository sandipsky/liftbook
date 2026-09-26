package com.example.liftbook.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.example.liftbook.BuildConfig
import com.example.liftbook.data.backup.BackupCodec
import com.example.liftbook.data.backup.BackupDocuments
import com.example.liftbook.data.backup.BackupFile
import com.example.liftbook.data.backup.BackupRows
import com.example.liftbook.data.backup.LocalRows
import com.example.liftbook.data.backup.planMerge
import com.example.liftbook.data.backup.toBackupFile
import com.example.liftbook.data.backup.toRows
import com.example.liftbook.data.backup.toUserPreferences
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.BackupDao
import com.example.liftbook.data.local.seed.ExerciseSeed
import com.example.liftbook.data.mapper.toDomain
import com.example.liftbook.data.preferences.SettingsKeys
import com.example.liftbook.data.preferences.lastExportedAt
import com.example.liftbook.data.preferences.setBackedUpSettings
import com.example.liftbook.data.preferences.toUserPreferences
import com.example.liftbook.di.IoDispatcher
import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.BackupSummary
import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.DocumentUri
import com.example.liftbook.domain.model.ImportMode
import com.example.liftbook.domain.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export, import and clearing (FR-6.3–6.5). Every change to the database is one transaction, so
 * an import that fails leaves this phone exactly as it was. Settings live in DataStore and are
 * written after the database, which is what matters most. The schedule changes with the rest, so
 * the caller brings the reminders in step afterwards (FR-7.6).
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: LiftBookDatabase,
    private val backupDao: BackupDao,
    private val dataStore: DataStore<Preferences>,
    private val documents: BackupDocuments,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupRepository {

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override fun observeCounts(): Flow<DataCounts> = backupDao.observeCounts().map { it.toDomain() }.distinctUntilChanged()

    override val lastExportedAt: Flow<Instant?> = preferences.map { it.lastExportedAt }.distinctUntilChanged()

    override suspend fun export(destination: DocumentUri): DataCounts {
        val rows = database.withTransaction { readAll() }
        val exportedAt = clock.instant()
        val backup = rows.toBackupFile(exportedAt, BuildConfig.VERSION_NAME, preferences.first().toUserPreferences())
        withContext(ioDispatcher) { documents.write(destination, BackupCodec.encode(backup)) }
        dataStore.edit { it[SettingsKeys.lastExportedAt] = exportedAt.toEpochMilli() }
        return backup.counts()
    }

    override suspend fun inspect(source: DocumentUri): BackupSummary {
        val backup = read(source)
        return BackupSummary(exportedAt = backup.exportedAt, counts = backup.counts())
    }

    override suspend fun import(source: DocumentUri, mode: ImportMode): DataCounts {
        val backup = read(source)
        val rows = backup.toRows()
        return when (mode) {
            ImportMode.MERGE -> database.withTransaction {
                val added = planMerge(rows, readLocal())
                insert(added)
                added.counts()
            }
            ImportMode.REPLACE -> {
                database.withTransaction {
                    deleteAll()
                    insert(rows)
                    restoreBuiltInExercises()
                }
                dataStore.edit { it.setBackedUpSettings(backup.settings.toUserPreferences()) }
                backup.counts()
            }
        }
    }

    override suspend fun clearAll() {
        database.withTransaction {
            deleteAll()
            restoreBuiltInExercises()
        }
        // Settings go too, and with them when the last backup was made: it was of data that's gone.
        dataStore.edit { it.clear() }
    }

    private suspend fun read(source: DocumentUri): BackupFile = withContext(ioDispatcher) {
        val text = try {
            documents.read(source)
        } catch (e: IOException) {
            throw BackupException(BackupProblem.UNREADABLE, e)
        } catch (e: SecurityException) {
            // The picker's grant to read the file has lapsed, or never covered it.
            throw BackupException(BackupProblem.UNREADABLE, e)
        }
        BackupCodec.decode(text)
    }

    private suspend fun readAll() = BackupRows(
        exercises = backupDao.getExercises(),
        routines = backupDao.getRoutines(),
        routineExercises = backupDao.getRoutineExercises(),
        workouts = backupDao.getFinishedWorkouts(),
        workoutExercises = backupDao.getFinishedWorkoutExercises(),
        sets = backupDao.getFinishedWorkoutSets(),
        bodyWeight = backupDao.getBodyWeight(),
        schedules = backupDao.getSchedules(),
    )

    private suspend fun readLocal() = LocalRows(
        exercises = backupDao.getExercises(),
        routines = backupDao.getRoutines(),
        workoutIds = backupDao.getWorkoutIds().toHashSet(),
        bodyWeight = backupDao.getBodyWeight(),
        schedules = backupDao.getSchedules(),
    )

    /** Parents before children, as the foreign keys need. */
    private suspend fun insert(rows: BackupRows) {
        backupDao.insertExercises(rows.exercises)
        backupDao.insertRoutines(rows.routines)
        backupDao.insertRoutineExercises(rows.routineExercises)
        backupDao.insertWorkouts(rows.workouts)
        backupDao.insertWorkoutExercises(rows.workoutExercises)
        backupDao.insertSets(rows.sets)
        backupDao.insertBodyWeight(rows.bodyWeight)
        backupDao.insertSchedules(rows.schedules)
    }

    /** Everything, the workout in progress included. */
    private suspend fun deleteAll() {
        backupDao.deleteSchedules()
        backupDao.deleteSets()
        backupDao.deleteWorkoutExercises()
        backupDao.deleteWorkouts()
        backupDao.deleteRoutineExercises()
        backupDao.deleteRoutines()
        backupDao.deleteBodyWeight()
        backupDao.deleteExercises()
    }

    /** The library always has every built-in this version ships, as on a fresh install. */
    private suspend fun restoreBuiltInExercises() {
        backupDao.insertExercisesIfMissing(ExerciseSeed.entities(createdAt = clock.instant()))
    }
}
