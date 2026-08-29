package com.jiahan.smartcamera.di

import javax.inject.Qualifier

/*
 * DI qualifiers, kept apart from the module that satisfies them.
 *
 * These are plain JSR-330 annotations with no dependency beyond javax.inject, so they can move to
 * a shared module as-is. Their @Provides counterparts cannot: AppModule needs BuildConfig, Play
 * Core and the note feature handlers. Repositories annotate constructor parameters with these, so
 * the annotations have to end up in a module the data layer can see.
 *
 * Splitting the file does not by itself break anything — everything here is still :app. It makes
 * the extraction a file move rather than a file edit, which is the whole of its purpose.
 */

/**
 * Qualifier for an application-scoped [kotlinx.coroutines.CoroutineScope] backed by a
 * [kotlinx.coroutines.SupervisorJob]. Use this instead of [kotlinx.coroutines.GlobalScope] for
 * fire-and-forget work that must outlive any single ViewModel or Screen but still be tied to the
 * process lifetime.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Qualifier for the IO [kotlinx.coroutines.CoroutineDispatcher]. Inject this instead of
 * referencing [kotlinx.coroutines.Dispatchers.IO] directly so tests can substitute a
 * `TestDispatcher`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Qualifier for the debug-build flag, for code that cannot see the application module's
 * `BuildConfig`. See the Build type bullet under Conventions in AGENTS.md for when to inject this
 * and — just as important — when to keep reading `BuildConfig.DEBUG` directly.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild