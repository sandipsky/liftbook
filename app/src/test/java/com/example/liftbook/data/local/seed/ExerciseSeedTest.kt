package com.example.liftbook.data.local.seed

import com.example.liftbook.domain.calculator.ExerciseNames
import com.example.liftbook.domain.model.Equipment
import com.example.liftbook.domain.model.ExerciseType
import com.example.liftbook.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

    @Test
    fun `every illustration is a bundled file, and every bundled file is an illustration`() {
        // The app opens the drawing from assets at runtime, so a typo here would only ever show
        // up as a blank tile on the device; and a file nothing names is dead weight in the APK.
        assertTrue(bundledDrawings.path, bundledDrawings.isDirectory)
        val named = seed.map { it.illustration }.toSet()
        named.forEach { name -> assertTrue(name, File(bundledDrawings, "$name.webp").isFile) }
        val bundled = bundledDrawings.listFiles { file -> file.isFile }.orEmpty().map { it.nameWithoutExtension }.toSet()
        assertEquals(emptySet<String>(), bundled - named)
    }

    @Test
    fun `every drawing has a bundled animation, and every animation a drawing`() {
        // The exercise page plays the animation named like the drawing; the two sets stay in step.
        val named = seed.map { it.illustration }.toSet()
        val animated = bundledAnimations.listFiles { file -> file.isFile }.orEmpty().map { it.nameWithoutExtension }.toSet()
        assertEquals(named, animated)
    }

    @Test
    fun `illustrationFor answers by id, and only for built-ins`() {
        assertEquals("barbell_bench_press", ExerciseSeed.illustrationFor("c468cc5d-6e4d-3f7f-8dbf-0936a13591d5"))
        assertNull(ExerciseSeed.illustrationFor("4a300d9d-e11e-31e5-9350-a00cbba6b629")) // retired: Rowing (Machine)
        assertNull(ExerciseSeed.illustrationFor("a-custom-exercise"))
    }

    @Test
    fun `retired ids stay reserved and out of the library`() {
        // A retired id must never be reused for a new built-in: old databases and backups still carry it.
        val library = seed.mapTo(HashSet()) { it.id }
        ExerciseSeed.retiredIds.forEach { id ->
            assertEquals(id, UUID.fromString(id).toString())
            assertTrue(id, id !in library)
        }
        assertEquals(3, ExerciseSeed.retiredIds.size)
    }

    /** Unit tests run with the module directory as the working directory. */
    private val bundledDrawings = File("src/main/assets/exercises")
    private val bundledAnimations = File(bundledDrawings, "animated")
}
