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
import android.view.ViewGroup

import com.buzbuz.smartautoclicker.core.common.overlays.menu.OverlayMenu
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.recorder.TouchRecorder
import com.buzbuz.smartautoclicker.feature.recordingconfig.R
import com.buzbuz.smartautoclicker.feature.recordingconfig.databinding.OverlayRecordingMenuBinding

/**
 * Floating overlay menu displayed during a gesture recording session.
 *
 * Provides live point count, pause/resume, stop (save), and cancel controls,
 * along with a canvas view that draws gesture trails and captures touch points.
 */
class RecordingMenu(
    private val scenarioId: Long,
    private val touchRecorder: TouchRecorder,
    private val onRecordingCompleted: (Recording) -> Unit,
) : OverlayMenu(theme = R.style.AppTheme_Overlay_FloatingMenu_CardView) {

    private lateinit var menuBinding: OverlayRecordingMenuBinding
    private var canvasView: RecordingCanvasView? = null

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup {
        menuBinding = OverlayRecordingMenuBinding.inflate(layoutInflater)
        return menuBinding.root
    }

    override fun onCreateOverlayView(): View {
        val canvas = RecordingCanvasView(context, touchRecorder) { count ->
            menuBinding.textPoints.text = "$count pts"
        }
        canvasView = canvas
        return canvas
    }

    override fun onCreate() {
        super.onCreate()
        setOverlayViewVisibility(true)
        touchRecorder.startRecording(scenarioId)
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_pause_resume -> onPauseResumeClicked()
            R.id.btn_stop -> onStopClicked()
            R.id.btn_cancel -> onCancelClicked()
        }
    }

    private fun onPauseResumeClicked() {
        when (touchRecorder.state.value) {
            TouchRecorder.State.RECORDING -> {
                touchRecorder.pauseRecording()
                menuBinding.btnPauseResume.setImageResource(R.drawable.ic_play_arrow)
                menuBinding.statusDot.visibility = View.INVISIBLE
                canvasView?.clearTrails()
                setOverlayViewVisibility(false)
            }
            TouchRecorder.State.PAUSED -> {
                touchRecorder.resumeRecording()
                menuBinding.btnPauseResume.setImageResource(R.drawable.ic_pause)
                menuBinding.statusDot.visibility = View.VISIBLE
                setOverlayViewVisibility(true)
            }
            TouchRecorder.State.IDLE -> Unit
        }
    }

    private fun onStopClicked() {
        val recording = touchRecorder.stopRecording()
        finish()
        onRecordingCompleted(recording)
    }

    private fun onCancelClicked() {
        touchRecorder.cancelRecording()
        finish()
    }
}
