package me.cpele.compotube

// Explicitly importing getValue and setValue fixes an error
// when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.content.Context
import android.content.Intent
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

    LaunchedEffect(Unit) {

        // Setup model init/dispose
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                eventFlow.value = Main.Event.Init
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // The event must be handled immediately because on destroy,
                // it won't be collected from the Flow
                handleEvent(
                    context,
                    model,
                    event = Main.Event.Dispose,
                    onNewModel = {}, // Won't change the model
                    launchIntent = {}, // Won't launch intent
                    dispatch = {}) // Won't dispatch
                super.onDestroy(owner)
            }
        })

        // Start collecting events
        eventFlow.filterNotNull().collect { event ->
            handleEvent(
                context, model, event,
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
            launch = launchIntent,
            dispatch = dispatch
        )
    }
}

private fun execute(
    context: Context,
    effect: Effect,
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
        Effect.GetAppContext -> dispatch(Main.Event.AppContextReceived(context.applicationContext))
        is Effect.ActForResult -> launch(effect.intent)
        is Effect.SavePref -> saveStrPref(
            context = context,
            name = effect.name,
            value = effect.value
        )
    }
}

fun saveStrPref(context: Context, name: String, value: String) {
    Log.d("chrp", "TODO: Save pref")
}

fun loadStrPref(
    context: Context,
    name: String,
    defValue: String?,
    onPrefLoaded: (String?) -> Unit
) {
    Log.d("chrp", "TODO: load pref")
}

private fun log(tag: String, text: String, throwable: Throwable?) {
    Log.d(tag, text, throwable)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

