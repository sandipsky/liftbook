package com.example.liftbook.data.local

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftbook.data.local.seed.ExerciseSeed

/**
 * v6, data only: takes the retired built-ins ([ExerciseSeed.retiredIds]) out of an existing
 * database. One that nothing refers to is deleted. One that a workout or routine still refers
 * to is archived instead — it leaves the library, and the history logged against it stays
 * readable (FR-1.3), as the `RESTRICT` foreign keys insist.
 */
class RetireExercisesMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // Fixed UUIDs from the seed, never user input.
        val ids = ExerciseSeed.retiredIds.joinToString { "'$it'" }
        db.execSQL(
            "DELETE FROM exercises WHERE id IN ($ids) " +
                "AND NOT EXISTS (SELECT 1 FROM workout_exercises WHERE exerciseId = exercises.id) " +
                "AND NOT EXISTS (SELECT 1 FROM workout_sets WHERE exerciseId = exercises.id) " +
                "AND NOT EXISTS (SELECT 1 FROM routine_exercises WHERE exerciseId = exercises.id)",
        )
        db.execSQL("UPDATE exercises SET isArchived = 1 WHERE id IN ($ids)")
    }
}
