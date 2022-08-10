@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.programs

import android.os.Parcelable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                        .focusableWithArrowKeys(),
                    singleLine = true,
                    value = model.query,
                    placeholder = { Text("Search Compotube") },
                    onValueChange = { value ->
                        dispatch(Event.QueryChanged(value))
                    }
                )
                Button(
                    modifier = Modifier
                        .wrapContentSize()
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .focusableWithArrowKeys(),
                    onClick = { dispatch(Event.QuerySent) }) {
                    Text("Submit")
                }
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

