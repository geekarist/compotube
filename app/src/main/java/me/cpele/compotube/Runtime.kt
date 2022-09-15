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
import androidx.core.content.ContextCompat
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
import me.cpele.compotube.core.Effect
import me.cpele.compotube.core.Main

interface RuntimeState {

    val dispatch: (Main.Event) -> Unit
    val launch: (Intent) -> Unit
    val youTube: YouTube
    val credential: GoogleAccountCredential
    val context: Context
    val coroutineScope: CoroutineScope
    val lifecycleOwner: LifecycleOwner
    val eventFlow: MutableStateFlow<Main.Event?>
    var model: Main.Model
}

@Composable
fun Runtime() {

    val runtimeState = rememberRuntimeState()

    LaunchedEffect(Unit) {
        setUpLifecycle(
            runtimeState = runtimeState,
            onCreate = Main.Event.LifecycleCreated,
            onDestroy = Main.Event.LifecycleDestroyed
        )
        collectEvents(
            runtimeState = runtimeState
        )
    }

    Main.View(model = runtimeState.model, dispatch = runtimeState.dispatch)
}

suspend fun collectEvents(runtimeState: RuntimeState) {
    runtimeState.eventFlow.filterNotNull().collect { event ->
        handleEvent(
            runtimeState = runtimeState,
            event = event
        )
    }
}

fun setUpLifecycle(
    runtimeState: RuntimeState,
    onCreate: Main.Event,
    onDestroy: Main.Event
) {
    runtimeState.lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            super.onCreate(owner)
            runtimeState.dispatch(onCreate)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            // The event must be handled immediately because on destroy,
            // it won't be collected from the Flow
            handleEvent(
                runtimeState,
                // Ignore new models after destroy
                onDestroy
            )
            super.onDestroy(owner)
        }
    })
}

@Composable
fun rememberRuntimeState(): RuntimeState {
    val context = LocalContext.current.applicationContext
    val runtimeCoroutineScope = rememberCoroutineScope()

    val eventFlow = remember {
        MutableStateFlow<Main.Event?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            eventFlow.value = Main.Event.AccountChosen(result)
        }
    )
    val lifecycleOwner = LocalLifecycleOwner.current

    var model by rememberSaveable { mutableStateOf(Main.Model()) }

    return remember {
        val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
        val backOff = ExponentialBackOff()
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes).setBackOff(backOff)
        val transport = NetHttpTransport()
        val jacksonFactory = JacksonFactory()
        val youTube = YouTube.Builder(transport, jacksonFactory, credential).build()

        object : RuntimeState {
            override val context = context.applicationContext
            override val eventFlow = eventFlow
            override val credential = credential
            override val youTube = youTube
            override val coroutineScope = runtimeCoroutineScope
            override val launch: (Intent) -> Unit = { launcher.launch(it) }
            override val dispatch: (Main.Event) -> Unit = { eventFlow.value = it }
            override val lifecycleOwner = lifecycleOwner
            override var model: Main.Model
                get() = model
                set(value) {
                    model = value
                }
        }
    }
}

private fun handleEvent(
    runtimeState: RuntimeState,
    event: Main.Event
) {
    try {
        val change = Main.update(runtimeState.model, event)
        change.effects.forEach { effect ->
            execute(
                effect = effect,
                platform = runtimeState,
                onNewModel = { runtimeState.model = it })
        }
    } catch (t: Throwable) {
        Toast.makeText(
            runtimeState.context,
            "Failure handling event $event: $t",
            Toast.LENGTH_SHORT
        ).show()
        Log.w("", "Failure handling event $event", t)
    }
}

private fun execute(
    effect: Effect,
    platform: RuntimeState,
    onNewModel: (Main.Model) -> Unit
) = try {
    when (effect) {
        is Effect.Modify<*> -> {
            val newModel = effect.newModel
            if (newModel is Main.Model) {
                onNewModel(newModel)
            } else {
                throw IllegalArgumentException("Invalid effect: must have a model of type ${Main.Model::class.simpleName}")
            }
        }
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
        is Effect.CheckPermission -> checkPermission(
            platform.context,
            effect.permission,
            platform.dispatch
        )
        is Effect.Search -> platform.coroutineScope.launch {
            search(
                platform.youTube,
                effect.query, platform.dispatch
            )
        }
        is Effect.RequestPermission -> TODO("Implement request permission on $effect")
    }
} catch (t: Throwable) {
    Log.w("", "Error executing effect: $effect", t)
}

fun checkPermission(context: Context, permission: String, dispatch: (Main.Event) -> Unit) {
    val applicationContext = context.applicationContext
    val checkResult = ContextCompat.checkSelfPermission(applicationContext, permission)
    return dispatch(Main.Event.PermissionChecked(checkResult))
}

fun selectAccount(credential: GoogleAccountCredential, accountName: String?) {
    credential.selectedAccountName = accountName
}

fun chooseAccount(credential: GoogleAccountCredential, launch: (Intent) -> Unit) {
    val intent = credential.newChooseAccountIntent()
    launch(intent)
}

suspend fun search(youTube: YouTube, query: String, dispatch: (Main.Event) -> Unit) {
    val response = withContext(Dispatchers.IO) {
        youTube.search().list("snippet").setQ(query).execute()
    }
    val responseEvent = withContext(Dispatchers.Default) {
        Main.Event.ResponseReceived.Response(response.map {
            Main.Event.ResponseReceived.Result
        })
    }
    withContext(Dispatchers.Main) {
        dispatch(Main.Event.ResponseReceived(responseEvent))
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

