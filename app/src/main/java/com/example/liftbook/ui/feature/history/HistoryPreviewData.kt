package com.example.liftbook.ui.feature.history

import com.example.liftbook.domain.calculator.byDay
import com.example.liftbook.domain.calculator.totals
import com.example.liftbook.domain.model.WorkoutListItem
import com.example.liftbook.ui.feature.routines.RoutinePreviewData
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Sample data for the history previews: a few weeks of push, pull and legs. */
internal object HistoryPreviewData {

    val zone: ZoneId = RoutinePreviewData.zone
    val today: LocalDate = RoutinePreviewData.today

    private fun workout(
        id: String,
        name: String,
        date: LocalDate,
        hour: Int,
        minutes: Long,
        volumeKg: Double,
        sets: Int,
        vararg exercises: String,
    ): WorkoutListItem {
        val start = date.atTime(hour, 5).atZone(zone).toInstant()
        return WorkoutListItem(id, name, start, start.plusSeconds(minutes * 60), volumeKg, sets, exercises.toList())
    }

    private val push = arrayOf("Bench Press (Barbell)", "Overhead Press (Barbell)", "Chest Dip", "Triceps Pushdown (Cable)")
    private val pull = arrayOf("Pull-Up", "Bent-Over Row (Barbell)", "Lat Pulldown (Cable)", "Biceps Curl (Dumbbell)")
    private val legs = arrayOf("Squat (Barbell)", "Romanian Deadlift (Barbell)", "Leg Press (Machine)")

    /** Newest first, as the history lists them. */
    val workouts: List<WorkoutListItem> = listOf(
        workout("w9", "Push", today.minusDays(1), 18, 64, 8_420.0, 18, *push),
        workout("w8", "Evening workout", today.minusDays(3), 19, 38, 0.0, 9, "Pull-Up", "Hanging Leg Raise", "Plank"),
        workout("w7", "Legs", today.minusDays(4), 7, 71, 12_900.0, 16, *legs),
        workout("w6", "Pull", today.minusDays(6), 18, 58, 6_150.0, 17, *pull),
        workout("w5", "Push", today.minusDays(8), 18, 62, 8_060.0, 18, *push),
        workout("w4", "Legs", today.minusDays(11), 7, 66, 12_400.0, 16, *legs),
        workout("w3", "Pull", today.minusDays(13), 18, 55, 5_980.0, 17, *pull),
        workout("w2", "Push", today.minusDays(29), 18, 60, 7_900.0, 18, *push),
        workout("w1", "Legs", today.minusDays(31), 7, 70, 12_100.0, 16, *legs),
    )

    /**
     * The list's rows, with each month's heading: built up front, since a preview can't wait for
     * paging to transform its data.
     */
    val listItems: List<HistoryListItem> = buildList {
        var previous: WorkoutListItem? = null
        workouts.forEach { workout ->
            monthHeadingBetween(previous, workout, zone)?.let { add(HistoryListItem.Month(it)) }
            add(HistoryListItem.Workout(workout))
            previous = workout
        }
    }

    fun calendar(month: YearMonth = YearMonth.from(today)): CalendarMonth {
        val inMonth = workouts.filter { YearMonth.from(it.startedAt.atZone(zone)) == month }.reversed()
        return CalendarMonth(month, inMonth.byDay(zone), inMonth.totals(), hasNext = month < YearMonth.from(today))
    }
}
