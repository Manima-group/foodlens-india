package com.manima.foodlens.feature.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.ContributionRepository
import com.manima.foodlens.core.database.ContributionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MyContributionsViewModel @Inject constructor(private val repo: ContributionRepository) : ViewModel() {
    val items: StateFlow<List<ContributionEntity>?> = repo.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun retry(localId: String) {
        viewModelScope.launch { repo.retry(localId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
