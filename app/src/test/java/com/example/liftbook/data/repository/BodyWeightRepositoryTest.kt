package com.example.liftbook.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.LiftBookDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The body-weight log (FR-5.4) against a real, in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class BodyWeightRepositoryTest {

    private lateinit var database: LiftBookDatabase
    private lateinit var repository: BodyWeightRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LiftBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BodyWeightRepositoryImpl(database, database.bodyWeightDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `weigh-ins come back oldest first, in kilograms, on their day`() = runTest {
        repository.log(LocalDate.of(2026, 9, 25), 82.4)
        repository.log(LocalDate.of(2026, 9, 20), 83.1)

        val entries = repository.observeEntries().first()

        assertEquals(listOf(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 25)), entries.map { it.date })
        assertEquals(listOf(83.1, 82.4), entries.map { it.weightKg })
    }

    @Test
    fun `logging a day again replaces its weigh-in, keeping its id`() = runTest {
        val day = LocalDate.of(2026, 9, 25)
        repository.log(day, 82.4)
        val id = repository.observeEntries().first().single().id

        repository.log(day, 81.9)

        val entry = repository.observeEntries().first().single()
        assertEquals(id, entry.id)
        assertEquals(81.9, entry.weightKg, 0.0)
    }

    @Test
    fun `a weigh-in can be deleted`() = runTest {
        repository.log(LocalDate.of(2026, 9, 25), 82.4)
        repository.log(LocalDate.of(2026, 9, 26), 82.1)
        val first = repository.observeEntries().first().first()

        repository.delete(first.id)

        assertEquals(listOf(LocalDate.of(2026, 9, 26)), repository.observeEntries().first().map { it.date })
    }

    @Test
    fun `nothing isn't a weight`() = runTest {
        val failure = runCatching { repository.log(LocalDate.of(2026, 9, 25), 0.0) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(repository.observeEntries().first().isEmpty())
    }
}
