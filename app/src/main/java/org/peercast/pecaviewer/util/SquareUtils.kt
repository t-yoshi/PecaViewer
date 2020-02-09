package org.peercast.pecaviewer.util

import android.app.Application
import androidx.annotation.WorkerThread
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object SquareUtils : KoinComponent {
    private val a by inject<Application>()

    private const val HTTP_USER_AGENT = "Mozilla/5.0 (Linux; U; Android) PecaPlay"
    private const val HTTP_CONNECT_TIMEOUT = 12L
    private const val HTTP_RW_TIMEOUT = 100L
    private const val MAX_CACHE_SIZE = 512 * 1024L

    private val connectionSpecs = listOf(
        ConnectionSpec.CLEARTEXT,
        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()
    )

    private val cacheDir = File(a.filesDir, "okhttp")

    val client = OkHttpClient.Builder()
        .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor {
            it.proceed(
                it.request().newBuilder()
                    .header("User-Agent", HTTP_USER_AGENT)
                    .build()
            )
        }
        .connectionSpecs(connectionSpecs)
        //.dispatcher(Dispatcher(AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService))
        .readTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)

        .also {
            if (BuildConfig.DEBUG) {
                it.addNetworkInterceptor(HttpLoggingInterceptor().also {
                    it.level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
        }
        .cache(Cache(cacheDir, MAX_CACHE_SIZE))
        .build()
}


/**Callback#onResponse内でfを実行し、その結果を返す*/
suspend fun <T> Call.runAwait(@WorkerThread f: (Response) -> T): T {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                kotlin.runCatching {
                    response.use { f(it) }
                }
                    .onSuccess<T>(continuation::resume)
                    .onFailure(::onFailure)
            }

            private fun onFailure(t: Throwable) {
                if (continuation.isCancelled)
                    return
                continuation.resumeWithException(t)
            }

            override fun onFailure(call: Call, e: IOException) {
                onFailure(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
            }
        }
    }
}

suspend fun <T> Call.await(): String {
    return runAwait { it.body?.string() ?: throw IOException("body is null") }
}
