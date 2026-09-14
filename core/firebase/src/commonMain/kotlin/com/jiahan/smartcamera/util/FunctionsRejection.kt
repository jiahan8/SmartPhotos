package com.jiahan.smartcamera.util

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.details

/**
 * The machine-readable `reason` a Cloud Function attached to a rejection, or null if it sent none.
 *
 * `functions/index.js` tags every `invalid-argument` it raises with one, because a single code
 * covers several rules and the client cannot tell them apart from the code alone. Both repositories
 * that read those rejections fold them into an `AppError` below the repository boundary, each reading
 * it through its callable seam's `toCallableRejection`, and this is their one copy of that wire
 * contract, which has to change on both sides at once.
 *
 * `internal`: nothing above the data layer may see a `FirebaseFunctionsException` (see AGENTS.md,
 * Error handling).
 */
internal fun FirebaseFunctionsException.reason(): String? =
    (details as? Map<*, *>)?.get(ARG_REASON) as? String

private const val ARG_REASON = "reason"