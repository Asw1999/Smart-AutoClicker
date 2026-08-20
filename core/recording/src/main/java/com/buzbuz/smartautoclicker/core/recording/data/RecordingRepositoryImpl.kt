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
package com.buzbuz.smartautoclicker.core.recording.data

import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.domain.RecordingRepository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val recordingDao: RecordingDao,
    @Dispatcher(HiltCoroutineDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : RecordingRepository {

    override fun getRecordings(scenarioId: Long): Flow<List<Recording>> =
        recordingDao.getRecordingsForScenario(scenarioId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getRecording(id: Long): Recording? = withContext(ioDispatcher) {
        val entity = recordingDao.getRecordingById(id) ?: return@withContext null
        val events = recordingDao.getTouchEvents(id)
        entity.toDomain(events)
    }

    override suspend fun saveRecording(recording: Recording): Long = withContext(ioDispatcher) {
        val entity = recording.toEntity()
        val eventEntities = recording.touchEvents.map { it.toEntity() }
        recordingDao.insertCompleteRecording(entity, eventEntities)
    }

    override suspend fun deleteRecording(id: Long): Unit = withContext(ioDispatcher) {
        recordingDao.deleteRecording(id)
    }

    override suspend fun deleteRecordingsForScenario(scenarioId: Long): Unit = withContext(ioDispatcher) {
        recordingDao.deleteRecordingsForScenario(scenarioId)
    }
}
