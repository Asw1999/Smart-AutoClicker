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

import android.graphics.PointF
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/**
 * A recorded sequence of touch events that can be replayed.
 *
 * @param id the unique identifier for this recording.
 * @param scenarioId the database id of the scenario this recording belongs to.
 * @param name user-visible name for the recording.
 * @param touchEvents ordered list of touch events in the recording.
 * @param durationMs total duration of the recording in milliseconds.
 * @param fingerCount maximum number of simultaneous fingers used.
 * @param createdAt timestamp when the recording was created.
 */
data class Recording(
    val id: Identifier = Identifier(0, asTemporary = true),
    val scenarioId: Long,
    val name: String,
    val touchEvents: List<TouchEvent> = emptyList(),
    val durationMs: Long = 0L,
    val fingerCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A single touch interaction point within a recording.
 *
 * @param pointerIndex finger identifier (0-9 for multi-touch).
 * @param action the type of touch action (DOWN, MOVE, UP).
 * @param position screen coordinates (x, y).
 * @param pressure touch pressure from 0.0 to 1.0.
 * @param timestampMs elapsed time in milliseconds since the start of the recording.
 */
data class TouchEvent(
    val pointerIndex: Int,
    val action: TouchAction,
    val position: PointF,
    val pressure: Float = 1.0f,
    val timestampMs: Long,
)

/**
 * Action type for a [TouchEvent].
 */
enum class TouchAction {
    DOWN,
    MOVE,
    UP,
}

/**
 * Parameters controlling how a [Recording] is replayed.
 *
 * @param speedMultiplier playback speed factor (1.0 = normal, 2.0 = 2x speed, 0.5 = half speed).
 * @param repeatCount number of times to replay the recording.
 * @param delayBetweenRepeatMs pause between consecutive replay iterations.
 * @param randomizePositionPx maximum random pixel offset applied to touch coordinates (anti-detection).
 * @param randomizeTimingMs maximum random millisecond offset applied to event intervals.
 */
data class ReplayParams(
    val speedMultiplier: Float = 1.0f,
    val repeatCount: Int = 1,
    val delayBetweenRepeatMs: Long = 0L,
    val randomizePositionPx: Int = 0,
    val randomizeTimingMs: Int = 0,
)
