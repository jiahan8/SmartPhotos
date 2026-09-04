package com.jiahan.smartcamera.data.repository

interface AnalyticsRepository {
    fun setUserId(userId: String?)
    fun logSearch(query: String)
    fun logNoteSearch(query: String)
    fun logNoteCreate(text: String)
    fun logNoteEdit(text: String)
    fun logFavoriteSearch(query: String)
    fun logExploreSearch(query: String)
    fun logText(text: String)
    fun logDisplayName(displayName: String)
    fun logUsername(username: String)
}