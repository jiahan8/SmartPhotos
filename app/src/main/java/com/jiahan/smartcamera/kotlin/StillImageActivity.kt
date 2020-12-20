/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jiahan.smartcamera.kotlin

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Pair
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jiahan.smartcamera.*
import com.jiahan.smartcamera.barcodescanner.BarcodeScannerProcessor
import com.jiahan.smartcamera.facedetector.FaceDetectorProcessor
import com.jiahan.smartcamera.labeldetector.LabelDetectorProcessor
import com.jiahan.smartcamera.objectdetector.ObjectDetectorProcessor
import com.jiahan.smartcamera.posedetector.PoseDetectorProcessor
import com.jiahan.smartcamera.preference.PreferenceUtils
import com.jiahan.smartcamera.textdetector.TextRecognitionProcessor
import com.google.android.gms.common.annotation.KeepName
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.firebase.storage.FirebaseStorage
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.*
import kotlin.math.max

/** Activity demonstrating different image detector features with a still image from camera.  */
@KeepName
class StillImageActivity : AppCompatActivity() {
  private var preview: ImageView? = null
  private var graphicOverlay: GraphicOverlay? = null
  private var selectedMode =
    OBJECT_DETECTION
  private var selectedSize: String? =
    SIZE_SCREEN
  private var isLandScape = false
  private var imageUri: Uri? = null
  // Max width (portrait mode)
  private var imageMaxWidth = 0
  // Max height (portrait mode)
  private var imageMaxHeight = 0
  private var imageProcessor: VisionImageProcessor? = null

  private lateinit var tabLayout: TabLayout

  private lateinit var userDatabase: UserDatabase
  private val mDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_still_image)

    bottomSheetText = ""

    userDatabase = UserDatabase.getInstance(application)!!

    tabLayout = findViewById(R.id.tl)


//    tabLayout.tabTextColors = resources.getColorStateList(R.color.colorWhite)
    tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab) {
        when (tab.position) {
          1 -> {
            selectedMode = "Image Labeling"
            createImageProcessor()
            tryReloadAndDetectInImage()
          }
          else ->{
            selectedMode = "Text Recognition"
            createImageProcessor()
            tryReloadAndDetectInImage()
          }
        }
      }
      override fun onTabUnselected(tab: TabLayout.Tab) {
      }
      override fun onTabReselected(tab: TabLayout.Tab) {}
    })


    findViewById<View>(R.id.view_text_button)!!.setOnClickListener(View.OnClickListener { view ->
      val bottomSheetDialog = BottomSheetDialog(view.context)
      val sheetView = layoutInflater.inflate(R.layout.bottom_sheet, view.rootView as ViewGroup, false)
      bottomSheetDialog.setContentView(sheetView)

      bottomSheetDialog.findViewById<TextView>(R.id.bottom_sheet_text)?.text = bottomSheetText

      bottomSheetDialog.show()

    })
    var bool = false
    findViewById<ImageView>(R.id.save_button)!!.setOnClickListener{
      var a = it as ImageView
//      var aa = a.tag as Integer
      if( bool == false ){

        it.setImageResource(R.drawable.ic_bookmark_white)

        var userdata = UserData( imageUri.toString(), bottomSheetText, 1L )
//        var userdata = UserData( "abc", "abc", 100 )
//        userDatabase.userDAO().addUser( userdata )
        mDisposable.add(
          Completable.ambArray(userDatabase!!.userDAO().addUser(userdata))
          .subscribeOn(Schedulers.io())
          .observeOn(Schedulers.io())
          .subscribe {
          })

        bool = true

      }else{

        it.setImageResource(R.drawable.ic_bookmark_border_white)

        mDisposable.add(
          Completable.ambArray(userDatabase!!.userDAO().deleteImage(imageUri.toString() ))
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe {
            })

        bool = false

      }
    }
    findViewById<View>(R.id.select_image_button).setOnClickListener { view: View ->
        // Menu for selecting either: a) take new photo b) select from existing
        val popup =
          PopupMenu(this@StillImageActivity, view)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
          val itemId =
            menuItem.itemId
          if (itemId == R.id.select_images_from_local) {
            startChooseImageIntentForResult()
            return@setOnMenuItemClickListener true
          } else if (itemId == R.id.take_photo_using_camera) {

//            when {
//              ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
//                      ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
//                startCameraIntentForResult()
//              }else -> {
//                var lol = ArrayList<String>()
//                lol.add( Manifest.permission.CAMERA )
//                lol.add( Manifest.permission.WRITE_EXTERNAL_STORAGE )
//                ActivityCompat.requestPermissions(this, lol.toTypedArray(), 1);
//              }
//            }
            if( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
              startCameraIntentForResult()
            }else {
//              requestPermissions()
              var lol = ArrayList<String>()
              lol.add(Manifest.permission.CAMERA)
              lol.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
              ActivityCompat.requestPermissions(this, lol.toTypedArray(), 1)
            }

            return@setOnMenuItemClickListener true
          }
          false
        }
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.camera_button_menu, popup.menu)
        popup.show()
      }
    preview = findViewById(R.id.preview)
    graphicOverlay = findViewById(R.id.graphic_overlay)

//    populateFeatureSelector()
//    populateSizeSelector()

    isLandScape =
      resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (savedInstanceState != null) {
      imageUri =
        savedInstanceState.getParcelable(KEY_IMAGE_URI)
      imageMaxWidth =
        savedInstanceState.getInt(KEY_IMAGE_MAX_WIDTH)
      imageMaxHeight =
        savedInstanceState.getInt(KEY_IMAGE_MAX_HEIGHT)
      selectedSize =
        savedInstanceState.getString(KEY_SELECTED_SIZE)
    }

    val rootView = findViewById<View>(R.id.root)
    rootView.viewTreeObserver.addOnGlobalLayoutListener(
      object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
          rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
          imageMaxWidth = rootView.width
//          imageMaxHeight = rootView.height - findViewById<View>(R.id.tl).height
          imageMaxHeight = rootView.height
          if (SIZE_SCREEN == selectedSize) {
            tryReloadAndDetectInImage()
          }
        }
      })

//    val settingsButton = findViewById<ImageView>(R.id.settings_button)
//    settingsButton.setOnClickListener {
//      val intent =
//        Intent(
//          applicationContext,
//          SettingsActivity::class.java
//        )
//      intent.putExtra(
//        SettingsActivity.EXTRA_LAUNCH_SOURCE,
//        LaunchSource.STILL_IMAGE
//      )
//      startActivity(intent)
//    }
  }

//  fun requestPermissions(){
//    if( ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) &&
//      ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
//      AlertDialog.Builder(this)
//        .setTitle("Camera and Storage Permissions needed to proceed.")
//        .setPositiveButton("Settings") { dialogInterface, i ->
//          dialogInterface.dismiss()
//
//          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//          val uri = Uri.fromParts("package", packageName, null)
//          intent.data = uri
//          startActivityForResult(intent, REQUEST_PERMISSION_SETTING)
//
//        }
//        .setNegativeButton("Cancel") { dialogInterface, i -> dialogInterface.dismiss() }
//        .setCancelable(false)
//        .show()
//    }else {
//      var lol = ArrayList<String>()
//      lol.add(Manifest.permission.CAMERA)
//      ActivityCompat.requestPermissions(this, lol.toTypedArray(), 1)
//    }
//  }
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (requestCode == 1){
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
      while( i < len ){
        var permission = permissions[i]
        if( grantResults[i] ==  PackageManager.PERMISSION_DENIED ){
          var showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission );
          if (! showRationale) {
            AlertDialog.Builder(this)
            .setTitle("Camera and Storage Permissions needed to proceed.")
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
            } else if (Manifest.permission.CAMERA.equals(permission)) {
//              showRationale(permission, R.string.permission_denied_contacts);
              // user did NOT check "never ask again"
              // this is a good place to explain the user
              // why you need the permission and ask if he wants
              // to accept it (the rationale)
//            Toast.makeText(this, "Camera Permission needed to proceed.", Toast.LENGTH_SHORT ).show()
            } else if ( Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission) ) {
//            Toast.makeText(this, "Storage Permission needed to proceed.", Toast.LENGTH_SHORT ).show()
          }
        }
        i++
      }


      }else if(requestCode == REQUEST_PERMISSION_SETTING ) {
        if( ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ){
          startCameraIntentForResult()
        }
      }

  }

  public override fun onResume() {
    super.onResume()
    Log.d(TAG, "onResume")
    createImageProcessor()
    tryReloadAndDetectInImage()
  }

//  private fun populateFeatureSelector() {
//    val featureSpinner = findViewById<Spinner>(R.id.feature_selector)
//    val options: MutableList<String> = ArrayList()
//    options.add(OBJECT_DETECTION)
//    options.add(OBJECT_DETECTION_CUSTOM)
//    options.add(CUSTOM_AUTOML_OBJECT_DETECTION)
//    options.add(FACE_DETECTION)
//    options.add(BARCODE_SCANNING)
//    options.add(TEXT_RECOGNITION)
//    options.add(IMAGE_LABELING)
//    options.add(IMAGE_LABELING_CUSTOM)
//    options.add(CUSTOM_AUTOML_LABELING)
//    options.add(POSE_DETECTION)
//
//    // Creating adapter for featureSpinner
//    val dataAdapter = ArrayAdapter(this, R.layout.spinner_style, options)
//    // Drop down layout style - list view with radio button
//    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//    // attaching data adapter to spinner
//    featureSpinner.adapter = dataAdapter
//    featureSpinner.onItemSelectedListener = object : OnItemSelectedListener {
//      override fun onItemSelected(
//        parentView: AdapterView<*>,
//        selectedItemView: View?,
//        pos: Int,
//        id: Long
//      ) {
//        if (pos >= 0) {
//          selectedMode = parentView.getItemAtPosition(pos).toString()
//          createImageProcessor()
//          tryReloadAndDetectInImage()
//        }
//      }
//
//      override fun onNothingSelected(arg0: AdapterView<*>?) {}
//    }
//  }

//  private fun populateSizeSelector() {
//    val sizeSpinner = findViewById<Spinner>(R.id.size_selector)
//    val options: MutableList<String> = ArrayList()
//    options.add(SIZE_SCREEN)
//    options.add(SIZE_1024_768)
//    options.add(SIZE_640_480)
//    // Creating adapter for featureSpinner
//    val dataAdapter =
//      ArrayAdapter(this, R.layout.spinner_style, options)
//    // Drop down layout style - list view with radio button
//    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//    // attaching data adapter to spinner
//    sizeSpinner.adapter = dataAdapter
//    sizeSpinner.onItemSelectedListener = object : OnItemSelectedListener {
//      override fun onItemSelected(
//        parentView: AdapterView<*>,
//        selectedItemView: View?,
//        pos: Int,
//        id: Long
//      ) {
//        if (pos >= 0) {
//          selectedSize = parentView.getItemAtPosition(pos).toString()
//          createImageProcessor()
//          tryReloadAndDetectInImage()
//        }
//      }
//
//      override fun onNothingSelected(arg0: AdapterView<*>?) {}
//    }
//  }

  public override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelable(
      KEY_IMAGE_URI,
      imageUri
    )
    outState.putInt(
      KEY_IMAGE_MAX_WIDTH,
      imageMaxWidth
    )
    outState.putInt(
      KEY_IMAGE_MAX_HEIGHT,
      imageMaxHeight
    )
    outState.putString(
      KEY_SELECTED_SIZE,
      selectedSize
    )
  }

  private fun startCameraIntentForResult() { // Clean up last time's image
    imageUri = null
    preview!!.setImageBitmap(null)
    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    if (takePictureIntent.resolveActivity(packageManager) != null) {
      val values = ContentValues()
      values.put(MediaStore.Images.Media.TITLE, "SmartCamera_" + System.currentTimeMillis().toString() )
      values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera")
      imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
      takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
      startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)

    }
  }

  private fun startChooseImageIntentForResult() {
    val intent = Intent()
    intent.type = "image/*"
//    intent.action = Intent.ACTION_GET_CONTENT
    intent.action = Intent.ACTION_OPEN_DOCUMENT
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE)

  }

  override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
  ) {
    if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {

//      imageUri = data!!.data

      findViewById<View>(R.id.view_text_button).visibility = View.VISIBLE
      findViewById<View>(R.id.save_button).visibility = View.VISIBLE

      selectedMode = "Text Recognition"
      createImageProcessor()
      tryReloadAndDetectInImage()

      try{
        val storageReference = FirebaseStorage.getInstance().reference.child("smart_camera").child(imageUri!!.lastPathSegment!!)
        imageUri?.let {
          storageReference.putFile(it)
        }
      }catch (e: Exception){
        Log.e("uploadfail", e.toString())
      }


    } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == Activity.RESULT_OK) {
      // In this case, imageUri is returned by the chooser, save it.
      imageUri = data!!.data

      findViewById<View>(R.id.view_text_button).visibility = View.VISIBLE
      findViewById<View>(R.id.save_button).visibility = View.VISIBLE

      selectedMode = "Text Recognition"
      createImageProcessor()
      tryReloadAndDetectInImage()

      try{
        val storageReference = FirebaseStorage.getInstance().reference.child("smart_camera_album").child(imageUri!!.lastPathSegment!!)
        imageUri?.let {
          storageReference.putFile(it)
        }
      }catch (e: Exception){
        Log.e("uploadfail", e.toString())
      }


    } else {
      super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun tryReloadAndDetectInImage() {
    Log.d(
      TAG,
      "Try reload and detect image"
    )
    try {
      if (imageUri == null) {
        return
      }

      if (SIZE_SCREEN == selectedSize && imageMaxWidth == 0) {
        // UI layout has not finished yet, will reload once it's ready.
        return
      }

      val imageBitmap = BitmapUtils.getBitmapFromContentUri(
        contentResolver,
        imageUri
      )
        ?: return
      // Clear the overlay first
      graphicOverlay!!.clear()
      // Get the dimensions of the image view
      val targetedSize = targetedWidthHeight
      // Determine how much to scale down the image
      val scaleFactor = max(
        imageBitmap.width.toFloat() / targetedSize.first.toFloat(),
        imageBitmap.height.toFloat() / targetedSize.second.toFloat()
      )
      val resizedBitmap = Bitmap.createScaledBitmap(
        imageBitmap,
        (imageBitmap.width / scaleFactor).toInt(),
        (imageBitmap.height / scaleFactor).toInt(),
        true
      )
      preview!!.setImageBitmap(resizedBitmap)
      if (imageProcessor != null) {
        graphicOverlay!!.setImageSourceInfo(
          resizedBitmap.width, resizedBitmap.height, /* isFlipped= */false
        )
        imageProcessor!!.processBitmap(resizedBitmap, graphicOverlay)
      } else {
        Log.e(
          TAG,
          "Null imageProcessor, please check adb logs for imageProcessor creation error"
        )
      }
    } catch (e: IOException) {
      Log.e(
        TAG,
        "Error retrieving saved image"
      )
      imageUri = null
    }
  }

  private val targetedWidthHeight: Pair<Int, Int>
    get() {
      val targetWidth: Int
      val targetHeight: Int
      when (selectedSize) {
        SIZE_SCREEN -> {
          targetWidth = imageMaxWidth
          targetHeight = imageMaxHeight
        }
        SIZE_640_480 -> {
          targetWidth = if (isLandScape) 640 else 480
          targetHeight = if (isLandScape) 480 else 640
        }
        SIZE_1024_768 -> {
          targetWidth = if (isLandScape) 1024 else 768
          targetHeight = if (isLandScape) 768 else 1024
        }
        else -> throw IllegalStateException("Unknown size")
      }
      return Pair(targetWidth, targetHeight)
    }

  private fun createImageProcessor() {
    try {
      when (selectedMode) {
        OBJECT_DETECTION -> {
          Log.i(
            TAG,
            "Using Object Detector Processor"
          )
          val objectDetectorOptions =
            PreferenceUtils.getObjectDetectorOptionsForStillImage(this)
          imageProcessor =
            ObjectDetectorProcessor(
              this,
              objectDetectorOptions
            )
        }
        OBJECT_DETECTION_CUSTOM -> {
          Log.i(
            TAG,
            "Using Custom Object Detector Processor"
          )
          val localModel = LocalModel.Builder()
            .setAssetFilePath("custom_models/bird_classifier.tflite")
            .build()
          val customObjectDetectorOptions =
            PreferenceUtils.getCustomObjectDetectorOptionsForStillImage(this, localModel)
          imageProcessor =
            ObjectDetectorProcessor(
              this,
              customObjectDetectorOptions
            )
        }
        CUSTOM_AUTOML_OBJECT_DETECTION -> {
          Log.i(
            TAG,
            "Using Custom AutoML Object Detector Processor"
          )
          val customAutoMLODTLocalModel = LocalModel.Builder()
            .setAssetManifestFilePath("automl/manifest.json")
            .build()
          val customAutoMLODTOptions = PreferenceUtils
            .getCustomObjectDetectorOptionsForStillImage(this, customAutoMLODTLocalModel)
          imageProcessor =
            ObjectDetectorProcessor(
              this,
              customAutoMLODTOptions
            )
        }
        FACE_DETECTION ->
          imageProcessor =
            FaceDetectorProcessor(this, null)
        BARCODE_SCANNING ->
          imageProcessor =
            BarcodeScannerProcessor(this)
        TEXT_RECOGNITION ->
          imageProcessor =
            TextRecognitionProcessor(this)
        IMAGE_LABELING ->
          imageProcessor =
            LabelDetectorProcessor(
              this,
              ImageLabelerOptions.DEFAULT_OPTIONS
            )
        IMAGE_LABELING_CUSTOM -> {
          Log.i(
            TAG,
            "Using Custom Image Label Detector Processor"
          )
          val localClassifier = LocalModel.Builder()
            .setAssetFilePath("custom_models/bird_classifier.tflite")
            .build()
          val customImageLabelerOptions =
            CustomImageLabelerOptions.Builder(localClassifier).build()
          imageProcessor =
            LabelDetectorProcessor(
              this,
              customImageLabelerOptions
            )
        }
        CUSTOM_AUTOML_LABELING -> {
          Log.i(
            TAG,
            "Using Custom AutoML Image Label Detector Processor"
          )
          val customAutoMLLabelLocalModel = LocalModel.Builder()
            .setAssetManifestFilePath("automl/manifest.json")
            .build()
          val customAutoMLLabelOptions = CustomImageLabelerOptions
            .Builder(customAutoMLLabelLocalModel)
            .setConfidenceThreshold(0f)
            .build()
          imageProcessor =
            LabelDetectorProcessor(
              this,
              customAutoMLLabelOptions
            )
        }
        POSE_DETECTION -> {
          val poseDetectorOptions =
            PreferenceUtils.getPoseDetectorOptionsForStillImage(this)
          val shouldShowInFrameLikelihood =
            PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodStillImage(this)
          Log.i(TAG, "Using Pose Detector with options $poseDetectorOptions")
          imageProcessor =
            PoseDetectorProcessor(this, poseDetectorOptions, shouldShowInFrameLikelihood)
        }
        else -> Log.e(
          TAG,
          "Unknown selectedMode: $selectedMode"
        )
      }
    } catch (e: Exception) {
      Log.e(
        TAG,
        "Can not create image processor: $selectedMode",
        e
      )
      Toast.makeText(
        applicationContext,
        "Can not create image processor: " + e.message,
        Toast.LENGTH_LONG
      )
        .show()
    }
  }

  companion object {
    private const val TAG = "StillImageActivity"
    private const val OBJECT_DETECTION = "Object Detection"
    private const val OBJECT_DETECTION_CUSTOM = "Custom Object Detection (Birds)"
    private const val CUSTOM_AUTOML_OBJECT_DETECTION = "Custom AutoML Object Detection (Flower)"
    private const val FACE_DETECTION = "Face Detection"
    private const val BARCODE_SCANNING = "Barcode Scanning"
    private const val TEXT_RECOGNITION = "Text Recognition"
    private const val IMAGE_LABELING = "Image Labeling"
    private const val IMAGE_LABELING_CUSTOM = "Custom Image Labeling (Birds)"
    private const val CUSTOM_AUTOML_LABELING = "Custom AutoML Image Labeling (Flower)"
    private const val POSE_DETECTION = "Pose Detection"

    private const val SIZE_SCREEN = "w:screen" // Match screen width
    private const val SIZE_1024_768 = "w:1024" // ~1024*768 in a normal ratio
    private const val SIZE_640_480 = "w:640" // ~640*480 in a normal ratio
    private const val KEY_IMAGE_URI = "com.jiahan.smartcamera.KEY_IMAGE_URI"
    private const val KEY_IMAGE_MAX_WIDTH = "com.jiahan.smartcamera.KEY_IMAGE_MAX_WIDTH"
    private const val KEY_IMAGE_MAX_HEIGHT = "com.jiahan.smartcamera.KEY_IMAGE_MAX_HEIGHT"
    private const val KEY_SELECTED_SIZE = "com.jiahan.smartcamera.KEY_SELECTED_SIZE"
    private const val REQUEST_IMAGE_CAPTURE = 1001
    private const val REQUEST_CHOOSE_IMAGE = 1002

    private const val REQUEST_PERMISSION_SETTING = 1003

    public var bottomSheetText = ""
  }
}
