package com.jiahan.smartcamera.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import com.jiahan.smartcamera.database.converter.DatabaseConverters
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.DatabaseNote

/**
 * The Room database for this app.
 *
 * The database *file* is still called `photo-database` (`DatabaseModule`, in :core:data), which is
 * now a misnomer: it holds the notes mirror and nothing else. Renaming it would point Room at a
 * different file and orphan every installed user's cache, so the name stays and this comment
 * explains it.
 */
@Database(
    entities = [DatabaseNote::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.DropPhotosTable::class)],
)
@TypeConverters(DatabaseConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * v1 -> v2 drops the `photos` table, which no production code ever read or wrote -- only its
     * own DAO test did. Its entity was also a second, unrelated `Photo` type competing with
     * `domain.Photo` (an Unsplash result), so deleting it removes dead schema and a name collision
     * at once.
     *
     * An `@DeleteTable` auto-migration rather than a hand-written `Migration(1, 2)`: Room validates
     * it against the exported `1.json` at compile time and registers it itself, so there is no SQL
     * to get wrong and no `addMigrations` call to forget -- and forgetting one would crash on
     * upgrade, since `DatabaseModule` builds the database with neither migrations nor a destructive
     * fallback.
     */
    @DeleteTable(tableName = "photos")
    class DropPhotosTable : AutoMigrationSpec

    abstract fun noteDao(): NoteDao
}

/**
 * How Room instantiates [AppDatabase] on a target with no reflection to find `AppDatabase_Impl`
 * by, which is every Apple one. Declared `expect` here and never implemented by hand: the Room
 * compiler writes an `actual` for each target during that target's KSP run, which is why the build
 * file adds the compiler to every `ksp<Target>` configuration rather than to one.
 */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}