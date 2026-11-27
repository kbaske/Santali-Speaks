package app.santals.speaks.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.santals.speaks.R
import app.santals.speaks.data.local.ThemeMode
import app.santals.speaks.domain.model.PlaybackSpeed

private val CACHE_LIMITS_MB = listOf(100, 200, 500, 1000, 2000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            SettingsMessage.CACHE_CLEARED -> context.getString(R.string.settings_clear_cache_done)
            SettingsMessage.ALL_DELETED -> context.getString(R.string.settings_delete_all_done)
        }
        snackbar.showSnackbar(text)
        vm.dismissMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_appearance)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.settings_playback)) {
                Text(stringResource(R.string.settings_default_speed), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaybackSpeed.entries.forEach { option ->
                        FilterChip(
                            selected = kotlin.math.abs(state.settings.defaultSpeed - option.factor) < 0.01f,
                            onClick = { vm.setDefaultSpeed(option.factor) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.settings_storage)) {
                Text(stringResource(R.string.settings_cache_limit, state.settings.cacheLimitMb))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CACHE_LIMITS_MB.forEach { mb ->
                        FilterChip(
                            selected = state.settings.cacheLimitMb == mb,
                            onClick = { vm.setCacheLimit(mb) },
                            label = { Text("$mb") },
                        )
                    }
                }
                Text(
                    stringResource(
                        R.string.settings_cache_usage,
                        Formatter.formatShortFileSize(context, state.cacheBytes),
                    ),
                )
                OutlinedButton(onClick = vm::clearCache) { Text(stringResource(R.string.settings_clear_cache)) }
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.settings_delete_all)) }
            }

            Section(stringResource(R.string.settings_privacy)) {
                Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }

            Section(stringResource(R.string.settings_permissions)) {
                Text(stringResource(R.string.settings_permission_internet), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_permission_notifications), style = MaterialTheme.typography.bodyMedium)
            }

            Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_about))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllData()
                    confirmDelete = false
                }) { Text(stringResource(R.string.settings_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
