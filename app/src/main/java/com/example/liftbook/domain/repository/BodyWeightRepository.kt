package com.example.liftbook.domain.repository

import com.example.liftbook.domain.model.BodyWeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The body-weight log (FR-5.4): at most one weigh-in a day. */
interface BodyWeightRepository {

    /** Every weigh-in, oldest first. A few hundred a year at most, so not paged. */
    fun observeEntries(): Flow<List<BodyWeightEntry>>

    /** Records [weightKg] for [date], replacing the weigh-in that day already had. */
    suspend fun log(date: LocalDate, weightKg: Double)

    suspend fun delete(id: String)
}
