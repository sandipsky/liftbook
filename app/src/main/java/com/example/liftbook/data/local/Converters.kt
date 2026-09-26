package com.example.liftbook.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Instants are stored as epoch milliseconds. Enums need no converter: Room stores them by name,
 * which is readable in the database inspector and survives reordering the enum.
 */
class Converters {
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
