@file:OptIn(ExperimentalMaterial3Api::class)

package me.cpele.compotube.kits

import android.accounts.AccountManager
import android.os.Parcelable
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
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.cpele.compotube.ModifierX.focusableWithArrowKeys
import me.cpele.compotube.R
import me.cpele.compotube.mvu.Change
import me.cpele.compotube.mvu.Effect

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
        if (model.isLoggedIn) {
            Column(modifier = Modifier.focusable(false)) {
                Box(
                    Modifier
                        .wrapContentHeight()
                ) {
                    Row {
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
        object QuerySent : Event()
        object LoginRequested : Event()
        data class QueryChanged(val value: String) : Event()
        data class AccountChosen(val result: ActivityResult) : Event()
        data class CredentialReceived(val credential: GoogleAccountCredential) : Event()
    }

    fun update(model: Model, event: Event): Change<Model> =
        when (event) {
            is Event.LoginRequested ->
                Change(model, Effect.GetCredential)
            is Event.CredentialReceived -> {
                val intent = event.credential.newChooseAccountIntent()
                Change(model, Effect.ActForResult(intent))
            }
            is Event.AccountChosen -> {
                val accountName = event.result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                val newModel = model.copy(accountName = accountName)
                Change(newModel, Effect.Toast("Account chosen: $accountName"))
            }
            is Event.QueryChanged ->
                Change(
                    model.copy(query = event.value),
                    Effect.Log("You're looking for ${event.value}")
                )
            is Event.QuerySent ->
                Change(model, Effect.Toast("Query sent: ${model.query}"))
        }
}

