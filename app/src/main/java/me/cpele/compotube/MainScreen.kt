package me.cpele.compotube

// Explicitly importing getValue and setValue fixes an error
// when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import me.cpele.compotube.kits.Main
import me.cpele.compotube.mvu.Effect

@Composable
fun MainScreen() {
    var model by rememberSaveable { mutableStateOf(Main.Model()) }
    val context = LocalContext.current.applicationContext
    Main.View(model = model, dispatch = { event ->
        val change = Main.update(model, event)
        model = change.model
        change.effects.forEach { effect ->
            execute(context, effect)
        }
    })
}

private fun execute(context: Context, effect: Effect) {
    when (effect) {
        is Effect.Toast -> toast(context, effect.text)
        is Effect.Log -> log(effect.text)
    }
}

private fun log(text: String) {
    Log.d("", text)
}

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

@Composable
private fun ChooseAccount(dispatch: (Main.Event) -> Unit) {
    val contract = ActivityResultContracts.StartActivityForResult()
    val launcher = rememberLauncherForActivityResult(contract = contract) { result ->
        dispatch(Main.Event.AccountChosen(result))
    }
    val context = LocalContext.current.applicationContext
    val chooseAccountIntent = rememberSaveable {
        val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
        val backOff = ExponentialBackOff()
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes).setBackOff(backOff)
        credential.newChooseAccountIntent()
    }
    launcher.launch(chooseAccountIntent)
}