package com.example.liftbook.data.backup

import com.example.liftbook.domain.model.BackupException
import com.example.liftbook.domain.model.BackupProblem
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetType
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.model.UserPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** The backup file's format (FR-6.3, FR-6.4, NFR-6): what's written, and what's refused. */
class BackupCodecTest {

    private val bench = BackupExercise(
        id = "bench",
        name = "Bench Press (Barbell)",
        primaryMuscle = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        type = ExerciseType.STRENGTH,
        isCustom = false,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val zercher = BackupExercise(
        id = "zercher",
        name = "Zercher Squat",
        primaryMuscle = MuscleGroup.QUADS,
        equipment = Equipment.BARBELL,
        type = ExerciseType.STRENGTH,
        isCustom = true,
        defaultRestSeconds = 150,
        notes = "Elbows in",
        createdAt = Instant.parse("2026-03-02T09:00:00Z"),
    )
    private val backup = BackupFile(
        exportedAt = Instant.parse("2026-09-26T10:15:30.123Z"),
        appVersion = "1.0",
        settings = BackupSettings(
            defaultRestSeconds = 120,
            firstDayOfWeek = "SUNDAY",
            themeMode = "DARK",
            remindersEnabled = false,
            reminderLeadMinutes = 15,
            snoozeMinutes = 5,
        ),
        exercises = listOf(bench, zercher),
        routines = listOf(
            BackupRoutine(
                id = "push",
                name = "Push",
                createdAt = Instant.parse("2026-04-01T08:00:00Z"),
                updatedAt = Instant.parse("2026-05-01T08:00:00Z"),
                exercises = listOf(BackupRoutineExercise(id = "push-1", exerciseId = "bench", targetSets = 3, targetReps = 8, targetWeightKg = 82.5)),
            ),
        ),
        workouts = listOf(
            BackupWorkout(
                id = "w1",
                name = "Push",
                routineId = "push",
                startedAt = Instant.parse("2026-09-25T18:00:00Z"),
                finishedAt = Instant.parse("2026-09-25T19:02:00Z"),
                note = "Felt strong",
                exercises = listOf(
                    BackupWorkoutExercise(
                        id = "w1-bench",
                        exerciseId = "bench",
                        sets = listOf(
                            BackupSet(id = "s1", setType = SetType.WARMUP, weightKg = 40.0, reps = 10, completedAt = Instant.parse("2026-09-25T18:05:00Z")),
                            BackupSet(id = "s2", weightKg = 82.5, reps = 8, completedAt = Instant.parse("2026-09-25T18:09:00Z")),
                        ),
                    ),
                ),
            ),
        ),
        bodyWeight = listOf(BackupWeighIn(id = "bw1", weightKg = 82.4, date = LocalDate.of(2026, 9, 25))),
        schedules = listOf(
            BackupSchedule(
                id = "mon-thu",
                days = listOf("MONDAY", "THURSDAY"),
                startTime = LocalTime.of(18, 30),
                routineId = "push",
                leadMinutes = 30,
                createdAt = Instant.parse("2026-06-01T12:00:00Z"),
            ),
        ),
    )

    private fun encoded(): JsonObject = Json.parseToJsonElement(BackupCodec.encode(backup)).jsonObject

    private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))

    private fun problemReading(text: String): BackupProblem? = try {
        BackupCodec.decode(text)
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun `a backup reads back exactly as it was written`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
    }

    @Test
    fun `the file names its format and version, and keeps values as stored`() {
        val json = encoded()

        assertEquals("liftbook-backup", json["format"]!!.jsonPrimitive.content)
        assertEquals(1, json["formatVersion"]!!.jsonPrimitive.int)
        assertEquals("2026-09-26T10:15:30.123Z", json["exportedAt"]!!.jsonPrimitive.content)
        val set = json["workouts"]!!.jsonArray[0].jsonObject["exercises"]!!.jsonArray[0].jsonObject["sets"]!!.jsonArray[1].jsonObject
        // Kilograms, whatever the display unit (FR-6.1).
        assertEquals("82.5", set["weightKg"]!!.jsonPrimitive.content)
        assertEquals("2026-09-25", json["bodyWeight"]!!.jsonArray[0].jsonObject["date"]!!.jsonPrimitive.content)
        // Empty values are left out rather than written as null.
        assertFalse("note" in json["bodyWeight"]!!.jsonArray[0].jsonObject)
    }

    @Test
    fun `text that isn't JSON, or JSON from something else, isn't a backup`() {
        assertEquals(BackupProblem.NOT_A_BACKUP, problemReading("PK\u0003\u0004 not json"))
        assertEquals(BackupProblem.NOT_A_BACKUP, problemReading("""{"name": "My playlist"}"""))
        assertEquals(BackupProblem.NOT_A_BACKUP, problemReading("[1, 2, 3]"))
        assertEquals(BackupProblem.NOT_A_BACKUP, problemReading(""))
    }

    @Test
    fun `a backup from a newer format is refused, rather than read wrongly`() {
        val newer = encoded().with("formatVersion", JsonPrimitive(BACKUP_FORMAT_VERSION + 1))

        assertEquals(BackupProblem.NEWER_VERSION, problemReading(newer.toString()))
    }

    @Test
    fun `fields a later app added in the same format are skipped`() {
        val withExtra = encoded().with("streaks", JsonPrimitive(12))

        assertEquals(backup, BackupCodec.decode(withExtra.toString()))
    }

    @Test
    fun `a missing field, a bad date or an unknown kind of exercise is damage`() {
        val json = encoded()
        assertEquals(BackupProblem.DAMAGED, problemReading(JsonObject(json - "exportedAt").toString()))
        assertEquals(BackupProblem.DAMAGED, problemReading(json.with("exportedAt", JsonPrimitive("yesterday")).toString()))
        assertEquals(BackupProblem.DAMAGED, problemReading(JsonObject(json - "formatVersion").toString()))
        val renamedType = BackupCodec.encode(backup).replace("\"STRENGTH\"", "\"POWERLIFTING\"")
        assertEquals(BackupProblem.DAMAGED, problemReading(renamedType))
    }

    @Test
    fun `what the database would reject is caught before anything is written`() {
        fun damaged(file: BackupFile) = assertEquals(BackupProblem.DAMAGED, problemReading(BackupCodec.encode(file)))

        // Sets of an exercise the backup doesn't have.
        damaged(backup.copy(exercises = listOf(zercher)))
        // The same workout twice.
        damaged(backup.copy(workouts = backup.workouts + backup.workouts))
        // Two weigh-ins on one day.
        damaged(backup.copy(bodyWeight = backup.bodyWeight + BackupWeighIn(id = "bw2", weightKg = 82.0, date = LocalDate.of(2026, 9, 25))))
        // A weight no set could hold.
        val negative = backup.workouts[0].let { workout ->
            workout.copy(exercises = listOf(workout.exercises[0].copy(sets = listOf(BackupSet(id = "s9", weightKg = -5.0, reps = 5)))))
        }
        damaged(backup.copy(workouts = listOf(negative)))
        // A workout that ended before it started.
        damaged(backup.copy(workouts = listOf(backup.workouts[0].copy(finishedAt = Instant.parse("2026-09-25T17:00:00Z")))))
        // A scheduled workout on no day, on a day that isn't one, or reminded more than a day ahead.
        val schedule = backup.schedules[0]
        damaged(backup.copy(schedules = listOf(schedule.copy(days = emptyList()))))
        damaged(backup.copy(schedules = listOf(schedule.copy(days = listOf("FUNDAY")))))
        damaged(backup.copy(schedules = listOf(schedule.copy(leadMinutes = 2 * 24 * 60))))
    }

    @Test
    fun `a schedule is written as days and a local time, and the reminder settings with it`() {
        val json = encoded()

        val schedule = json["schedules"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("MONDAY", "THURSDAY"), schedule["days"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("18:30", schedule["startTime"]!!.jsonPrimitive.content)
        val preferences = BackupCodec.decode(BackupCodec.encode(backup)).settings.toUserPreferences()
        assertFalse(preferences.remindersEnabled)
        assertEquals(15, preferences.reminderLeadMinutes)
        assertEquals(5, preferences.snoozeMinutes)
    }

    @Test
    fun `a backup from before schedules were added reads with none, and default reminder settings`() {
        val older = encoded().let { JsonObject(it - "schedules" + ("settings" to JsonObject(mapOf("themeMode" to JsonPrimitive("DARK"))))) }

        val read = BackupCodec.decode(older.toString())

        assertEquals(emptyList<BackupSchedule>(), read.schedules)
        assertEquals(UserPreferences(firstDayOfWeek = read.settings.toUserPreferences().firstDayOfWeek, themeMode = ThemeMode.DARK), read.settings.toUserPreferences())
    }

    @Test
    fun `an unknown setting doesn't stop an import, it takes its default`() {
        val read = BackupCodec.decode(BackupCodec.encode(backup.copy(settings = BackupSettings(themeMode = "SEPIA"))))

        assertEquals(ThemeMode.SYSTEM, read.settings.toUserPreferences().themeMode)
        assertEquals(90, read.settings.toUserPreferences().defaultRestSeconds)
    }

    @Test
    fun `older formats are brought forward one version at a time, in order`() {
        val steps = mapOf<Int, (JsonObject) -> JsonObject>(
            1 to { it.with("trail", JsonPrimitive("1")) },
            2 to { it.with("trail", JsonPrimitive(it["trail"]!!.jsonPrimitive.content + "2")) },
        )

        val migrated = BackupMigrations.migrate(JsonObject(emptyMap()), from = 1, to = 3, steps = steps)

        assertEquals("12", migrated["trail"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a version with no way forward is a bug, not a silent skip`() {
        try {
            BackupMigrations.migrate(JsonObject(emptyMap()), from = 1, to = 2, steps = emptyMap())
            fail("Expected a missing migration to fail")
        } catch (e: IllegalStateException) {
            // Expected.
        }
    }

    @Test
    fun `a workout's or a schedule's link to a routine the file doesn't have is dropped`() {
        val rows = backup.copy(routines = emptyList()).toRows()

        assertEquals(listOf(null), rows.workouts.map { it.routineId })
        assertEquals(listOf(null), rows.schedules.map { it.routineId })
    }

    @Test
    fun `rows take their order from the file, and each set its workout and exercise from its parent`() {
        val rows = backup.toRows()

        assertEquals(listOf(0, 1), rows.sets.map { it.position })
        assertEquals(setOf("w1"), rows.sets.map { it.workoutId }.toSet())
        assertEquals(setOf("bench"), rows.sets.map { it.exerciseId }.toSet())
        assertEquals(backup.workouts, rows.toBackupFile(backup.exportedAt, null, backup.settings.toUserPreferences()).workouts)
        assertEquals(backup.schedules, rows.toBackupFile(backup.exportedAt, null, backup.settings.toUserPreferences()).schedules)
    }
}
