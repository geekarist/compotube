@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube

// This fixes an error when using `by rememberSaveable { mutableStateOf(...) }`
// See https://stackoverflow.com/a/63877349
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.cpele.compotube.kits.Main
import me.cpele.compotube.mvu.Effect
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
                    var model by rememberSaveable { mutableStateOf(Main.Model()) }
                    Main.View(model = model, dispatch = { event ->
                        val change = Main.update(model, event)
                        model = change.model
                        change.effects.forEach { effect ->
                            when (effect) {
                                is Effect.Toast -> toast(effect.text)
                                is Effect.Log -> log(effect.text)
                            }
                        }
                    })
                }
            }
        }
    }

    private fun log(text: String) {
        Log.d(javaClass.simpleName, text)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
