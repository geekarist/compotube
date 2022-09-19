package me.cpele.compotube.sandbox

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Main {
    private val TAG = this::class.simpleName

    @Composable
    fun View() {
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val googleCredential = remember {
            GoogleAccountCredential.usingOAuth2(
                context.applicationContext,
                listOf(YouTubeScopes.YOUTUBE_READONLY)
            )
        }
        val chooseAccountLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { activityResult ->
                Log.d(TAG, "Received account choice: $activityResult")
                activityResult.data
                    ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    ?.let { accountName ->
                        googleCredential.selectedAccountName = accountName
                    }
            }
        )
        val requestPermLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { activityResult ->
                Log.d(TAG, "Received permission response: $activityResult")
            }
        )

        Column(Modifier.padding(16.dp)) {
            Button(onClick = {
                search(
                    coroutineScope,
                    googleCredential,
                    context,
                    chooseAccountLauncher,
                    requestPermLauncher
                )
            }) {
                Text(text = "Search")
            }
        }
    }

    private fun search(
        coroutineScope: CoroutineScope,
        googleCredential: GoogleAccountCredential,
        context: Context,
        chooseAccountLauncher: ActivityResultLauncher<Intent>,
        requestPermLauncher: ManagedActivityResultLauncher<String, Boolean>
    ) = try {
        val query = "hello"
        Log.d(TAG, "Searching for $query")
        val transport = NetHttpTransport()
        val jsonFactory = JacksonFactory()
        val youTube = YouTube
            .Builder(transport, jsonFactory, googleCredential)
            .setApplicationName(context.getString(R.string.app_name))
            .build()

        when {
            !isGooglePlayServicesAvailable(context.applicationContext) -> {
                Toast.makeText(
                    context,
                    "Error: Google Play Services must be installed to use this application. Please install it and try again.",
                    Toast.LENGTH_SHORT
                ).show()
                TODO("Let the user install the application then continue")
            }
            googleCredential.selectedAccountName == null -> {
                val permGetAccounts = Manifest.permission.GET_ACCOUNTS
                val checkResult = context.checkSelfPermission(permGetAccounts)
                val hasPerm = checkResult == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    Toast.makeText(
                        context,
                        "A Google user account must be chosen. Please choose an account",
                        Toast.LENGTH_SHORT
                    ).show()
                    chooseAccountLauncher.launch(googleCredential.newChooseAccountIntent())
                } else {
                    requestPermLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                }
            }
            !isDeviceOnline(context) -> {
                Toast.makeText(
                    context,
                    "Your device is offline. Please connect to the internet then retry.",
                    Toast.LENGTH_SHORT
                ).show()
                TODO("Let the user connect then continue")
            }
            else -> {
                Log.d(
                    TAG,
                    "Requirements are satisfied ⇒ executing search"
                )
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val response = youTube
                            .search()
                            .list("snippet")
                            .setQ(query)
                            .execute()
                        val results = response.items
                        Log.d(
                            TAG,
                            "Found ${results.size} results!"
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Error searching for \"$query\"", t)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error searching for \"$query\": ${t.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Error searching", t)
        Toast.makeText(context, "Error searching: ${t.message}", Toast.LENGTH_SHORT).show()
    }

    private fun isDeviceOnline(context: Context) =
        context.getSystemService<ConnectivityManager>()
            ?.activeNetworkInfo?.isConnected == true

    private fun isGooglePlayServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

