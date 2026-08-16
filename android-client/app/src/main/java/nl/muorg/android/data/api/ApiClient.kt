package nl.muorg.android.data.api

import coil.ImageLoader
import coil.request.CachePolicy
import nl.muorg.android.data.preferences.AppPreferences
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
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
    // Image traffic gets its OWN dispatcher. OkHttp defaults to five in-flight
    // requests PER HOST, and every cover in the app comes from the one host —
    // so a screen of playlist tiles (four covers each) plus the catalog call
    // all queue through five slots. On a fast link they drain unnoticed; over
    // real network latency the tail of that queue hits the call timeout and
    // the covers come back as errors, which is what left 2x2 collages with
    // missing quarters on device but never on an emulator.
    //
    // newBuilder shares the connection pool, so this costs sockets, not
    // connections, and keeps slow cover fetches from starving API calls.
    val imageClient = okHttpClient.newBuilder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            }
        )
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    return ImageLoader.Builder(context)
        .okHttpClient(imageClient)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        // A cover is immutable for a given track id, so cache it regardless of
        // what headers the server sends; otherwise every revisit refetches.
        .respectCacheHeaders(false)
        .build()
}
