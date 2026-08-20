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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Room entity representing a saved recording session.
 */
@Entity(tableName = "recording_table")
@Serializable
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "scenario_id", index = true)
    val scenarioId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "finger_count")
    val fingerCount: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * Room entity representing a single recorded touch point.
 */
@Entity(
    tableName = "recorded_touch_table",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recording_id")]
)
@Serializable
data class RecordedTouchEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "recording_id")
    val recordingId: Long,
    @ColumnInfo(name = "pointer_index")
    val pointerIndex: Int,
    @ColumnInfo(name = "touch_action")
    val touchAction: Int,
    @ColumnInfo(name = "x")
    val x: Float,
    @ColumnInfo(name = "y")
    val y: Float,
    @ColumnInfo(name = "pressure")
    val pressure: Float,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
)

/**
 * Room DAO for recording and touch event tables.
 */
@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTouchEvents(events: List<RecordedTouchEntity>)

    @Transaction
    suspend fun insertCompleteRecording(
        recording: RecordingEntity,
        events: List<RecordedTouchEntity>,
    ): Long {
        val recordingId = insertRecording(recording)
        val eventsWithId = events.map { it.copy(recordingId = recordingId) }
        insertTouchEvents(eventsWithId)
        return recordingId
    }

    @Query("SELECT * FROM recording_table WHERE scenario_id = :scenarioId ORDER BY created_at DESC")
    fun getRecordingsForScenario(scenarioId: Long): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recording_table WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recorded_touch_table WHERE recording_id = :recordingId ORDER BY timestamp_ms ASC")
    suspend fun getTouchEvents(recordingId: Long): List<RecordedTouchEntity>

    @Query("DELETE FROM recording_table WHERE id = :id")
    suspend fun deleteRecording(id: Long)

    @Query("DELETE FROM recording_table WHERE scenario_id = :scenarioId")
    suspend fun deleteRecordingsForScenario(scenarioId: Long)

    @Query("SELECT COUNT(*) FROM recording_table WHERE scenario_id = :scenarioId")
    suspend fun getRecordingCountForScenario(scenarioId: Long): Int
}
