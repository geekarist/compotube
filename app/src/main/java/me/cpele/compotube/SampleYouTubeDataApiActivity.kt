@file:Suppress("DEPRECATION")

package me.cpele.compotube

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import java.io.IOException

@Suppress("DEPRECATION", "ConvertToStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty")
@SuppressLint("SetTextI18n")
class SampleYouTubeDataApiActivity : Activity(), PermissionCallbacks {
    private var mCredential: GoogleAccountCredential? = null
    private var mOutputText: TextView? = null
    private var mCallApiButton: Button? = null
    var mProgress: ProgressDialog? = null

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityLayout = LinearLayout(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        activityLayout.layoutParams = lp
        activityLayout.orientation = LinearLayout.VERTICAL
        activityLayout.setPadding(16, 16, 16, 16)
        val tlp = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mCallApiButton = Button(this)
        mCallApiButton!!.text = BUTTON_TEXT
        mCallApiButton!!.setOnClickListener {
            mCallApiButton!!.isEnabled = false
            mOutputText!!.text = ""
            resultsFromApi
            mCallApiButton!!.isEnabled = true
        }
        activityLayout.addView(mCallApiButton)
        mOutputText = TextView(this)
        mOutputText!!.layoutParams = tlp
        mOutputText!!.setPadding(16, 16, 16, 16)
        mOutputText!!.isVerticalScrollBarEnabled = true
        mOutputText!!.movementMethod = ScrollingMovementMethod()
        mOutputText!!.text = "Click the \'" + BUTTON_TEXT + "\' button to test the API."
        activityLayout.addView(mOutputText)
        mProgress = ProgressDialog(this)
        mProgress!!.setMessage("Calling YouTube Data API ...")
        setContentView(activityLayout)

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
            applicationContext, listOf(*SCOPES)
        )
            .setBackOff(ExponentialBackOff())
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private val resultsFromApi: Unit
        get() {
            if (!isGooglePlayServicesAvailable) {
                acquireGooglePlayServices()
            } else if (mCredential!!.selectedAccountName == null) {
                chooseAccount()
            } else if (!isDeviceOnline) {
                mOutputText!!.text = "No network connection available."
            } else {
                MakeRequestTask(mCredential).execute()
            }
        }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private fun chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS
            )
        ) {
            val accountName = getPreferences(MODE_PRIVATE)
                .getString(PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                mCredential!!.selectedAccountName = accountName
                resultsFromApi
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                    mCredential!!.newChooseAccountIntent(),
                    REQUEST_ACCOUNT_PICKER
                )
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                this,
                "This app needs to access your Google account (via Contacts).",
                REQUEST_PERMISSION_GET_ACCOUNTS,
                Manifest.permission.GET_ACCOUNTS
            )
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     * activity result.
     * @param data Intent (containing result data) returned by incoming
     * activity result.
     */
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, data: Intent
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOOGLE_PLAY_SERVICES -> if (resultCode != RESULT_OK) {
                mOutputText!!.text = "This app requires Google Play Services. Please install " +
                        "Google Play Services on your device and relaunch this app."
            } else {
                resultsFromApi
            }
            REQUEST_ACCOUNT_PICKER -> if (resultCode == RESULT_OK && data.extras != null) {
                val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (accountName != null) {
                    val settings = getPreferences(MODE_PRIVATE)
                    val editor = settings.edit()
                    editor.putString(PREF_ACCOUNT_NAME, accountName)
                    editor.apply()
                    mCredential!!.selectedAccountName = accountName
                    resultsFromApi
                }
            }
            REQUEST_AUTHORIZATION -> if (resultCode == RESULT_OK) {
                resultsFromApi
            }
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     * requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     * which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(
            requestCode, permissions, grantResults, this
        )
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     * permission
     * @param list The requested permission list. Never null.
     */
    override fun onPermissionsGranted(requestCode: Int, list: List<String>) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     * permission
     * @param list The requested permission list. Never null.
     */
    override fun onPermissionsDenied(requestCode: Int, list: List<String>) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private val isDeviceOnline: Boolean
        get() {
            val connMgr = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connMgr.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private val isGooglePlayServicesAvailable: Boolean
        get() {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
            return connectionStatusCode == ConnectionResult.SUCCESS
        }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private fun acquireGooglePlayServices() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     * Google Play Services on this device.
     */
    fun showGooglePlayServicesAvailabilityErrorDialog(
        connectionStatusCode: Int
    ) {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val dialog = apiAvailability.getErrorDialog(
            this@SampleYouTubeDataApiActivity,
            connectionStatusCode,
            REQUEST_GOOGLE_PLAY_SERVICES
        )
        dialog!!.show()
    }

    /**
     * An asynchronous task that handles the YouTube Data API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    @SuppressLint("StaticFieldLeak")
    private inner class MakeRequestTask(credential: GoogleAccountCredential?) :
        AsyncTask<Void?, Void?, List<String?>?>() {
        private var mService: YouTube? = null
        private var mLastError: Exception? = null

        init {
            val transport = NetHttpTransport()
            val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
            mService = YouTube.Builder(
                transport, jsonFactory, credential
            )
                .setApplicationName("YouTube Data API Android Quickstart")
                .build()
        }

        /**
         * Background task to call YouTube Data API.
         * @param params no parameters needed for this task.
         */
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): List<String?>? {
            return try {
                dataFromApi
            } catch (e: Exception) {
                mLastError = e
                cancel(true)
                null
            }
        }// Get a list of up to 10 files.

        /**
         * Fetch information about the "GoogleDevelopers" YouTube channel.
         * @return List of Strings containing information about the channel.
         * @throws IOException
         */
        @get:Throws(IOException::class)
        private val dataFromApi: List<String?>
            get() {
                // Get a list of up to 10 files.
                val channelInfo: MutableList<String?> = ArrayList()
                val result = mService!!.channels().list("snippet,contentDetails,statistics")
                    .setForUsername("GoogleDevelopers")
                    .execute()
                val channels = result.items
                if (channels != null) {
                    val channel = channels[0]
                    channelInfo.add(
                        "This channel's ID is " + channel.id + ". " +
                                "Its title is '" + channel.snippet.title + ", " +
                                "and it has " + channel.statistics.viewCount + " views."
                    )
                }
                return channelInfo
            }

        @Deprecated("Deprecated in Java")
        override fun onPreExecute() {
            mOutputText!!.text = ""
            mProgress!!.show()
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(output: List<String?>?) {
            mProgress!!.hide()
            if (output == null || output.isEmpty()) {
                mOutputText!!.text = "No results returned."
            } else {
                val mutableOutput: MutableList<String?>? = output as? MutableList<String?>?
                mutableOutput?.add(0, "Data retrieved using the YouTube Data API:")
                mOutputText!!.text = TextUtils.join("\n", output)
            }
        }

        @Deprecated("Deprecated in Java")
        @SuppressLint("SetTextI18n")
        override fun onCancelled() {
            mProgress!!.hide()
            if (mLastError != null) {
                when (mLastError) {
                    is GooglePlayServicesAvailabilityIOException -> {
                        showGooglePlayServicesAvailabilityErrorDialog(
                            (mLastError as GooglePlayServicesAvailabilityIOException)
                                .connectionStatusCode
                        )
                    }
                    is UserRecoverableAuthIOException -> {
                        startActivityForResult(
                            (mLastError as UserRecoverableAuthIOException).intent,
                            REQUEST_AUTHORIZATION
                        )
                    }
                    else -> {
                        mOutputText!!.text = ("The following error occurred:\n"
                                + mLastError!!.message)
                    }
                }
            } else {
                mOutputText!!.text = "Request cancelled."
            }
        }
    }

    companion object {
        const val REQUEST_ACCOUNT_PICKER = 1000
        const val REQUEST_AUTHORIZATION = 1001
        const val REQUEST_GOOGLE_PLAY_SERVICES = 1002
        const val REQUEST_PERMISSION_GET_ACCOUNTS = 1003
        private const val BUTTON_TEXT = "Call YouTube Data API"
        private const val PREF_ACCOUNT_NAME = "accountName"
        private val SCOPES = arrayOf(YouTubeScopes.YOUTUBE_READONLY)
    }
}