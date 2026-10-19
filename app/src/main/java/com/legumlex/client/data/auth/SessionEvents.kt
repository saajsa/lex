package com.legumlex.client.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// The server refused the stored sign-in (it expired, or the contact signed
// out elsewhere). AuthInterceptor reports it here; the app shell forgets the
// sign-in and returns to the sign-in screen, which shows `notice` once.
object SessionEvents {
    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    val notice = MutableStateFlow<String?>(null)

    fun end() {
        notice.value = "Your session has ended. Please sign in again."
        _ended.tryEmit(Unit)
    }
}
