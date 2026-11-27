package app.santals.speaks.service.intent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds text that another app sent to Santali Speaks ("Speak in Santali" from the text menu,
 * or the share sheet). The home screen loads it into the editor; nothing is spoken automatically.
 */
@Singleton
class IncomingTextHolder @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        _pending.value = text.take(MAX_LENGTH)
    }

    fun consume() {
        _pending.value = null
    }

    companion object {
        /** Guard against very large shared content. */
        const val MAX_LENGTH = 50_000
    }
}
