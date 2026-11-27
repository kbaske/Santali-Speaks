package app.santals.speaks.presentation.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.santals.speaks.R
import app.santals.speaks.domain.model.SpeechFailureKind
import app.santals.speaks.presentation.common.messageRes
import app.santals.speaks.presentation.theme.OlChikiFont

/** Error card with a retry (when the failure can be retried) and a dismiss action. */
@Composable
fun ErrorBanner(kind: SpeechFailureKind, onRetry: (() -> Unit)?, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(kind.messageRes()),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (onRetry != null && kind.retryable) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

/** Shows how much of the editor text is Ol Chiki, and warns about mixed or non-Ol Chiki input. */
@Composable
fun ScriptHint(script: ScriptStatus, letters: Int) {
    val text = when (script) {
        ScriptStatus.EMPTY -> null
        ScriptStatus.OL_CHIKI -> stringResource(R.string.home_script_ok, letters)
        ScriptStatus.MIXED -> stringResource(R.string.home_script_mixed)
        ScriptStatus.NONE -> stringResource(R.string.home_script_none)
        ScriptStatus.TOO_LONG -> stringResource(R.string.error_text_too_long)
    } ?: return
    val isProblem = script == ScriptStatus.NONE || script == ScriptStatus.TOO_LONG
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** Compact player bar shown above the navigation on other screens while a reading is active. */
@Composable
fun FloatingPlayerBar(state: ReadingUiState, onToggle: () -> Unit, onOpen: () -> Unit) {
    val session = state.session
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.home_now_playing,
                        session?.fullText?.lineSequence()?.firstOrNull()?.take(60).orEmpty(),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = OlChikiFont,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilledIconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (state.playback.isPlaying) R.string.player_pause else R.string.player_play,
                    ),
                )
            }
        }
    }
}
