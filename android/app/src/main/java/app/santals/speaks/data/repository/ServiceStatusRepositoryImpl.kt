package app.santals.speaks.data.repository

import app.santals.speaks.data.remote.SpeechApi
import app.santals.speaks.domain.model.SpeechServiceStatus
import app.santals.speaks.domain.repository.ServiceStatusRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ServiceStatusRepositoryImpl @Inject constructor(
    private val api: SpeechApi,
) : ServiceStatusRepository {

    override suspend fun check(): SpeechServiceStatus = try {
        if (api.status().speechConfigured == true) SpeechServiceStatus.AVAILABLE
        else SpeechServiceStatus.UNAVAILABLE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SpeechServiceStatus.UNAVAILABLE
    }
}
