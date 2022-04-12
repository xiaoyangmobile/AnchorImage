package com.example.anchorimage

import android.content.Context
import android.graphics.*
import android.graphics.Matrix.ScaleToFit
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class AnchorEditImageView(context: Context, attributeSet: AttributeSet) :
    AppCompatImageView(context, attributeSet), View.OnTouchListener,
    View.OnLayoutChangeListener {

    private val mAnchorBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.vr_car_img_focus);
    }
    private val mAnchorBitmapWidth: Int by lazy {
        mAnchorBitmap.width / 2
    }
    private val mAnchorPaint: Paint by lazy {
        Paint()
    }
    var mAnchorCenter: PointF? = null


    // These are set so we don't keep allocating them on the heap
    private val mBaseMatrix = Matrix()
    private val mDrawMatrix = Matrix()
    private val mSuppMatrix = Matrix()
    private val mDisplayRect = RectF()
    private val mMatrixValues = FloatArray(9)

    private val mInterpolator: Interpolator = AccelerateDecelerateInterpolator()
    private val mZoomDuration = 200
    private val mMinScale = 1.0f
    private val mMaxScale = 3.0f

    private val HORIZONTAL_EDGE_NONE = -1
    private val HORIZONTAL_EDGE_LEFT = 0
    private val HORIZONTAL_EDGE_RIGHT = 1
    private val HORIZONTAL_EDGE_BOTH = 2
    private val VERTICAL_EDGE_NONE = -1
    private val VERTICAL_EDGE_TOP = 0
    private val VERTICAL_EDGE_BOTTOM = 1
    private val VERTICAL_EDGE_BOTH = 2

    private val mAllowParentInterceptOnEdge = true
    private var mBlockParentIntercept = false

    private var mVerticalScrollEdge = VERTICAL_EDGE_BOTH
    private var mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH

    private val mScaleType = ScaleType.FIT_CENTER

    private val mBaseRotation = 0f

    private var mAnchorLastTouchX = 0f
    private var mAnchorLastTouchY = 0f

    private var mAnchorEnableListener: (() -> Unit)? = null
    private var mDragOrScaleListener: (() -> Unit)? = null

    private val mScaleDragDetector: CustomGestureDetector by lazy {
        CustomGestureDetector(context, onGestureListener)
    }
    private val onGestureListener = object : CustomGestureDetector.OnGestureListener {
        override fun onDrag(dx: Float, dy: Float) {
            if (mScaleDragDetector.isScaling) {
                return
            }
            if (getScale() == 1f) {
                return
            }
            mSuppMatrix.postTranslate(dx, dy)
            checkAndDisplayMatrix()
            mDragOrScaleListener?.let {
                it.invoke()
                mDragOrScaleListener = null
            }

            val parent: ViewParent = parent
            if (mAllowParentInterceptOnEdge && !mScaleDragDetector.isScaling && !mBlockParentIntercept) {
                if (mHorizontalScrollEdge == HORIZONTAL_EDGE_BOTH || mHorizontalScrollEdge == HORIZONTAL_EDGE_LEFT && dx >= 1f
                    || mHorizontalScrollEdge == HORIZONTAL_EDGE_RIGHT && dx <= -1f
                    || mVerticalScrollEdge == VERTICAL_EDGE_TOP && dy >= 1f
                    || mVerticalScrollEdge == VERTICAL_EDGE_BOTTOM && dy <= -1f
                ) {
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            } else {
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }

        override fun onScale(
            scaleFactor: Float,
            focusX: Float,
            focusY: Float,
            dx: Float,
            dy: Float
        ) {
            if (mAnchorCenter == null) {
                return
            }
            val currentScale = getScale()
            if (currentScale < mMaxScale || scaleFactor < 1f) {
                mSuppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
                mSuppMatrix.postTranslate(dx, dy)
                checkAndDisplayMatrix()
                if (currentScale > 1 || dx > 5 || dy > 5) {
                    mDragOrScaleListener?.let {
                        it.invoke()
                        mDragOrScaleListener = null
                    }
                }

            }
        }

    }

    init {
        super.setScaleType(ScaleType.MATRIX)
        setOnTouchListener(this)
        addOnLayoutChangeListener(this)
    }

    fun setOnAnchorEnableListener(listener: (() -> Unit)) {
        mAnchorEnableListener = listener
    }

    fun setOnDragOrScaleListener(listener: (() -> Unit)) {
        mDragOrScaleListener = listener
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        var handled = false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (mAnchorCenter == null) {
                    mAnchorCenter = PointF(event.x / width, event.y / height)
                    invalidate()
                    mAnchorEnableListener?.invoke()
                }
                val parent = v.parent
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP -> {
                if (getScale() < 1.1) {
                    val rect = getDisplayRect()
                    if (rect != null) {
                        v.post(
                            AnimatedZoomRunnable(
                                getScale(), mMinScale,
                                rect.centerX(), rect.centerY()
                            )
                        )
                        handled = true
                    }
                } else if (getScale() > mMaxScale) {
                    val rect = getDisplayRect()
                    if (rect != null) {
                        v.post(
                            AnimatedZoomRunnable(
                                getScale(), mMaxScale,
                                rect.centerX(), rect.centerY()
                            )
                        )
                        handled = true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
            }
        }
        // Drag Anchor
        if (mAnchorCenter != null && event.pointerCount == 1) {
            val displayRect = getDisplayRect()
            val left = displayRect.width() * mAnchorCenter!!.x + displayRect.left - mAnchorBitmapWidth
            val right = left + mAnchorBitmapWidth * 2
            val top = displayRect.height() * mAnchorCenter!!.y + displayRect.top - mAnchorBitmapWidth
            val bottom = top + mAnchorBitmapWidth * 2
            val anchorRect = RectF(left, top, right, bottom)
            if (anchorRect.contains(event.x, event.y)) {
                processAnchorDrag(event, displayRect)
                return true
            }
        }
        // Try the Scale/Drag detector
        if (mScaleDragDetector != null) {
            val wasScaling = mScaleDragDetector.isScaling
            val wasDragging = mScaleDragDetector.isDragging
            handled = mScaleDragDetector.onTouchEvent(event)
            val didntScale = !wasScaling && !mScaleDragDetector.isScaling
            val didntDrag = !wasDragging && !mScaleDragDetector.isDragging
            mBlockParentIntercept = didntScale && didntDrag
        }
        return handled
    }

    fun getScale(): Float {
        return sqrt(
            getValue(mSuppMatrix, Matrix.MSCALE_X).toDouble().pow(2.0) + getValue(
                mSuppMatrix,
                Matrix.MSKEW_Y
            ).toDouble().pow(2.0)
        ).toFloat()
    }

    private fun  processAnchorDrag(event: MotionEvent, displayRect: RectF) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mAnchorLastTouchX = event.x
                mAnchorLastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                val deltaX = x - mAnchorLastTouchX
                val deltaY = y - mAnchorLastTouchY
                if (abs(deltaX) >= 5 || abs(deltaY) >= 5) {
                    mAnchorCenter!!.x += deltaX/displayRect.width()
                    mAnchorCenter!!.y += deltaY/displayRect.height()
                    invalidate()
                    mAnchorLastTouchX = x
                    mAnchorLastTouchY = y
                    mDragOrScaleListener?.let {
                        it.invoke()
                        mDragOrScaleListener = null
                    }
                }
            }
        }
    }

    private fun getAnchorArea(): RectF {
        val displayRect = getDisplayRect()
        val left = displayRect.width() * mAnchorCenter!!.x + displayRect.left
        val right = left + mAnchorBitmapWidth * 2
        val top = displayRect.height() * mAnchorCenter!!.y + displayRect.top
        val bottom = top + mAnchorBitmapWidth * 2
        return RectF(left, top, right, bottom)
    }

    private fun getValue(matrix: Matrix, whichValue: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[whichValue]
    }

    private fun checkAndDisplayMatrix() {
        if (checkMatrixBounds()) {
            setImageViewMatrix(getDrawMatrix());
        }
    }

    private fun checkMatrixBounds(): Boolean {
        val rect: RectF = getDisplayRect(getDrawMatrix()) ?: return false
        val height = rect.height()
        val width = rect.width()
        var deltaX = 0f
        var deltaY = 0f
        val viewHeight: Int = getImageViewHeight(this)
        if (height <= viewHeight) {
            deltaY = when (mScaleType) {
                ScaleType.FIT_START -> -rect.top
                ScaleType.FIT_END -> viewHeight - height - rect.top
                else -> (viewHeight - height) / 2 - rect.top
            }
            mVerticalScrollEdge = VERTICAL_EDGE_BOTH
        } else if (rect.top > 0) {
            mVerticalScrollEdge = VERTICAL_EDGE_TOP
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            mVerticalScrollEdge = VERTICAL_EDGE_BOTTOM
            deltaY = viewHeight - rect.bottom
        } else {
            mVerticalScrollEdge = VERTICAL_EDGE_NONE
        }
        val viewWidth: Int = getImageViewWidth(this)
        if (width <= viewWidth) {
            deltaX = when (mScaleType) {
                ScaleType.FIT_START -> -rect.left
                ScaleType.FIT_END -> viewWidth - width - rect.left
                else -> (viewWidth - width) / 2 - rect.left
            }
            mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
        } else if (rect.left > 0) {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_LEFT
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right
            mHorizontalScrollEdge = HORIZONTAL_EDGE_RIGHT
        } else {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_NONE
        }
        // Finally actually translate the matrix
        mSuppMatrix.postTranslate(deltaX, deltaY)
        return true
    }

    private fun getDrawMatrix(): Matrix {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mSuppMatrix)
        return mDrawMatrix
    }

    private fun getDisplayRect(): RectF {
        checkMatrixBounds()
        return getDisplayRect(getDrawMatrix())
    }

    private fun getDisplayRect(matrix: Matrix): RectF {
        val d: Drawable = drawable
        mDisplayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix.mapRect(mDisplayRect)
        return mDisplayRect
    }

    private fun getImageViewWidth(imageView: ImageView): Int {
        return imageView.width - imageView.paddingLeft - imageView.paddingRight
    }

    private fun getImageViewHeight(imageView: ImageView): Int {
        return imageView.height - imageView.paddingTop - imageView.paddingBottom
    }

    private fun updateBaseMatrix(drawable: Drawable?) {
        if (drawable == null) {
            return
        }
        val viewWidth = getImageViewWidth(this).toFloat()
        val viewHeight = getImageViewHeight(this).toFloat()
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        mBaseMatrix.reset()
        val widthScale = viewWidth / drawableWidth
        val heightScale = viewHeight / drawableHeight
        if (mScaleType == ScaleType.CENTER) {
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth) / 2f,
                (viewHeight - drawableHeight) / 2f
            )
        } else if (mScaleType == ScaleType.CENTER_CROP) {
            val scale = Math.max(widthScale, heightScale)
            mBaseMatrix.postScale(scale, scale)
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f
            )
        } else if (mScaleType == ScaleType.CENTER_INSIDE) {
            val scale = Math.min(1.0f, Math.min(widthScale, heightScale))
            mBaseMatrix.postScale(scale, scale)
            mBaseMatrix.postTranslate(
                (viewWidth - drawableWidth * scale) / 2f,
                (viewHeight - drawableHeight * scale) / 2f
            )
        } else {
            var mTempSrc = RectF(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat())
            val mTempDst = RectF(0f, 0f, viewWidth, viewHeight)
            if (mBaseRotation.toInt() % 180 != 0) {
                mTempSrc = RectF(0f, 0f, drawableHeight.toFloat(), drawableWidth.toFloat())
            }
            when (mScaleType) {
                ScaleType.FIT_CENTER -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    ScaleToFit.CENTER
                )
                ScaleType.FIT_START -> mBaseMatrix.setRectToRect(
                    mTempSrc,
                    mTempDst,
                    ScaleToFit.START
                )
                ScaleType.FIT_END -> mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.END)
                ScaleType.FIT_XY -> mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.FILL)
                else -> {
                }
            }
        }
        resetMatrix()
    }

    private fun resetMatrix() {
        mSuppMatrix.reset()
        setRotationBy(mBaseRotation)
        setImageViewMatrix(getDrawMatrix())
        checkMatrixBounds()
    }

    fun setRotationBy(degrees: Float) {
        mSuppMatrix.postRotate(degrees % 360)
        checkAndDisplayMatrix()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        mAnchorCenter?.let {
            val rect = getDisplayRect(getDrawMatrix())
            // RectF(-11.06035, -11.392242, 1140.9381, 853.86414)
            val startX = (rect.right - rect.left) * it.x + rect.left - mAnchorBitmapWidth
            val startY = (rect.bottom - rect.top) * it.y + rect.top - mAnchorBitmapWidth
            canvas?.drawBitmap(mAnchorBitmap, startX, startY, mAnchorPaint)
        }
    }

    private fun setImageViewMatrix(matrix: Matrix) {
        imageMatrix = matrix
        mAnchorCenter?.let {
            invalidate()
        }
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        // Update our base matrix, as the bounds have changed
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix(drawable)
        }
    }

    private inner class AnimatedZoomRunnable(
        currentZoom: Float,
        targetZoom: Float,
        private val mFocalX: Float,
        private val mFocalY: Float
    ) : Runnable {
        private val mStartTime: Long = System.currentTimeMillis()
        private val mZoomStart: Float = currentZoom
        private val mZoomEnd: Float = targetZoom

        override fun run() {
            val t = interpolate()
            val scale = mZoomStart + t * (mZoomEnd - mZoomStart)
            val deltaScale: Float = scale / getScale()
            onGestureListener.onScale(deltaScale, mFocalX, mFocalY, 0f, 0f)
            // We haven't hit our target scale yet, so post ourselves again
            if (t < 1f) {
                postOnAnimation(this)
            }
        }

        private fun interpolate(): Float {
            var t: Float = 1f * (System.currentTimeMillis() - mStartTime) / mZoomDuration
            t = Math.min(1f, t)
            t = mInterpolator.getInterpolation(t)
            return t
        }
    }

}