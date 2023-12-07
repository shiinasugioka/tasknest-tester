package edu.uw.ischool.shiina12.tasknest_tester

import android.os.AsyncTask
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.Events

const val TAG = "ApiAsyncTask"

class ApiAsyncTask internal constructor(private val mActivity: MainActivity) :
    AsyncTask<Void?, Void?, Void?>() {

    override fun doInBackground(vararg params: Void?): Void? {
        try {
            mActivity.clearResultsText()
            mActivity.updateResultsText(dataFromApi)
        } catch (availabilityException: GooglePlayServicesAvailabilityIOException) {
            mActivity.showGooglePlayServicesAvailabilityErrorDialog(
                availabilityException.connectionStatusCode
            )
        } catch (userRecoverableException: UserRecoverableAuthIOException) {
            mActivity.startActivityForResult(
                userRecoverableException.intent,
                MainActivity.REQUEST_AUTHORIZATION
            )
        } catch (e: Exception) {
            mActivity.updateStatus(
                """
                    The following error occurred:
                    ${e.message}
                    """.trimIndent()
            )
        }
        if (mActivity.mProgress?.isShowing == true) {
            mActivity.mProgress!!.dismiss()
        }
        return null
    }

    private val dataFromApi: List<String>
        get() {
            // List the next 10 events from the primary calendar.
            val eventStrings: MutableList<String> = ArrayList()
            val events: Events? = mActivity.mService!!.events().list("primary")
                .setMaxResults(10)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()
            val items: MutableList<Event>? = events?.items
            Log.i(TAG, "items: $items")
            if (items != null) {
                for (event in items) {
                    val start: DateTime = event.start.dateTime
                    eventStrings.add(
                        java.lang.String.format("%s (%s)", event.summary, start)
                    )
                }
            }
            return eventStrings
        }

}