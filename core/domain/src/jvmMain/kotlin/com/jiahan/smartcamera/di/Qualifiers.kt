package com.jiahan.smartcamera.di

import javax.inject.Qualifier

/*
 * DI qualifiers, kept apart from the module that satisfies them.
 *
 * These are plain JSR-330 annotations with no dependency beyond javax.inject. Their @Provides
 * counterparts cannot follow them down: AppModule needs BuildConfig and Play Core. Repositories
 * annotate constructor parameters with these, so the annotations have to sit in a module the data
 * layer can see, and :core:domain is it.
 *
 * `jvmMain`, not `commonMain`, and it is the module's only non-common file. javax.inject is a JVM
 * artifact and Hilt is Android-only, so a qualifier is the one thing here an iOS target could never
 * use — putting it in commonMain would break the metadata compilation that keeps the rest of this
 * module honest. Nothing at the injection sites had to change for the move: an Android consumer
 * resolves this module's `jvm` variant and so still sees these three.
 *
 * If a shared data layer ever happens, these do not come with it — see the Kotlin Multiplatform
 * section of ARCHITECTURE.md for the Hilt ceiling that decides it.
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