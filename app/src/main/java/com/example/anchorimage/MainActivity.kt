package com.example.anchorimage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.core.view.isVisible
import photoview.PhotoView

class MainActivity : AppCompatActivity() {
    private lateinit var imageView: AnchorImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        imageView = findViewById<AnchorImageView>(R.id.image_view)
        imageView.setImageResource(R.mipmap.img1)
        imageView.setAnchor(AnchorImageView.Anchor("1"), true)

        val scaleGesture = ScaleGesture(imageView)
        val detector = ScaleGestureDetector(this, scaleGesture)
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Log.e("MainActivity", v.left.toString() + ":" + v.top)
            }
            detector.onTouchEvent(event)
        }

        val img2 = findViewById<PhotoView>(R.id.image_view1)
        img2.setImageResource(R.mipmap.img1)

//        img2.setOnMatrixChangeListener {
//            Log.e("MainActivity", it.toString())
//        }

        val img3 = findViewById<AnchorEditImageView>(R.id.image_view2)
        img3.setImageResource(R.mipmap.img1)

        val btn = findViewById<Button>(R.id.btn)
        btn.setOnClickListener {
            if (imageView.isVisible) {
                imageView.visibility = View.GONE
                img3.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.VISIBLE
                img3.visibility = View.GONE
            }
        }
    }
}