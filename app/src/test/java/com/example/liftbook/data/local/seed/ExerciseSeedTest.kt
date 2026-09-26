package com.example.liftbook.data.local.seed

import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The built-in library (FR-1.1) is data that ships forever; these guard its integrity. */
class ExerciseSeedTest {

    private val seed = ExerciseSeed.exercises

    @Test
    fun `ids are unique, well-formed UUIDs`() {
        assertEquals(seed.size, seed.map { it.id }.toSet().size)
        seed.forEach { assertEquals(it.id, UUID.fromString(it.id).toString()) }
    }

    @Test
    fun `ids never change`() {
        // Workouts, exports and other devices refer to built-ins by id. If this fails, an id was
        // regenerated — restore it rather than updating the test.
        val byName = seed.associateBy { it.name }
        assertEquals("c468cc5d-6e4d-3f7f-8dbf-0936a13591d5", byName.getValue("Bench Press (Barbell)").id)
        assertEquals("eceb445b-d692-38a6-bd0a-d40234d6538a", byName.getValue("Running").id)
    }

    @Test
    fun `names are unique ignoring case, and already normalised`() {
        assertEquals(seed.size, seed.map { it.name.lowercase() }.toSet().size)
        seed.forEach { exercise ->
            assertEquals(ExerciseNames.normalize(exercise.name), exercise.name)
            assertTrue(exercise.name, exercise.name.length <= ExerciseNames.MAX_LENGTH)
        }
    }

    @Test
    fun `every muscle group and piece of equipment has exercises`() {
        val muscles = seed.map { it.primaryMuscle }.toSet()
        val equipment = seed.map { it.equipment }.toSet()
        // OTHER exists for custom exercises that fit nowhere else.
        assertEquals(MuscleGroup.entries.toSet() - MuscleGroup.OTHER, muscles)
        assertEquals(Equipment.entries.toSet(), equipment)
    }

    @Test
    fun `every type is represented`() {
        assertEquals(ExerciseType.entries.toSet(), seed.map { it.type }.toSet())
    }

    @Test
    fun `cardio exercises and cardio machines log time and distance`() {
        seed.filter { it.primaryMuscle == MuscleGroup.CARDIO || it.equipment == Equipment.CARDIO_MACHINE }
            .forEach { assertEquals(it.name, ExerciseType.CARDIO, it.type) }
    }

    @Test
    fun `loaded equipment is always logged with weight`() {
        val loaded = setOf(Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.CABLE, Equipment.MACHINE)
        seed.filter { it.equipment in loaded }
            .forEach { assertEquals(it.name, ExerciseType.STRENGTH, it.type) }
    }
}
