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

import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.SystemClock
import android.util.Log

import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.recording.domain.TouchAction
import com.buzbuz.smartautoclicker.core.recording.domain.TouchEvent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * High-performance background touch recorder using Shizuku ADB shell privileges.
 *
 * Reads raw touch events directly from Linux kernel input nodes (/dev/input/event*)
 * via `getevent`, allowing touch events to pass through seamlessly to the underlying
 * active application/game while recording exact touch trajectories simultaneously.
 */
@Singleton
class ShizukuTouchRecorder @Inject constructor(
    private val displayConfigManager: DisplayConfigManager,
) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private var recordingJob: Job? = null
    private var getEventProcess: Process? = null

    private var touchDeviceMaxX: Float = 0f
    private var touchDeviceMaxY: Float = 0f

    /**
     * Check whether Shizuku is currently running and permission is granted.
     */
    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /**
     * Start background event recording via Shizuku shell process.
     */
    fun startRecording(
        onEventCaptured: (TouchEvent) -> Unit,
        onPointsCountUpdated: (Int) -> Unit,
    ) {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku is not available or permission denied")
            return
        }

        stopRecording()
        _isRecording.value = true

        val screenSize = displayConfigManager.displayConfig.sizePx
        val screenWidth = screenSize.x.toFloat()
        val screenHeight = screenSize.y.toFloat()

        val startTimeMs = SystemClock.uptimeMillis()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Determine touch device dimensions and start getevent
                val process = Shizuku.newProcess(arrayOf("sh", "-c", "getevent -lt"), null, null)
                getEventProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var currentX = 0f
                var currentY = 0f
                var currentPointerId = 0
                var currentAction = TouchAction.DOWN
                var hasCoordinates = false
                var totalRecorded = 0

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    // Parse getevent output:
                    // format: [  timestamp ] /dev/input/eventX: EV_ABS       ABS_MT_POSITION_X    000003b2
                    // or:     [  timestamp ] /dev/input/eventX: EV_SYN       SYN_REPORT           00000000
                    if (line.contains("ABS_MT_POSITION_X") || line.contains("0035")) {
                        val hexVal = line.trim().split("\\s+".toRegex()).lastOrNull()
                        val rawX = hexVal?.toIntOrNull(16)?.toFloat() ?: continue
                        if (touchDeviceMaxX == 0f || rawX > touchDeviceMaxX) touchDeviceMaxX = max(rawX, screenWidth)
                        currentX = (rawX / touchDeviceMaxX) * screenWidth
                        hasCoordinates = true
                    } else if (line.contains("ABS_MT_POSITION_Y") || line.contains("0036")) {
                        val hexVal = line.trim().split("\\s+".toRegex()).lastOrNull()
                        val rawY = hexVal?.toIntOrNull(16)?.toFloat() ?: continue
                        if (touchDeviceMaxY == 0f || rawY > touchDeviceMaxY) touchDeviceMaxY = max(rawY, screenHeight)
                        currentY = (rawY / touchDeviceMaxY) * screenHeight
                        hasCoordinates = true
                    } else if (line.contains("ABS_MT_TRACKING_ID") || line.contains("0039")) {
                        val hexVal = line.trim().split("\\s+".toRegex()).lastOrNull()
                        val trackingId = hexVal?.toLongOrNull(16) ?: -1L
                        currentAction = if (trackingId == 0xffffffffL || trackingId == -1L) {
                            TouchAction.UP
                        } else {
                            TouchAction.DOWN
                        }
                    } else if (line.contains("SYN_REPORT")) {
                        if (hasCoordinates) {
                            val elapsed = SystemClock.uptimeMillis() - startTimeMs
                            val event = TouchEvent(
                                pointerIndex = currentPointerId,
                                action = currentAction,
                                position = PointF(currentX, currentY),
                                pressure = 1.0f,
                                timestampMs = elapsed,
                            )
                            onEventCaptured(event)
                            totalRecorded++
                            onPointsCountUpdated(totalRecorded)

                            if (currentAction == TouchAction.DOWN) {
                                currentAction = TouchAction.MOVE
                            }
                            hasCoordinates = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running Shizuku getevent process", e)
            } finally {
                _isRecording.value = false
            }
        }
    }

    /**
     * Stop the Shizuku recording process.
     */
    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            getEventProcess?.destroy()
        } catch (e: Exception) {
            // Ignored
        }
        getEventProcess = null
        _isRecording.value = false
    }

    companion object {
        private const val TAG = "ShizukuTouchRecorder"
    }
}
