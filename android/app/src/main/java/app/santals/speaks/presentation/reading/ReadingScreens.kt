package app.santals.speaks.presentation.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.santals.speaks.R
import app.santals.speaks.domain.model.ChunkStatus
import app.santals.speaks.domain.model.HistoryItem
import app.santals.speaks.domain.model.PlaybackSpeed
import app.santals.speaks.presentation.theme.OlChikiFont
import app.santals.speaks.domain.model.SpeechServiceStatus
import app.santals.speaks.service.reading.SessionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: ReadingViewModel,
    onEdit: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onReplayStarted: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val busy = state.session?.status == SessionStatus.PREPARING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.nav_history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.nav_about))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.santali_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = OlChikiFont,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.isOnline) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text(stringResource(R.string.home_offline_banner), modifier = Modifier.padding(12.dp))
                    }
                }
            }
            item {
                val serviceRes = when (state.serviceStatus) {
                    SpeechServiceStatus.CHECKING -> R.string.home_speech_service_checking
                    SpeechServiceStatus.AVAILABLE -> R.string.home_speech_service_ok
                    SpeechServiceStatus.UNAVAILABLE -> R.string.home_speech_service_unavailable
                }
                Text(
                    text = stringResource(R.string.home_speech_service, stringResource(serviceRes)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item {
                val preview = state.input.take(PREVIEW_CHARS)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (preview.isBlank()) stringResource(R.string.home_input_hint) else preview,
                            fontFamily = OlChikiFont,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        ScriptHint(state.script, state.olChikiLetters)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = vm::paste) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        Text(stringResource(R.string.home_paste))
                    }
                    OutlinedButton(onClick = vm::clear, enabled = state.input.isNotEmpty()) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                        Text(stringResource(R.string.home_clear))
                    }
                }
            }

            item {
                SpeakButton(
                    enabled = (state.script == ScriptStatus.OL_CHIKI || state.script == ScriptStatus.MIXED) && !busy,
                    busy = busy,
                    onClick = { if (vm.speak()) onOpenPlayer() },
                )
            }

            state.notice?.let { notice ->
                item {
                    NoticeText(notice, onDismiss = vm::dismissNotice)
                }
            }
            state.error?.let { kind ->
                item {
                    ErrorBanner(kind, onRetry = vm::retry, onDismiss = vm::dismissError)
                }
            }

            state.session?.let { session ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlayer),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.home_now_playing, session.fullText.take(60)),
                                fontFamily = OlChikiFont,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.home_open_player),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            item {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(stringResource(R.string.home_recent_empty), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(state.recent, key = { it.id }) { item ->
                RecentRow(item = item, onClick = { vm.replay(item.id, onReplayStarted) })
            }
        }
    }
}

@Composable
private fun SpeakButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (busy) R.string.home_speak_busy else R.string.home_speak))
    }
}

@Composable
private fun NoticeText(notice: NoticeKind, onDismiss: () -> Unit) {
    val text = when (notice) {
        NoticeKind.CLIPBOARD_EMPTY -> stringResource(R.string.home_clipboard_empty)
        NoticeKind.TEXT_LOADED -> stringResource(R.string.home_text_loaded)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
    }
}

@Composable
private fun RecentRow(item: HistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = item.text,
                fontFamily = OlChikiFont,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(historyStatusRes(item)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun historyStatusRes(item: HistoryItem): Int = when (item.status) {
    app.santals.speaks.domain.model.HistoryStatus.READY -> R.string.history_status_ready
    app.santals.speaks.domain.model.HistoryStatus.PENDING -> R.string.history_status_pending
    app.santals.speaks.domain.model.HistoryStatus.FAILED -> R.string.history_status_failed
}

private const val PREVIEW_CHARS = 280

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputScreen(vm: ReadingViewModel, onBack: () -> Unit, onSpeakStarted: () -> Unit) {
    val state by vm.state.collectAsState()
    val busy = state.session?.status == SessionStatus.PREPARING
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.home_input_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::onInputChanged,
                label = { Text(stringResource(R.string.home_input_label)) },
                placeholder = { Text(stringResource(R.string.home_input_hint)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = OlChikiFont, fontSize = 20.sp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            ScriptHint(state.script, state.olChikiLetters)
            state.notice?.let { NoticeText(it, onDismiss = vm::dismissNotice) }
            state.error?.let { ErrorBanner(it, onRetry = vm::retry, onDismiss = vm::dismissError) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = vm::paste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Text(stringResource(R.string.home_paste))
                }
                OutlinedButton(onClick = vm::clear, enabled = state.input.isNotEmpty()) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Text(stringResource(R.string.home_clear))
                }
            }
            SpeakButton(
                enabled = (state.script == ScriptStatus.OL_CHIKI || state.script == ScriptStatus.MIXED) && !busy,
                busy = busy,
                onClick = { if (vm.speak()) onSpeakStarted() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: ReadingViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val session = state.session
    val play = state.playback
    val canResume = session?.status == SessionStatus.FAILED || session?.status == SessionStatus.WAITING_NETWORK

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        if (session == null && !play.hasQueue) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.player_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val statusRes = when (session?.status) {
                    SessionStatus.PREPARING -> R.string.player_status_preparing
                    SessionStatus.WAITING_NETWORK -> R.string.player_status_waiting_network
                    SessionStatus.COMPLETE -> R.string.player_status_done
                    SessionStatus.FAILED -> R.string.player_status_failed
                    null -> R.string.player_status_done
                }
                Text(stringResource(statusRes), style = MaterialTheme.typography.titleMedium)
            }

            item {
                Column {
                    LinearProgressIndicator(progress = { play.progress }, modifier = Modifier.fillMaxWidth())
                    Slider(
                        value = play.progress,
                        onValueChange = vm::seekToFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = vm::seekBack) {
                        Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.player_back10))
                    }
                    FilledIconButton(onClick = vm::togglePlayPause, modifier = Modifier.height(64.dp).padding(0.dp)) {
                        Icon(
                            imageVector = if (play.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (play.isPlaying) R.string.player_pause else R.string.player_play),
                        )
                    }
                    IconButton(onClick = vm::seekForward) {
                        Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.player_forward10))
                    }
                    IconButton(onClick = vm::stop) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.player_stop))
                    }
                }
            }

            item {
                Text(stringResource(R.string.player_speed), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PlaybackSpeed.entries.forEach { option ->
                        FilterChip(
                            selected = kotlin.math.abs(play.speed - option.factor) < 0.01f,
                            onClick = { vm.setSpeed(option.factor) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            state.error?.let { kind ->
                item { ErrorBanner(kind, onRetry = vm::retry, onDismiss = vm::dismissError) }
            }

            if (canResume) {
                item {
                    FilledTonalButton(onClick = vm::retry, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (session?.status == SessionStatus.FAILED) R.string.player_retry_part
                                else R.string.player_resume_preparing,
                            ),
                        )
                    }
                }
            }

            play.currentText?.let { current ->
                item {
                    Text(stringResource(R.string.player_text_heading), style = MaterialTheme.typography.titleMedium)
                    Text(current, fontFamily = OlChikiFont, style = MaterialTheme.typography.bodyLarge)
                }
            }

            session?.let { s ->
                item {
                    Text(
                        text = "${stringResource(R.string.player_progress)}: ${s.readyCount} / ${s.chunks.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                itemsIndexed(s.chunks, key = { _, c -> c.index }) { index, chunk ->
                    val statusText = when (chunk.status) {
                        ChunkStatus.READY -> stringResource(R.string.player_status_ready)
                        ChunkStatus.FAILED -> stringResource(R.string.player_status_failed)
                        ChunkStatus.GENERATING, ChunkStatus.PENDING -> stringResource(R.string.player_status_preparing)
                    }
                    Text(
                        text = stringResource(R.string.player_part_status, index + 1, statusText),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
