package com.example.liftbook.data.backup

import com.example.liftbook.data.preferences.isLeadMinutes
import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupProblem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek

/**
 * Turns a [BackupFile] into text and back (FR-6.3, FR-6.4, NFR-6). Reading checks, in order,
 * that the text is a LiftBook backup, that its version is one this app knows — bringing an
 * older one forward — and that what's in it is consistent, so an import never starts on a file
 * it would have to abandon halfway.
 */
internal object BackupCodec {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        // The format marker and version are defaults, and must still be written.
        encodeDefaults = true
        explicitNulls = false
        // A later app may add optional fields without a new version; they're skipped here.
        ignoreUnknownKeys = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    /** Reads a backup written by this app or an earlier one. Throws [BackupException] otherwise. */
    fun decode(text: String): BackupFile {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP, e)
        }
        val root = element as? JsonObject ?: throw BackupException(BackupProblem.NOT_A_BACKUP)

        if (root.string("format") != BACKUP_FORMAT) throw BackupException(BackupProblem.NOT_A_BACKUP)
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw BackupException(BackupProblem.DAMAGED)
        when {
            version > BACKUP_FORMAT_VERSION -> throw BackupException(BackupProblem.NEWER_VERSION)
            version < 1 -> throw BackupException(BackupProblem.DAMAGED)
        }

        val backup = try {
            json.decodeFromJsonElement(BackupFile.serializer(), BackupMigrations.migrate(root, from = version))
        } catch (e: SerializationException) {
            throw BackupException(BackupProblem.DAMAGED, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupProblem.DAMAGED, e)
        }
        if (!backup.isConsistent()) throw BackupException(BackupProblem.DAMAGED)
        return backup
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

/**
 * Brings an older backup forward to the current format, one version at a time (NFR-6). Each
 * step turns format n into n + 1 and is kept forever, so a backup of any age still imports.
 * There are none yet: version 1 is the only format there has been.
 */
internal object BackupMigrations {

    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun migrate(
        backup: JsonObject,
        from: Int,
        to: Int = BACKUP_FORMAT_VERSION,
        steps: Map<Int, (JsonObject) -> JsonObject> = STEPS,
    ): JsonObject {
        var current = backup
        for (version in from until to) {
            val step = checkNotNull(steps[version]) { "No migration from backup format $version" }
            current = step(current)
        }
        return current
    }
}

/**
 * What the database would reject, or what would make no sense on screen, caught before an
 * import writes anything: ids that repeat, sets of an exercise the backup doesn't have, numbers
 * no set could hold, two weigh-ins on one day, a schedule entry on no day at all.
 */
internal fun BackupFile.isConsistent(): Boolean {
    val exerciseIds = exercises.map { it.id }
    if (!exerciseIds.allDistinct() || exercises.any { it.name.isBlank() }) return false
    val known = exerciseIds.toHashSet()

    val routineExercises = routines.flatMap { it.exercises }
    if (!routines.map { it.id }.allDistinct() || !routineExercises.map { it.id }.allDistinct()) return false
    if (routines.any { it.name.isBlank() }) return false
    val validTargets = routineExercises.all { target ->
        target.exerciseId in known &&
            target.targetSets >= 1 &&
            target.targetReps.isNonNegative() &&
            target.targetWeightKg.isNonNegative() &&
            target.targetDurationSeconds.isNonNegative() &&
            target.targetDistanceMeters.isNonNegative() &&
            target.restSecondsOverride.isNonNegative()
    }
    if (!validTargets) return false

    val workoutExercises = workouts.flatMap { it.exercises }
    val sets = workoutExercises.flatMap { it.sets }
    if (!workouts.map { it.id }.allDistinct() || !workoutExercises.map { it.id }.allDistinct() || !sets.map { it.id }.allDistinct()) return false
    if (workouts.any { it.finishedAt < it.startedAt }) return false
    if (workoutExercises.any { it.exerciseId !in known || !it.restSecondsOverride.isNonNegative() }) return false
    val validSets = sets.all { set ->
        set.weightKg.isNonNegative() && set.reps.isNonNegative() && set.durationSeconds.isNonNegative() && set.distanceMeters.isNonNegative()
    }
    if (!validSets) return false

    if (!bodyWeight.map { it.id }.allDistinct() || !bodyWeight.map { it.date }.allDistinct()) return false
    if (!bodyWeight.all { it.weightKg > 0.0 && it.weightKg.isFinite() }) return false

    if (!schedules.map { it.id }.allDistinct()) return false
    return schedules.all { schedule ->
        schedule.days.isNotEmpty() &&
            schedule.days.allDistinct() &&
            schedule.days.all { name -> DayOfWeek.entries.any { it.name == name } } &&
            (schedule.leadMinutes == null || isLeadMinutes(schedule.leadMinutes))
    }
}

private fun <T> List<T>.allDistinct(): Boolean = toHashSet().size == size

private fun Int?.isNonNegative(): Boolean = this == null || this >= 0

private fun Double?.isNonNegative(): Boolean = this == null || (isFinite() && this >= 0.0)
