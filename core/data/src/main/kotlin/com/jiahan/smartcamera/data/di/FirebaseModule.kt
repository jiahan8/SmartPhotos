package com.jiahan.smartcamera.data.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.messaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gitlive.firebase.Firebase as GitLiveFirebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.auth.FirebaseAuth as GitLiveFirebaseAuth
import dev.gitlive.firebase.auth.auth as gitLiveAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore as GitLiveFirebaseFirestore
import dev.gitlive.firebase.firestore.firestore as gitLiveFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions as GitLiveFirebaseFunctions
import dev.gitlive.firebase.functions.functions as gitLiveFunctions
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig
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
 * GitLive's multiplatform instances, for the repositories that have moved to :core:firebase, are
 * provided here too -- beside the Android SDK's while both kinds of repository exist, and in place
 * of one once nothing injects the Android type.
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

    // GitLive's multiplatform Remote Config, for FirebaseRemoteConfigRepository in :core:firebase.
    // The Android SDK's provider went when that class moved: nothing injects that type any more.
    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig = GitLiveFirebase.remoteConfig

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    // GitLive's, wrapping the same default instance, for DefaultNoteRepository in :core:firebase. The
    // Android SDK's above stays while DefaultUserRepository still injects it.
    @Provides
    @Singleton
    fun provideGitLiveFirebaseFirestore(): GitLiveFirebaseFirestore = GitLiveFirebase.gitLiveFirestore

    // GitLive's, for FirebaseAnalyticsRepository in :core:firebase; the Android SDK's provider went
    // with that class, as nothing else injects the type.
    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = GitLiveFirebase.analytics

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // GitLive's, for DefaultAuthRepository in :core:firebase. The Android SDK's above stays while
    // DefaultUserRepository still injects it.
    @Provides
    @Singleton
    fun provideGitLiveFirebaseAuth(): GitLiveFirebaseAuth = GitLiveFirebase.gitLiveAuth

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions

    // GitLive's multiplatform wrapper over the same default Functions instance, for the repositories
    // in :core:firebase. A distinct type from the Android SDK's above, so the two bindings coexist
    // while the remaining repositories still inject that one.
    @Provides
    @Singleton
    fun provideGitLiveFirebaseFunctions(): GitLiveFirebaseFunctions = GitLiveFirebase.gitLiveFunctions

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}