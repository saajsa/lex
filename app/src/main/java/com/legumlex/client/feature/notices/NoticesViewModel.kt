package com.legumlex.client.feature.notices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class NoticesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoticesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<NoticesUiState>(NoticesUiState.Loading)
    val state: StateFlow<NoticesUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _filter = MutableStateFlow(NoticeFilter.ALL)
    val filter: StateFlow<NoticeFilter> = _filter.asStateFlow()

    private val _sort = MutableStateFlow(NoticeSort.DEADLINE)
    val sort: StateFlow<NoticeSort> = _sort.asStateFlow()

    private val _grouped = MutableStateFlow(true)
    val grouped: StateFlow<Boolean> = _grouped.asStateFlow()

    /** Every notice (roots + nested responses), unfiltered — for the filter counts. */
    private val _allFlat = MutableStateFlow<List<NoticeSummary>>(emptyList())
    val allFlat: StateFlow<List<NoticeSummary>> = _allFlat.asStateFlow()

    private var all: List<NoticeSummary> = emptyList()

    init { load() }

    fun load() = viewModelScope.launch { _state.value = NoticesUiState.Loading; fetch() }

    fun refresh() = viewModelScope.launch {
        if (_isRefreshing.value) return@launch
        _isRefreshing.value = true; fetch(preserve = true); _isRefreshing.value = false
    }

    fun setFilter(f: NoticeFilter) { _filter.value = f; render() }
    fun setSort(s: NoticeSort) { _sort.value = s; render() }
    fun setGrouped(g: Boolean) { _grouped.value = g; render() }

    private suspend fun fetch(preserve: Boolean = false) {
        repo.loadList()
            .onSuccess {
                all = it
                _allFlat.value = it.flatMap { root -> listOf(root) + root.children }
                render()
            }
            .onFailure { e ->
                if (!(preserve && _state.value is NoticesUiState.Ready))
                    _state.value = NoticesUiState.Error(messageFor(e))
            }
    }

    private fun render() {
        // Thread-preserving filter: a thread is kept (root + its responses intact)
        // when the root OR any nested response matches — so a reply never gets
        // orphaned from its parent. "Filtered, thread kept."
        fun matches(n: NoticeSummary): Boolean = when (_filter.value) {
            NoticeFilter.ALL      -> true
            NoticeFilter.OUTBOUND -> n.direction == NoticeDirection.OUTBOUND
            NoticeFilter.INBOUND  -> n.direction == NoticeDirection.INBOUND
            NoticeFilter.ACTIVE   -> !n.isClosed
            NoticeFilter.CLOSED   -> n.isClosed
        }
        val roots = all.filter { root -> matches(root) || root.children.any(::matches) }

        val sorted = roots.sortedWith(sortComparator(_sort.value))
        val total = roots.sumOf { 1 + it.children.size }
        val threadCount = roots.size

        val sections = if (_grouped.value) {
            NoticeBand.values().mapNotNull { band ->
                val inBand = sorted.filter { bandOf(it) == band }
                if (inBand.isEmpty()) null else NoticeSection(band, inBand)
            }
        } else {
            listOf(NoticeSection(null, sorted))
        }
        _state.value = NoticesUiState.Ready(sections, total, threadCount)
    }

    /** A thread's priority band — bumped to "Needs you" if anything in it awaits the client. */
    private fun bandOf(root: NoticeSummary): NoticeBand {
        val thread = listOf(root) + root.children
        return when {
            thread.any { it.needsApproval } -> NoticeBand.NEEDS_YOU
            thread.any { !it.isClosed }     -> NoticeBand.IN_PROGRESS
            else                            -> NoticeBand.CLOSED
        }
    }

    private fun sortComparator(s: NoticeSort): Comparator<NoticeSummary> = when (s) {
        // nulls always sort last; deadline ascending, recent/amount descending.
        NoticeSort.DEADLINE -> Comparator { a, b ->
            val x = a.responseDueDate; val y = b.responseDueDate
            when { x == null && y == null -> 0; x == null -> 1; y == null -> -1; else -> x.compareTo(y) }
        }
        NoticeSort.RECENT -> Comparator { a, b ->
            val x = a.createdAt; val y = b.createdAt
            when { x == null && y == null -> 0; x == null -> 1; y == null -> -1; else -> y.compareTo(x) }
        }
        NoticeSort.AMOUNT -> Comparator { a, b ->
            val x = a.claimAmount; val y = b.claimAmount
            when { x == null && y == null -> 0; x == null -> 1; y == null -> -1; else -> y.compareTo(x) }
        }
        NoticeSort.STATUS -> compareBy { it.statusLabel }
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            401, 403 -> "Your session expired. Sign in again."
            in 500..599 -> "Server error. Try again in a moment."
            else -> "Couldn't reach the server (HTTP ${e.code()})."
        }
        else -> "Couldn't reach the server. Check your connection."
    }
}

class NoticeDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoticesRepository(ApiClient.get(app))

    private val _state = MutableStateFlow<NoticeDetailUiState>(NoticeDetailUiState.Loading)
    val state: StateFlow<NoticeDetailUiState> = _state.asStateFlow()

    private val _approving = MutableStateFlow(false)
    val approving: StateFlow<Boolean> = _approving.asStateFlow()

    private var id: String? = null

    fun start(noticeId: String) {
        if (id == noticeId && _state.value is NoticeDetailUiState.Ready) return
        id = noticeId
        load()
    }

    fun load() = viewModelScope.launch {
        val nid = id ?: return@launch
        _state.value = NoticeDetailUiState.Loading
        repo.loadDetail(nid)
            .onSuccess { _state.value = NoticeDetailUiState.Ready(it) }
            .onFailure { _state.value = NoticeDetailUiState.Error("Couldn't load this notice.") }
    }

    fun approve() = viewModelScope.launch {
        val nid = id ?: return@launch
        if (_approving.value) return@launch
        _approving.value = true
        repo.approve(nid).onSuccess { _state.value = NoticeDetailUiState.Ready(it) }
        _approving.value = false
    }
}
