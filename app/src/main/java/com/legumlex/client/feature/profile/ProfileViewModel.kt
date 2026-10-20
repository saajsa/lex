package com.legumlex.client.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Ready(val profile: Profile) : ProfileState
    data class Failed(val message: String) : ProfileState
}

/**
 * Verifying a mobile number, in two steps — the number, then the code sent to
 * it on WhatsApp (by SMS on request after 30 seconds). Closed when not shown.
 */
data class VerifyFlow(
    val open: Boolean = false,
    val number: String = "",
    val ref: String? = null,
    val sentTo: String = "",
    val resendIn: Int = 0,
    val canSms: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProfileRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<ProfileState>(
        ProfileHub.profile.value?.let { ProfileState.Ready(it) } ?: ProfileState.Loading
    )
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _verify = MutableStateFlow(VerifyFlow())
    val verify: StateFlow<VerifyFlow> = _verify.asStateFlow()

    /** A note after a change that succeeded ("Your number is verified."). */
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()
    /** Whether [note] reports a failure, so it is drawn as one. */
    private val _noteIsError = MutableStateFlow(false)
    val noteIsError: StateFlow<Boolean> = _noteIsError.asStateFlow()
    private fun fail(msg: String?) { _note.value = msg ?: "The request could not be completed. Please try again."; _noteIsError.value = true }

    private var watch: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.load()
                .onSuccess { ProfileHub.set(it); _state.value = ProfileState.Ready(it) }
                .onFailure { if (_state.value !is ProfileState.Ready) _state.value = ProfileState.Failed(it.message ?: "") }
        }
    }

    fun openVerify() {
        _note.value = null
        // Prefilled with the number on record: verifying it is the usual case.
        val onRecord = (_state.value as? ProfileState.Ready)?.profile?.mobile?.number.orEmpty()
        _verify.value = VerifyFlow(open = true, number = onRecord)
    }

    fun closeVerify() {
        watch?.cancel()
        _verify.value = VerifyFlow()
    }

    fun onNumber(n: String) = _verify.update { it.copy(number = n.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20), error = null) }

    /** Step 1 → 2: send the code. */
    fun sendCode() {
        val number = _verify.value.number
        if (number.count { it.isDigit() } < 10) {
            _verify.update { it.copy(error = "Enter a 10-digit mobile number, or an international one starting with +.") }
            return
        }
        watch?.cancel()
        _verify.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            repo.requestCode(number)
                .onSuccess { (ref, to, resend) ->
                    _verify.update { it.copy(busy = false, ref = ref, sentTo = to, resendIn = resend, canSms = false) }
                    startWatch(ref)
                }
                .onFailure { e -> _verify.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Step 2: the code. A spent code goes back to step 1. */
    fun submitCode(code: String) {
        val ref = _verify.value.ref ?: return
        if (code.length != 6) { _verify.update { it.copy(error = "Enter all 6 digits of the code.") }; return }
        _verify.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.verify(ref, code)
                .onSuccess { m ->
                    closeVerify()
                    updateMobile(m)
                    _noteIsError.value = false; _note.value = "Your number is verified. Codes and case updates now come to it."
                }
                .onFailure { e ->
                    val restart = (e as? StepException)?.error.let { it != null && it != "wrong" }
                    watch?.cancel()
                    _verify.update {
                        if (restart) it.copy(busy = false, ref = null, error = e.message)
                        else it.copy(busy = false, error = e.message)
                    }
                    if (!restart) startWatch(ref)
                }
        }
    }

    fun sendBySms() {
        val ref = _verify.value.ref ?: return
        _verify.update { it.copy(canSms = false) }
        viewModelScope.launch {
            repo.codeBySms(ref)
                .onSuccess { msg -> _verify.update { it.copy(info = msg, error = null) } }
                .onFailure { e -> _verify.update { it.copy(canSms = true, error = e.message) } }
        }
    }

    fun changeNumber() {
        watch?.cancel()
        _verify.update { it.copy(ref = null, error = null, info = null) }
    }

    fun setUpdates(on: Boolean) {
        viewModelScope.launch {
            repo.setUpdates(on).onSuccess { updateMobile(it) }.onFailure { fail(it.message) }
        }
    }

    fun removeNumber() {
        viewModelScope.launch {
            repo.remove()
                .onSuccess { updateMobile(it); _noteIsError.value = false; _note.value = "Code sign-in and WhatsApp updates have stopped. The number remains on your record." }
                .onFailure { fail(it.message) }
        }
    }

    private fun updateMobile(m: Mobile) {
        val cur = (_state.value as? ProfileState.Ready)?.profile ?: return
        val next = cur.copy(mobile = m)
        ProfileHub.set(next)
        _state.value = ProfileState.Ready(next)
    }

    // Counts down to "Send A New Code"; asks every 5 seconds whether the SMS
    // may be offered, until it may or the code is spent.
    private fun startWatch(ref: String) {
        watch?.cancel()
        watch = viewModelScope.launch {
            var polling = true
            var tick = 0
            while (true) {
                delay(1_000)
                tick++
                _verify.update { it.copy(resendIn = (it.resendIn - 1).coerceAtLeast(0)) }
                if (polling && tick % 5 == 0) {
                    repo.codeStatus(ref).onSuccess { (st, canSms) ->
                        if (st != "sent") polling = false
                        if (canSms && _verify.value.info == null) { _verify.update { it.copy(canSms = true) }; polling = false }
                    }
                }
                if (!polling && _verify.value.resendIn == 0) break
            }
        }
    }
}
