package app.santals.speaks.di

import android.content.Context
import androidx.room.Room
import app.santals.speaks.BuildConfig
import app.santals.speaks.data.local.AppDatabase
import app.santals.speaks.data.local.HistoryDao
import app.santals.speaks.data.remote.SpeechApi
import app.santals.speaks.data.repository.HistoryRepositoryImpl
import app.santals.speaks.data.repository.ServiceStatusRepositoryImpl
import app.santals.speaks.data.repository.SpeechRepositoryImpl
import app.santals.speaks.domain.repository.HistoryRepository
import app.santals.speaks.domain.repository.ServiceStatusRepository
import app.santals.speaks.domain.repository.SpeechRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (BuildConfig.DEBUG) {
            // BASIC logs only the method, URL and status. Request bodies (user text) are never logged.
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideSpeechApi(retrofit: Retrofit): SpeechApi = retrofit.create(SpeechApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSpeechRepository(impl: SpeechRepositoryImpl): SpeechRepository

    @Binds
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    abstract fun bindServiceStatusRepository(impl: ServiceStatusRepositoryImpl): ServiceStatusRepository
}
