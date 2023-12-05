package edu.uw.ischool.shiina12.tasknest_tester

import android.app.ProgressDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import edu.uw.ischool.shiina12.tasknest_tester.databinding.ActivityGetEventFragmentBinding
import edu.uw.ischool.shiina12.tasknest_tester.model.GetEventModel
import edu.uw.ischool.shiina12.tasknest_tester.util.Constants
import edu.uw.ischool.shiina12.tasknest_tester.util.executeAsyncTask
import kotlinx.coroutines.cancel
import pub.devrel.easypermissions.EasyPermissions
import java.io.IOException

const val TAG = "GetEventFragment"

class GetEventFragment : Fragment() {

//    private var _binding: FragmentGetEventBinding? = null
//    private val binding get() = _binding

    private var _binding: ActivityGetEventFragmentBinding? = null
    private val binding get() = _binding!!

    private var mCredential: GoogleAccountCredential? = null  // to access our account
    private var mService: Calendar? = null  // to access the calendar

    private var mProgress: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initCredentials()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ActivityGetEventFragmentBinding.inflate(inflater, container, false)
        initView()

        return binding.root
    }

    private fun initView() {
        mProgress = ProgressDialog(requireContext())
        mProgress!!.setMessage("Loading...")

        with(binding) {
            btnCalendar.setOnClickListener {
                btnCalendar.isEnabled = false
                txtOut.text = ""
                getResultsFromAPI()
                btnCalendar.isEnabled = true
            }
        }
    }

    /**
     * Define the account
     */
    private fun initCredentials() {
        mCredential = GoogleAccountCredential.usingOAuth2(
            requireContext(),
            arrayListOf(CalendarScopes.CALENDAR)
        )
            .setBackOff(ExponentialBackOff())

        initCalendarBuild(mCredential)
    }

    /**
     * Define the Calendar based on the account
     */
    private fun initCalendarBuild(credential: GoogleAccountCredential?) {
        val transport = AndroidHttp.newCompatibleTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        mService = Calendar.Builder(
            transport, jsonFactory, credential
        )
            .setApplicationName(Constants.APPLICATION_NAME)
            .build()
    }

    /**
     * Performs the necessary checks and operations until successfully
     * retrieving data from the calendar.
     * 1) Check if the device supports Google Play Service
     * 2) Check if there is a Google account logged into the application
     * 3) Check if device is connected to internet
     * 4) If all conditions are met, throw the Get request
     **/
    private fun getResultsFromAPI() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices()
        } else if (mCredential!!.selectedAccountName == null) {
            chooseAccount()
        } else if (!isDeviceOnline()) {
            binding.txtOut.text = "No network connection available."
        } else {
            makeRequestTask()
        }
    }

    private fun acquireGooglePlayServices() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(requireContext())
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
        }
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(
            this,
            connectionStatusCode,
            Constants.REQUEST_GOOGLE_PLAY_SERVICES
        )
        dialog?.show()
    }

    private fun chooseAccount() {
        if (EasyPermissions.hasPermissions(
                requireContext(), android.Manifest.permission.GET_ACCOUNTS
            )
        ) {
            val accountName = this.activity?.getPreferences(Context.MODE_PRIVATE)
                ?.getString(Constants.PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                mCredential!!.selectedAccountName = accountName
                getResultsFromAPI()
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                    mCredential!!.newChooseAccountIntent(),
                    Constants.REQUEST_ACCOUNT_PICKER
                )
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                this,
                "This app needs to access your Google account.",
                Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                android.Manifest.permission.GET_ACCOUNTS
            )
        }
    }

    private fun getDataFromCalendar(): MutableList<GetEventModel> {
        val now = DateTime(System.currentTimeMillis())
        val eventStrings = ArrayList<GetEventModel>()
        try {
            val events = mService!!.events().list("primary")
                .setMaxResults(10)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute()

            val items = events.items

            for (event in items) {
                var start = event.start.dateTime
                if (start == null) {
                    start = event.start.date
                }

                eventStrings.add(
                    GetEventModel(
                        summary = event.summary,
                        startDate = start.toString()
                    )
                )
            }

            return eventStrings

        } catch (e: IOException) {
            Log.d(TAG, e.message.toString())
        }

        return eventStrings
    }

    private fun makeRequestTask() {
        var mLastError: Exception? = null

        lifecycleScope.executeAsyncTask(
            // show our progress design
            onStart = {
                mProgress!!.show()
            },
            // get data list from the calendar
            doInBackground = {
                try {
                    getDataFromCalendar()
                } catch (e: Exception) {
                    mLastError = e
                    lifecycleScope.cancel()
                    null
                }
            },
            // get the Post data
            onPostExecute = { output ->
                mProgress!!.hide()
                if (output == null || output.size == 0) {
                    Log.d(TAG, "post output is null")
                } else {
                    for (i in 0 until output.size) {
                        binding.txtOut.text = (TextUtils.join("\n", output))
                        Log.d(
                            TAG,
                            output[i].id.toString() + " " + output[i].summary + " " + output[i].startDate
                        )
                    }
                }
            },
            // checks in case operations fail
            onCancelled = {
                mProgress!!.hide()
                if (mLastError != null) {
                    if (mLastError is GooglePlayServicesAvailabilityIOException) {
                        showGooglePlayServicesAvailabilityErrorDialog(
                            (mLastError as GooglePlayServicesAvailabilityIOException)
                                .connectionStatusCode
                        )
                    } else if (mLastError is UserRecoverableAuthIOException) {
                        this.startActivityForResult(
                            (mLastError as UserRecoverableAuthIOException).intent,
                            Constants.REQUEST_AUTHORIZATION
                        )
                    } else {
                        binding.txtOut.text =
                            "The following error ocurred:\n" + mLastError!!.message
                    }
                } else {
                    binding.txtOut.text = "Request cancelled."
                }
            }
        )
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(requireContext())
        return connectionStatusCode == ConnectionResult.SUCCESS
    }

    private fun isDeviceOnline(): Boolean {
        val connMgr =
            this.activity?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return (networkInfo != null) && networkInfo.isConnected
    }


}