package org.peercast.pecaviewer.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import org.peercast.pecaviewer.BuildConfig
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object SquareUtils {

    private const val HTTP_USER_AGENT = "Mozilla/5.0 (Linux; U; Android) PecaPlay"
    private const val HTTP_CONNECT_TIMEOUT = 12L
    private const val HTTP_RW_TIMEOUT = 100L
    private const val MAX_CACHE_SIZE = 512 * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor {
                it.proceed(
                    it.request().newBuilder()
                        .header("User-Agent", HTTP_USER_AGENT)
                        //.header("Connection", "close")
                        .build()
                )
            }
        .connectionSpecs(
            listOf(
                ConnectionSpec.CLEARTEXT,
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()
            )
        )
        .readTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(HTTP_RW_TIMEOUT, TimeUnit.SECONDS)
        .also {
            if (BuildConfig.DEBUG) {
                it.addInterceptor(HttpLoggingInterceptor().also {
                    it.level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
        }
//        .cookieJar(SimpleCookieJar)
        .build()

//    private object SimpleCookieJar : CookieJar {
//        private val cookie = HashMap<HttpUrl, List<Cookie>>()
//
//        override fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {
//            cookie[url] = cookies
//        }
//
//        override fun loadForRequest(url: HttpUrl): List<Cookie> {
//            return cookie[url] ?: emptyList()
//        }
//    }

    fun retrofitBuilder(): Retrofit.Builder = Retrofit.Builder().client(client)
}

suspend fun <T> Call<T>.exAwait(): Response<T> = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback<T> {
        override fun onFailure(call: Call<T>, t: Throwable) {
            if (!cont.isCancelled) {
                cont.resumeWithException(t)
            }
        }

        override fun onResponse(call: Call<T>, response: Response<T>) {
            cont.resume(response)
        }
    })

    cont.invokeOnCancellation {
        cancel()
    }
}