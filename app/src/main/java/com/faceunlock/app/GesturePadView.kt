package com.faceunlock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Captures a single continuous touch gesture in absolute screen coordinates. */
class GesturePadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Recorded points in RAW (screen) coordinates. */
    val rawPoints = mutableListOf<PointF>()

    /** View-local points, only for drawing. */
    private val drawPoints = mutableListOf<PointF>()
    private val path = Path()
    var onChanged: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00D9B2")
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C6CF0")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clear()
                add(event)
            }
            MotionEvent.ACTION_MOVE -> add(event)
            MotionEvent.ACTION_UP -> {
                add(event)
                onChanged?.invoke()
            }
        }
        invalidate()
        return true
    }

    private fun add(event: MotionEvent) {
        rawPoints.add(PointF(event.rawX, event.rawY))
        val lx = event.x
        val ly = event.y
        drawPoints.add(PointF(lx, ly))
        if (drawPoints.size == 1) path.moveTo(lx, ly) else path.lineTo(lx, ly)
    }

    fun clear() {
        rawPoints.clear()
        drawPoints.clear()
        path.reset()
        invalidate()
        onChanged?.invoke()
    }

    fun hasGesture() = rawPoints.size >= 2

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawPoints.isNotEmpty()) {
            canvas.drawPath(path, paint)
            val s = drawPoints.first()
            val e = drawPoints.last()
            canvas.drawCircle(s.x, s.y, 16f, dotPaint)
            canvas.drawCircle(e.x, e.y, 16f, dotPaint)
        }
    }
}
