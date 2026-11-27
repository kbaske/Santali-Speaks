package app.santals.speaks.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.santals.speaks.data.local.AppSettings
import app.santals.speaks.data.local.AudioCache
import app.santals.speaks.data.local.SettingsStore
import app.santals.speaks.data.local.ThemeMode
import app.santals.speaks.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val cacheBytes: Long = 0L,
    val message: SettingsMessage? = null,
)

enum class SettingsMessage { CACHE_CLEARED, ALL_DELETED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val history: HistoryRepository,
    private val cache: AudioCache,
) : ViewModel() {

    private val cacheBytes = MutableStateFlow(0L)
    private val message = MutableStateFlow<SettingsMessage?>(null)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, cacheBytes, message) { s, bytes, msg ->
        SettingsUiState(settings = s, cacheBytes = bytes, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCacheSize()
    }

    fun setTheme(mode: ThemeMode) = launch { settings.setThemeMode(mode) }

    fun setDefaultSpeed(speed: Float) = launch { settings.setDefaultSpeed(speed) }

    fun setCacheLimit(mb: Int) = launch {
        settings.setCacheLimitMb(mb)
        withContext(Dispatchers.IO) { cache.enforceLimit(mb.coerceIn(50, 2000) * 1024L * 1024L) }
        refreshCacheSize()
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cache.clear() }
            refreshCacheSize()
            message.value = SettingsMessage.CACHE_CLEARED
        }
    }

    /** Deletes every saved reading, all audio, and all preferences. */
    fun deleteAllData() {
        viewModelScope.launch {
            history.clearAll()
            settings.clearAll()
            refreshCacheSize()
            message.value = SettingsMessage.ALL_DELETED
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            cacheBytes.value = withContext(Dispatchers.IO) { cache.totalBytes() }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
