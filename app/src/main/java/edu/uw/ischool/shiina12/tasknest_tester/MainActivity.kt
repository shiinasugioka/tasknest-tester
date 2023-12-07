package edu.uw.ischool.shiina12.tasknest_tester

import android.accounts.AccountManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import edu.uw.ischool.shiina12.tasknest_tester.old_files.TAG


class MainActivity : AppCompatActivity() {

    private var mCredential: GoogleAccountCredential? = null
    var mService: Calendar? = null
    var mProgress: ProgressDialog? = null

    private lateinit var eventButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultsText: TextView

    companion object {
        const val REQUEST_ACCOUNT_PICKER = 1000
        const val REQUEST_AUTHORIZATION = 1001
        const val REQUEST_GOOGLE_PLAY_SERVICES = 1002
        const val PREF_ACCOUNT_NAME = "tasknest_cal_tester"
        const val APPLICATION_NAME = "TaskNest Calendar Tester"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        eventButton = findViewById(R.id.create_event_button)
        statusText = findViewById(R.id.status_text)
        resultsText = findViewById(R.id.results_text)

        // Initialize credentials and service object
        initCredentials()

        // Button Events
        eventButton.setOnClickListener {
            addCalendarEvent()
        }

    }

    private fun initCredentials() {
        mCredential = GoogleAccountCredential.usingOAuth2(
            applicationContext,
            arrayListOf(CalendarScopes.CALENDAR)
        )
            .setBackOff(ExponentialBackOff())

        initCalendarBuild(mCredential)
    }

    private fun initCalendarBuild(credential: GoogleAccountCredential?) {
        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        mService = Calendar.Builder(
            transport, jsonFactory, credential
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun addCalendarEvent() {
        CreateEventTask(mService).execute()
    }


    override fun onResume() {
        super.onResume()
        if (isGooglePlayServicesAvailable()) {
            refreshResults()
        } else {
            statusText.text =
                "Google Play Services required: after installing, close and relaunch this app."

        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(applicationContext)
        return connectionStatusCode == ConnectionResult.SUCCESS
    }

    private fun refreshResults() {
        if (mCredential!!.selectedAccountName == null) {
            chooseAccount()
        } else if (!isDeviceOnline()) {
            statusText.text = "No network connection available."
        }

        mProgress?.show()
        ApiAsyncTask(this).execute()
    }

    private fun chooseAccount() {
        startActivityForResult(
            mCredential!!.newChooseAccountIntent(),
            REQUEST_ACCOUNT_PICKER
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOOGLE_PLAY_SERVICES -> if (resultCode != RESULT_OK) {
                isGooglePlayServicesAvailable()
            }

            REQUEST_ACCOUNT_PICKER -> if (data != null) {
                if (resultCode == RESULT_OK && data.extras != null) {
                    val accountName: String? = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    mCredential?.setSelectedAccountName(accountName)
                    val settings = getPreferences(MODE_PRIVATE)
                    val editor = settings.edit()
                    editor.putString(PREF_ACCOUNT_NAME, accountName)
                    editor.apply()
                } else if (resultCode == RESULT_CANCELED) {
                    statusText.text = "Account unspecified."
                }
            }

            REQUEST_AUTHORIZATION -> if (resultCode != RESULT_OK) {
                chooseAccount()
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun isDeviceOnline(): Boolean {
        Log.d(TAG, "check for device online called")
        val connMgr =
            this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return (networkInfo != null) && networkInfo.isConnected
    }

    fun clearResultsText() {
        runOnUiThread {
            statusText.text = "Retrieving data…"
            resultsText.text = ""
        }
    }

    fun updateResultsText(dataStrings: List<String?>?) {
        runOnUiThread {
            if (dataStrings == null) {
                statusText.text = "Error retrieving data!"
            } else if (dataStrings.isEmpty()) {
                statusText.text = "No data found."
            } else {
                statusText.text = "Data retrieved using the Google Calendar API:"
                resultsText.text = TextUtils.join("\n\n", dataStrings)
            }
        }
    }

    fun updateStatus(message: String?) {
        runOnUiThread { statusText.text = message }
    }

    fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(
            this,
            connectionStatusCode,
            REQUEST_GOOGLE_PLAY_SERVICES
        )
        dialog?.show()
    }


}