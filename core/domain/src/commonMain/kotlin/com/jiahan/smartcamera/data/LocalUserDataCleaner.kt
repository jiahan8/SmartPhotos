package com.jiahan.smartcamera.data

/**
 * Erases everything on this device that belongs to the signed-in user -- the local half of signing
 * out or deleting an account. It does not sign anybody out and does not touch the network; the
 * caller has already done that.
 *
 * The list of stores it clears is the implementation's, `DefaultLocalUserDataCleaner` in :core:data,
 * beside the stores themselves. It is an interface only because its caller, `DefaultAuthRepository`,
 * lives in :core:firebase and cannot see :core:data.
 */
interface LocalUserDataCleaner {

    /**
     * Clears every per-user store.
     *
     * Throws rather than returning a `Result`, because both call sites already run inside `safeCall`
     * and a clear that half-succeeded is a failure the caller has to see.
     */
    suspend fun clearLocalUserData()
}