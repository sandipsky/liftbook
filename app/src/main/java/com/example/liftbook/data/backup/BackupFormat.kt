@file:UseSerializers(InstantIsoSerializer::class, LocalDateIsoSerializer::class, LocalTimeIsoSerializer::class)

package com.example.liftbook.data.backup

import com.example.liftbook.domain.model.DataCounts
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/*
 * The backup file (FR-6.3, NFR-6): one JSON document holding everything a user has logged.
 *
 * Its shape is a contract with every backup already written. Never rename or remove a field, or
 * change what one means. A new optional field, with a default, keeps the version; any other
 * change bumps BACKUP_FORMAT_VERSION and adds a step to BackupMigrations that brings the older
 * shape forward, so this app can always read what an earlier one wrote.
 *
 * Values are as stored: weights in kilograms, distances in metres, durations in seconds.
 * Instants are ISO-8601 in UTC and dates ISO days, so the file reads the same anywhere. A list's
 * order is its order on screen, so positions aren't written. Workouts nest their exercises and
 * sets, which is what keeps a set's workout and exercise in step with its parent.
 */

/** Marks the file as a LiftBook backup, whatever it's named. */
internal const val BACKUP_FORMAT = "liftbook-backup"

/** The format this app writes, and the newest it reads. */
internal const val BACKUP_FORMAT_VERSION = 1

@Serializable
internal data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Instant,
    /** The app's version name, for reading a support report; imports never depend on it. */
    val appVersion: String? = null,
    val settings: BackupSettings = BackupSettings(),
    /** Every exercise, built-in ones included: they carry the user's archiving and rest times. */
    val exercises: List<BackupExercise> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    /** Finished workouts only; one still in progress isn't history yet. */
    val workouts: List<BackupWorkout> = emptyList(),
    val bodyWeight: List<BackupWeighIn> = emptyList(),
    /** The weekly schedule behind reminders (FR-7.1). Added within version 1, so it's optional. */
    val schedules: List<BackupSchedule> = emptyList(),
) {
    fun counts(): DataCounts = DataCounts(
        workouts = workouts.size,
        routines = routines.size,
        customExercises = exercises.count { it.isCustom },
        weighIns = bodyWeight.size,
        schedules = schedules.size,
    )
}

/**
 * The settings of FR-6.2 and FR-7, by name. Stored as text so an unknown value is skipped, not
 * fatal. The reminder settings were added within version 1: like every field, they're optional.
 */
@Serializable
internal data class BackupSettings(
    val defaultRestSeconds: Int? = null,
    val firstDayOfWeek: String? = null,
    val themeMode: String? = null,
    val remindersEnabled: Boolean? = null,
    val reminderLeadMinutes: Int? = null,
    val snoozeMinutes: Int? = null,
)

@Serializable
internal data class BackupExercise(
    val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val type: ExerciseType,
    val isCustom: Boolean,
    val isArchived: Boolean = false,
    val defaultRestSeconds: Int? = null,
    val notes: String? = null,
    val createdAt: Instant,
)

@Serializable
internal data class BackupRoutine(
    val id: String,
    val name: String,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val exercises: List<BackupRoutineExercise> = emptyList(),
)

@Serializable
internal data class BackupRoutineExercise(
    val id: String,
    val exerciseId: String,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val restSecondsOverride: Int? = null,
    val notes: String? = null,
)

@Serializable
internal data class BackupWorkout(
    val id: String,
    val name: String,
    val routineId: String? = null,
    val startedAt: Instant,
    val finishedAt: Instant,
    val note: String? = null,
    val exercises: List<BackupWorkoutExercise> = emptyList(),
)

@Serializable
internal data class BackupWorkoutExercise(
    val id: String,
    val exerciseId: String,
    val note: String? = null,
    val restSecondsOverride: Int? = null,
    val sets: List<BackupSet> = emptyList(),
)

@Serializable
internal data class BackupSet(
    val id: String,
    val setType: SetType = SetType.NORMAL,
    val isCompleted: Boolean = true,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val completedAt: Instant? = null,
)

@Serializable
internal data class BackupWeighIn(
    val id: String,
    val weightKg: Double,
    val date: LocalDate,
    val note: String? = null,
)

/**
 * A scheduled workout (FR-7.1). A snooze or a skip is the state of one day's reminder, not part
 * of the plan, so neither is written.
 */
@Serializable
internal data class BackupSchedule(
    val id: String,
    /** ISO day names, "MONDAY" to "SUNDAY". */
    val days: List<String>,
    /** Local time: "18:00". */
    val startTime: LocalTime,
    val routineId: String? = null,
    /** Null follows the default lead time. */
    val leadMinutes: Int? = null,
    val isEnabled: Boolean = true,
    val createdAt: Instant,
)

/** "2026-09-26T18:30:00Z" — exact to the millisecond, as stored. */
internal object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("liftbook.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = parsing { Instant.parse(decoder.decodeString()) }
}

/** "2026-09-26". */
internal object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("liftbook.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = parsing { LocalDate.parse(decoder.decodeString()) }
}

/** "18:00". */
internal object LocalTimeIsoSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("liftbook.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalTime = parsing { LocalTime.parse(decoder.decodeString()) }
}

/** A malformed date is bad data like any other, so it fails the way the rest of decoding does. */
private inline fun <T> parsing(parse: () -> T): T = try {
    parse()
} catch (e: DateTimeParseException) {
    throw SerializationException("Not an ISO-8601 value: ${e.parsedString}", e)
}
