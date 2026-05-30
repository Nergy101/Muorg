package nl.muorg.android.data.api

import coil.ImageLoader
import coil.request.CachePolicy
import nl.muorg.android.data.preferences.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Builds an OkHttpClient that injects the Bearer token from AppPreferences on every request.
 */
fun buildAuthOkHttpClient(preferences: AppPreferences): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    return OkHttpClient.Builder()
        .addInterceptor { chain ->
            val apiKey = preferences.apiKeyState.value
            val request = chain.request().newBuilder()
                .apply {
                    if (apiKey.isNotBlank()) {
                        header("Authorization", "Bearer $apiKey")
                    }
                }
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()
}

/**
 * Builds a Coil ImageLoader that uses the same auth OkHttpClient so cover art loads correctly.
 */
fun buildCoilImageLoader(
    context: android.content.Context,
    okHttpClient: OkHttpClient,
): ImageLoader {
    return ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()
}
