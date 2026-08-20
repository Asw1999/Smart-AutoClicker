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
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

import com.buzbuz.smartautoclicker.core.common.overlays.OverlayManager
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.recorder.TouchRecorder
import com.buzbuz.smartautoclicker.feature.recordingconfig.R
import com.buzbuz.smartautoclicker.feature.recordingconfig.databinding.OverlayRecordingBinding

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full-screen overlay that captures touch events for recording
 * while showing a visual indicator (green border + controls).
 */
@Singleton
class RecordingOverlay @Inject constructor(
    private val overlayManager: OverlayManager,
    private val touchRecorder: TouchRecorder,
) {

    private var binding: OverlayRecordingBinding? = null
    private var onRecordingCompleted: ((Recording) -> Unit)? = null
    private var onRecordingCancelled: (() -> Unit)? = null

    /**
     * Show the recording overlay and start capturing touches.
     *
     * @param context the context to inflate views.
     * @param scenarioId the scenario ID to associate the recording with.
     * @param onCompleted callback invoked when recording is stopped with a result.
     * @param onCancelled callback invoked when recording is cancelled.
     */
    fun show(
        context: Context,
        scenarioId: Long,
        onCompleted: (Recording) -> Unit,
        onCancelled: () -> Unit = {},
    ) {
        if (binding != null) return

        onRecordingCompleted = onCompleted
        onRecordingCancelled = onCancelled

        val inflater = LayoutInflater.from(context)
        val viewBinding = OverlayRecordingBinding.inflate(inflater)
        binding = viewBinding

        setupViews(viewBinding)
        touchRecorder.startRecording(scenarioId)

        // Overlay layout params - full screen, intercept touches
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayManager.overlayWindowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Set touch listener on root to capture all motion events
        viewBinding.recordingOverlayRoot.setOnTouchListener { _, event ->
            touchRecorder.recordMotionEvent(event)
            viewBinding.textEventCount.text = "${touchRecorder.eventCount} pts"
            // Return false so touch passes through to apps underneath if configured,
            // or true to consume
            false
        }

        overlayManager.addView(viewBinding.root, params)
    }

    /**
     * Hide and remove the recording overlay.
     */
    fun hide() {
        binding?.let { viewBinding ->
            overlayManager.removeView(viewBinding.root)
            binding = null
            onRecordingCompleted = null
            onRecordingCancelled = null
        }
    }

    private fun setupViews(viewBinding: OverlayRecordingBinding) {
        viewBinding.buttonPauseResume.setOnClickListener {
            when (touchRecorder.state.value) {
                TouchRecorder.State.RECORDING -> {
                    touchRecorder.pauseRecording()
                    viewBinding.buttonPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    viewBinding.statusIndicator.visibility = View.INVISIBLE
                }
                TouchRecorder.State.PAUSED -> {
                    touchRecorder.resumeRecording()
                    viewBinding.buttonPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    viewBinding.statusIndicator.visibility = View.VISIBLE
                }
                TouchRecorder.State.IDLE -> Unit
            }
        }

        viewBinding.buttonStop.setOnClickListener {
            val recording = touchRecorder.stopRecording()
            val callback = onRecordingCompleted
            hide()
            callback?.invoke(recording)
        }
    }
}
