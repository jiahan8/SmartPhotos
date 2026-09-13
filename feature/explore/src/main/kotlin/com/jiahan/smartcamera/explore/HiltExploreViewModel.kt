package com.jiahan.smartcamera.explore

import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [ExploreViewModel], adding the annotations and nothing else.
 *
 * [ExploreViewModel] lives in :feature:explore-viewmodel's `commonMain`, where neither
 * `HiltViewModel` (an Android artifact) nor `javax.inject` (a JVM one) resolves, so it cannot be
 * Hilt's entry point itself. Subclassing keeps what `hiltViewModel()` gives every other ViewModel --
 * above all the `ViewModelComponent`, so a `ViewModelScoped` dependency is still one instance per
 * ViewModel -- at the cost of the base class being `open` and its constructor restated here.
 *
 * The alternative considered, and not built, was a plain `viewModel { }` initializer fed from a
 * Hilt entry point. It needs no `open`, but it constructs the ViewModel outside
 * `ViewModelComponent`, so the `NoteErrorReporter`/`NoteShareDelegate` pair that four of the other
 * ViewModels inject would have to be shared by hand in each initializer -- the silently dropped
 * share failure `NoteErrorReporter`'s scope exists to prevent.
 */
@HiltViewModel
class HiltExploreViewModel @Inject constructor(
    photoRepository: PhotoRepository,
    analyticsRepository: AnalyticsRepository,
    errorHandler: ErrorHandler,
) : ExploreViewModel(photoRepository, analyticsRepository, errorHandler)