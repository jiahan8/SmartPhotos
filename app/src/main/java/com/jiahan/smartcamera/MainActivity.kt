package com.jiahan.smartcamera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jiahan.smartcamera.kotlin.StillImageActivity
import com.facebook.drawee.backends.pipeline.Fresco
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Fresco.initialize(this)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener {
            startActivity( Intent(this, StillImageActivity::class.java) )
        }
        findViewById<Button>(R.id.view_button).setOnClickListener {
            if( ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                startActivity( Intent(this, SearchActivity::class.java) )
            }else {
//              requestPermissions()
                var lol = ArrayList<String>()
                lol.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(this, lol.toTypedArray(), 1)
            }
        }

        if (!allPermissionsGranted()) {
            getRuntimePermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
//        when (requestCode) {
//            1 -> {
//                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    // permission was granted, yay! Do the
//                    // contacts-related task you need to do.
//                    startActivity( Intent(this, SearchActivity::class.java) )
//                } else {
//
//                    // permission denied, boo! Disable the
//                    // functionality that depends on this permission.
////                    Toast.makeText(
////                        MainActivity.this,
////                        "Permission denied to read your External storage",
////                        Toast.LENGTH_SHORT
////                    ).show();
//                }
//            }
//        }
        if (requestCode == 1) {
////         If request is cancelled, the result arrays are empty.
//        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//          // permission was granted, yay! Do the
//          // contacts-related task you need to do.
//          startCameraIntentForResult()
//        } else {
//          // permission denied, boo! Disable the
//          // functionality that depends on this permission.
////          Toast.makeText(this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
//
////          requestPermissions()
//
//        }
            var i: Int = 0
            var len = permissions.size
            while (i < len) {
                var permission = permissions[i]
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    var showRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                    if (!showRationale) {
                        AlertDialog.Builder(this)
                            .setTitle("Storage Permissions needed to proceed.")
                            .setPositiveButton("Settings") { dialogInterface, i ->
                                dialogInterface.dismiss()

                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivityForResult(intent, REQUEST_PERMISSION_SETTING)

                            }
                            .setNegativeButton("Cancel") { dialogInterface, i -> dialogInterface.dismiss() }
                            .setCancelable(false)
                            .show()
                        // user also CHECKED "never ask again"
                        // you can either enable some fall back,
                        // disable features of your app
                        // or open another dialog explaining
                        // again the permission and directing to
                        // the app setting
                    } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
//                        Toast.makeText(this, "Storage Permission needed to proceed.", Toast.LENGTH_SHORT).show()
                    }
                }
                i++
            }
        }
    }


    private fun getRequiredPermissions(): Array<String?> {
        return try {
            val info = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
            val ps = info.requestedPermissions
            if (ps != null && ps.isNotEmpty()) {
                ps
            } else {
                arrayOfNulls(0)
            }
        } catch (e: Exception) {
            arrayOfNulls(0)
        }
    }
    private fun allPermissionsGranted(): Boolean {
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    return false
                }
            }
        }
        return true
    }
    private fun getRuntimePermissions() {
        val allNeededPermissions = ArrayList<String>()
        for (permission in getRequiredPermissions()) {
            permission?.let {
                if (!isPermissionGranted(this, it)) {
                    allNeededPermissions.add(permission)
                }
            }
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS)
        }
    }
    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }

    private fun checkPermission(): Boolean {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        val res: Int = checkCallingOrSelfPermission(permission)
        return res == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "ChooserActivity"
        private const val PERMISSION_REQUESTS = 1
//        private val CLASSES = arrayOf<Class<*>>(
//            LivePreviewActivity::class.java,
//            StillImageActivity::class.java,
//            CameraXLivePreviewActivity::class.java
//        )
//        private val DESCRIPTION_IDS = intArrayOf(
//            R.string.desc_camera_source_activity,
//            R.string.desc_still_image_activity,
//            R.string.desc_camerax_live_preview_activity
//        )
        private const val REQUEST_PERMISSION_SETTING = 1003

        }


}