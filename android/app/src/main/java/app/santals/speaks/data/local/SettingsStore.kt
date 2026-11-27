package app.santals.speaks.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val defaultSpeed: Float = 1.0f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cacheLimitMb: Int = 200,
)

/** User preferences in DataStore. Corrupt or unreadable storage falls back to defaults. */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                onboardingDone = prefs[KEY_ONBOARDING] ?: false,
                defaultSpeed = (prefs[KEY_SPEED] ?: 1.0f).coerceIn(0.5f, 2.5f),
                themeMode = runCatching { ThemeMode.valueOf(prefs[KEY_THEME] ?: ThemeMode.SYSTEM.name) }
                    .getOrDefault(ThemeMode.SYSTEM),
                cacheLimitMb = (prefs[KEY_CACHE_MB] ?: 200).coerceIn(50, 2000),
            )
        }

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[KEY_ONBOARDING] = done }

    suspend fun setDefaultSpeed(speed: Float) = store.edit { it[KEY_SPEED] = speed }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }

    suspend fun setCacheLimitMb(mb: Int) = store.edit { it[KEY_CACHE_MB] = mb.coerceIn(50, 2000) }

    /** Removes every preference, returning the app to its first-run state. */
    suspend fun clearAll() = store.edit { it.clear() }

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        val KEY_SPEED = floatPreferencesKey("default_speed")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_CACHE_MB = intPreferencesKey("cache_limit_mb")
    }
}
