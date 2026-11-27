package app.santals.speaks.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.santals.speaks.domain.model.HistoryItem
import app.santals.speaks.domain.repository.HistoryRepository
import app.santals.speaks.service.reading.ReadingSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val favoritesOnly: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val readings: ReadingSessionManager,
) : ViewModel() {

    private val favoritesOnly = MutableStateFlow(false)

    val state: StateFlow<HistoryUiState> = combine(history.observeAll(), favoritesOnly) { items, fav ->
        HistoryUiState(
            items = if (fav) items.filter { it.favorite } else items,
            favoritesOnly = fav,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFavoritesOnly(value: Boolean) {
        favoritesOnly.value = value
    }

    fun toggleFavorite(item: HistoryItem) {
        viewModelScope.launch { history.setFavorite(item.id, !item.favorite) }
    }

    fun delete(item: HistoryItem) {
        viewModelScope.launch { history.delete(item.id) }
    }

    fun clearAll() {
        viewModelScope.launch { history.clearAll() }
    }

    fun replay(item: HistoryItem, onStarted: () -> Unit) {
        viewModelScope.launch {
            if (readings.start(item.text, existingHistoryId = item.id)) onStarted()
        }
    }
}
