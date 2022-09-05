@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.kits

import android.accounts.AccountManager
import android.content.Context
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.SearchListResponse
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.ModifierX.focusableWithArrowKeys
import me.cpele.compotube.R
import me.cpele.compotube.mvu.Change
import me.cpele.compotube.mvu.Effect
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
        object Init : Event()
        object LoginRequested : Event()
        data class AppContextReceived(val appContext: Context) : Event()
        data class AccountChosen(val result: ActivityResult) : Event()
        data class QueryChanged(val value: String) : Event()
        data class StrPrefLoaded(val value: String?) : Event()
        object QuerySent : Event()
        data class ResultReceived(val result: SearchListResponse?) : Event()
        object Dispose : Event()
    }

    fun update(model: Model, event: Event): Change<Model> =
        try {
            when (event) {
                is Event.Init ->
                    Change(model, Effect.LoadPref(Main.javaClass.name, null))
                is Event.StrPrefLoaded ->
                    Change(modelFromJsonStr(event.value))
                is Event.LoginRequested ->
                    Change(model, Effect.GetAppContext)
                is Event.AppContextReceived -> {
                    val scopes = listOf(YouTubeScopes.YOUTUBE_READONLY)
                    val backOff = ExponentialBackOff()
                    val appContext = event.appContext
                    val credential = GoogleAccountCredential
                        .usingOAuth2(appContext, scopes)
                        .setBackOff(backOff)
                    val intent = credential.newChooseAccountIntent()
                    Change(model, Effect.ActForResult(intent))
                }
                is Event.AccountChosen -> {
                    val accountName =
                        event.result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    val newModel = model.copy(accountName = accountName)
                    Change(newModel, Effect.Toast("Account chosen: $accountName"))
                }
                is Event.QueryChanged ->
                    Change(
                        model.copy(query = event.value),
                        Effect.Log(
                            tag = javaClass.simpleName,
                            text = "You're looking for ${event.value}"
                        )
                    )
                is Event.QuerySent ->
                    Change(
                        model,
                        Effect.Toast("Query sent: ${model.query}"),
                        Effect.Search(model.query)
                    )
                is Event.ResultReceived -> {
                    val result = event.result
                    val items = result?.items ?: emptyList()
                    Change(
                        model,
                        Effect.Toast("Received ${items.size} results"),
                        Effect.Log(tag = javaClass.simpleName, text = "Received result: $result")
                    )
                }
                Event.Dispose -> {
                    val jsonStr = modelToJsonStr(model)
                    val savePrefEffect = Effect.SavePref(javaClass.name, jsonStr)
                    Change(model, savePrefEffect)
                }
            }
        } catch (t: Throwable) {
            Change(
                model,
                Effect.Toast("Failure handling event $event: $t"),
                Effect.Log(
                    tag = javaClass.simpleName,
                    text = "Failure handling event $event",
                    throwable = t
                )
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

    private fun getStringOrNull(jsonObj: JSONObject, name: String) =
        if (jsonObj.isNull(name)) {
            null
        } else {
            jsonObj.getString(name)
        }
}

