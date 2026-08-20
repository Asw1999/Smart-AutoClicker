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
package com.buzbuz.smartautoclicker.core.recording.domain

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing [Recording] persistence.
 */
interface RecordingRepository {

    /**
     * Get all recordings for a given scenario as a Flow.
     */
    fun getRecordings(scenarioId: Long): Flow<List<Recording>>

    /**
     * Get a recording by its database ID, including all its touch events.
     */
    suspend fun getRecording(id: Long): Recording?

    /**
     * Save a complete recording with all its touch events.
     * @return the assigned database ID.
     */
    suspend fun saveRecording(recording: Recording): Long

    /**
     * Delete a recording and all its associated touch events.
     */
    suspend fun deleteRecording(id: Long)

    /**
     * Delete all recordings belonging to a scenario.
     */
    suspend fun deleteRecordingsForScenario(scenarioId: Long)
}
