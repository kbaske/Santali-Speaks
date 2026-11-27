package app.santals.speaks.data.remote

import app.santals.speaks.data.remote.dto.StatusDto
import app.santals.speaks.data.remote.dto.TtsRequestDto
import app.santals.speaks.data.remote.dto.TtsResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Retrofit interface for the Santali Speaks server. Paths are relative to BuildConfig.API_BASE_URL. */
interface SpeechApi {

    @POST("v1/tts")
    suspend fun synthesize(@Body body: TtsRequestDto): TtsResponseDto

    @GET("status")
    suspend fun status(): StatusDto
}
