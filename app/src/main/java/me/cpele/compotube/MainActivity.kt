@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube

// This fixes an error when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.ui.theme.CompotubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            rememberCoroutineScope()
            CompotubeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var model by rememberSaveable { mutableStateOf(Model()) }
                    View(model = model, dispatch = { event ->
                        val change: Change = update(model, event)
                        model = change.model
                        change.effects.forEach { effect ->
                            when (effect) {
                                is Effect.Toast -> toast(effect.text)
                            }
                        }
                    })
                }
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

class Change(val model: Model, vararg val effects: Effect) {
    override fun toString(): String =
        "Change(model=$model, effects=${effects.contentToString()})"
}

sealed class Effect {
    data class Toast(val text: String) : Effect()
}

@Parcelize
data class Model(val query: String = "") : Parcelable

@Composable
fun View(model: Model, dispatch: (Event) -> Unit) {
    Column {
        BasicTextField(value = model.query, onValueChange = {
            dispatch(Event.QueryChanged(it))
        })
        Text(text = "You're looking for: " + model.query)
    }
}

sealed class Event {
    data class QueryChanged(val value: String) : Event()
}

private fun update(model: Model, event: Event): Change =
    when (event) {
        is Event.QueryChanged -> Change(model.copy(query = event.value))
    }
