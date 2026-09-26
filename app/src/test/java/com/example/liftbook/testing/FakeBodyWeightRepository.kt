package com.example.liftbook.testing

import com.example.liftbook.domain.model.BodyWeightEntry
import com.example.liftbook.domain.repository.BodyWeightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** An in-memory body-weight log with the real rule: one weigh-in a day, logging again replaces it. */
class FakeBodyWeightRepository(initial: List<BodyWeightEntry> = emptyList()) : BodyWeightRepository {

    val entries = MutableStateFlow(initial.sortedBy { it.date })

    /** When set, the next write throws, as a failing database would. */
    var failNextWrite = false

    private var idCount = 0

    override fun observeEntries(): Flow<List<BodyWeightEntry>> = entries

    override suspend fun log(date: LocalDate, weightKg: Double) {
        failIfAsked()
        require(weightKg > 0.0) { "A body weight is more than nothing" }
        entries.update { current ->
            val existing = current.firstOrNull { it.date == date }
            val entry = existing?.copy(weightKg = weightKg) ?: BodyWeightEntry(id = "bw-${++idCount}", date = date, weightKg = weightKg)
            (current.filterNot { it.date == date } + entry).sortedBy { it.date }
        }
    }

    override suspend fun delete(id: String) {
        failIfAsked()
        entries.update { current -> current.filterNot { it.id == id } }
    }

    private fun failIfAsked() {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("Simulated write failure")
        }
    }
}
