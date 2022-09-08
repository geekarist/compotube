package me.cpele.compotube

// Explicitly importing getValue and setValue
// (`androidx.compose.runtime.*`) fixes an error
// when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.cpele.compotube.kits.Main
import me.cpele.compotube.mvu.Effect

interface Platform {

    val dispatch: (Main.Event) -> Unit
    val launch: (Intent) -> Unit
    val youTube: YouTube
    val credential: GoogleAccountCredential
    val context: Context
    val coroutineScope: CoroutineScope
}

@Composable
fun Runtime() {

    val eventFlow = remember {
        MutableStateFlow<Main.Event?>(null)
    }
    val contract = ActivityResultContracts.StartActivityForResult()
    val launcher = rememberLauncherForActivityResult(contract = contract) { result ->
        eventFlow.value = Main.Event.AccountChosen(result)
    }
    val context = LocalContext.current.applicationContext
    val runtimeCoroutineScope = rememberCoroutineScope()

    val platform = remember {
        val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
        val backOff = ExponentialBackOff()
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes).setBackOff(backOff)
        val transport = NetHttpTransport()
        val jacksonFactory = JacksonFactory()
        val youTube = YouTube.Builder(transport, jacksonFactory, credential).build()

        object : Platform {
            override val context = context.applicationContext
            override val credential = credential
            override val youTube = youTube
            override val coroutineScope = runtimeCoroutineScope
            override val launch: (Intent) -> Unit = { launcher.launch(it) }
            override val dispatch: (Main.Event) -> Unit = { eventFlow.value = it }
        }
    }

    var model by rememberSaveable { mutableStateOf(Main.Model()) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    platform,
                    model,
                    onNewModel = {}, // Ignore new models after destroy
                    Main.Event.LifecycleDestroyed
                )
                super.onDestroy(owner)
            }
        })

        // Start collecting events
        eventFlow.filterNotNull().collect { event ->
            handleEvent(
                platform = platform,
                model = model,
                onNewModel = { model = it },
                event = event
            )
        }
    }

    Main.View(model = model, dispatch = platform.dispatch)
}

private fun handleEvent(
    platform: Platform,
    model: Main.Model,
    onNewModel: (Main.Model) -> Unit,
    event: Main.Event
) {
    val change = Main.update(
        model,
        event
    )
    onNewModel(change.model)
    change.effects.forEach { effect ->
        execute(effect, platform)
    }
}

private fun execute(effect: Effect, platform: Platform) {

    when (effect) {
        is Effect.Toast -> toast(
            platform.context,
            effect.text
        )
        is Effect.Log -> log(effect.tag, effect.text, effect.throwable)
        is Effect.LoadPref -> loadStrPref(
            context = platform.context,
            name = effect.name,
            defValue = effect.defValue,
            onPrefLoaded = {
                platform.dispatch
                (Main.Event.StrPrefLoaded(it))
            }
        )
        is Effect.SavePref -> saveStrPref(
            context = platform.context,
            name = effect.name,
            value = effect.value
        )
        Effect.ChooseAccount -> chooseAccount(
            platform.credential,
            platform.launch
        )
        is Effect.SelectAccount -> selectAccount(platform.credential, effect.accountName)
        is Effect.Search -> platform.coroutineScope.launch {
            search(
                platform.youTube,
                effect.query, platform.dispatch
            )
        }
    }
}

fun selectAccount(credential: GoogleAccountCredential, accountName: String?) {
    credential.selectedAccountName = accountName
}

fun chooseAccount(credential: GoogleAccountCredential, launch: (Intent) -> Unit) {
    val intent = credential.newChooseAccountIntent()
    launch(intent)
}

suspend fun search(youTube: YouTube, query: String, dispatch: (Main.Event) -> Unit) {
    val result = withContext(Dispatchers.IO) {
        youTube.search().list("TODO").setQ(query).execute()
    }
    withContext(Dispatchers.Main) {
        dispatch(Main.Event.ResultReceived(result))
    }
}

fun saveStrPref(context: Context, name: String, value: String) {
    val prefs = getDefaultSharedPrefs(context)
    prefs.edit().putString(name, value).apply()
}

fun loadStrPref(
    context: Context,
    name: String,
    defValue: String?,
    onPrefLoaded: (String?) -> Unit
) {
    val prefs = getDefaultSharedPrefs(context)
    val value = prefs.getString(name, defValue)
    onPrefLoaded(value)
}

private fun getDefaultSharedPrefs(context: Context): SharedPreferences {
    val name = context.packageName + "_preferences"
    val mode = Context.MODE_PRIVATE
    return context.getSharedPreferences(name, mode)
}

private fun log(tag: String, text: String, throwable: Throwable?) {
    Log.d(tag, text, throwable)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
