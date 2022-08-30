package me.cpele.compotube

// Explicitly importing getValue and setValue fixes an error
// when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
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
    LaunchedEffect(Unit) { // When opening the screen, collect events
        eventFlow.filterNotNull().collect { event ->
            val change = Main.update(model, event)
            model = change.model
            change.effects.forEach { effect ->
                execute(context, effect, launcher)
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
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    when (effect) {
        is Effect.Toast -> toast(context, effect.text)
        is Effect.Log -> log(effect.text)
        is Effect.ChooseAccount -> chooseAccount(context, launcher)
    }
}

private fun log(text: String) {
    Log.d("", text)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

private fun chooseAccount(
    context: Context,
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
    val backOff = ExponentialBackOff()
    val appContext = context.applicationContext
    val credential = GoogleAccountCredential.usingOAuth2(appContext, scopes).setBackOff(backOff)
    val chooseAccountIntent = credential.newChooseAccountIntent()
    launcher.launch(chooseAccountIntent)
}