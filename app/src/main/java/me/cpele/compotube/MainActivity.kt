package me.cpele.compotube

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import me.cpele.compotube.ui.theme.CompotubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompotubeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var model: Model = rememberSaveable { Model() }
                    var dispatch: (Event) -> Unit = { }
                    dispatch = { event ->
                        val change: Change = update(model, event)
                        model = change.model
                        change.effects.forEach { effect ->
                            when (effect) {
                                is Effect.Toast -> toast(effect.text)
                            }
                        }
                    }
                    View(model = model, dispatch = dispatch)
                }
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun update(model: Model, event: Event): Change =
        when (event) {
            is Event.QueryChanged -> Change(model.copy(query = event.value))
        }
}

class Change(val model: Model, vararg val effects: Effect) {
    override fun toString(): String =
        "Change(model=$model, effects=${effects.contentToString()})"
}

sealed class Effect {
    data class Toast(val text: String) : Effect()
}

data class Model(val query: String = "")

@Composable
fun View(model: Model, dispatch: (Event) -> Unit) {
    BasicTextField(value = model.query, onValueChange = { dispatch(Event.QueryChanged(it)) })
    Text(text = "You're looking for: " + model.query)
}

sealed class Event {
    data class QueryChanged(val value: String) : Event()
}