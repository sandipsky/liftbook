package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.RestTimer
import com.example.liftbook.domain.model.WorkoutExercise
import java.time.Instant

/** Rest timer rules (FR-3.5). */
object RestTimes {

    /** The durations offered, in seconds. 0 turns the timer off. */
    val OPTIONS: List<Int> = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300)

    /** How much one tap of −/+ takes off or adds. */
    const val ADJUST_SECONDS = 30

    /**
     * How long to rest after a set of [exercise]: this workout's own override (from the routine),
     * else the exercise's own rest time, else the global default. 0 means no timer.
     */
    fun secondsFor(exercise: WorkoutExercise, defaultSeconds: Int): Int =
        exercise.restSecondsOverride ?: exercise.exercise.defaultRestSeconds ?: defaultSeconds

    /** A rest of [seconds] starting [now], or none when the timer is off. */
    fun start(now: Instant, seconds: Int): RestTimer? =
        if (seconds > 0) RestTimer(startedAt = now, endsAt = now.plusSeconds(seconds.toLong())) else null

    /**
     * [rest] made longer or shorter by [seconds]. Adding to a rest that's already over counts
     * from [now]; taking it down to nothing ends it, so the result is null.
     */
    fun adjust(rest: RestTimer, seconds: Int, now: Instant): RestTimer? {
        val base = if (rest.isOver(now)) now else rest.endsAt
        val endsAt = base.plusSeconds(seconds.toLong())
        return if (endsAt.isAfter(now)) rest.copy(endsAt = endsAt) else null
    }
}
