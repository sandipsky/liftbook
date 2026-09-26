package com.example.liftbook.domain.model

import java.time.Instant

/**
 * A file the user picked with the system file picker, by its URI (FR-6.3, FR-6.4). Opaque above
 * the data layer, which is the only place that opens it.
 */
@JvmInline
value class DocumentUri(val value: String)

/** How much training data this phone, or a backup, holds. */
data class DataCounts(
    /** Finished workouts; one in progress isn't history yet. */
    val workouts: Int = 0,
    val routines: Int = 0,
    val customExercises: Int = 0,
    val weighIns: Int = 0,
    /** Entries in the weekly workout schedule (FR-7.1). */
    val schedules: Int = 0,
) {
    val isEmpty: Boolean get() = workouts == 0 && routines == 0 && customExercises == 0 && weighIns == 0 && schedules == 0
}

/** A backup file, read but not yet imported (FR-6.4). */
data class BackupSummary(
    val exportedAt: Instant,
    val counts: DataCounts,
)

enum class ImportMode {
    /** Adds what isn't on this phone yet. Nothing already here is changed, settings included. */
    MERGE,

    /** Deletes everything on this phone, then restores the backup, settings included. */
    REPLACE,
}

/** Why a file can't be imported. */
enum class BackupProblem {
    /** The file couldn't be opened or read. */
    UNREADABLE,

    /** It isn't a LiftBook backup. */
    NOT_A_BACKUP,

    /** A newer LiftBook wrote it, in a format this one doesn't know yet (NFR-6). */
    NEWER_VERSION,

    /** A LiftBook backup, but part of it doesn't read as valid data. */
    DAMAGED,
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception("Backup: $problem", cause)
