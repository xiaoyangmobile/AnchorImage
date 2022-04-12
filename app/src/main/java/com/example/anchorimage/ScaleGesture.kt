package com.example.anchorimage

import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation


class ScaleGesture(sourceView: View) : ScaleGestureDetector.OnScaleGestureListener {
    private var beforeFactor = 0f
    private var mPivotX = 0f
    private var mPivotY = 0f
    private var mSourceView: View = sourceView

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val factor = detector.scaleFactor
        val animation: Animation = ScaleAnimation(
            beforeFactor, factor,
            beforeFactor, factor, mPivotX, mPivotY
        )
        animation.fillAfter = true
        mSourceView.startAnimation(animation)
        beforeFactor = factor
        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        if (detector.isInProgress) {
            return false
        }
        beforeFactor = 1f
        mPivotX = detector.focusX
        mPivotY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        val factor = detector.scaleFactor
        val nWidth = mSourceView.width * factor
        val nHeight = mSourceView.height * factor
        val centerX = mPivotX + mSourceView.left
        val centerY = mPivotY + mSourceView.top
        val left = centerX - (centerX - mSourceView.left) * factor
        val top = centerY - (centerY - mSourceView.top) * factor
        val right = left + nWidth
        val bottom = top + nHeight

        mSourceView.layout(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        mSourceView.clearAnimation()
    }
}