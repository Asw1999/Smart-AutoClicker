/*
 * Copyright (C) 2026 Smart-AutoClicker Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.recordingconfig.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

import com.buzbuz.smartautoclicker.core.recording.recorder.TouchRecorder

/**
 * Fullscreen transparent canvas view that captures user touch events,
 * draws real-time gesture trails, and feeds coordinates into [TouchRecorder].
 */
class RecordingCanvasView(
    context: Context,
    private val touchRecorder: TouchRecorder,
    private val onPointsCountUpdated: (Int) -> Unit,
) : View(context) {

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Green
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val strokePaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright green
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val touchPointPaint = Paint().apply {
        color = Color.parseColor("#FF5252") // Red accent dot
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Active gesture paths per pointer ID
    private val activePaths = mutableMapOf<Int, Path>()
    private val activePoints = mutableMapOf<Int, PointF>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw screen boundary border
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        // Draw active finger trails
        for (path in activePaths.values) {
            canvas.drawPath(path, strokePaint)
        }

        // Draw active touch points (finger heads)
        for (point in activePoints.values) {
            canvas.drawCircle(point.x, point.y, 16f, touchPointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (touchRecorder.state.value != TouchRecorder.State.RECORDING) return false

        touchRecorder.recordMotionEvent(event)
        onPointsCountUpdated(touchRecorder.eventCount)

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val path = Path()
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                path.moveTo(x, y)
                activePaths[pointerId] = path
                activePoints[pointerId] = PointF(x, y)
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    activePaths[id]?.lineTo(x, y)
                    activePoints[id] = PointF(x, y)
                }
                invalidate()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                activePaths.remove(pointerId)
                activePoints.remove(pointerId)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    activePaths.clear()
                    activePoints.clear()
                }
                invalidate()
            }
        }

        return true
    }

    fun clearTrails() {
        activePaths.clear()
        activePoints.clear()
        invalidate()
    }
}
