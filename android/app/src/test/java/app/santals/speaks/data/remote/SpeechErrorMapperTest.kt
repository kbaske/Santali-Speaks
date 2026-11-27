package app.santals.speaks.data.remote

import app.santals.speaks.domain.model.SpeechFailureKind
import org.junit.Test
import kotlin.test.assertEquals

class SpeechErrorMapperTest {

    @Test
    fun mapsEveryDocumentedServerCode() {
        val expected = mapOf(
            "SERVICE_NOT_CONFIGURED" to SpeechFailureKind.SERVICE_NOT_CONFIGURED,
            "SERVICE_UNAVAILABLE" to SpeechFailureKind.SERVICE_UNAVAILABLE,
            "TIMEOUT" to SpeechFailureKind.TIMEOUT,
            "RATE_LIMITED" to SpeechFailureKind.RATE_LIMITED,
            "MODEL_UNAVAILABLE" to SpeechFailureKind.MODEL_UNAVAILABLE,
            "AUTHENTICATION_FAILED" to SpeechFailureKind.AUTHENTICATION_FAILED,
            "UNSUPPORTED_INPUT" to SpeechFailureKind.UNSUPPORTED_INPUT,
            "INVALID_TEXT" to SpeechFailureKind.INVALID_TEXT,
            "PAYLOAD_TOO_LARGE" to SpeechFailureKind.TEXT_TOO_LONG,
            "UNSUPPORTED_LANGUAGE" to SpeechFailureKind.UNSUPPORTED_LANGUAGE,
            "UNSUPPORTED_AUDIO_FORMAT" to SpeechFailureKind.UNSUPPORTED_AUDIO_FORMAT,
            "GENERATION_FAILED" to SpeechFailureKind.GENERATION_FAILED,
            "BAD_RESPONSE" to SpeechFailureKind.BAD_RESPONSE,
        )
        for ((code, kind) in expected) {
            assertEquals(kind, SpeechErrorMapper.fromServerCode(code, 500), code)
        }
    }

    @Test
    fun fallsBackToHttpStatusForUnknownOrMissingCodes() {
        assertEquals(SpeechFailureKind.RATE_LIMITED, SpeechErrorMapper.fromServerCode(null, 429))
        assertEquals(SpeechFailureKind.SERVICE_UNAVAILABLE, SpeechErrorMapper.fromServerCode("WHATEVER", 503))
        assertEquals(SpeechFailureKind.UNKNOWN, SpeechErrorMapper.fromServerCode(null, 418))
    }

    @Test
    fun retryabilityIsConsistentForTransientFailures() {
        assertEquals(true, SpeechFailureKind.TIMEOUT.retryable)
        assertEquals(true, SpeechFailureKind.NO_NETWORK.retryable)
        assertEquals(false, SpeechFailureKind.UNSUPPORTED_INPUT.retryable)
        assertEquals(false, SpeechFailureKind.MODEL_UNAVAILABLE.retryable)
    }
}
