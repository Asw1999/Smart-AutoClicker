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

import android.graphics.PointF
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.recording.domain.Recording
import com.buzbuz.smartautoclicker.core.recording.domain.TouchAction
import com.buzbuz.smartautoclicker.core.recording.domain.TouchEvent

fun RecordingEntity.toDomain(events: List<RecordedTouchEntity> = emptyList()): Recording =
    Recording(
        id = Identifier(databaseId = id),
        scenarioId = scenarioId,
        name = name,
        touchEvents = events.map { it.toDomain() },
        durationMs = durationMs,
        fingerCount = fingerCount,
        createdAt = createdAt,
    )

fun Recording.toEntity(): RecordingEntity =
    RecordingEntity(
        id = id.databaseId,
        scenarioId = scenarioId,
        name = name,
        durationMs = durationMs,
        fingerCount = fingerCount,
        createdAt = createdAt,
    )

fun RecordedTouchEntity.toDomain(): TouchEvent =
    TouchEvent(
        pointerIndex = pointerIndex,
        action = when (touchAction) {
            0 -> TouchAction.DOWN
            1 -> TouchAction.MOVE
            2 -> TouchAction.UP
            else -> TouchAction.DOWN
        },
        position = PointF(x, y),
        pressure = pressure,
        timestampMs = timestampMs,
    )

fun TouchEvent.toEntity(recordingId: Long = 0): RecordedTouchEntity =
    RecordedTouchEntity(
        recordingId = recordingId,
        pointerIndex = pointerIndex,
        touchAction = when (action) {
            TouchAction.DOWN -> 0
            TouchAction.MOVE -> 1
            TouchAction.UP -> 2
        },
        x = position.x,
        y = position.y,
        pressure = pressure,
        timestampMs = timestampMs,
    )
