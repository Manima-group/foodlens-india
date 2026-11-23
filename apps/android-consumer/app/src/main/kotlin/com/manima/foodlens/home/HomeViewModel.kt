package com.manima.foodlens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.ConnectivityMonitor
import com.manima.foodlens.core.data.ContributionRepository
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.data.RemoteConfig
import com.manima.foodlens.core.database.HistoryEntity
import com.manima.foodlens.core.database.LocalContributionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recent: List<HistoryEntity> = emptyList(),
    val favoriteCount: Int = 0,
    val pendingContributions: Int = 0,
    val online: Boolean = true,
    val remote: RemoteConfig = RemoteConfig()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    contributions: ContributionRepository,
    connectivity: ConnectivityMonitor,
    private val config: ConfigRepository
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        library.recent(RECENT),
        library.favorites(),
        contributions.observe(),
        connectivity.isOnline,
        config.state
    ) { recent, favorites, contribs, online, remote ->
        HomeUiState(
            recent = recent,
            favoriteCount = favorites.size,
            pendingContributions = contribs.count {
                it.state == LocalContributionState.QUEUED || it.state == LocalContributionState.UPLOADING
            },
            online = online,
            remote = remote
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), HomeUiState())

    fun retryServer() {
        viewModelScope.launch { config.refresh(force = true) }
    }

    private companion object {
        const val RECENT = 5
        const val STOP_MS = 5_000L
    }
}
