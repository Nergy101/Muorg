package nl.muorg.android.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import nl.muorg.android.data.api.MuorgApiService
import nl.muorg.android.data.api.buildAuthOkHttpClient
import nl.muorg.android.data.api.buildCoilImageLoader
import nl.muorg.android.data.db.AppDatabase
import nl.muorg.android.data.db.LocalPlaylistDao
import nl.muorg.android.data.db.LocalTrackDao
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.data.repository.LibraryRepository
import nl.muorg.android.data.repository.PlaylistRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppPreferences(
        @ApplicationContext context: Context,
    ): AppPreferences = AppPreferences(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(preferences: AppPreferences): OkHttpClient =
        buildAuthOkHttpClient(preferences)

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        preferences: AppPreferences,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        // Use a dynamic CallFactory so the base URL is resolved per-request from DataStore.
        // This means saving new credentials in ConnectScreen takes effect immediately
        // without needing to recreate the Retrofit/OkHttp singletons.
        val dynamicCallFactory = okhttp3.Call.Factory { request ->
            val savedUrl = runBlocking { preferences.serverUrl.first() }
            val base = if (savedUrl.isBlank()) "http://localhost:7700/" else "${savedUrl.trimEnd('/')}/"
            // Re-resolve the host/scheme from saved URL while keeping the path from the request.
            val originalUrl = request.url
            val newUrl = base.toHttpUrl().newBuilder()
                .encodedPath(originalUrl.encodedPath)
                .encodedQuery(originalUrl.encodedQuery)
                .build()
            val newRequest = request.newBuilder().url(newUrl).build()
            okHttpClient.newCall(newRequest)
        }

        // We must supply a non-empty baseUrl to satisfy Retrofit's builder validation,
        // even though the actual URL is resolved dynamically above.
        return Retrofit.Builder()
            .baseUrl("http://localhost:7700/")
            .callFactory(dynamicCallFactory)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideMuorgApiService(retrofit: Retrofit): MuorgApiService =
        retrofit.create(MuorgApiService::class.java)

    @Provides
    @Singleton
    fun provideLibraryRepository(api: MuorgApiService): LibraryRepository =
        LibraryRepository(api)

    @Provides
    @Singleton
    fun providePlaylistRepository(api: MuorgApiService): PlaylistRepository =
        PlaylistRepository(api)

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader = buildCoilImageLoader(context, okHttpClient)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "muorg_local.db").build()

    @Provides
    @Singleton
    fun provideLocalTrackDao(db: AppDatabase): LocalTrackDao = db.localTrackDao()

    @Provides
    @Singleton
    fun provideLocalPlaylistDao(db: AppDatabase): LocalPlaylistDao = db.localPlaylistDao()
}
