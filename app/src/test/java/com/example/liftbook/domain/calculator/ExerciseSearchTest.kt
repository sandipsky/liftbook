package com.example.liftbook.domain.calculator

import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseFilter
import com.example.liftbook.domain.model.MuscleGroup
import com.example.liftbook.testing.exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {

    private val benchBarbell = exercise("Bench Press (Barbell)", MuscleGroup.CHEST, Equipment.BARBELL)
    private val benchDumbbell = exercise("Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL)
    private val inclineDumbbell = exercise("Incline Bench Press (Dumbbell)", MuscleGroup.CHEST, Equipment.DUMBBELL)
    private val pullUp = exercise("Pull-Up", MuscleGroup.BACK, Equipment.NONE)
    private val bentOverRow = exercise("Bent-Over Row (Barbell)", MuscleGroup.BACK, Equipment.BARBELL)
    private val oneArmRow = exercise("One-Arm Row (Dumbbell)", MuscleGroup.BACK, Equipment.DUMBBELL)
    private val rowing = exercise("Rowing (Machine)", MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)
    private val latPulldown = exercise("Lat Pulldown (Cable)", MuscleGroup.BACK, Equipment.CABLE)
    private val romanianDeadlift = exercise("Romanian Deadlift (Barbell)", MuscleGroup.HAMSTRINGS, Equipment.BARBELL)
    private val curl = exercise("Bicep Curl (Barbell)", MuscleGroup.BICEPS, Equipment.BARBELL)
    private val abWheel = exercise("Ab Wheel Rollout", MuscleGroup.CORE, Equipment.OTHER)
    private val lunge = exercise("Walking Lunge (Dumbbell)", MuscleGroup.QUADS, Equipment.DUMBBELL)

    private val library = listOf(
        benchBarbell, benchDumbbell, inclineDumbbell, pullUp, bentOverRow, oneArmRow, rowing,
        latPulldown, romanianDeadlift, curl, abWheel, lunge,
    )

    private fun search(query: String = "", muscle: MuscleGroup? = null, equipment: Equipment? = null) =
        searchExercises(library, ExerciseFilter(query, muscle, equipment))

    @Test
    fun `no query and no filters returns the whole library alphabetically`() {
        assertEquals(library.sortedBy { it.name.lowercase() }, search())
    }

    @Test
    fun `blank query is treated as no query`() {
        assertEquals(search(), search("   "))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf(benchBarbell, benchDumbbell, inclineDumbbell), search("BENCH"))
    }

    @Test
    fun `every word must match, in any order`() {
        assertEquals(listOf(benchDumbbell, inclineDumbbell), search("dumbbell bench"))
        assertEquals(emptyList<Any>(), search("bench curl"))
    }

    @Test
    fun `spacing and punctuation don't matter`() {
        assertEquals(listOf(pullUp), search("pull up"))
        assertEquals(listOf(pullUp), search("pullup"))
        assertEquals(listOf(pullUp), search("PULL-UP"))
    }

    @Test
    fun `names that start with the query come before other matches`() {
        // Alphabetically "Bent-Over Row" and "One-Arm Row" come first, but "Rowing" starts with the query.
        assertEquals(listOf(rowing, bentOverRow, oneArmRow), search("row"))
    }

    @Test
    fun `a match inside a word ranks below whole-word matches`() {
        val downwardDog = exercise("Downward Dog", MuscleGroup.OTHER, Equipment.NONE)
        val results = searchExercises(library + downwardDog, ExerciseFilter("down"))
        assertEquals(listOf(downwardDog, latPulldown), results)
    }

    @Test
    fun `accents are ignored`() {
        val elevation = exercise("Élévation latérale", MuscleGroup.SHOULDERS, Equipment.DUMBBELL)
        assertEquals(listOf(elevation), searchExercises(listOf(elevation, curl), ExerciseFilter("elevation")))
    }

    @Test
    fun `plural words find singular names`() {
        assertEquals(listOf(curl), search("curls"))
        assertEquals(listOf(lunge), search("lunges"))
        assertEquals(listOf(abWheel), search("abs"))
    }

    @Test
    fun `gym shorthand is understood`() {
        assertEquals(listOf(oneArmRow), search("db row"))
        assertEquals(listOf(romanianDeadlift), search("rdl"))
        assertEquals(listOf(benchBarbell), search("bb bench"))
    }

    @Test
    fun `shorthand still matches names that literally contain it`() {
        val dbShrug = exercise("DB Shrug", MuscleGroup.BACK, Equipment.DUMBBELL, isCustom = true)
        val results = searchExercises(library + dbShrug, ExerciseFilter("db"))
        assertTrue(dbShrug in results)
        assertTrue(benchDumbbell in results)
    }

    @Test
    fun `muscle filter keeps only that muscle group`() {
        assertEquals(listOf(bentOverRow, latPulldown, oneArmRow, pullUp), search(muscle = MuscleGroup.BACK))
    }

    @Test
    fun `equipment filter keeps only that equipment`() {
        assertEquals(listOf(benchDumbbell, inclineDumbbell, oneArmRow, lunge), search(equipment = Equipment.DUMBBELL))
    }

    @Test
    fun `query and filters combine`() {
        assertEquals(listOf(oneArmRow), search("row", muscle = MuscleGroup.BACK, equipment = Equipment.DUMBBELL))
        assertEquals(emptyList<Any>(), search("bench", muscle = MuscleGroup.BACK))
    }

    @Test
    fun `normalizing strips accents, case and punctuation`() {
        assertEquals("bench press barbell", normalizeForSearch("  Bench-Press (Barbell) "))
        assertEquals("elevation laterale", normalizeForSearch("Élévation Latérale"))
    }
}
