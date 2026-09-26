package com.example.liftbook.data.local.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Clock

/**
 * Seeds the exercise library inside the database-creation transaction, so the library is never
 * observed empty and a crash mid-seed leaves no half-filled table behind.
 */
class ExerciseSeedCallback(private val clock: Clock) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        ExerciseSeed.insertInto(db, createdAt = clock.instant())
    }
}
