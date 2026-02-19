package com.jiahan.smartcamera.util

/**
 * Application-wide constants for various configurations and limits
 */
object AppConstants {

    // Pagination
    const val DEFAULT_PAGE_SIZE = 10

    // Text limits
    const val MAX_POST_TEXT_LENGTH = 500
    const val MAX_USERNAME_LENGTH = 30
    const val MAX_DISPLAY_NAME_LENGTH = 50

    // Media constraints
    const val VIDEO_THUMBNAIL_DIMENSION = 1080
    const val VIDEO_THUMBNAIL_TIME_MICROSECONDS = 1_000_000L // 1 second
    const val VIDEO_THUMBNAIL_DEFAULT_WIDTH = 640
    const val VIDEO_THUMBNAIL_DEFAULT_HEIGHT = 360

    // Debounce delays
    const val DEBOUNCE_MS = 300L

    // StateFlow
    const val STATE_FLOW_TIMEOUT_MS = 5000L
    const val STATEFLOW_WHILE_SUBSCRIBED_MS = 5000L

    // Remote Config
    const val REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 3600L // 1 hour

    // Animation durations
    const val ANIMATION_DURATION_SHORT_MS = 300
    const val TEXT_FIELD_TRANSITION_FADE_DURATION_MS = 500
    const val TEXT_FIELD_PLACEHOLDER_ROTATION_DELAY_MS = 3000L
    const val TEXT_FIELD_TRANSITION_DELAY_MS = 500L
    const val AUTH_ACTION_DELAY_MS = 1000L
}

