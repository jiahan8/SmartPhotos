package com.jiahan.smartcamera.util

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.details

/**
 * The machine-readable `reason` a Cloud Function attached to a rejection, or null if it sent none.
 *
 * The GitLive twin of :core:data's `util/FunctionsErrorExt.kt`, for the repositories that have moved
 * here: two copies of one wire contract until `DefaultUserRepository`, the last caller of that one,
 * follows. `internal` for the same reason -- nothing above the data layer may see a
 * `FirebaseFunctionsException` (see AGENTS.md, Error handling).
 */
internal fun FirebaseFunctionsException.reason(): String? =
    (details as? Map<*, *>)?.get(ARG_REASON) as? String

private const val ARG_REASON = "reason"