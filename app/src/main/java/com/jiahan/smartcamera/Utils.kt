package com.jiahan.smartcamera

import android.view.View
import android.view.ViewAnimationUtils
import androidx.databinding.BindingAdapter
import com.facebook.drawee.view.SimpleDraweeView
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*

object Utils {

    // convert timestamp to string with format
    @JvmStatic
    fun getDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format: Format = SimpleDateFormat("E hh:mm:ssa")
        return format.format(date)
    }

    fun getDateTime2(timestamp: Long): String {
        val date = Date(timestamp)
        val format: Format = SimpleDateFormat("E hh:mma M/d/y")
        return format.format(date)
    }

    @JvmStatic
    fun animate(x: Int, y: Int, constraintLayout: View) {
//        Log.e("coordinate", x +","+ y);
        val cx = x / 2
        val cy = y / 2
        val radius = Math.max(constraintLayout.width, constraintLayout.height).toFloat()
        val animator = ViewAnimationUtils.createCircularReveal(constraintLayout, cx, cy, 0f, radius)
        animator.duration = 1000
        constraintLayout.visibility = View.VISIBLE
        animator.start()
    }

    @JvmStatic
    fun cutoffstring(sentence: String): String {
        var sentence = sentence
        if (sentence.length > 101) {
            sentence = sentence.substring(0, 100) + "..."
        }
        return sentence
    }

    // data binding for image resources
    @JvmStatic
    @BindingAdapter("imageUri")
    fun setImageResource(simpleDraweeView: SimpleDraweeView, imageUrl: String?) {
        simpleDraweeView.setImageURI(imageUrl)
        if (imageUrl != null)
            if (imageUrl != "") {
                simpleDraweeView.colorFilter = null
            }
    }

    @JvmStatic
    val versionName: String
        get() = BuildConfig.VERSION_NAME
}