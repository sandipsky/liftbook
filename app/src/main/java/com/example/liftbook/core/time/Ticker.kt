package com.example.liftbook.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

/**
 * "Now", once a second, on the second — for a clock on screen (FR-3.5, FR-3.6). It only says
 * when to redraw: what's shown is always computed from a stored instant, never counted, so a
 * late or missed tick loses no time (architecture §6.1).
 */
fun Clock.ticks(): Flow<Instant> = flow {
    while (true) {
        val now = instant()
        emit(now)
        delay(MILLIS_PER_SECOND - now.toEpochMilli() % MILLIS_PER_SECOND)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
