/*
 * Android library: the shared vocabulary that is Android-bound but is not Compose.
 *
 * It exists because `:feature:auth` needed `validateUsername` and `validateDisplayName`, which auth
 * and profile both call, and neither existing core module could take them. :core:domain has no
 * Android Gradle plugin, so it cannot hold the string resources a validator's `ValidationResult`
 * points at -- `ValidationResult` itself lives there precisely because it carries a bare `Int`
 * rather than a resource. :core:ui is the Compose vocabulary, and a validator is not a composable.
 *
 * So this is the third destination the extractions kept implying and did not have. The rule it
 * follows is the `cd_back` one from :feature:explore: a resource -- or a function -- with consumers
 * in two different future modules goes *down* rather than sideways. What made a new module the
 * answer rather than a corner of :core:ui is that the family is not UI-only. The ten strings here
 * are the username/name/email vocabulary, and they are read from three places at once: the
 * validators below, AuthScreen and ProfileScreen's field labels, and `appErrorMessageResId` in
 * :app, which renders `AppError.UsernameTaken`/`UsernameReserved`. Splitting the family so the
 * labels sat in :core:ui and the messages here would make one screen import two aliased `R`s to
 * say one thing.
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
}

android {
    // Its own, like every library here. :app reaches this module's R as
    // `com.jiahan.smartcamera.core.common.R as CommonR` -- the Kotlin package of the sources is
    // still com.jiahan.smartcamera.util, so the move itself needed no import churn.
    namespace = "com.jiahan.smartcamera.core.common"
}

dependencies {

    // api, not implementation: `ValidationResult` is the return type of both public functions here,
    // so every caller compiles against :core:domain through this edge. Same reasoning as :core:ui's
    // edge for HomeNote.
    api(project(":core:domain"))

    // `androidx.core.net.toUri`, in MediaUriExt.
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
