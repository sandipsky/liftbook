package com.example.liftbook.testing

import com.example.liftbook.domain.repository.RestTimerScheduler
import java.time.Instant

/** Records the rest alert instead of setting an alarm. */
class FakeRestTimerScheduler : RestTimerScheduler {

    /** When the alert would fire; null when none is scheduled. */
    var scheduledAt: Instant? = null
        private set

    var nextExerciseName: String? = null
        private set

    override fun schedule(endsAt: Instant, nextExerciseName: String?) {
        scheduledAt = endsAt
        this.nextExerciseName = nextExerciseName
    }

    override fun cancel() {
        scheduledAt = null
        nextExerciseName = null
    }
}
