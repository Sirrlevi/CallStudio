package com.callstudio.app.ui

import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.data.callStatus
import com.callstudio.app.scheduling.ScheduleCoordinator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val whenFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatWhen(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(whenFormat)

fun ScheduleCoordinator.Problem.message(): String = when (this) {
    ScheduleCoordinator.Problem.NOT_POSITIVE -> "Enter a number greater than zero."
    ScheduleCoordinator.Problem.TOO_FAR -> "Choose a time within the next year."
    ScheduleCoordinator.Problem.IN_THE_PAST -> "That time has already passed. Pick a later time."
    ScheduleCoordinator.Problem.DUPLICATE -> "This caller already has a call at about that time."
}

fun CallEventEntity.statusLabel(): String = when (callStatus()) {
    CallStatus.ENDED -> {
        val a = answeredAtMillis
        val e = endedAtMillis
        if (a != null && e != null) "Answered · ${TimeFormat.elapsed(e - a)}" else "Answered"
    }
    CallStatus.REJECTED -> "Declined"
    CallStatus.MISSED -> "Missed"
    CallStatus.CANCELLED -> "Cancelled"
    CallStatus.ERROR -> "Couldn't connect"
    CallStatus.SCHEDULED -> "Scheduled"
    CallStatus.RINGING -> "Ringing"
    CallStatus.ACTIVE -> "In progress"
}
