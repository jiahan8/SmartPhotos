package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.domain.MediaUri

/**
 * Text and media another app shared into this one, waiting for the note composer.
 *
 * `MainViewModel` builds it from the OS's intent, converting each stream with `toMediaUri()`, and
 * posts it to `IncomingShareHandler`; `HiltNoteViewModel` takes it off the handler and hands it to
 * [NoteViewModel]. It lives here, apart from that handler, because the shared ViewModel receives
 * it -- the handler is a Hilt singleton two Android modules resolve, and stays in :feature:note.
 */
data class IncomingShare(val text: String?, val uris: List<MediaUri>)