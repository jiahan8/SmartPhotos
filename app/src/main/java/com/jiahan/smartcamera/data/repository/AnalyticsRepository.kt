package com.jiahan.smartcamera.data.repository

interface AnalyticsRepository {
    fun setUserId(userId: String?)
    fun logSearchEvent(value: String)
    fun logSearchCustomEvent(value: String)
    fun logNoteCustomEvent(value: String)
    fun logEditNoteCustomEvent(value: String)
    fun logFavoriteSearchCustomEvent(value: String)
    fun logExploreSearchCustomEvent(value: String)
    fun logTextCustomEvent(value: String)
    fun logDisplayNameCustomEvent(value: String)
    fun logUsernameCustomEvent(value: String)
}