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
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

/**
 * Action that replays a recorded touch sequence.
 *
 * @param id the unique identifier of this action.
 * @param eventId the identifier of the event this action belongs to.
 * @param name the user-defined name of this action.
 * @param priority the execution order of this action.
 * @param recordingId database ID of the recording to play.
 * @param replaySpeed playback speed multiplier (1.0 = normal).
 * @param replayRepeat number of times to repeat the recording.
 * @param replayDelayMs pause between repetitions in milliseconds.
 * @param replayRandomizePx maximum random pixel offset for coordinates.
 * @param replayRandomizeTimingMs maximum random millisecond offset for timing.
 */
data class PlayRecording(
    override val id: Identifier = Identifier(0, asTemporary = true),
    override val eventId: Identifier = Identifier(0, asTemporary = true),
    override val name: String? = null,
    override var priority: Int = 0,
    val recordingId: Long? = null,
    val replaySpeed: Float = 1.0f,
    val replayRepeat: Int = 1,
    val replayDelayMs: Long = 0L,
    val replayRandomizePx: Int = 0,
    val replayRandomizeTimingMs: Int = 0,
) : Action() {

    override fun isComplete(): Boolean =
        super.isComplete() && recordingId != null && recordingId > 0

    override fun hashCodeNoIds(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + priority
        result = 31 * result + (recordingId?.hashCode() ?: 0)
        result = 31 * result + replaySpeed.hashCode()
        result = 31 * result + replayRepeat
        result = 31 * result + replayDelayMs.hashCode()
        result = 31 * result + replayRandomizePx
        result = 31 * result + replayRandomizeTimingMs
        return result
    }

    override fun deepCopy(): Action = copy()
}
