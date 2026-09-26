package com.example.liftbook.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.liftbook.data.local.dao.BodyWeightDao
import com.example.liftbook.data.local.dao.ExerciseDao
import com.example.liftbook.data.local.dao.ProgressDao
import com.example.liftbook.data.local.dao.RoutineDao
import com.example.liftbook.data.local.dao.SetDao
import com.example.liftbook.data.local.dao.WorkoutDao
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import com.example.liftbook.data.local.entity.ExerciseEntity
import com.example.liftbook.data.local.entity.RoutineEntity
import com.example.liftbook.data.local.entity.RoutineExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutEntity
import com.example.liftbook.data.local.entity.WorkoutExerciseEntity
import com.example.liftbook.data.local.entity.WorkoutSetEntity

/**
 * Schema changes ship with an explicit migration (an AutoMigration for additive changes) and
 * the exported schema in app/schemas/. Never fall back to destructive migration: this database
 * is the only copy of the user's training history.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        BodyWeightEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: the workout's rest timer (FR-3.5) — two nullable columns on workouts.
        AutoMigration(from = 1, to = 2),
        // v3: the body-weight log (FR-5.4) — a new table.
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class LiftBookDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun setDao(): SetDao
    abstract fun progressDao(): ProgressDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        const val NAME = "liftbook.db"
    }
}
