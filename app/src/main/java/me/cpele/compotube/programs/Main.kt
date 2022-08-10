@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.programs

import android.os.Parcelable
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.ModifierX.focusableWithArrowKeys
import me.cpele.compotube.mvu.Change
import me.cpele.compotube.mvu.Effect

object Main {

    @Parcelize
    data class Model(val query: String = "") : Parcelable

    @Composable
    fun View(model: Model, dispatch: (Event) -> Unit) {
        Column(modifier = Modifier.focusable(false)) {
            Box(
                Modifier
                    .wrapContentHeight()
            ) {
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusableWithArrowKeys()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                                dispatch(Event.QuerySent)
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    value = model.query,
                    placeholder = { Text("Search Compotube") },
                    onValueChange = { value ->
                        dispatch(Event.QueryChanged(value))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        dispatch(Event.QuerySent)
                    })
                )
            }
        }
    }

    sealed class Event {
        object QuerySent : Event()
        data class QueryChanged(val value: String) : Event()
    }

    fun update(model: Model, event: Event): Change<Model> =
        when (event) {
            is Event.QueryChanged -> Change(
                model.copy(query = event.value),
                Effect.Log("You're looking for ${event.value}")
            )
            is Event.QuerySent -> Change(model, Effect.Toast("Query sent: ${model.query}"))
        }
}

