package com.jiahan.smartcamera.note

import android.content.res.Resources
import com.jiahan.smartcamera.core.common.R
import com.jiahan.smartcamera.util.resolve

/**
 * The text a [NoteActionError] shows.
 *
 * Split from the identity when [NoteActionError] went to :core:domain's `commonMain`: the identity
 * is what a ViewModel emits, this is what an Android screen renders, and it stays beside the
 * `share_note_failure` string it reads -- the same split as `ErrorMessage` and `ErrorMessages.kt`.
 */
fun NoteActionError.resolve(resources: Resources): String = when (this) {
    is NoteActionError.Failed -> message.resolve(resources)
    NoteActionError.ShareFailed -> resources.getString(R.string.share_note_failure)
}