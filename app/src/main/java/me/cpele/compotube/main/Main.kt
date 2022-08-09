@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.main

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.mvu.Change

object Main {

    @Parcelize
    data class Model(val query: String = "") : Parcelable

    @Composable
    fun View(model: Model, dispatch: (Event) -> Unit) {
        Column {
            TextField(value = model.query, onValueChange = {
                dispatch(Event.QueryChanged(it))
            })
            Text(text = "You're looking for: " + model.query)
        }
    }

    sealed class Event {
        data class QueryChanged(val value: String) : Event()
    }

    fun update(model: Model, event: Event): Change<Model> =
        when (event) {
            is Event.QueryChanged -> Change(model.copy(query = event.value))
        }
}

