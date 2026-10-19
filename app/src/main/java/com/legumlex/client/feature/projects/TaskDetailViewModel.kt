package com.legumlex.client.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface TaskDetailUiState {
    data object Loading : TaskDetailUiState
    data class Ready(val task: TaskDetail) : TaskDetailUiState
    data class Error(val message: String) : TaskDetailUiState
}

class TaskDetailViewModel(
    app: Application,
    val taskId: String,
    val canComment: Boolean = true,
) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<TaskDetailUiState>(TaskDetailUiState.Loading)
    val state: StateFlow<TaskDetailUiState> = _state.asStateFlow()

    private val _posting = MutableStateFlow(false)
    val posting: StateFlow<Boolean> = _posting.asStateFlow()

    /** One-shot error for comment posting; screen clears it after showing. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = TaskDetailUiState.Loading
            repo.taskDetail(taskId)
                .onSuccess { _state.value = TaskDetailUiState.Ready(it) }
                .onFailure { e -> _state.value = TaskDetailUiState.Error(loadErr(e)) }
        }
    }

    /** Post a client comment; on success append it in place (no reload flicker). */
    fun postComment(content: String) {
        val text = content.trim()
        if (text.isEmpty() || _posting.value) return
        viewModelScope.launch {
            _posting.value = true
            repo.postTaskComment(taskId, text)
                .onSuccess { added ->
                    _state.update { cur ->
                        if (cur is TaskDetailUiState.Ready)
                            TaskDetailUiState.Ready(cur.task.copy(comments = cur.task.comments + added))
                        else cur
                    }
                }
                .onFailure { _actionError.value = "Couldn't post your comment. Try again." }
            _posting.value = false
        }
    }

    fun clearActionError() { _actionError.value = null }

    private fun loadErr(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403    -> "Your session expired. Sign in again."
            404         -> "This task is no longer available."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn't load this task (HTTP ${e.code()})."
        }
        else -> "Couldn't reach the server. Check your connection."
    }
}
