package com.jiahan.smartcamera.util

import com.google.firebase.functions.FirebaseFunctionsException

/**
 * The machine-readable `reason` a Cloud Function attached to a rejection, or null if it sent none.
 *
 * `functions/index.js` tags every `invalid-argument` it raises with one, because a single code
 * covers several rules and the client cannot tell them apart from the code alone. Both repositories
 * that read those rejections fold them into an `AppError` here in :core:data, and each had its own
 * copy of this cast and its own `"reason"` constant -- a wire contract with the server that has to
 * change on both sides at once, so it belongs in one place.
 *
 * `internal`: nothing above :core:data may see a `FirebaseFunctionsException`, which is the whole
 * reason these rejections are folded down here (see AGENTS.md, Error handling).
 */
internal fun FirebaseFunctionsException.reason(): String? =
    (details as? Map<*, *>)?.get(ARG_REASON) as? String

private const val ARG_REASON = "reason"