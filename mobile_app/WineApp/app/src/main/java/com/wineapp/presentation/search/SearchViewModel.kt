package com.wineapp.presentation.search

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.wineapp.data.local.FavoriteKind
import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.domain.usecase.GetFavoritesUseCase
import com.wineapp.domain.usecase.SearchWinesUseCase
import com.wineapp.domain.usecase.ToggleFavoriteUseCase
import com.wineapp.presentation.common.BaseViewModel
import com.wineapp.presentation.common.ui.WineListSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Поиск. Без сортировки и фильтра — серверная пагинация как есть.
 * API не умеет сортировать/фильтровать (а рейтинг вообще считается на клиенте),
 * поэтому при активных сортировке или фильтре выдача по запросу загружается целиком,
 * обрабатывается локально и показывается порциями по [PAGE_SIZE].
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchWinesUseCase: SearchWinesUseCase,
    getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : BaseViewModel<SearchState, SearchIntent>() {
    private var currentQuery = ""
    private var currentPage = 1
    private var searchJob: Job? = null

    /** Вся выдача по [fullQuery] — только для локального режима. */
    private var fullQuery: String? = null
    private var fullWines: List<Wine> = emptyList()

    private val _controls = MutableStateFlow(SearchControls())
    val controls: StateFlow<SearchControls> = _controls.asStateFlow()

    private val localMode: Boolean
        get() = _controls.value.let { it.sort != null || it.styleFilter != null }

    /** ID вин в «Понравилось» — для сердечек на карточках. */
    val likedIds: StateFlow<Set<String>> = getFavoritesUseCase()
        .map { list -> list.filter { it.kind == FavoriteKind.LIKED }.map { it.wineId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    override fun getInitialState(): SearchState = SearchState.Idle()

    override fun reduce(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.Search -> handleSearch(intent.query)
            is SearchIntent.LoadMore -> handleLoadMore()
            is SearchIntent.ClearSearch -> handleClear()
            is SearchIntent.SelectWine -> { /* Navigation */ }
            is SearchIntent.RecentSearchSelected -> handleSearch(intent.query)
            is SearchIntent.ToggleFavorite -> handleToggleFavorite(intent.wineId)
            is SearchIntent.ChangeSort -> {
                _controls.value = _controls.value.copy(sort = intent.sort)
                handleSearch(currentQuery)
            }
            is SearchIntent.ChangeStyleFilter -> {
                _controls.value = _controls.value.copy(styleFilter = intent.style)
                handleSearch(currentQuery)
            }
        }
    }

    private fun handleSearch(query: String) {
        val trimmed = query.trim()
        currentQuery = trimmed
        currentPage = 1
        searchJob?.cancel()
        if (localMode && fullQuery == trimmed) {
            showLocalPage()
            return
        }
        updateState(SearchState.Loading(trimmed))
        searchJob = viewModelScope.launch {
            if (localMode) {
                delay(LOCAL_DEBOUNCE_MS) // полная выдача тяжелее — не качаем на каждую букву
                loadFull(trimmed)
                    .onSuccess { wines ->
                        fullQuery = trimmed
                        fullWines = wines
                        rememberStyles(wines)
                        showLocalPage()
                    }
                    .onFailure { error -> onSearchFailed(error) }
            } else {
                searchWinesUseCase(trimmed, 1, PAGE_SIZE)
                    .onSuccess { searchResult ->
                        rememberStyles(searchResult.wines)
                        if (searchResult.wines.isEmpty()) {
                            updateState(SearchState.Empty(trimmed))
                        } else {
                            updateState(SearchState.Success(searchResult, trimmed))
                        }
                    }
                    .onFailure { error -> onSearchFailed(error) }
            }
        }
    }

    private fun onSearchFailed(error: Throwable) {
        Log.e("SearchViewModel", "Search failed", error)
        updateState(SearchState.Error(error.message ?: "Ошибка поиска"))
    }

    /** Все страницы выдачи: первая — чтобы узнать totalCount, остальные параллельно. */
    private suspend fun loadFull(query: String): Result<List<Wine>> = runCatching {
        val first = searchWinesUseCase(query, 1, FULL_PAGE_SIZE).getOrThrow()
        val pages = ((first.totalCount + FULL_PAGE_SIZE - 1) / FULL_PAGE_SIZE)
            .coerceAtMost(MAX_FULL_PAGES)
        val rest = kotlinx.coroutines.coroutineScope {
            (2..pages).map { page ->
                async { searchWinesUseCase(query, page, FULL_PAGE_SIZE).getOrThrow().wines }
            }.awaitAll()
        }
        (first.wines + rest.flatten()).distinctBy { it.id }
    }

    /** Фильтр + сортировка по полной выдаче, показываем первые currentPage * PAGE_SIZE. */
    private fun showLocalPage() {
        val controls = _controls.value
        val filtered = controls.styleFilter
            ?.let { style -> fullWines.filter { it.style == style } }
            ?: fullWines
        val sorted = when (controls.sort) {
            WineListSort.RATING -> filtered.sortedByDescending { it.rating ?: -1f }
            WineListSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            WineListSort.NEWEST, WineListSort.OLDEST, null -> filtered
        }
        val shown = sorted.take(currentPage * PAGE_SIZE)
        if (shown.isEmpty()) {
            updateState(SearchState.Empty(currentQuery))
        } else {
            updateState(
                SearchState.Success(
                    SearchResult(
                        wines = shown,
                        totalCount = sorted.size,
                        page = currentPage,
                        hasMore = shown.size < sorted.size
                    ),
                    currentQuery
                )
            )
        }
    }

    /** Категории для меню фильтра: базовые из каталога + встреченные в выдаче. */
    private fun rememberStyles(wines: List<Wine>) {
        val known = _controls.value.styles
        val found = wines.mapNotNull { it.style?.takeIf(String::isNotBlank) }
        if (found.all { it in known }) return
        _controls.value = _controls.value.copy(styles = (known + found).distinct())
    }

    private fun handleLoadMore() {
        val state = _state.value
        if (state !is SearchState.Success || !state.result.hasMore) return
        currentPage++
        if (localMode) {
            showLocalPage()
            return
        }
        // Уже показанное запоминаем до Loading — иначе догруженная страница затрёт предыдущие.
        val shown = state.result.wines
        updateState(SearchState.Loading(currentQuery, true))
        searchJob = viewModelScope.launch {
            searchWinesUseCase(currentQuery, currentPage, PAGE_SIZE)
                .onSuccess { searchResult ->
                    rememberStyles(searchResult.wines)
                    val combined = SearchResult(
                        wines = (shown + searchResult.wines).distinctBy { it.id },
                        totalCount = searchResult.totalCount,
                        page = searchResult.page,
                        hasMore = searchResult.hasMore
                    )
                    updateState(SearchState.Success(combined, currentQuery))
                }
                .onFailure { error ->
                    Log.e("SearchViewModel", "Load more failed", error)
                    currentPage--
                    updateState(state)
                }
        }
    }

    private fun handleClear() {
        searchJob?.cancel()
        currentQuery = ""
        currentPage = 1
        updateState(SearchState.Idle())
    }

    private fun handleToggleFavorite(wineId: String) {
        viewModelScope.launch {
            val isFavorite = wineId in likedIds.value
            toggleFavoriteUseCase(wineId, isFavorite, FavoriteKind.LIKED)
                .onFailure { error ->
                    Log.e("SearchViewModel", "Toggle favorite failed", error)
                }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val FULL_PAGE_SIZE = 100 // максимум, который принимает API
        const val MAX_FULL_PAGES = 30
        const val LOCAL_DEBOUNCE_MS = 300L
    }
}
