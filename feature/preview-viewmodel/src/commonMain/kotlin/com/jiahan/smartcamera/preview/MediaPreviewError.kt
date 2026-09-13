package com.jiahan.smartcamera.preview

/**
 * What a media preview's `actionError` reports, for its screen to render as copy.
 *
 * Photo and video previews share it and differ only in the string, which each screen picks for
 * itself. One case today -- an enum rather than `Unit` so the screen's `when` names what it is
 * rendering, and a second failure is an entry both screens are made to handle.
 */
enum class MediaPreviewError { SHARE_FAILED }