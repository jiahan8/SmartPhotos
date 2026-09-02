package com.jiahan.smartcamera.data.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Firebase SDK singletons the data layer injects.
 *
 * This lived in :app until now, which put the whole Firebase surface in the application's
 * dependency block to satisfy repositories one layer down. Every consumer of every binding below is
 * a `Default*`/`Firebase*` repository in this module -- Firestore, Auth, Functions, RemoteConfig,
 * Analytics and Messaging are named by :core:data sources and by nothing in :app -- so the
 * providers belong here, and moving them drops five `implementation(libs.firebase.*)` lines from
 * :app's build file that were there for code it does not contain.
 *
 * It also closes the graph: with these providers up in the application module, nothing below :app
 * could assemble a repository, so :core:data's own Hilt-shaped tests had to restate them.
 *
 * What stays in :app is what :app itself names: AppCheck (installed in `MyApp`), Crashlytics
 * (`DefaultErrorHandler`), firebase-messaging (its `FirebaseMessagingService` subclass -- so both
 * modules name that artifact and both declare it), and the two with no source reference at all,
 * firebase-perf and firebase-inappmessaging-display, which auto-initialise.
 *
 * `provideFirebaseInAppMessaging` used to close this file and did not come along: nothing in the
 * build injects `FirebaseInAppMessaging`. Removing it changes no behaviour -- the display library
 * initialises itself and shows campaigns without being asked -- but an unused binding in a Hilt
 * module reads exactly like a live one, so it survives every refactor unless something goes
 * looking. Same rule as an unused dependency, one layer in.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}