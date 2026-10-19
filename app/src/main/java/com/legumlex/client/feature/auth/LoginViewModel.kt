package com.legumlex.client.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.LoginResult
import com.legumlex.client.data.auth.SessionEvents
import com.legumlex.client.data.auth.SignInException
import com.legumlex.client.data.auth.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle    : LoginState
    data object Loading : LoginState
    data class Success(val result: LoginResult) : LoginState
    data class Error(val message: String) : LoginState
}

/** The sign-in screen's three views, as on the web door. */
enum class LoginView { Choose, Mobile, Email }

/**
 * Sign-in by mobile, in two steps: the number, then the code. `ref` names
 * the code sent; `canSms` offers "Send by SMS instead" (the server allows it
 * 30 seconds after the WhatsApp); `resendIn` counts down to a new code.
 */
data class MobileState(
    val number: String = "",
    val ref: String? = null,
    val sentNote: String = "",
    val canSms: Boolean = false,
    val resendIn: Int = 0,
    val info: String? = null,
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(
        api    = ApiClient.get(app),
        tokens = TokenStore(app),
    )

    // A session the server ended opens the screen with its note.
    private val _state = MutableStateFlow<LoginState>(
        SessionEvents.notice.value?.let { LoginState.Error(it) } ?: LoginState.Idle
    )
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _view = MutableStateFlow(LoginView.Choose)
    val view: StateFlow<LoginView> = _view.asStateFlow()

    private val _mobile = MutableStateFlow(MobileState())
    val mobile: StateFlow<MobileState> = _mobile.asStateFlow()

    private var watch: Job? = null

    init { SessionEvents.notice.value = null }

    fun show(view: LoginView) {
        if (_state.value is LoginState.Loading) return
        _view.value = view
        clearError()
        if (view == LoginView.Mobile) changeNumber() else stopWatch()
    }

    fun onNumberChange(number: String) {
        _mobile.update { it.copy(number = number.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20)) }
    }

    /** Step 1: send the code. A number not verified for sign-in gets the server's alert. */
    fun onSendCode() {
        val number = _mobile.value.number
        if (number.count { it.isDigit() } < 10) {
            _state.value = LoginState.Error("Enter your 10-digit mobile number.")
            return
        }
        stopWatch()
        viewModelScope.launch {
            _state.value = LoginState.Loading
            repo.requestCode(number)
                .onSuccess { sent ->
                    _state.value = LoginState.Idle
                    _mobile.update {
                        it.copy(ref = sent.ref, canSms = false, info = null, resendIn = sent.resendAfter,
                            sentNote = listOf(sent.message, "Enter the 6-digit code sent to ${sent.to}.")
                                .filter { s -> s.isNotBlank() }.joinToString(" "))
                    }
                    startWatch(sent.ref)
                }
                .onFailure { e -> _state.value = LoginState.Error(e.message ?: "The code could not be sent.") }
        }
    }

    /** Step 2: the code. Six digits sign in; a dead code goes back to step 1. */
    fun onCode(code: String) {
        val ref = _mobile.value.ref ?: return
        if (code.length != 6) {
            _state.value = LoginState.Error("Enter all 6 digits of the code.")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Loading
            repo.signInWithCode(ref, code)
                .onSuccess { stopWatch() }
                .onFailure { e ->
                    if ((e as? SignInException)?.error in setOf<String?>("locked", "expired", "used")) changeNumber()
                }
                .handleResult()
        }
    }

    fun onSendBySms() {
        val ref = _mobile.value.ref ?: return
        viewModelScope.launch {
            _mobile.update { it.copy(canSms = false) }
            repo.codeBySms(ref)
                .onSuccess { note ->
                    clearError()
                    _mobile.update { it.copy(info = note) }
                }
                .onFailure { e ->
                    _mobile.update { it.copy(canSms = true) }
                    _state.value = LoginState.Error(e.message ?: "The SMS could not be sent.")
                }
        }
    }

    /** Back to step 1, keeping the number typed. */
    fun changeNumber() {
        stopWatch()
        _mobile.update { MobileState(number = it.number) }
    }

    // Counts down to "Send a new code", and asks the server every 5 seconds
    // whether the SMS may be offered, until it may or the code is spent.
    private fun startWatch(ref: String) {
        watch = viewModelScope.launch {
            var polling = true
            var tick = 0
            while (true) {
                delay(1_000)
                tick++
                _mobile.update { it.copy(resendIn = (it.resendIn - 1).coerceAtLeast(0)) }
                if (polling && tick % 5 == 0) {
                    repo.codeStatus(ref).onSuccess { st ->
                        if (st.state != "sent") polling = false
                        if (st.canSms && _mobile.value.info == null) {
                            _mobile.update { it.copy(canSms = true) }
                            polling = false
                        }
                    }
                }
                if (!polling && _mobile.value.resendIn == 0) break
            }
        }
    }

    private fun stopWatch() {
        watch?.cancel()
        watch = null
    }

    /** Called by LoginScreen once Credential Manager returns a Google ID token. */
    fun onIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            repo.signInWithGoogle(idToken).handleResult()
        }
    }

    /** Called by LoginScreen when the user submits the email/password form. */
    fun onEmailLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = LoginState.Error("Enter your email and password.")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Loading
            repo.signInWithEmail(email, password).handleResult()
        }
    }

    private fun Result<LoginResult>.handleResult() {
        onSuccess { _state.value = LoginState.Success(it) }
        onFailure { e ->
            _state.value = LoginState.Error(
                (e as? SignInException)?.message ?: e.message ?: "Sign-in failed"
            )
        }
    }

    /** Called by LoginScreen if user cancelled the Google sheet. */
    fun onCancelled() {
        if (_state.value is LoginState.Loading) _state.value = LoginState.Idle
    }

    /** Called by LoginScreen for credential-manager errors before the API call. */
    fun onCredentialError(message: String) {
        _state.value = LoginState.Error(message)
    }

    fun clearError() {
        if (_state.value is LoginState.Error) _state.value = LoginState.Idle
    }
}
