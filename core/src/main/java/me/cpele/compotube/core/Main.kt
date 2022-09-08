@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.core

import android.accounts.AccountManager
import android.os.Parcelable
import android.util.Log
import android.view.KeyEvent
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.core.ModifierX.focusableWithArrowKeys
import org.json.JSONObject

object Main {

    @Parcelize
    data class Model(
        val query: String = "",
        val accountName: String? = null
    ) : Parcelable {
        @IgnoredOnParcel
        val isLoggedIn: Boolean = accountName != null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun View(model: Model, dispatch: (Event) -> Unit) {
        if (model.isLoggedIn.also { Log.d("", "Logged in? $it") }) {
            Log.d("", "Account name: ${model.accountName}")
            Column(modifier = Modifier.focusable(false)) {
                Box(
                    Modifier
                        .wrapContentHeight()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextField(
                            modifier = Modifier
                                .weight(weight = 1f, fill = true)
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
                        val accountName = model.accountName
                            ?: throw IllegalStateException("Missing account name")
                        Text(
                            modifier = Modifier.wrapContentWidth(),
                            text = accountName
                        )
                    }
                }
            }
        } else {
            Box {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val appName = stringResource(id = R.string.app_name)
                    Text(text = "To use $appName you need to be logged in")
                    Button(onClick = { dispatch(Event.LoginRequested) }) {
                        Text("Login")
                    }
                }
            }
        }
    }

    sealed class Event {

        object LifecycleCreated : Event()

        data class StrPrefLoaded(val value: String?) : Event()

        object LoginRequested : Event()

        data class AccountChosen(val result: ActivityResult) : Event()

        data class QueryChanged(val value: String) : Event()

        object QuerySent : Event()

        data class ResponseReceived(val response: Response?) : Event() {
            object Result
            data class Response(
                val items: List<Result>
            )
        }

        object LifecycleDestroyed : Event()
    }

    fun update(model: Model, event: Event): Change<Model> =
        try {
            when (event) {
                Event.LifecycleCreated -> loadPref(model)
                is Event.StrPrefLoaded -> Change(modelFromJsonStr(event.value))
                Event.LoginRequested -> Change(model, Effect.ChooseAccount)
                is Event.AccountChosen -> updateAccount(model, event)
                is Event.QueryChanged -> updateQuery(model, event)
                Event.QuerySent -> search(model)
                is Event.ResponseReceived -> updateResults(model, event)
                Event.LifecycleDestroyed -> savePref(model)
            }
        } catch (t: Throwable) {
            fail(model, event, t)
        }

    private fun fail(
        model: Model,
        event: Event,
        t: Throwable
    ) = Change(
        model,
        Effect.Toast("Failure handling event $event: $t"),
        Effect.Log(
            tag = javaClass.simpleName,
            text = "Failure handling event $event",
            throwable = t
        )
    )

    private fun savePref(model: Model): Change<Model> {
        val jsonStr = modelToJsonStr(model)
        val savePrefEffect = Effect.SavePref(javaClass.name, jsonStr)
        return Change(model, savePrefEffect)
    }

    private fun updateResults(
        model: Model,
        event: Event.ResponseReceived
    ): Change<Model> {
        val result = event.response
        val items = result?.items ?: emptyList()
        return Change(
            model,
            Effect.Toast("Received ${items.size} results"),
            Effect.Log(tag = javaClass.simpleName, text = "Received result: $result")
        )
    }

    private fun search(model: Model) = Change(
        model,
        Effect.Toast("Query sent: ${model.query}"),
        Effect.Search(model.query)
    )

    private fun updateQuery(
        model: Model,
        event: Event.QueryChanged
    ) = Change(
        model.copy(query = event.value),
        Effect.Log(
            tag = javaClass.simpleName,
            text = "You're looking for ${event.value}"
        )
    )

    private fun loadPref(model: Model) =
        Change(model, Effect.LoadPref(Main.javaClass.name, null))

    private fun updateAccount(
        model: Model,
        event: Event.AccountChosen
    ): Change<Model> {
        val accountName =
            event.result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val newModel = model.copy(accountName = accountName)
        return Change(
            newModel,
            Effect.Toast("Account chosen: $accountName"),
            Effect.SelectAccount(accountName)
        )
    }

    private fun modelToJsonStr(model: Model): String =
        JSONObject().apply {
            put("accountName", model.accountName)
            put("query", model.query)
        }.toString()

    private fun modelFromJsonStr(value: String?): Model =
        value?.let {
            val jsonObj = try {
                JSONObject(value)
            } catch (t: Throwable) {
                throw IllegalArgumentException("Error parsing JSON: $value", t)
            }
            Model(
                query = jsonObj.optString("query"),
                accountName = getStringOrNull(jsonObj, "accountName")
            )
        } ?: Model()

    private fun getStringOrNull(
        jsonObj: JSONObject,
        @Suppress("SameParameterValue")
        name: String
    ) = if (jsonObj.isNull(name)) {
        null
    } else {
        jsonObj.getString(name)
    }
}

