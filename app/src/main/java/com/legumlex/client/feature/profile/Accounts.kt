package com.legumlex.client.feature.profile

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.MainActivity
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.ui.components.WizardDialog
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardTextField
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

/**
 * One person, several client accounts (owner, 1 Nov 2026), as the portal's
 * account menu has it: a client in their own name who is also the contact of
 * their companies moves between those accounts here. The firm marks which
 * contacts are the same person; the server lists them (v1/accounts) and asks
 * for one code to the person's mobile unless this sign-in already proved
 * them (a sign-in by code does).
 */
data class PersonAccount(val id: Int, val label: String, val kind: String, val current: Boolean)

/** A code sent to move to [target]: its reference, where it went, and whether SMS may be asked for. */
data class SwitchCode(val ref: String, val target: PersonAccount, val sentTo: String, val canSms: Boolean = false)

data class AccountsState(
    val accounts: List<PersonAccount> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val code: SwitchCode? = null,
    /** The new account is in place: the app starts again in it. */
    val switched: Boolean = false,
)

class AccountsViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val auth = AuthRepository(api, TokenStore(app), app.cacheDir)
    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val d = api.accounts()["data"] as? Map<String, Any?> ?: emptyMap()
                (d["accounts"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map {
                    PersonAccount(
                        id = (it["id"] as? Number)?.toInt() ?: 0,
                        label = it["label"] as? String ?: "",
                        kind = it["kind"] as? String ?: "",
                        current = it["current"] == true,
                    )
                }.filter { it.id > 0 }
            }.onSuccess { list -> _state.value = _state.value.copy(accounts = list) }
            // An older server has no accounts list: nothing to show.
        }
    }

    /** Move to [a]: at once when this sign-in is proved, else a code to the person's mobile. */
    fun choose(a: PersonAccount) {
        if (a.current || _state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            runCatching { api.accountSwitch(a.id) }
                .onSuccess { r ->
                    @Suppress("UNCHECKED_CAST")
                    val d = r["data"] as? Map<String, Any?> ?: emptyMap()
                    if (d["code"] == true) {
                        _state.value = _state.value.copy(busy = false,
                            code = SwitchCode(d["ref"] as? String ?: "", a, d["to"] as? String ?: "your mobile"))
                        pollSms()
                    } else {
                        done(d)
                    }
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = message(it)) }
        }
    }

    fun submit(code: String) {
        val c = _state.value.code ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { api.accountSwitchStep("verify", c.ref, to = c.target.id, code = code) }
                .onSuccess { r ->
                    @Suppress("UNCHECKED_CAST")
                    done(r["data"] as? Map<String, Any?> ?: emptyMap())
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = message(it)) }
        }
    }

    fun sms() {
        val c = _state.value.code ?: return
        viewModelScope.launch {
            runCatching { api.accountSwitchStep("sms", c.ref) }
                .onSuccess { _state.value = _state.value.copy(info = it["message"] as? String ?: "The code is on its way by SMS.", code = c.copy(canSms = false)) }
                .onFailure { _state.value = _state.value.copy(error = message(it)) }
        }
    }

    fun dismiss() { _state.value = _state.value.copy(code = null, error = null, info = null, busy = false) }

    /** Whether "Send By SMS Instead" may be offered yet: the server says so after a minute. */
    private fun pollSms() {
        viewModelScope.launch {
            repeat(12) {
                kotlinx.coroutines.delay(10_000)
                val c = _state.value.code ?: return@launch
                if (c.canSms) return@launch
                runCatching { api.accountSwitchStep("status", c.ref) }.onSuccess { r ->
                    @Suppress("UNCHECKED_CAST")
                    val d = r["data"] as? Map<String, Any?> ?: emptyMap()
                    if (d["can_sms"] == true) _state.value.code?.let { _state.value = _state.value.copy(code = it.copy(canSms = true)) }
                }
            }
        }
    }

    private suspend fun done(d: Map<String, Any?>) {
        val token = d["token"] as? String
        val contact = ((d["contact"] as? Map<*, *>)?.get("id") as? Number)?.toInt()
        val client = ((d["client"] as? Map<*, *>)?.get("id") as? Number)?.toInt()
        if (token.isNullOrBlank() || contact == null || client == null) {
            _state.value = _state.value.copy(busy = false, error = "The account could not be opened. Please try again.")
            return
        }
        auth.switchedTo(token, client, contact)
        _state.value = _state.value.copy(busy = false, code = null, switched = true)
    }

    private fun message(e: Throwable): String = when (e) {
        is HttpException -> runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "The request could not be completed (error ${e.code()})."
        is java.io.IOException -> "Network error. Please try again."
        else -> e.message ?: "The request could not be completed. Please try again."
    }
}

/**
 * The account switch beside the bell (owner, 1 Nov 2026): shown only to a
 * person the firm has given more than one account; its menu lists them, the
 * open one ticked. A code, when one is needed, is asked in a wizard dialog.
 */
@Composable
fun AccountSwitch(vm: AccountsViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(s.switched) {
        // Everything is read again for the account now open: the app starts afresh in it.
        if (s.switched) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
    LaunchedEffect(s.error, s.code) {
        // A switch refused before any code was sent: said here, the menu being closed.
        if (s.error != null && s.code == null) android.widget.Toast.makeText(ctx, s.error, android.widget.Toast.LENGTH_LONG).show()
    }
    if (s.accounts.size < 2) return

    Box {
        IconButton(onClick = { open = true }, enabled = !s.busy) {
            Icon(Icons.Outlined.SwitchAccount, contentDescription = "Switch Account", tint = Lex.colors.secondaryForeground)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Lex.colors.card) {
            Text("Switch Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = Lex.colors.mutedForeground, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            s.accounts.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(a.label, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                            Text(a.kind, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                        }
                    },
                    trailingIcon = if (a.current) ({ Icon(Icons.Outlined.Check, contentDescription = "Open now", tint = Lex.colors.foreground) }) else null,
                    enabled = !a.current,
                    onClick = { open = false; vm.choose(a) },
                )
            }
        }
    }

    s.code?.let { c -> WizardDialog(onDismiss = vm::dismiss) { SwitchCodeStep(c, s, vm) } }
}

@Composable
private fun SwitchCodeStep(c: SwitchCode, s: AccountsState, vm: AccountsViewModel) {
    var code by remember(c.ref) { mutableStateOf("") }
    WizardFrame(
        title = "Switch Account",
        steps = listOf("Code"),
        step = 0,
        ready = code.length == 6,
        saving = s.busy,
        saveLabel = "Open Account",
        savingLabel = "Opening…",
        nextLabel = "Open Account",
        error = s.error,
        onClose = vm::dismiss,
        onStep = { },
        onSave = { vm.submit(code) },
        onNext = null,
    ) {
        WizardHeading(
            "Open ${c.target.label}",
            "To keep each account's matters private, we have sent a code to your mobile ${c.sentTo} on WhatsApp. Please enter the 6 digits.",
        )
        WizardTextField(
            "6-Digit Code", code,
            {
                code = it.filter(Char::isDigit).take(6)
                if (code.length == 6 && !s.busy) vm.submit(code)
            },
            enabled = !s.busy, keyboardType = KeyboardType.NumberPassword,
        )
        s.info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.success.softText)
        }
        if (c.canSms) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = vm::sms) { Text("Send By SMS Instead", color = Lex.colors.foreground) }
        }
    }
}
