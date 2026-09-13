package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaCaptureRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [NoteViewModel] -- the arrangement `HiltExploreViewModel` records the
 * reasons for, plus one job left at this edge: taking the share the OS handed `MainViewModel` off
 * [IncomingShareHandler], so the shared class receives the [IncomingShare] itself.
 *
 * The handler stays in this module rather than following into `commonMain`. It is a `Singleton`
 * that :app posts to and this module consumes from, so both resolve it through Hilt, and
 * `javax.inject` does not resolve in `commonMain`. Consuming it while building the super
 * constructor's arguments happens as the ViewModel is constructed, which is when `NoteViewModel`'s
 * own `init` used to do it -- so a share is still taken once, by the first composer built after it
 * arrived.
 */
@HiltViewModel
class HiltNoteViewModel @Inject constructor(
    noteRepository: NoteRepository,
    mediaUploadRepository: MediaUploadRepository,
    userPreferencesRepository: UserPreferencesRepository,
    analyticsRepository: AnalyticsRepository,
    mediaCaptureRepository: MediaCaptureRepository,
    incomingShareHandler: IncomingShareHandler,
    errorHandler: ErrorHandler,
) : NoteViewModel(
    noteRepository,
    mediaUploadRepository,
    userPreferencesRepository,
    analyticsRepository,
    mediaCaptureRepository,
    incomingShareHandler.consume(),
    errorHandler,
)