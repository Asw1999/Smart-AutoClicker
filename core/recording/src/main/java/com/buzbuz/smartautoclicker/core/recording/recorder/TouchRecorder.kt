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
package com.buzbuz.smartautoclicker.core.recording.recorder

import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.domain.TouchAction
import com.buzbuz.smartautoclicker.core.recording.domain.TouchEvent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures user touch interactions and converts them into a [Recording].
 *
 * State machine:
 * IDLE -> RECORDING -> PAUSED -> RECORDING -> IDLE (via stop)
 */
@Singleton
class TouchRecorder @Inject constructor(
    val shizukuRecorder: ShizukuTouchRecorder,
) {

    enum class State {
        IDLE,
        RECORDING,
        PAUSED,
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    val isShizukuAvailable: Boolean
        get() = shizukuRecorder.isShizukuAvailable()

    private val recordedEvents = mutableListOf<TouchEvent>()
    private var recordingStartTimeUptimeMs: Long = 0L
    private var pausedTimeOffsetMs: Long = 0L
    private var pauseStartTimeUptimeMs: Long = 0L
    private var activeScenarioId: Long = 0L
    private var maxPointersSeen: Int = 1

    /**
     * Start a new recording session.
     *
     * @param scenarioId the scenario this recording will be associated with.
     */
    fun startRecording(scenarioId: Long, onPointsUpdated: ((Int) -> Unit)? = null) {
        synchronized(recordedEvents) {
            recordedEvents.clear()
            activeScenarioId = scenarioId
            maxPointersSeen = 1
            pausedTimeOffsetMs = 0L
            recordingStartTimeUptimeMs = SystemClock.uptimeMillis()
            _state.value = State.RECORDING

            if (shizukuRecorder.isShizukuAvailable()) {
                shizukuRecorder.startRecording(
                    onEventCaptured = { event -> addTouchEvent(event) },
                    onPointsCountUpdated = { count -> onPointsUpdated?.invoke(count) },
                )
            }
        }
    }

    /**
     * Pause the current recording without losing already recorded events.
     */
    fun pauseRecording() {
        if (_state.value != State.RECORDING) return
        pauseStartTimeUptimeMs = SystemClock.uptimeMillis()
        _state.value = State.PAUSED
        shizukuRecorder.stopRecording()
    }

    /**
     * Resume a paused recording session.
     */
    fun resumeRecording(onPointsUpdated: ((Int) -> Unit)? = null) {
        if (_state.value != State.PAUSED) return
        pausedTimeOffsetMs += SystemClock.uptimeMillis() - pauseStartTimeUptimeMs
        _state.value = State.RECORDING

        if (shizukuRecorder.isShizukuAvailable()) {
            shizukuRecorder.startRecording(
                onEventCaptured = { event -> addTouchEvent(event) },
                onPointsCountUpdated = { count -> onPointsUpdated?.invoke(count) },
            )
        }
    }

    /**
     * Directly add a touch event captured from external sources (e.g. Shizuku / kernel).
     */
    fun addTouchEvent(event: TouchEvent) {
        if (_state.value != State.RECORDING) return
        synchronized(recordedEvents) {
            if (event.pointerIndex + 1 > maxPointersSeen) {
                maxPointersSeen = event.pointerIndex + 1
            }
            recordedEvents.add(event)
        }
    }

    /**
     * Stop recording and return the completed [Recording].
     *
     * @param recordingName optional custom name for the recording.
     * @return the assembled [Recording] containing all captured touch events.
     */
    fun stopRecording(recordingName: String? = null): Recording {
        shizukuRecorder.stopRecording()
        synchronized(recordedEvents) {
            val totalDurationMs = if (recordedEvents.isNotEmpty()) {
                recordedEvents.last().timestampMs
            } else {
                0L
            }

            val name = recordingName ?: "Recording ${System.currentTimeMillis()}"
            val recording = Recording(
                id = Identifier(0, asTemporary = true),
                scenarioId = activeScenarioId,
                name = name,
                touchEvents = recordedEvents.toList(),
                durationMs = totalDurationMs,
                fingerCount = maxPointersSeen,
                createdAt = System.currentTimeMillis(),
            )

            recordedEvents.clear()
            _state.value = State.IDLE
            return recording
        }
    }

    /**
     * Cancel the current recording and discard all captured data.
     */
    fun cancelRecording() {
        shizukuRecorder.stopRecording()
        synchronized(recordedEvents) {
            recordedEvents.clear()
            _state.value = State.IDLE
        }
    }

    /**
     * Feed a [MotionEvent] into the recorder.
     * Should be called from the overlay view's `onTouchEvent`.
     */
    fun recordMotionEvent(event: MotionEvent) {
        if (_state.value != State.RECORDING) return

        val currentUptimeMs = SystemClock.uptimeMillis()
        val relativeTimestampMs = currentUptimeMs - recordingStartTimeUptimeMs - pausedTimeOffsetMs

        if (relativeTimestampMs < 0) return

        synchronized(recordedEvents) {
            val pointerCount = event.pointerCount
            if (pointerCount > maxPointersSeen) {
                maxPointersSeen = pointerCount
            }

            val actionMasked = event.actionMasked
            val actionIndex = event.actionIndex

            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    recordPointerEvent(event, 0, TouchAction.DOWN, relativeTimestampMs)
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    recordPointerEvent(event, actionIndex, TouchAction.DOWN, relativeTimestampMs)
                }

                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until pointerCount) {
                        recordPointerEvent(event, i, TouchAction.MOVE, relativeTimestampMs)
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    recordPointerEvent(event, actionIndex, TouchAction.UP, relativeTimestampMs)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    for (i in 0 until pointerCount) {
                        recordPointerEvent(event, i, TouchAction.UP, relativeTimestampMs)
                    }
                }
            }
        }
    }

    private fun recordPointerEvent(
        event: MotionEvent,
        pointerIndex: Int,
        action: TouchAction,
        timestampMs: Long,
    ) {
        if (pointerIndex >= event.pointerCount) return

        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val pressure = event.getPressure(pointerIndex)

        recordedEvents.add(
            TouchEvent(
                pointerIndex = pointerId,
                action = action,
                position = PointF(x, y),
                pressure = pressure,
                timestampMs = timestampMs,
            )
        )
    }

    val eventCount: Int
        get() = synchronized(recordedEvents) { recordedEvents.size }
}
