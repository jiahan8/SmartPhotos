package com.jiahan.smartcamera.data.repository

interface AnalyticsRepository {
    fun logSearchEvent(value: String)
    fun logSearchCustomEvent(value: String)
    fun logNoteCustomEvent(value: String)
    fun logFavoriteSearchCustomEvent(value: String)
    fun logTextCustomEvent(value: String)
}