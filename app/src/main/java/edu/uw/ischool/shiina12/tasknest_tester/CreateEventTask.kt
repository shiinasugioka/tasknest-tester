package edu.uw.ischool.shiina12.tasknest_tester

import android.os.AsyncTask
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.EventReminder
import java.io.IOException


class CreateEventTask internal constructor(private var mService: Calendar?) :
    AsyncTask<Void?, Void?, Void?>() {

    @Deprecated("Deprecated in Java")
    override fun doInBackground(vararg params: Void?): Void? {
        addCalendarEvent()
        return null
    }

    private fun addCalendarEvent() {
        val event: Event = Event()
            .setSummary("Google I/O 2015")
            .setLocation("800 Howard St., San Francisco, CA 94103")
            .setDescription("A chance to hear more about Google's developer products.")

        val startDateTime = DateTime("2023-12-10T09:00:00-07:00")

        val start = EventDateTime()
            .setDateTime(startDateTime)
            .setTimeZone("America/Los_Angeles")
        event.setStart(start)

        val endDateTime = DateTime("2023-12-10T17:00:00-07:00")

        val end = EventDateTime()
            .setDateTime(endDateTime)
            .setTimeZone("America/Los_Angeles")
        event.setEnd(end)

        val recurrence = arrayOf("RRULE:FREQ=DAILY;COUNT=2")
        event.setRecurrence(listOf(*recurrence))

        val attendees = arrayOf(
            EventAttendee().setEmail("test@example.com"),
            EventAttendee().setEmail("sbrin@example.com")
        )
        event.setAttendees(listOf(*attendees))

        val reminderOverrides = arrayOf(
            EventReminder().setMethod("email").setMinutes(24 * 60),
            EventReminder().setMethod("popup").setMinutes(10)
        )

        val reminders: Event.Reminders = Event.Reminders()
            .setUseDefault(false)
            .setOverrides(listOf(*reminderOverrides))
        event.setReminders(reminders)

        val calendarId = "primary"

        try {
            mService?.events()?.insert(calendarId, event)?.execute()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}