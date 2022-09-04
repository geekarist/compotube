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

    LaunchedEffect(Unit) { // When opening the screen, collect events

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                eventFlow.value = Main.Event.Init
            }

            override fun onDestroy(owner: LifecycleOwner) {
                eventFlow.value = Main.Event.Dispose
                super.onDestroy(owner)
            }
        })

        eventFlow.filterNotNull().collect { event ->
            val change = Main.update(model, event)
            model = change.model
            change.effects.forEach { effect ->
                execute(
                    context,
                    effect,
                    launch = { launcher.launch(it) },
                    dispatch = { eventFlow.value = it }
                )
            }
        }
    }

    Main.View(model = model, dispatch = { event ->
        eventFlow.value = event
    })
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
        is Effect.Log -> log(effect.text, effect.throwable)
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
    Log.d("", "TODO: Save pref")
}

fun loadStrPref(
    context: Context,
    name: String,
    defValue: String?,
    onPrefLoaded: (String?) -> Unit
) {
    Log.d("", "TODO: Load pref")
}

private fun log(text: String, throwable: Throwable?) {
    Log.d("", text, throwable)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

