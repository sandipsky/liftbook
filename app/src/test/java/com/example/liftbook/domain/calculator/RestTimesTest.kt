package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.WorkoutExercise
import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RestTimesTest {

    private val now = Instant.parse("2026-09-25T19:00:00Z")

    private fun workoutExercise(own: Int? = null, override: Int? = null) = WorkoutExercise(
        id = "we",
        exercise = exercise("Bench Press (Barbell)").copy(defaultRestSeconds = own),
        sets = emptyList(),
        restSecondsOverride = override,
    )

    @Test
    fun `rest comes from this workout, else the exercise, else the default`() {
        assertEquals(90, RestTimes.secondsFor(workoutExercise(), defaultSeconds = 90))
        assertEquals(180, RestTimes.secondsFor(workoutExercise(own = 180), defaultSeconds = 90))
        assertEquals(120, RestTimes.secondsFor(workoutExercise(own = 180, override = 120), defaultSeconds = 90))
        // An exercise can turn its timer off while the default stays on.
        assertEquals(0, RestTimes.secondsFor(workoutExercise(own = 0), defaultSeconds = 90))
    }

    @Test
    fun `a rest runs from now for its length, and a length of zero is no rest`() {
        assertEquals(RestTimer(now, now.plusSeconds(90)), RestTimes.start(now, 90))
        assertNull(RestTimes.start(now, 0))
    }

    @Test
    fun `adjusting moves the end, keeping the start`() {
        val rest = RestTimer(now.minusSeconds(30), now.plusSeconds(60))

        assertEquals(RestTimer(now.minusSeconds(30), now.plusSeconds(90)), RestTimes.adjust(rest, 30, now))
        assertEquals(RestTimer(now.minusSeconds(30), now.plusSeconds(30)), RestTimes.adjust(rest, -30, now))
    }

    @Test
    fun `taking a rest down to nothing ends it`() {
        assertNull(RestTimes.adjust(RestTimer(now.minusSeconds(80), now.plusSeconds(10)), -30, now))
    }

    @Test
    fun `adding to a rest that's over counts from now`() {
        val over = RestTimer(now.minusSeconds(120), now.minusSeconds(20))

        assertEquals(now.plusSeconds(30), RestTimes.adjust(over, 30, now)!!.endsAt)
    }

    @Test
    fun `time left never goes below zero`() {
        val rest = RestTimer(now.minusSeconds(90), now.minusSeconds(1))

        assertEquals(Duration.ZERO, rest.remaining(now))
        assertEquals(true, rest.isOver(now))
        assertEquals(Duration.ofSeconds(1), RestTimer(now, now.plusSeconds(1)).remaining(now))
    }
}
