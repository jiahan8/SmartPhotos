package com.jiahan.smartcamera.repository

interface AnalyticsRepository {
    fun logSearchEvent(value: String)
}