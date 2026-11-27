package app.santals.speaks.presentation.reading

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.santals.speaks.data.local.SettingsStore
import app.santals.speaks.data.network.NetworkMonitor
import app.santals.speaks.domain.model.HistoryItem
import app.santals.speaks.domain.model.SpeechFailureKind
import app.santals.speaks.domain.model.SpeechServiceStatus
import app.santals.speaks.domain.repository.HistoryRepository
import app.santals.speaks.domain.repository.ServiceStatusRepository
import app.santals.speaks.domain.text.OlChikiText
import app.santals.speaks.domain.text.ValidationCode
import app.santals.speaks.service.clipboard.ClipboardReader
import app.santals.speaks.service.intent.IncomingTextHolder
import app.santals.speaks.service.playback.PlaybackController
import app.santals.speaks.service.playback.PlaybackUiState
import app.santals.speaks.service.reading.ReadingSession
import app.santals.speaks.service.reading.ReadingSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Script analysis of the editor text, shown to the user as feedback. */
enum class ScriptStatus { EMPTY, OL_CHIKI, MIXED, NONE, TOO_LONG }

data class ReadingUiState(
    val input: String = "",
    val script: ScriptStatus = ScriptStatus.EMPTY,
    val olChikiLetters: Int = 0,
    val isOnline: Boolean = true,
    val serviceStatus: SpeechServiceStatus = SpeechServiceStatus.CHECKING,
    val session: ReadingSession? = null,
    val playback: PlaybackUiState = PlaybackUiState(),
    val recent: List<HistoryItem> = emptyList(),
    val error: SpeechFailureKind? = null,
    /** Short transient notice (for example "Text loaded" or "Clipboard is empty"). */
    val notice: NoticeKind? = null,
)

enum class NoticeKind { CLIPBOARD_EMPTY, TEXT_LOADED }

/**
 * Shared by the home, text-input and player screens. Holds the editor text and reflects the
 * singleton reading session and playback state, so every screen shows the same truth.
 */
@HiltViewModel
class ReadingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readings: ReadingSessionManager,
    private val playback: PlaybackController,
    private val history: HistoryRepository,
    private val incoming: IncomingTextHolder,
    private val serviceStatus: ServiceStatusRepository,
    private val network: NetworkMonitor,
    private val settings: SettingsStore,
) : ViewModel() {

    private val input = MutableStateFlow("")
    private val error = MutableStateFlow<SpeechFailureKind?>(null)
    private val notice = MutableStateFlow<NoticeKind?>(null)
    private val service = MutableStateFlow(SpeechServiceStatus.CHECKING)

    private val base = combine(
        input,
        readings.session,
        playback.state,
        network.isOnline,
        history.observeAll().map { it.take(RECENT_COUNT) },
    ) { text, session, play, online, recent ->
        val analysis = OlChikiText.analyze(text)
        ReadingUiState(
            input = text,
            script = scriptStatus(text, analysis.code, analysis.olChikiLetters, analysis.otherLetters),
            olChikiLetters = analysis.olChikiLetters,
            isOnline = online,
            session = session,
            playback = play,
            recent = recent,
        )
    }

    val state: StateFlow<ReadingUiState> = combine(base, service, error, notice) { b, s, e, n ->
        b.copy(serviceStatus = s, error = e, notice = n)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())

    init {
        viewModelScope.launch {
            readings.messages.collect { error.value = it }
        }
        viewModelScope.launch {
            incoming.pending.collect { text ->
                if (text != null) {
                    input.value = text
                    notice.value = NoticeKind.TEXT_LOADED
                    incoming.consume()
                }
            }
        }
        viewModelScope.launch { service.value = serviceStatus.check() }
    }

    fun onInputChanged(text: String) {
        input.value = text
        notice.value = null
    }

    fun paste() {
        val text = ClipboardReader.readPlainText(context)
        if (text == null) {
            notice.value = NoticeKind.CLIPBOARD_EMPTY
        } else {
            input.value = text
            notice.value = NoticeKind.TEXT_LOADED
        }
    }

    fun clear() {
        input.value = ""
        notice.value = null
        error.value = null
    }

    /** Starts reading the current editor text. Returns false if the text is not valid Ol Chiki. */
    fun speak(): Boolean {
        error.value = null
        viewModelScope.launch {
            playback.setSpeed(settings.settings.first().defaultSpeed)
        }
        return readings.start(input.value)
    }

    fun retry() {
        error.value = null
        readings.resume()
    }

    fun stop() {
        readings.stop()
    }

    fun togglePlayPause() {
        playback.togglePlayPause()
    }

    fun seekBack() {
        playback.seekBack()
    }

    fun seekForward() {
        playback.seekForward()
    }

    fun seekToFraction(fraction: Float) {
        playback.seekToFraction(fraction)
    }

    fun setSpeed(factor: Float) {
        playback.setSpeed(factor)
    }

    fun dismissError() {
        error.value = null
    }

    fun dismissNotice() {
        notice.value = null
    }

    /** Opens a saved reading. Its audio comes from the cache if it exists, otherwise it is generated again. */
    fun replay(id: Long, onStarted: () -> Unit) {
        viewModelScope.launch {
            val item = history.get(id) ?: return@launch
            error.value = null
            playback.setSpeed(settings.settings.first().defaultSpeed)
            if (readings.start(item.text, existingHistoryId = id)) onStarted()
        }
    }

    fun recheckService() {
        viewModelScope.launch { service.value = serviceStatus.check() }
    }

    private fun scriptStatus(text: String, code: ValidationCode, ol: Int, other: Int): ScriptStatus = when {
        text.isBlank() -> ScriptStatus.EMPTY
        code == ValidationCode.TOO_LONG -> ScriptStatus.TOO_LONG
        ol == 0 -> ScriptStatus.NONE
        other > 0 -> ScriptStatus.MIXED
        else -> ScriptStatus.OL_CHIKI
    }

    private companion object {
        const val RECENT_COUNT = 5
    }
}
