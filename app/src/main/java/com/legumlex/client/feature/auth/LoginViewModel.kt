package com.legumlex.client.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.LoginResult
import com.legumlex.client.data.auth.SignInException
import com.legumlex.client.data.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle    : LoginState
    data object Loading : LoginState
    data class Success(val result: LoginResult) : LoginState
    data class Error(val message: String) : LoginState
}

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository(
        api    = ApiClient.get(app),
        tokens = TokenStore(app),
    )

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

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
