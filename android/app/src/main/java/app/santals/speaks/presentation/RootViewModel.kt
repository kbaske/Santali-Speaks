package app.santals.speaks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.santals.speaks.data.local.AppSettings
import app.santals.speaks.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App-wide state read once at launch: the theme and whether onboarding is done.
 * [settings] stays null until DataStore has loaded, which keeps the splash screen visible.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    private val _settings = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { _settings.value = it }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { store.setOnboardingDone(true) }
    }
}
