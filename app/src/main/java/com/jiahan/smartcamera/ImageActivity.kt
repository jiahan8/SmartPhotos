package com.jiahan.smartcamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.facebook.drawee.view.SimpleDraweeView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ImageActivity : AppCompatActivity() {

    var simpleDraweeView: SimpleDraweeView? = null
//    PhotoView simpleDraweeView;
//    private lateinit var binding: ImageActivityBinding

    override fun onStart() {
        super.onStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)

        simpleDraweeView = findViewById(R.id.iv_image)

//        val toolbar = findViewById<Toolbar>(R.id.image_toolbar)
//        setSupportActionBar(toolbar)
//        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
//        supportActionBar!!.setDisplayShowHomeEnabled(true)
//        // setting themes of search view icon, tried setting in styles.xml
//        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("theme", false)) { // is light
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                toolbar.setNavigationIcon(R.drawable.ic_backspace_dark)
//            }
//        } else {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                toolbar.setNavigationIcon(R.drawable.ic_backspace)
//            }
//        }

//        toolbar.setNavigationOnClickListener {
//            finish()
//        }
//        supportActionBar!!.setDisplayShowTitleEnabled(false)
//        simpleDraweeView!!.setOnClickListener(View.OnClickListener { view: View? ->
//            if (!supportActionBar!!.isShowing) {
//                supportActionBar!!.show()
//                val view1 = window.decorView
//                view1.systemUiVisibility = 0
//            } else {
//                supportActionBar!!.hide()
//                val view1 = window.decorView
//                val uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE
//                view1.systemUiVisibility = uiOptions
//            }
//        })

        val intent = intent
        if (intent != null) {
                // Create global configuration and initialize ImageLoader with this config
//                ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(this).build();
//                ImageLoader.getInstance().init(config);
//                ImageLoader.getInstance().loadImage(intent.getStringExtra("imageurl"), new SimpleImageLoadingListener(){
//                    @Override
//                    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
//                new Thread(){
//                    @Override
//                    public void run() {
//                        URL url = null;
//                        Bitmap loadedImage;
//                        try {
//                            url = new URL(intent.getStringExtra("imageurl"));
//                            loadedImage = BitmapFactory.decodeStream(url.openConnection().getInputStream());
//                            photoView.setVisibility(View.VISIBLE);
//                            photoView.setImageBitmap(loadedImage);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }.start();

                simpleDraweeView!!.setVisibility(View.VISIBLE)
//                Log.e("contentimage", "img" + intent.getStringExtra("imageurl"))
//                val uri = Uri.parse(intent.getStringExtra("imageurl"))
//                Uri uri = Uri.parse( "https://upload.wikimedia.org/wikipedia/commons/thumb/8/82/Kristen_Stewart_Cannes_2016.jpg/340px-Kristen_Stewart_Cannes_2016.jpg" );
            val uri = intent.getStringExtra("imageid")
            simpleDraweeView!!.setImageURI(uri)
//                new LoadImage().execute(intent.getStringExtra("imageurl"));

            findViewById<View>(R.id.view_text_button)!!.setOnClickListener(View.OnClickListener { view ->
                val bottomSheetDialog = BottomSheetDialog(view.context)
                val sheetView = layoutInflater.inflate(R.layout.bottom_sheet, view.rootView as ViewGroup, false)
                bottomSheetDialog.setContentView(sheetView)

                bottomSheetDialog.findViewById<TextView>(R.id.bottom_sheet_text)?.text = intent.getStringExtra("imagetext")

                bottomSheetDialog.show()
            })

        } else {
        }


    }

    companion object {
        fun getBitmapFromURL(src: String?): Bitmap? {
            return try {
                val url = URL(src)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                BitmapFactory.decodeStream(input)
            } catch (e: IOException) {
                // Log exception
                null
            }
        }
    }
}