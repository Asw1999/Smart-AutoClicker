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
package com.buzbuz.smartautoclicker.feature.recordingconfig.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.domain.RecordingRepository
import com.buzbuz.smartautoclicker.core.recording.domain.ReplayParams
import com.buzbuz.smartautoclicker.core.recording.replay.ReplayEngine

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingListViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val replayEngine: ReplayEngine,
) : ViewModel() {

    private var currentScenarioId: Long = 0L

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    fun loadRecordings(scenarioId: Long) {
        currentScenarioId = scenarioId
        viewModelScope.launch {
            recordingRepository.getRecordings(scenarioId)
                .collect { list -> _recordings.value = list }
        }
    }

    fun saveRecording(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.saveRecording(recording)
        }
    }

    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(id)
        }
    }

    fun testReplay(recording: Recording, params: ReplayParams = ReplayParams()) {
        viewModelScope.launch {
            _isReplaying.value = true
            try {
                val fullRecording = recordingRepository.getRecording(recording.id.databaseId) ?: recording
                replayEngine.replay(fullRecording, params)
            } finally {
                _isReplaying.value = false
            }
        }
    }
}
