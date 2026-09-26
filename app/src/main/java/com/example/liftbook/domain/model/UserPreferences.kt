package com.example.liftbook.domain.model

/**
 * User settings (§2.10 of the architecture). Each slice adds the keys it needs. The settings
 * screen arrives with FR-6.1; until then the rest default is changed from the workout screen.
 */
data class UserPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Rest after a set, for exercises without their own rest time; 0 turns the timer off (FR-3.5). */
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 90
    }
}

/**
 * The display unit system. Values are always stored metric — kilograms and metres — and
 * converted only for display (FR-6.1). Distances follow the same choice: km with kg,
 * miles with lb.
 */
enum class WeightUnit {
    KG,
    LB,
}
