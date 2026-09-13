/*
 * Kotlin Multiplatform module: the Room database -- `AppDatabase`, `NoteDao`, `DatabaseNote` with its
 * mappers, and `DatabaseConverters` -- in `commonMain`, and the schema JSON it exports in `schemas/`.
 *
 * The first shared module with an Android target, and that is Room's doing rather than a change of
 * policy. Every other multiplatform module here gives Android its `jvm` variant, which works because
 * their code runs the same on both. Room's does not: its compiler generates `AppDatabase_Impl` per
 * target, and the Android runtime that code meets on a device is a different artifact from the JVM
 * one. So Android gets an Android build of this module, through the target
 * `smartphotos.kmp.android.library` adds beside the usual JVM and Apple ones.
 *
 * What stays in :core:data is what needs a `Context`: `DatabaseModule`, which opens the file with
 * Android's framework SQLite exactly as before, and `AppDatabaseMigrationTest`, which pins that
 * upgrade -- its class doc says why it did not move.
 */
plugins {
    // kotlin.multiplatform with the JVM and Apple targets, plus the Android target on the SDK
    // levels every Android module shares.
    id("smartphotos.kmp.android.library")
    // DatabaseConverters calls Json.encodeToString/decodeFromString at reified call sites. With the
    // plugin those resolve to MediaDetail's generated serializer at compile time; without it they
    // fall back to runtime reflection over Kotlin metadata, which still compiles and still passes
    // DatabaseConvertersTest -- the kind of thing release R8 can break with every test green. It
    // came here from :core:data with the converter. Don't drop it as an unused plugin.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.jiahan.smartcamera.core.database"
        // No `withHostTest`, deliberately. commonTest builds its database with the Context-free
        // in-memory builder, which Android's Room does not have, so it could not compile for an
        // Android host test. AGP's configuration warning that commonTest exists without one is
        // this decision, not an oversight.
    }

    sourceSets {
        commonMain.dependencies {
            // api: DatabaseNote carries MediaDetail and maps to Note, and :core:data compiles
            // against both.
            api(project(":core:domain"))
            // api: AppDatabase is a RoomDatabase, and :core:data's DatabaseModule builds one.
            api(libs.room.runtime)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            // kotlin-test and runTest rather than junit and runBlocking: commonTest compiles for the
            // Apple targets too, and neither of those exists there.
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // NoteDaoTest holds one collection open across a write, which is what Turbine is for.
            implementation(libs.turbine)
            // Room's own SQLite, built for every target, so the suites open a real database through
            // one driver on the JVM and on an iOS simulator alike.
            implementation(libs.sqlite.bundled)
        }
    }
}

room {
    // Where :core:data's `room.schemaLocation` KSP argument used to point, now set the way Room
    // documents for a multiplatform module, with one KSP run per target below. The JSON is still
    // the source of truth for migrations: AppDatabaseMigrationTest reads it as a test asset.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // One per target, not one for the module: each target's KSP run generates that target's
    // AppDatabase_Impl and its `actual` AppDatabaseConstructor.
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}