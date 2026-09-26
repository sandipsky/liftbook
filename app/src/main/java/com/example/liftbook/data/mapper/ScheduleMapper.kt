package com.example.liftbook.data.mapper

import com.example.liftbook.data.local.entity.WorkoutScheduleEntity
import com.example.liftbook.data.local.projection.ScheduleRow
import com.example.liftbook.domain.model.ScheduleDraft
import com.example.liftbook.domain.model.WorkoutSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.EnumSet

fun ScheduleRow.toDomain(): WorkoutSchedule = WorkoutSchedule(
    id = schedule.id,
    days = daysFromBits(schedule.daysOfWeek),
    startTime = LocalTime.ofSecondOfDay(schedule.startTimeMinutes.coerceIn(0, LAST_MINUTE) * SECONDS_PER_MINUTE),
    routineId = schedule.routineId,
    routineName = routineName,
    leadMinutes = schedule.leadTimeMinutes,
    isEnabled = schedule.isEnabled,
    snoozedUntil = schedule.snoozedUntil,
    skippedOn = schedule.skippedOn,
)

fun ScheduleDraft.toEntity(id: String, createdAt: Instant): WorkoutScheduleEntity = WorkoutScheduleEntity(
    id = id,
    daysOfWeek = days.toBits(),
    startTimeMinutes = startTime.minutesOfDay(),
    routineId = routineId,
    leadTimeMinutes = leadMinutes,
    isEnabled = true,
    snoozedUntil = null,
    skippedOn = null,
    createdAt = createdAt,
)

/** ISO days as bits, Monday the lowest (§2.7): Monday is 1, Sunday 64. */
fun Set<DayOfWeek>.toBits(): Int = fold(0) { bits, day -> bits or day.bit() }

fun daysFromBits(bits: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(EnumSet.noneOf(DayOfWeek::class.java)) { (bits and it.bit()) != 0 }

fun LocalTime.minutesOfDay(): Int = hour * MINUTES_PER_HOUR + minute

private fun DayOfWeek.bit(): Int = 1 shl (value - 1)

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60
private const val LAST_MINUTE = 24 * 60 - 1
