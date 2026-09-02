package com.jiahan.smartcamera.fake

import android.content.Context
import com.jiahan.smartcamera.util.ResourceProvider

/**
 * [ResourceProvider] test double backed by a real [Context] so tests resolve the same localized
 * strings the production UI renders. This keeps assertions independent of hard-coded English text.
 */
class FakeResourceProvider(private val context: Context) : ResourceProvider {

    override fun getString(resId: Int): String = context.getString(resId)

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}