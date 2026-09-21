package com.wineapp.presentation.search

import com.wineapp.domain.model.SearchResult
import com.wineapp.domain.model.Wine
import com.wineapp.presentation.common.BaseState
import com.wineapp.presentation.common.BaseIntent

sealed interface SearchState : BaseState {
    data class Idle(val recentSearches: List<String> = emptyList()) : SearchState
    data class Loading(val query: String, val isLoadMore: Boolean = false) : SearchState
    data class Success(val result: SearchResult, val query: String) : SearchState
    data class Empty(val query: String) : SearchState
    data class Error(val message: String) : SearchState
}

sealed interface SearchIntent : BaseIntent {
    data class Search(val query: String) : SearchIntent
    data object LoadMore : SearchIntent
    data object ClearSearch : SearchIntent
    data class SelectWine(val wine: Wine) : SearchIntent
    data class RecentSearchSelected(val query: String) : SearchIntent
}