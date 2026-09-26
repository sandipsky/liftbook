package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.WorkoutSetEntity
import com.example.liftbook.data.local.projection.ExerciseSessionRow
import com.example.liftbook.data.local.projection.ExerciseSessionWithSets
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ExerciseSessionMapperTest {

    private fun set(
        position: Int,
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
        isCompleted: Boolean = true,
        setType: SetType = SetType.NORMAL,
    ) = WorkoutSetEntity(
        id = "set-$position",
        workoutExerciseId = "we",
        workoutId = "w",
        exerciseId = "e",
        position = position,
        setType = setType,
        isCompleted = isCompleted,
        weightKg = weightKg,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        completedAt = null,
    )

    @Test
    fun `strength sets read weight and reps`() {
        assertEquals(SetMetrics.Strength(80.0, 5), set(0, weightKg = 80.0, reps = 5).toMetrics(ExerciseType.STRENGTH))
    }

    @Test
    fun `strength sets missing a value are dropped rather than invented`() {
        assertNull(set(0, weightKg = 80.0).toMetrics(ExerciseType.STRENGTH))
        assertNull(set(0, reps = 5).toMetrics(ExerciseType.STRENGTH))
    }

    @Test
    fun `bodyweight sets read reps, with any added weight`() {
        assertEquals(SetMetrics.Bodyweight(12, null), set(0, reps = 12).toMetrics(ExerciseType.BODYWEIGHT))
        assertEquals(SetMetrics.Bodyweight(8, 10.0), set(0, weightKg = 10.0, reps = 8).toMetrics(ExerciseType.BODYWEIGHT))
        assertNull(set(0, weightKg = 10.0).toMetrics(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `cardio sets read duration, with optional distance`() {
        assertEquals(SetMetrics.Cardio(1_500, 5_000.0), set(0, durationSeconds = 1_500, distanceMeters = 5_000.0).toMetrics(ExerciseType.CARDIO))
        assertEquals(SetMetrics.Cardio(60, null), set(0, durationSeconds = 60).toMetrics(ExerciseType.CARDIO))
        assertNull(set(0, distanceMeters = 5_000.0).toMetrics(ExerciseType.CARDIO))
    }

    @Test
    fun `a session keeps only completed sets, in logging order`() {
        val row = ExerciseSessionRow("we", "w", "Push", Instant.EPOCH, ExerciseType.STRENGTH)
        val session = ExerciseSessionWithSets(
            session = row,
            sets = listOf(
                set(2, weightKg = 77.5, reps = 6),
                set(0, weightKg = 40.0, reps = 10, setType = SetType.WARMUP),
                set(3, weightKg = 80.0, reps = 5, isCompleted = false),
                set(1, weightKg = 80.0, reps = 5),
            ),
        ).toDomain()

        assertEquals("Push", session.workoutName)
        assertEquals(
            listOf(
                LoggedSet(SetType.WARMUP, SetMetrics.Strength(40.0, 10)),
                LoggedSet(SetType.NORMAL, SetMetrics.Strength(80.0, 5)),
                LoggedSet(SetType.NORMAL, SetMetrics.Strength(77.5, 6)),
            ),
            session.sets,
        )
    }
}
