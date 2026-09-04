/*
 * Android library: the shared vocabulary that is Android-bound but is not Compose.
 *
 * It exists because `:feature:auth` needed `validateUsername` and `validateDisplayName`, which auth
 * and profile both call, and neither existing core module could take them. :core:domain has no
 * Android Gradle plugin, so it could not hold the string resources a validator's `ValidationResult`
 * pointed at. :core:ui is the Compose vocabulary, and a validator is not a composable.
 *
 * The validators have since gone on to :core:domain after all, once `ValidationResult.Error`
 * started carrying a `ValidationError` identity rather than an `R.string` id -- there was never
 * anything Android about a blank check and a regex except the resource they filled in. What stayed
 * is the half that is genuinely Android: the strings, and `validationErrorMessageResId`, which
 * resolves them for the three ViewModels that call a validator.
 *
 * So this is the third destination the extractions kept implying and did not have. The rule it
 * follows is the `cd_back` one from :feature:explore: a resource -- or a function -- with consumers
 * in two different future modules goes *down* rather than sideways. What made a new module the
 * answer rather than a corner of :core:ui is that the family is not UI-only. The strings here are
 * the username/name/email/password vocabulary, and they are read from three places at once:
 * `validationErrorMessageResId` below, AuthScreen and ProfileScreen's field labels, and
 * `appErrorMessageResId` in :app, which renders `AppError.UsernameTaken`/`UsernameReserved`.
 * Splitting the family so the labels sat in :core:ui and the messages here would make one screen
 * import two aliased `R`s to say one thing.
 *
 * `:feature:profile` then added the other two tenants, and they are the reason this module is a
 * module rather than a corner of :core:ui. `MediaFileRepository` and `MediaUriExt` were stranded in
 * :core:data -- the interface because its signatures carry `Uri`/`Bitmap`, which :core:domain
 * cannot hold, and the extensions because they convert between `Uri` and `MediaUri`. A feature
 * module must not depend on :core:data, so both came down here while `DefaultMediaFileRepository`
 * stayed. That is the general shape to expect: **an Android-typed contract belongs here, its
 * implementation stays in :core:data.** `AppUpdateRepository` is the one that has not moved,
 * deliberately -- only :app's MainViewModel injects it.
 *
 * Deliberately NOT Compose, and it should stay that way: `smartphotos.android.compose` is not
 * applied, so anything Compose-shaped that lands here fails to compile rather than quietly making
 * this a second :core:ui.
 */
plugins {
    id("smartphotos.android.library")
    // Hilt arrives here for NoteShareDelegate and NoteErrorReporter, which are @ViewModelScoped
    // @Inject classes: Dagger has to generate their factories in the module that owns them, so the
    // processor has to run here rather than only in :app where the component is assembled. This is
    // what :core:data already does, and what NiA does in several of its core modules -- DI is not
    // what this module's charter excludes. Compose is, and that is still not applied.
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // Its own, like every library here. :app reaches this module's R as
    // `com.jiahan.smartcamera.core.common.R as CommonR` -- the Kotlin package of the sources is
    // still com.jiahan.smartcamera.util, so the move itself needed no import churn.
    namespace = "com.jiahan.smartcamera.core.common"
}

dependencies {

    // api, not implementation: `MediaUri` is MediaUriExt's return type and `ValidationError`
    // validationErrorMessageResId's parameter, so every caller compiles against :core:domain
    // through this edge. Same reasoning as :core:ui's edge for HomeNote.
    api(project(":core:domain"))

    // `androidx.core.net.toUri`, in MediaUriExt.
    implementation(libs.androidx.core.ktx)

    // Declared rather than inherited through :core:domain's `api`, because NoteShareDelegate uses
    // coroutineScope/async/awaitAll and MutableSharedFlow directly. Inheriting a transitive `api`
    // for something a module uses itself breaks silently if the exporter ever narrows -- the same
    // rule that has :feature:explore declaring its own Compose.
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    // NoteShareDelegate is the first thing here with behaviour worth testing directly. It cannot
    // borrow :core:testing's fakes -- that module depends on this one, so the edge would be a
    // cycle -- hence mockk for the two interfaces it collaborates with.
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
