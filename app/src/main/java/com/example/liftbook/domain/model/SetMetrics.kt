package com.example.liftbook.domain.model

/**
 * What a set recorded. A sealed type rather than four nullable fields, so a set that doesn't
 * match its exercise type — a cardio set with a weight and no duration — can't be represented
 * above the data layer.
 */
sealed interface SetMetrics {
    data class Strength(val weightKg: Double, val reps: Int) : SetMetrics

    data class Cardio(val durationSeconds: Int, val distanceMeters: Double?) : SetMetrics

    data class Bodyweight(val reps: Int, val addedWeightKg: Double?) : SetMetrics
}

/** FR-3.10. Persisted by name — never rename a constant. */
enum class SetType {
    NORMAL,
    WARMUP,
    DROP,
    FAILURE,
}
