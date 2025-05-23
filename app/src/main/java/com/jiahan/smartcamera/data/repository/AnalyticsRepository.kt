package com.jiahan.smartcamera.data.repository

interface AnalyticsRepository {
    fun logSearchEvent(value: String)
    fun logSearchCustomEvent(value: String)
}