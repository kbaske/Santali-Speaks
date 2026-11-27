package app.santals.speaks.data.repository

import app.santals.speaks.data.local.AudioCache
import app.santals.speaks.data.local.SettingsStore
import app.santals.speaks.data.network.NetworkMonitor
import app.santals.speaks.data.remote.SpeechApi
import app.santals.speaks.data.remote.SpeechErrorMapper
import app.santals.speaks.data.remote.dto.ErrorEnvelopeDto
import app.santals.speaks.data.remote.dto.TtsRequestDto
import app.santals.speaks.domain.audio.AudioSniffer
import app.santals.speaks.domain.audio.CacheKey
import app.santals.speaks.domain.model.SpeechClip
import app.santals.speaks.domain.model.SpeechException
import app.santals.speaks.domain.model.SpeechFailureKind
import app.santals.speaks.domain.repository.SpeechRepository
import app.santals.speaks.domain.text.OlChikiText
import app.santals.speaks.domain.text.ValidationCode
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import javax.inject.Inject
import javax.net.ssl.SSLException

/**
 * Cache-first speech generation.
 *
 *  1. Validate the text as Ol Chiki (the text itself is never changed).
 *  2. Return cached audio if it exists, so offline playback and repeat listening cost nothing.
 *  3. Otherwise require connectivity, call the server, and retry only transient failures.
 *  4. Identify the audio container before writing it; unknown formats are rejected.
 */
class SpeechRepositoryImpl @Inject constructor(
    private val api: SpeechApi,
    private val cache: AudioCache,
    private val network: NetworkMonitor,
    private val settings: SettingsStore,
    private val gson: Gson,
) : SpeechRepository {

    override suspend fun findCached(text: String): SpeechClip? = withContext(Dispatchers.IO) {
        val key = CacheKey.of(text)
        val file = cache.find(key) ?: return@withContext null
        cache.touch(file)
        val container = AudioSniffer.identify(readHeader(file)) ?: return@withContext null
        SpeechClip(
            cacheKey = key,
            filePath = file.absolutePath,
            mimeType = container.mimeType,
            sizeBytes = file.length(),
            fromCache = true,
        )
    }

    override suspend fun speak(text: String): SpeechClip {
        val analysis = OlChikiText.analyze(text)
        if (!analysis.ok) {
            throw SpeechException(kindForValidation(analysis.code), analysis.code.name)
        }

        findCached(text)?.let { return it }

        if (!network.currentlyOnline()) {
            throw SpeechException(SpeechFailureKind.NO_NETWORK, "offline and not cached")
        }

        val response = withRetry {
            api.synthesize(TtsRequestDto(text = text, language = LANGUAGE))
        }

        val base64 = response.audioBase64
            ?: throw SpeechException(SpeechFailureKind.BAD_RESPONSE, "missing audioBase64")
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (e: IllegalArgumentException) {
            throw SpeechException(SpeechFailureKind.UNSUPPORTED_AUDIO_FORMAT, "invalid base64")
        }
        val container = AudioSniffer.identify(bytes)
            ?: throw SpeechException(SpeechFailureKind.UNSUPPORTED_AUDIO_FORMAT, "unknown container")

        val key = CacheKey.of(text)
        val limitBytes = settings.settings.first().cacheLimitMb * 1024L * 1024L
        val file = withContext(Dispatchers.IO) {
            cache.write(key, bytes, container.extension).also { cache.enforceLimit(limitBytes) }
        }

        return SpeechClip(
            cacheKey = key,
            filePath = file.absolutePath,
            mimeType = container.mimeType,
            sizeBytes = file.length(),
            fromCache = false,
        )
    }

    /** Retries transient failures with a short backoff. Non-transient failures are thrown at once. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val kind = classify(e)
                attempt += 1
                if (!kind.retryable || attempt > MAX_RETRIES) {
                    throw SpeechException(kind, e.javaClass.simpleName)
                }
                delay(BACKOFF_BASE_MS * attempt)
            }
        }
    }

    private fun classify(error: Exception): SpeechFailureKind = when (error) {
        is SpeechException -> error.kind
        is HttpException -> SpeechErrorMapper.fromServerCode(readErrorCode(error), error.code())
        is SocketTimeoutException, is InterruptedIOException -> SpeechFailureKind.TIMEOUT
        is UnknownHostException, is ConnectException -> SpeechFailureKind.NO_NETWORK
        is SSLException -> SpeechFailureKind.SERVICE_UNAVAILABLE
        is IOException -> SpeechFailureKind.NO_NETWORK
        is JsonParseException -> SpeechFailureKind.BAD_RESPONSE
        else -> SpeechFailureKind.UNKNOWN
    }

    private fun readErrorCode(error: HttpException): String? {
        return try {
            val body = error.response()?.errorBody()?.string() ?: return null
            gson.fromJson(body, ErrorEnvelopeDto::class.java)?.error?.code
        } catch (e: Exception) {
            null
        }
    }

    private fun readHeader(file: File): ByteArray {
        val buffer = ByteArray(HEADER_BYTES)
        val read = file.inputStream().use { it.read(buffer) }
        return if (read <= 0) ByteArray(0) else buffer.copyOf(read)
    }

    private fun kindForValidation(code: ValidationCode): SpeechFailureKind = when (code) {
        ValidationCode.NO_OLCHIKI -> SpeechFailureKind.UNSUPPORTED_INPUT
        ValidationCode.TOO_LONG -> SpeechFailureKind.TEXT_TOO_LONG
        else -> SpeechFailureKind.INVALID_TEXT
    }

    private companion object {
        const val LANGUAGE = "sat"
        const val MAX_RETRIES = 2
        const val BACKOFF_BASE_MS = 1_000L
        const val HEADER_BYTES = 16
    }
}
