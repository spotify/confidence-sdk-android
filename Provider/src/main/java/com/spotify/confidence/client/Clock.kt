package com.spotify.confidence.client

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

interface Clock {
    fun currentTime(): Date

    class CalendarBacked private constructor() : Clock {

        companion object {
            fun systemUTC(): Clock = CalendarBacked()
        }

        override fun currentTime(): Date = Calendar.getInstance().apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.time
    }
}