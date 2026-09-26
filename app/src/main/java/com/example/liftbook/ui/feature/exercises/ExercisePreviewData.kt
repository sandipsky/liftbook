package com.example.liftbook.ui.feature.exercises

import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.Exercise
import com.example.liftbook.domain.model.ExerciseSession
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Sample data for the exercise screens' previews. */
internal object ExercisePreviewData {

    val zone: ZoneId = ZoneId.of("UTC")
    val today: LocalDate = LocalDate.of(2026, 9, 25)

    private val created = today.atStartOfDay(zone).toInstant()

    private fun exercise(
        id: String,
        name: String,
        muscle: MuscleGroup,
        equipment: Equipment,
        type: ExerciseType = ExerciseType.STRENGTH,
        isCustom: Boolean = false,
        isArchived: Boolean = false,
    ) = Exercise(id, name, muscle, equipment, type, isCustom, isArchived, created)

    val benchPress = exercise("bench", "Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL)
    val cableYRaise = exercise("y-raise", "Cable Y-Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, isCustom = true)
    val pullUp = exercise("pull-up", "Pull-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT)
    val rowing = exercise("rowing", "Rowing (Machine)", MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE, ExerciseType.CARDIO)
    val archivedCurl = exercise(
        "spider-curl", "Spider Curl (EZ Bar)", MuscleGroup.BICEPS, Equipment.BARBELL, isCustom = true, isArchived = true,
    )

    val library: List<Exercise> = listOf(
        exercise("ab-wheel", "Ab Wheel Rollout", MuscleGroup.CORE, Equipment.OTHER, ExerciseType.BODYWEIGHT),
        exercise("arnold", "Arnold Press (Dumbbell)", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
        exercise("back-ext", "Back Extension", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT),
        benchPress,
        exercise("bench-db", "Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL),
        exercise("bss", "Bulgarian Split Squat (Dumbbell)", MuscleGroup.QUADS, Equipment.DUMBBELL),
        cableYRaise,
        exercise("chin-up", "Chin-Up", MuscleGroup.BACK, Equipment.NONE, ExerciseType.BODYWEIGHT),
        exercise("deadlift", "Deadlift (Barbell)", MuscleGroup.BACK, Equipment.BARBELL),
        exercise("face-pull", "Face Pull (Cable)", MuscleGroup.SHOULDERS, Equipment.CABLE),
        exercise("hip-thrust", "Hip Thrust (Barbell)", MuscleGroup.GLUTES, Equipment.BARBELL),
        pullUp,
        rowing,
    )

    private fun session(id: String, name: String, daysAgo: Long, vararg sets: LoggedSet) = ExerciseSession(
        workoutExerciseId = id,
        workoutId = "w-$id",
        workoutName = name,
        startedAt = today.minusDays(daysAgo).atTime(LocalTime.of(18, 30)).atZone(zone).toInstant(),
        sets = sets.toList(),
    )

    private fun strength(kg: Double, reps: Int, type: SetType = SetType.NORMAL) =
        LoggedSet(type, SetMetrics.Strength(weightKg = kg, reps = reps))

    val benchSessions: List<ExerciseSession> = listOf(
        session(
            "s3", "Push", 3,
            strength(40.0, 10, SetType.WARMUP),
            strength(80.0, 5),
            strength(80.0, 5),
            strength(77.5, 6),
            strength(60.0, 12, SetType.DROP),
        ),
        session("s2", "Upper", 7, strength(77.5, 5), strength(77.5, 5), strength(77.5, 4)),
        session("s1", "Push", 11, strength(75.0, 6), strength(75.0, 5), strength(72.5, 6)),
    )
}
