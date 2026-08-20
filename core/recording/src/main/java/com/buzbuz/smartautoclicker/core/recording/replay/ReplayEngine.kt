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
package com.buzbuz.smartautoclicker.core.recording.replay

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log

import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.common.actions.gesture.buildSingleStroke
import com.buzbuz.smartautoclicker.core.common.actions.utils.MAXIMUM_STROKE_DURATION_MS
import com.buzbuz.smartautoclicker.core.common.actions.utils.MINIMUM_STROKE_DURATION_MS
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.domain.ReplayParams
import com.buzbuz.smartautoclicker.core.recording.domain.TouchAction
import com.buzbuz.smartautoclicker.core.recording.domain.TouchEvent

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Replays recorded touch sequences via [AndroidActionExecutor.dispatchGesture].
 *
 * Converts a sequence of [TouchEvent]s into [GestureDescription] objects,
 * splitting long recordings into contiguous stroke segments as required by the
 * Android Accessibility framework (max 59,999ms per stroke).
 */
@Singleton
class ReplayEngine @Inject constructor(
    private val actionExecutor: AndroidActionExecutor,
) {

    /**
     * Replay a [Recording] according to the given [ReplayParams].
     *
     * @param recording the recording to replay.
     * @param params parameters for speed, repeat count, delays, and randomization.
     * @return true if all iterations completed successfully, false if any failed or was cancelled.
     */
    suspend fun replay(recording: Recording, params: ReplayParams): Boolean {
        if (recording.touchEvents.isEmpty()) return true

        val random = if (params.randomizePositionPx > 0 || params.randomizeTimingMs > 0) {
            Random.Default
        } else {
            null
        }

        repeat(params.repeatCount) { iteration ->
            val success = replaySinglePass(recording, params, random)
            if (!success) {
                Log.w(TAG, "Replay failed at iteration $iteration of ${params.repeatCount}")
                return false
            }

            if (iteration < params.repeatCount - 1 && params.delayBetweenRepeatMs > 0) {
                val delayMs = if (random != null && params.randomizeTimingMs > 0) {
                    val offset = random.nextLong(-params.randomizeTimingMs.toLong(), params.randomizeTimingMs.toLong())
                    max(0L, params.delayBetweenRepeatMs + offset)
                } else {
                    params.delayBetweenRepeatMs
                }
                delay(delayMs)
            }
        }

        return true
    }

    private suspend fun replaySinglePass(
        recording: Recording,
        params: ReplayParams,
        random: Random?,
    ): Boolean {
        // Group events by pointer ID to support multi-touch
        val eventsByPointer = recording.touchEvents.groupBy { it.pointerIndex }

        // Break each pointer's events into stroke sessions (DOWN -> MOVE* -> UP)
        val strokeSessions = mutableListOf<StrokeSession>()

        for ((pointerId, pointerEvents) in eventsByPointer) {
            var currentStrokeEvents = mutableListOf<TouchEvent>()

            for (event in pointerEvents) {
                when (event.action) {
                    TouchAction.DOWN -> {
                        currentStrokeEvents = mutableListOf(event)
                    }
                    TouchAction.MOVE -> {
                        currentStrokeEvents.add(event)
                    }
                    TouchAction.UP -> {
                        currentStrokeEvents.add(event)
                        if (currentStrokeEvents.isNotEmpty()) {
                            strokeSessions.add(
                                StrokeSession(
                                    pointerId = pointerId,
                                    events = currentStrokeEvents.toList(),
                                )
                            )
                            currentStrokeEvents = mutableListOf()
                        }
                    }
                }
            }

            // Handle unclosed strokes (missing UP)
            if (currentStrokeEvents.isNotEmpty()) {
                strokeSessions.add(
                    StrokeSession(
                        pointerId = pointerId,
                        events = currentStrokeEvents.toList(),
                    )
                )
            }
        }

        if (strokeSessions.isEmpty()) return true

        // Sort all strokes by their start timestamp
        strokeSessions.sortBy { it.startTimeMs }

        // Build and dispatch gestures
        // Merge simultaneous strokes across pointers into a single GestureDescription
        val gestureBatches = batchSimultaneousStrokes(strokeSessions)

        var lastGestureEndMs = 0L

        for (batch in gestureBatches) {
            val batchStartMs = (batch.minOf { it.startTimeMs } / params.speedMultiplier).toLong()

            // Wait until this batch should start relative to the previous one
            val waitBeforeBatchMs = batchStartMs - lastGestureEndMs
            if (waitBeforeBatchMs > 0) {
                val actualWait = if (random != null && params.randomizeTimingMs > 0) {
                    val offset = random.nextLong(-params.randomizeTimingMs.toLong(), params.randomizeTimingMs.toLong())
                    max(0L, waitBeforeBatchMs + offset)
                } else {
                    waitBeforeBatchMs
                }
                delay(actualWait)
            }

            val gesture = buildGestureForBatch(batch, params, random)
            if (gesture != null) {
                actionExecutor.dispatchGesture(gesture)
            }

            val batchEndMs = (batch.maxOf { it.endTimeMs } / params.speedMultiplier).toLong()
            lastGestureEndMs = max(lastGestureEndMs, batchEndMs)
        }

        return true
    }

    private fun batchSimultaneousStrokes(strokes: List<StrokeSession>): List<List<StrokeSession>> {
        if (strokes.isEmpty()) return emptyList()

        val batches = mutableListOf<MutableList<StrokeSession>>()
        var currentBatch = mutableListOf(strokes.first())

        for (i in 1 until strokes.size) {
            val stroke = strokes[i]
            val batchStart = currentBatch.minOf { it.startTimeMs }
            val batchEnd = currentBatch.maxOf { it.endTimeMs }

            // If strokes overlap in time, batch them together (multi-touch gesture)
            if (stroke.startTimeMs <= batchEnd && stroke.pointerId != currentBatch.last().pointerId) {
                currentBatch.add(stroke)
            } else {
                batches.add(currentBatch)
                currentBatch = mutableListOf(stroke)
            }
        }
        batches.add(currentBatch)

        return batches
    }

    private fun buildGestureForBatch(
        batch: List<StrokeSession>,
        params: ReplayParams,
        random: Random?,
    ): GestureDescription? {
        val builder = GestureDescription.Builder()
        val baseStartMs = batch.minOf { it.startTimeMs }
        var hasValidStrokes = false

        for (stroke in batch) {
            if (stroke.events.isEmpty()) continue

            val path = Path()
            val firstEvent = stroke.events.first()
            val startPoint = randomizePosition(firstEvent.position, params.randomizePositionPx, random)
            path.moveTo(startPoint.x, startPoint.y)

            for (i in 1 until stroke.events.size) {
                val event = stroke.events[i]
                val point = randomizePosition(event.position, params.randomizePositionPx, random)
                path.lineTo(point.x, point.y)
            }

            val relativeStartMs = ((stroke.startTimeMs - baseStartMs) / params.speedMultiplier).toLong()
            val durationMs = (stroke.durationMs / params.speedMultiplier).toLong()
                .coerceIn(MINIMUM_STROKE_DURATION_MS, MAXIMUM_STROKE_DURATION_MS)

            try {
                builder.addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        max(0L, relativeStartMs),
                        durationMs,
                    )
                )
                hasValidStrokes = true
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to add stroke to gesture", ex)
            }
        }

        return if (hasValidStrokes) builder.build() else null
    }

    private fun randomizePosition(
        point: PointF,
        maxOffsetPx: Int,
        random: Random?,
    ): PointF {
        if (random == null || maxOffsetPx <= 0) return point
        val offsetX = random.nextFloat() * 2 * maxOffsetPx - maxOffsetPx
        val offsetY = random.nextFloat() * 2 * maxOffsetPx - maxOffsetPx
        return PointF(max(0f, point.x + offsetX), max(0f, point.y + offsetY))
    }

    private data class StrokeSession(
        val pointerId: Int,
        val events: List<TouchEvent>,
    ) {
        val startTimeMs: Long = events.firstOrNull()?.timestampMs ?: 0L
        val endTimeMs: Long = events.lastOrNull()?.timestampMs ?: 0L
        val durationMs: Long = max(MINIMUM_STROKE_DURATION_MS, endTimeMs - startTimeMs)
    }

    companion object {
        private const val TAG = "ReplayEngine"
    }
}
