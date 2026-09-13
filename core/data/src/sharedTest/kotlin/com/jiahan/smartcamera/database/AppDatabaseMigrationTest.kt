package com.jiahan.smartcamera.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The v1 -> v2 auto-migration, run against a real database file.
 *
 * Nothing else in this module can catch a broken migration: every other suite builds the database
 * with `inMemoryDatabaseBuilder`, which creates the current schema outright and never migrates. An
 * upgrade path is only exercised by opening a file written by the *old* version, which is what
 * [MigrationTestHelper] does using the exported `schemas/` JSON.
 *
 * The stakes are the reason this exists at all: `DatabaseModule` builds the database with neither
 * `addMigrations` nor `fallbackToDestructiveMigration`, so a migration Room cannot apply is not a
 * silent cache reset -- it throws on open, on the launch after the update, for every installed
 * user. A test suite that never migrates would stay entirely green through that.
 *
 * `runMigrationsAndValidate` does the schema half itself: it applies the migration and compares the
 * result against `2.json`, failing on any column, index or table that does not match. Passing
 * `validateDroppedTables = true` is what makes it also assert `photos` is gone rather than merely
 * unused. What it cannot check is the *data*, so that is what the assertions below are for -- a
 * migration that satisfied the schema by recreating `notes` empty would pass validation and lose
 * every cached note.
 *
 * **Why this suite is in :core:data while [AppDatabase] is in :core:database.** Every other test of
 * the database followed it into that module's `commonTest`, on Room's bundled SQLite. This one pins
 * the upgrade a user's device actually performs: `DatabaseModule` opens the file through Android's
 * framework SQLite, and that is the engine this suite migrates with -- on the JVM under Robolectric
 * and on a device. Moving it would test the migration against a different SQLite from the one users
 * upgrade with. The schema JSON it reads is :core:database's, added as a test asset in this
 * module's build file.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(AppDatabase.DropPhotosTable()),
    )

    private companion object {
        const val TEST_DB = "migration-test"

        /** The v1 column list, from `schemas/.../1.json`. */
        const val INSERT_NOTE =
            "INSERT INTO notes " +
                    "(note_id, text, created_date, favorite, media_list, username, profile_picture_url) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)"
    }

    /**
     * The whole point of the migration: `photos` goes, `notes` and everything in it stays.
     *
     * Both rows are written through the v1 schema, so this is the row shape a user who installed
     * before the update actually has on disk.
     */
    @Test
    fun migrate1To2_dropsPhotos_andKeepsEveryNoteRow() {
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(
                INSERT_NOTE,
                arrayOf<Any?>("note-1", "a cached note", 300L, 1, null, "tester", null),
            )
            v1.execSQL(
                INSERT_NOTE,
                arrayOf<Any?>("note-2", null, 100L, 0, """[{"isVideo":false}]""", "tester", "http://p"),
            )
        }

        // Applies the migration and validates the result against 2.json; the flag adds the
        // assertion that a table absent from the new schema was actually dropped.
        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        v2.query("SELECT note_id, text, created_date, favorite, media_list, profile_picture_url FROM notes ORDER BY created_date DESC")
            .use { cursor ->
                assertEquals(2, cursor.count)

                assertTrue(cursor.moveToFirst())
                assertEquals("note-1", cursor.getString(0))
                assertEquals("a cached note", cursor.getString(1))
                assertEquals(300L, cursor.getLong(2))
                assertEquals(1, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))

                assertTrue(cursor.moveToNext())
                assertEquals("note-2", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals("""[{"isVideo":false}]""", cursor.getString(4))
                assertEquals("http://p", cursor.getString(5))
            }
    }

    /**
     * `media_list` is a `kotlinx.serialization` JSON string in a TEXT column, so the migration must
     * not touch it -- an on-disk format surviving an upgrade unaltered is the thing that keeps old
     * rows decodable, per the `@SerialName` rule in AGENTS.md.
     */
    @Test
    fun migrate1To2_leavesTheSerializedMediaListByteIdentical() {
        val mediaJson =
            """[{"photoUrl":"https://example.com/p.jpg","isVideo":false,"generatedText":["a cat"]}]"""
        helper.createDatabase(TEST_DB, 1).use { v1 ->
            v1.execSQL(INSERT_NOTE, arrayOf<Any?>("note-1", "t", 1L, 0, mediaJson, "tester", null))
        }

        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        v2.query("SELECT media_list FROM notes WHERE note_id = 'note-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(mediaJson, cursor.getString(0))
        }
    }

    /** An empty v1 install upgrades too -- the common case, and the one with no rows to save. */
    @Test
    fun migrate1To2_succeedsOnAnEmptyDatabase() {
        helper.createDatabase(TEST_DB, 1).close()

        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        v2.query("SELECT count(*) FROM notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    /** The dropped table is gone from the catalogue, not merely unreferenced. */
    @Test
    fun migrate1To2_removesThePhotosTableFromTheSchema() {
        helper.createDatabase(TEST_DB, 1).close()

        val v2 = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        v2.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertFalse(tables.contains("photos"))
            assertTrue(tables.contains("notes"))
        }
    }
}
