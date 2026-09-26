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
fun detectPersonalRecords(previous: List<LoggedSet>, current: List<LoggedSet>): List<PersonalRecord> =
    detectRecordSets(previous, current).map { it.record }

/** A record, and the set that set it: its index in the sets it was found in. */
data class SetRecord(val setIndex: Int, val record: PersonalRecord)

/**
 * [detectPersonalRecords], saying which of [current] set each record, so the set can be flagged
 * where it's shown (FR-5.2). When sets tie, the first to reach the number holds the record. The
 * records come in the same order as [detectPersonalRecords] gives them.
 */
fun detectRecordSets(previous: List<LoggedSet>, current: List<LoggedSet>): List<SetRecord> {
    val before = previous.working().map { it.metrics }
    val now = current.working()
    if (before.isEmpty() || now.isEmpty()) return emptyList()

    val records = mutableListOf<SetRecord>()
    val strengthBefore = before.mapNotNull { it as? SetMetrics.Strength }
    val strengthNow = now.mapNotNull { set -> (set.metrics as? SetMetrics.Strength)?.let { IndexedValue(set.index, it) } }
    if (strengthBefore.isNotEmpty() && strengthNow.isNotEmpty()) {
        // maxWith and maxBy keep the first of equal elements, so the first to reach it holds it.
        val heaviest = strengthNow.maxWith(compareBy({ it.value.weightKg }, { it.value.reps }))
        if (heaviest.value.weightKg > strengthBefore.maxOf { it.weightKg } + EPSILON_KG) {
            records += SetRecord(heaviest.index, PersonalRecord.HeaviestWeight(heaviest.value.weightKg, heaviest.value.reps))
        }
        val best = strengthNow.maxBy { oneRepMax(it.value.weightKg, it.value.reps) }
        val bestEstimate = oneRepMax(best.value.weightKg, best.value.reps)
        if (bestEstimate > strengthBefore.maxOf { oneRepMax(it.weightKg, it.reps) } + EPSILON_KG) {
            records += SetRecord(best.index, PersonalRecord.BestEstimatedOneRepMax(bestEstimate, best.value.weightKg, best.value.reps))
        }
    }

    val repsBefore = before.mapIndexed(::IndexedValue).mostRepsByWeight()
    now.map { IndexedValue(it.index, it.metrics) }.mostRepsByWeight().entries
        .sortedBy { it.value.weightKg }
        .forEach { (grams, best) ->
            val earlier = repsBefore[grams] ?: return@forEach
            if (best.reps > earlier.reps) records += SetRecord(best.setIndex, PersonalRecord.MostReps(best.reps, best.weightKg))
        }
    return records
}

/** Working sets, each with its index among all the sets given. */
private fun List<LoggedSet>.working(): List<IndexedSet> =
    mapIndexedNotNull { index, set -> if (set.setType == SetType.WARMUP) null else IndexedSet(index, set.metrics) }

private class IndexedSet(val index: Int, val metrics: SetMetrics)

private data class RepsAtWeight(val setIndex: Int, val weightKg: Double, val reps: Int)

/**
 * The most reps done at each weight, keyed by the weight in whole grams. Weights typed in
 * pounds are stored converted, so the same plate total can differ in the last few digits of a
 * Double; grams are far finer than any plate and absorb that.
 */
private fun List<IndexedValue<SetMetrics>>.mostRepsByWeight(): Map<Long, RepsAtWeight> {
    val best = mutableMapOf<Long, RepsAtWeight>()
    forEach { (index, metrics) ->
        val set = when (metrics) {
            is SetMetrics.Strength -> RepsAtWeight(index, metrics.weightKg, metrics.reps)
            is SetMetrics.Bodyweight -> RepsAtWeight(index, metrics.addedWeightKg ?: 0.0, metrics.reps)
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
