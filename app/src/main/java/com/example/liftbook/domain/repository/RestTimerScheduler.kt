package com.example.liftbook.domain.repository

import java.time.Instant

/**
 * Alerts the user when a rest ends — a notification and vibration, with the screen off, in Doze,
 * or after the app is killed (FR-3.5, NFR-3). The alert is the authority; the countdown on
 * screen is only subtraction from the same end time (architecture §5.3).
 */
interface RestTimerScheduler {

    /**
     * Replaces any alert already scheduled with one at [endsAt]. [nextExerciseName] names the
     * exercise of the next set, for the alert's text; null when every set is done.
     */
    fun schedule(endsAt: Instant, nextExerciseName: String?)

    /** Cancels the scheduled alert, and takes down one already showing. */
    fun cancel()
}
