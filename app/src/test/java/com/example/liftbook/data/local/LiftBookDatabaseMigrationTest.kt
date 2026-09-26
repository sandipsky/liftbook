package com.example.liftbook.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.liftbook.data.local.entity.BodyWeightEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * Migrations are tested against the committed schema JSON (architecture §2.12). Room's
 * MigrationTestHelper reads schemas from instrumentation assets, which Robolectric unit tests
 * don't have, so this builds the old database straight from app/schemas and then lets Room open
 * it — running the migration and checking the result against the current schema, which throws
 * if they differ.
 */
@RunWith(RobolectricTestRunner::class)
class LiftBookDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 to 2 keeps every workout and adds an empty rest timer`() = runTest {
        createFromSchema(version = 1) { db ->
            db.execSQL("INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES ('done', 'Push', NULL, 0, 3600000, 'Good')")
            db.execSQL("INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note) VALUES ('live', 'Pull', NULL, 7200000, NULL, NULL)")
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            val active = database.workoutDao().observeActive().first()!!
            assertEquals("live", active.workout.id)
            assertNull(active.workout.restStartedAt)
            assertNull(active.workout.restEndsAt)
            database.openHelper.readableDatabase.query("SELECT COUNT(*), note FROM workouts WHERE id = 'done'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("Good", cursor.getString(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 to 3 keeps every workout and adds an empty body-weight log`() = runTest {
        createFromSchema(version = 2) { db ->
            db.execSQL(
                "INSERT INTO workouts (id, name, routineId, startedAt, finishedAt, note, restStartedAt, restEndsAt) " +
                    "VALUES ('done', 'Push', NULL, 0, 3600000, 'Good', NULL, NULL)",
            )
        }

        val database = Room.databaseBuilder(context, LiftBookDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            // Opening runs the migration and validates the migrated schema.
            assertEquals(emptyList<Any>(), database.bodyWeightDao().observeAll().first())
            assertEquals("Push", database.workoutDao().getById("done")?.name)
            // The new table works, one weigh-in a day.
            database.bodyWeightDao().insert(
                BodyWeightEntryEntity("e", 82.4, LocalDate.of(2026, 9, 26), null),
            )
            assertEquals(82.4, database.bodyWeightDao().getOn(LocalDate.of(2026, 9, 26))?.weightKg)
        } finally {
            database.close()
        }
    }

    /** Creates the database exactly as schema [version] describes it, then runs [populate] on it. */
    private fun createFromSchema(version: Int, populate: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices") ?: continue
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper -> populate(helper.writableDatabase) }
    }

    private companion object {
        /** Unit tests run in the module directory. */
        val SCHEMA_DIR = File("schemas/com.example.liftbook.data.local.LiftBookDatabase")
    }
}
