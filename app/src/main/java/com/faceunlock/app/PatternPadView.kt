package com.faceunlock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * A 3x3 pattern grid (9 dots). The user connects dots like an Android pattern
 * lock; the selected dot order is recorded and can be replayed via root.
 */
class PatternPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dots = ArrayList<PointF>()          // local coordinates of 9 dots
    val selected = ArrayList<Int>()                 // chosen dot indices, in order
    private var currentX = 0f
    private var currentY = 0f
    private var drawing = false
    var onChanged: (() -> Unit)? = null

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C5C8A")
        style = Paint.Style.FILL
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00D9B2")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C6CF0")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var dotR = 22f
    private var hitR = 70f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dots.clear()
        val grid = minOf(w * 0.8f, h * 0.55f)
        val left = (w - grid) / 2f
        val top = (h - grid) / 2f + h * 0.08f
        val step = grid / 2f
        for (row in 0..2) for (col in 0..2) {
            dots.add(PointF(left + col * step, top + row * step))
        }
        dotR = grid / 26f
        hitR = grid / 7f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selected.clear()
                drawing = true
                hitTest(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> hitTest(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                drawing = false
                onChanged?.invoke()
            }
        }
        invalidate()
        return true
    }

    private fun hitTest(x: Float, y: Float) {
        for (i in dots.indices) {
            if (i in selected) continue
            if (hypot((x - dots[i].x).toDouble(), (y - dots[i].y).toDouble()) < hitR) {
                selected.add(i)
                onChanged?.invoke()
            }
        }
    }

    fun clearPattern() {
        selected.clear()
        drawing = false
        invalidate()
        onChanged?.invoke()
    }

    fun hasPattern() = selected.size >= 2

    /** Screen-coordinate centers of the selected dots, in order. */
    fun selectedScreenPoints(): List<PointF> {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return selected.map { PointF(loc[0] + dots[it].x, loc[1] + dots[it].y) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // connecting lines between selected dots
        for (i in 1 until selected.size) {
            val a = dots[selected[i - 1]]
            val b = dots[selected[i]]
            canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
        }
        // live segment to finger
        if (drawing && selected.isNotEmpty()) {
            val last = dots[selected.last()]
            canvas.drawLine(last.x, last.y, currentX, currentY, linePaint)
        }
        // dots
        for (i in dots.indices) {
            val p = dots[i]
            canvas.drawCircle(p.x, p.y, dotR, gridPaint)
            if (i in selected) canvas.drawCircle(p.x, p.y, dotR * 1.5f, selPaint)
        }
    }
}
