package com.jiahan.smartcamera.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.messaging.messaging
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig
import javax.inject.Singleton

/**
 * The Firebase singletons the data layer injects: GitLive's multiplatform instances, each wrapping the
 * Android SDK's default one, for the repositories in :core:firebase.
 *
 * This lived in :app once, which put the whole Firebase surface in the application's dependency block
 * to satisfy repositories one layer down. Every binding below is consumed only by a repository
 * `DataModule` constructs -- nothing in :app names these types -- so the providers belong here.
 *
 * It also closes the graph: with these providers up in the application module, nothing below :app
 * could assemble a repository, so :core:data's own Hilt-shaped tests had to restate them.
 *
 * Every binding is GitLive's since `DefaultUserRepository`, the last repository to inject an Android
 * SDK instance, moved to :core:firebase. The Android SDK is still underneath -- GitLive's Android
 * implementation, and `DefaultMediaUploadRepository`'s Storage, which it builds itself.
 *
 * What stays in :app is what :app itself names: AppCheck (installed in `MyApp`), Crashlytics
 * (`DefaultErrorHandler`), firebase-messaging (its `FirebaseMessagingService` subclass -- :core:firebase
 * names that artifact too, for its topic calls), and the two with no source reference at all,
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
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = Firebase.messaging
}