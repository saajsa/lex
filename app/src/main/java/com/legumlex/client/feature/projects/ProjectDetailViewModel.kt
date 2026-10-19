package com.legumlex.client.feature.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.File

sealed interface ProjectDetailUiState {
    data object Loading : ProjectDetailUiState
    data class Ready(
        val detail: ProjectDetail,
        val tasks: List<TaskSummary>,
        val milestones: List<Milestone>,
        val files: List<ProjectFile>,
        val activity: List<ProjectActivityItem>,
        val discussions: List<DiscussionSummary>,
    ) : ProjectDetailUiState
    data class Error(val message: String) : ProjectDetailUiState
}

class ProjectDetailViewModel(
    app: Application,
    val projectId: String,
) : AndroidViewModel(app) {
    private val repo = ProjectsRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
    val state: StateFlow<ProjectDetailUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** True while a create/upload/post action is running. */
    private val _actionInFlight = MutableStateFlow(false)
    val actionInFlight: StateFlow<Boolean> = _actionInFlight.asStateFlow()

    /** One-shot snackbar messages (success or failure). */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    /** Inline discussion thread the user expanded, with comments. */
    private val _openDiscussion = MutableStateFlow<DiscussionDetail?>(null)
    val openDiscussion: StateFlow<DiscussionDetail?> = _openDiscussion.asStateFlow()
    private val _discussionLoading = MutableStateFlow(false)
    val discussionLoading: StateFlow<Boolean> = _discussionLoading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ProjectDetailUiState.Loading
            fetch(preserveCurrent = false)
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            fetch(preserveCurrent = true)
            _isRefreshing.value = false
        }
    }

    private suspend fun fetch(preserveCurrent: Boolean) {
        try {
            coroutineScope {
                val detail = repo.detail(projectId).getOrThrow()

                // Skip child fetches for tabs the project itself disabled.
                val tasksDeferred       = async { if (detail.settings.viewTasks)       repo.tasks(projectId).getOrNull().orEmpty()       else emptyList() }
                val milestonesDeferred  = async { if (detail.settings.viewMilestones)  repo.milestones(projectId).getOrNull().orEmpty()  else emptyList() }
                val filesDeferred        = async { if (detail.settings.viewFiles)       repo.files(projectId).getOrNull().orEmpty()       else emptyList() }
                val activityDeferred    = async { if (detail.settings.viewActivityLog) repo.activity(projectId).getOrNull().orEmpty()    else emptyList() }
                val discussionsDeferred = async { if (detail.settings.viewDiscussions) repo.discussions(projectId).getOrNull().orEmpty() else emptyList() }

                _state.value = ProjectDetailUiState.Ready(
                    detail      = detail,
                    tasks       = tasksDeferred.await(),
                    milestones  = milestonesDeferred.await(),
                    files       = filesDeferred.await(),
                    activity    = activityDeferred.await(),
                    discussions = discussionsDeferred.await(),
                )
            }
        } catch (e: Throwable) {
            if (!(preserveCurrent && _state.value is ProjectDetailUiState.Ready)) {
                _state.value = ProjectDetailUiState.Error(loadErr(e))
            }
        }
    }

    // --- write actions ------------------------------------------------------

    fun createTask(
        name: String,
        startDate: String,
        dueDate: String?,
        priority: Int,
        milestone: Int,
        description: String?,
        onDone: () -> Unit = {},
    ) {
        if (_actionInFlight.value) return
        viewModelScope.launch {
            _actionInFlight.value = true
            repo.createTask(projectId, name, startDate, dueDate, priority, milestone, description)
                .onSuccess { _toast.value = "Task created"; onDone(); refresh() }
                .onFailure { _toast.value = "Couldn't create the task. Try again." }
            _actionInFlight.value = false
        }
    }

    fun uploadFile(part: MultipartBody.Part) {
        if (_actionInFlight.value) return
        viewModelScope.launch {
            _actionInFlight.value = true
            repo.uploadFile(projectId, part)
                .onSuccess { added ->
                    _state.update { cur ->
                        if (cur is ProjectDetailUiState.Ready)
                            cur.copy(files = listOf(added) + cur.files)
                        else cur
                    }
                    _toast.value = "File uploaded"
                }
                .onFailure { _toast.value = "Upload failed. Try again." }
            _actionInFlight.value = false
        }
    }

    /** Stream a project file to the cache dir; [onReady] gets the file + mime. */
    fun openFile(file: ProjectFile, onReady: (File, String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = repo.downloadFile(file.id)
                    val name = (file.originalFileName ?: file.fileName).ifBlank { "document" }
                    val out = File(getApplication<Application>().cacheDir, "proj_${file.id}_$name")
                    body.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
                    out to (file.fileType ?: "application/octet-stream")
                }
            }.onSuccess { (f, mime) -> onReady(f, mime) }.onFailure { onError() }
        }
    }

    // --- discussions --------------------------------------------------------

    fun toggleDiscussion(id: String) {
        if (_openDiscussion.value?.id == id) { _openDiscussion.value = null; return }
        viewModelScope.launch {
            _discussionLoading.value = true
            repo.discussion(id)
                .onSuccess { _openDiscussion.value = it }
                .onFailure { _toast.value = "Couldn't open the discussion." }
            _discussionLoading.value = false
        }
    }

    fun postDiscussionComment(id: String, content: String) {
        val text = content.trim()
        if (text.isEmpty() || _actionInFlight.value) return
        viewModelScope.launch {
            _actionInFlight.value = true
            repo.postDiscussionComment(id, text)
                .onSuccess { added ->
                    _openDiscussion.update { d ->
                        if (d != null && d.id == id) d.copy(comments = d.comments + added) else d
                    }
                    // bump the thread's comment count in the list
                    _state.update { cur ->
                        if (cur is ProjectDetailUiState.Ready)
                            cur.copy(discussions = cur.discussions.map {
                                if (it.id == id) it.copy(totalComments = it.totalComments + 1) else it
                            })
                        else cur
                    }
                }
                .onFailure { _toast.value = "Couldn't post your reply. Try again." }
            _actionInFlight.value = false
        }
    }

    private fun loadErr(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403    -> "Your session expired. Sign in again."
            404         -> "This project is no longer available."
            in 500..599 -> "Server error. Try again in a moment."
            else        -> "Couldn't load this project (HTTP ${e.code()})."
        }
        else -> "Couldn't reach the server. Check your connection."
    }
}
