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

import android.view.LayoutInflater
import android.view.View

import com.buzbuz.smartautoclicker.core.common.overlays.other.FullscreenOverlay
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.recorder.TouchRecorder
import com.buzbuz.smartautoclicker.feature.recordingconfig.databinding.OverlayRecordingBinding

/**
 * Fullscreen overlay that captures touch events for recording
 * while displaying a green border indicator and control buttons.
 *
 * Managed by OverlayManager via navigation.
 */
class RecordingOverlay(
    private val scenarioId: Long,
    private val touchRecorder: TouchRecorder,
    private val onRecordingCompleted: (Recording) -> Unit,
) : FullscreenOverlay() {

    private lateinit var binding: OverlayRecordingBinding

    override fun onCreateView(layoutInflater: LayoutInflater): View {
        binding = OverlayRecordingBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated() {
        touchRecorder.startRecording(scenarioId)

        binding.recordingOverlayRoot.setOnTouchListener { _, event ->
            touchRecorder.recordMotionEvent(event)
            binding.textEventCount.text = "${touchRecorder.eventCount} pts"
            false
        }

        binding.buttonPauseResume.setOnClickListener {
            when (touchRecorder.state.value) {
                TouchRecorder.State.RECORDING -> {
                    touchRecorder.pauseRecording()
                    binding.buttonPauseResume.setImageResource(android.R.drawable.ic_media_play)
                    binding.statusIndicator.visibility = View.INVISIBLE
                }
                TouchRecorder.State.PAUSED -> {
                    touchRecorder.resumeRecording()
                    binding.buttonPauseResume.setImageResource(android.R.drawable.ic_media_pause)
                    binding.statusIndicator.visibility = View.VISIBLE
                }
                TouchRecorder.State.IDLE -> Unit
            }
        }

        binding.buttonStop.setOnClickListener {
            val recording = touchRecorder.stopRecording()
            finish()
            onRecordingCompleted(recording)
        }
    }
}
