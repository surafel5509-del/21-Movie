package com.torrentclaw.movies.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.torrentclaw.movies.BuildConfig
import com.torrentclaw.movies.network.ApiKeyProvider
import com.torrentclaw.movies.network.AuthInterceptor
import com.torrentclaw.movies.network.BuildTimeApiKey
import com.torrentclaw.movies.network.TorrentClawApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 20L
    private const val USER_AGENT = "TorrentClawMovies/${BuildConfig.VERSION_NAME} (Android)"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true      // the API adds fields regularly
        isLenient = true              // some upstreams stringify numbers
        coerceInputValues = true      // null instead of crashing on bad enums
        explicitNulls = false
        encodeDefaults = false
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(apiKeyProvider: ApiKeyProvider): AuthInterceptor =
        AuthInterceptor(apiKeyProvider = apiKeyProvider, userAgent = USER_AGENT)

    /**
     * The key compiled into the APK, empty in public builds. Bound separately
     * from any other [String] so injection stays unambiguous.
     */
    @Provides
    @Singleton
    @BuildTimeApiKey
    fun provideBuildTimeApiKey(): String = BuildConfig.TORRENTCLAW_API_KEY

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // Debug: bodies. Release: off, so magnet links never hit logcat.
            level = if (BuildConfig.HTTP_LOGGING_ENABLED) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.TORRENTCLAW_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideTorrentClawApi(retrofit: Retrofit): TorrentClawApi =
        retrofit.create(TorrentClawApi::class.java)

}
