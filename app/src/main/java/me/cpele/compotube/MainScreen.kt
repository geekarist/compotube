package me.cpele.compotube

// Explicitly importing getValue and setValue
// (`androidx.compose.runtime.*`) fixes an error
// when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import me.cpele.compotube.kits.Main
import me.cpele.compotube.mvu.Effect

@Composable
fun MainScreen() {

    val eventFlow = remember {
        MutableStateFlow<Main.Event?>(null)
    }
    val contract = ActivityResultContracts.StartActivityForResult()
    val launcher = rememberLauncherForActivityResult(contract = contract) { result ->
        eventFlow.value = Main.Event.AccountChosen(result)
    }
    var model by rememberSaveable { mutableStateOf(Main.Model()) }
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val credential = remember {
        val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
        val backOff = ExponentialBackOff()
        GoogleAccountCredential.usingOAuth2(context, scopes).setBackOff(backOff)
    }
    val youTube = remember {
        val transport = NetHttpTransport()
        val jacksonFactory = JacksonFactory()
        YouTube.Builder(transport, jacksonFactory, credential).build()
    }

    LaunchedEffect(Unit) {

        // Setup model init/dispose
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                eventFlow.value = Main.Event.LifecycleCreated
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // The event must be handled immediately because on destroy,
                // it won't be collected from the Flow
                handleEvent(
                    context,
                    model,
                    Main.Event.LifecycleDestroyed,
                    credential,
                    youTube,
                    onNewModel = {}, // Won't change the model
                    launchIntent = {}, // Won't launch intent
                    dispatch = {} // Won't dispatch
                )
                super.onDestroy(owner)
            }
        })

        // Start collecting events
        eventFlow.filterNotNull().collect { event ->
            handleEvent(
                context, model, event, credential, youTube,
                onNewModel = { newModel: Main.Model -> model = newModel },
                launchIntent = { launcher.launch(it) },
                dispatch = { eventFlow.value = it }
            )
        }
    }

    Main.View(model = model, dispatch = { event ->
        eventFlow.value = event
    })
}

private fun handleEvent(
    context: Context,
    model: Main.Model,
    event: Main.Event,
    credential: GoogleAccountCredential,
    youTube: YouTube,
    onNewModel: (Main.Model) -> Unit,
    launchIntent: (Intent) -> Unit,
    dispatch: (Main.Event) -> Unit
) {
    val change = Main.update(model, event)
    onNewModel(change.model)
    change.effects.forEach { effect ->
        execute(
            context,
            effect,
            credential,
            youTube,
            launch = launchIntent,
            dispatch = dispatch
        )
    }
}

private fun execute(
    context: Context,
    effect: Effect,
    credential: GoogleAccountCredential,
    youTube: YouTube,
    launch: (Intent) -> Unit,
    dispatch: (Main.Event) -> Unit
) {
    when (effect) {
        is Effect.LoadPref -> loadStrPref(
            context = context,
            name = effect.name,
            defValue = effect.defValue,
            onPrefLoaded = { dispatch(Main.Event.StrPrefLoaded(it)) }
        )
        is Effect.Toast -> toast(context, effect.text)
        is Effect.Log -> log(effect.tag, effect.text, effect.throwable)
        is Effect.Search -> search(youTube, effect.query, dispatch)
        is Effect.SavePref -> saveStrPref(
            context = context,
            name = effect.name,
            value = effect.value
        )
        Effect.ChooseAccount -> chooseAccount(credential, launch)
        is Effect.SelectAccount -> handleAccountName(credential, effect.accountName)
    }
}

fun handleAccountName(credential: GoogleAccountCredential, accountName: String?) {
    credential.selectedAccountName = accountName
}

fun chooseAccount(credential: GoogleAccountCredential, launch: (Intent) -> Unit) {
    val intent = credential.newChooseAccountIntent()
    launch(intent)
}

fun search(youTube: YouTube, query: String, dispatch: (Main.Event) -> Unit) {
    val result = youTube.search().list("TODO").setQ(query).execute()
    dispatch(Main.Event.ResultReceived(result))
}

fun saveStrPref(context: Context, name: String, value: String) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit().putString(name, value).apply()
}

fun loadStrPref(
    context: Context,
    name: String,
    defValue: String?,
    onPrefLoaded: (String?) -> Unit
) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val value = prefs.getString(name, defValue)
    onPrefLoaded(value)
}

private fun log(tag: String, text: String, throwable: Throwable?) {
    Log.d(tag, text, throwable)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

