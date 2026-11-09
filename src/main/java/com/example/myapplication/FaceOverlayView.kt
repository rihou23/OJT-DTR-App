package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }
    private val eraserPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private var ovalRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val ovalWidth = width * 0.70f
        val ovalHeight = ovalWidth * 1.35f
        val left = (width - ovalWidth) / 2
        val top = (height - ovalHeight) / 2.5f
        ovalRect.set(left, top, left + ovalWidth, top + ovalHeight)
        canvas.drawOval(ovalRect, eraserPaint)
        canvas.drawOval(ovalRect, borderPaint)
    }

    fun setStatus(isValid: Boolean) {
        borderPaint.color = if (isValid) Color.GREEN else Color.RED
        invalidate()
    }
    fun getOvalRect() = ovalRect
}