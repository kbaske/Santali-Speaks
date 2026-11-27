package app.santals.speaks.domain.repository

import app.santals.speaks.domain.model.SpeechServiceStatus

interface ServiceStatusRepository {
    /** Asks the server whether Santali speech is configured. Never throws. */
    suspend fun check(): SpeechServiceStatus
}
