package com.example.anchorimage

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class AnchorImageView(context: Context, attributeSet: AttributeSet): AppCompatImageView(context, attributeSet) {
    private val anchorList: ArrayList<ImageViewAnchorData> = ArrayList()

    fun setAnchors(list: ArrayList<ImageViewAnchorData>) {
        anchorList.clear()
        anchorList.addAll(list)
    }

    fun setAnchor(anchor: Anchor, inSetting: Boolean) {
        anchorList.add(ImageViewAnchorData(anchor, inSetting, 0.3575793f, 0.37609836f))
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val width = this.width
        val height = this.height
        for (data in anchorList) {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.vr_car_img_focus_s)
            val delta = bitmap.width/2
            canvas!!.drawBitmap(bitmap, data.positionX*width - delta, data.positionY*height - delta, Paint())
            bitmap.recycle()
        }
    }

    data class ImageViewAnchorData(val anchor: Anchor, val inSetting: Boolean, val positionX: Float, val positionY: Float)

    data class Anchor(
        val id: String,
    )
}