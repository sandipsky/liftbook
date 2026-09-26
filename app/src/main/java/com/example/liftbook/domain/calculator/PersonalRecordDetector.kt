package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.LoggedSet
import com.example.liftbook.domain.model.PersonalRecord
import com.example.liftbook.domain.model.SetMetrics
import com.example.liftbook.domain.model.SetType
import kotlin.math.roundToLong

/**
 * The personal records [current] sets against [previous] — the same exercise's sets from every
 * earlier workout (FR-5.2):
 *
 * - **Heaviest weight**: more weight than any earlier working set.
 * - **Best estimated 1RM**: a higher Epley estimate than any earlier set.
 * - **Most reps at a weight**: more reps than ever before at a weight lifted before.
 *
 * Warm-ups count on neither side (FR-3.10). The first time an exercise is done nothing is a
 * record — every number would be — and "most reps" only counts at a weight done before, for
 * the same reason. Heaviest weight and 1RM apply to weighted sets; most reps also applies to
 * bodyweight sets, at their added weight. Cardio has no records.
 */
fun detectPersonalRecords(previous: List<LoggedSet>, current: List<LoggedSet>): List<PersonalRecord> {
    val before = previous.working()
    val now = current.working()
    if (before.isEmpty() || now.isEmpty()) return emptyList()

    val records = mutableListOf<PersonalRecord>()
    val strengthBefore = before.mapNotNull { it as? SetMetrics.Strength }
    val strengthNow = now.mapNotNull { it as? SetMetrics.Strength }
    if (strengthBefore.isNotEmpty() && strengthNow.isNotEmpty()) {
        val heaviest = strengthNow.maxWith(compareBy({ it.weightKg }, { it.reps }))
        if (heaviest.weightKg > strengthBefore.maxOf { it.weightKg } + EPSILON_KG) {
            records += PersonalRecord.HeaviestWeight(heaviest.weightKg, heaviest.reps)
        }
        val best = strengthNow.maxBy { oneRepMax(it.weightKg, it.reps) }
        val bestEstimate = oneRepMax(best.weightKg, best.reps)
        if (bestEstimate > strengthBefore.maxOf { oneRepMax(it.weightKg, it.reps) } + EPSILON_KG) {
            records += PersonalRecord.BestEstimatedOneRepMax(bestEstimate, best.weightKg, best.reps)
        }
    }

    val repsBefore = before.mostRepsByWeight()
    now.mostRepsByWeight().entries
        .sortedBy { it.value.weightKg }
        .forEach { (grams, best) ->
            val earlier = repsBefore[grams] ?: return@forEach
            if (best.reps > earlier.reps) records += PersonalRecord.MostReps(best.reps, best.weightKg)
        }
    return records
}

/** Working sets as reps at a weight, or nothing for cardio. */
private fun List<LoggedSet>.working(): List<SetMetrics> = filter { it.setType != SetType.WARMUP }.map { it.metrics }

private data class RepsAtWeight(val weightKg: Double, val reps: Int)

/**
 * The most reps done at each weight, keyed by the weight in whole grams. Weights typed in
 * pounds are stored converted, so the same plate total can differ in the last few digits of a
 * Double; grams are far finer than any plate and absorb that.
 */
private fun List<SetMetrics>.mostRepsByWeight(): Map<Long, RepsAtWeight> {
    val best = mutableMapOf<Long, RepsAtWeight>()
    forEach { metrics ->
        val set = when (metrics) {
            is SetMetrics.Strength -> RepsAtWeight(metrics.weightKg, metrics.reps)
            is SetMetrics.Bodyweight -> RepsAtWeight(metrics.addedWeightKg ?: 0.0, metrics.reps)
            is SetMetrics.Cardio -> return@forEach
        }
        val grams = (set.weightKg * GRAMS_PER_KG).roundToLong()
        val current = best[grams]
        if (current == null || set.reps > current.reps) best[grams] = set
    }
    return best
}

/** Far below any real difference in load, far above floating-point noise. */
private const val EPSILON_KG = 1e-6
private const val GRAMS_PER_KG = 1_000.0
