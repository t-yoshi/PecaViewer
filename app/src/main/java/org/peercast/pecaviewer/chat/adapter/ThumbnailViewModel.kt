package org.peercast.pecaviewer.chat.adapter

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.databinding.ObservableField
import com.squareup.picasso.Downloader
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import okhttp3.*
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.BuildConfig
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.util.ISquareHolder
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ThumbnailViewModel {
    val background = ObservableField<Drawable>()
    val image = ObservableField<Drawable>()
    val description = ObservableField("")
    val clickListener = ObservableField(DO_NOTHING)

    private var prevTarget: Target? = null

    abstract class BasePresenter(protected val vm: ThumbnailViewModel) : Target, KoinComponent {
        protected val a by inject<Application>()

        init {
            //以前の(遅い)読み込みをキャンセル
            vm.prevTarget?.let {
                ThumbnailLoader.cancelRequest(it)
            }
            vm.clickListener.set(DO_NOTHING)
            vm.description.set("")
            vm.background.set(
                a.getDrawable(
                    when (this) {
                        is YouTubePresenter -> R.drawable.frame_bg_red
                        is NicoPresenter -> R.drawable.frame_bg_grey
                        else -> R.drawable.frame_bg_blue
                    }
                )
            )
            vm.prevTarget = this
        }

        abstract fun load()

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
            vm.image.set(placeHolderDrawable)
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
            vm.image.set(errorDrawable ?: a.getDrawable(R.drawable.ic_warning_gray_24dp))
            vm.description.set(e.message)
        }

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            //Timber.d("from-> $from")
            vm.image.set(BitmapDrawable(a.resources, bitmap))
        }
    }

    // YouTubeのサムネを表示する
    //   クリックすると動画へ飛ぶ
    class YouTubePresenter(
        vm: ThumbnailViewModel,
        private val u: Uri,
        private val thumbnail: Uri
    ) : BasePresenter(vm) {
        override fun load() {
            vm.clickListener.set(OnActionView(a, u))
            ThumbnailLoader.loadInto(thumbnail, this)
        }
    }

    // ニコのサムネを表示する
    //   クリックすると動画へ飛ぶ
    class NicoPresenter(
        vm: ThumbnailViewModel,
        private val u: Uri,
        private val videoId: String
    ) : BasePresenter(vm), Callback {
        private val square by inject<ISquareHolder>()
        override fun load() {
            val req = Request.Builder()
                .url("http://ext.nicovideo.jp/api/getthumbinfo/$videoId")
                .cacheControl(MAX_STALE_10DAYS)
                .build()
            square.okHttpClient.newCall(req).enqueue(this)
            vm.clickListener.set(OnActionView(a, u))
        }

        override fun onFailure(call: Call, e: IOException) {
            Timber.w(e)
            onBitmapFailed(e, null)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val s = response.body?.string() ?: throw IOException("body is null")
                val u = RE_THUMBNAIL_URL.find(s)?.groupValues?.get(1)
                    ?: throw IOException("missing <thumbnail_url>")
                handler.post {
                    ThumbnailLoader.loadInto(Uri.parse(u), this)
                }
            } catch (e: IOException) {
                onFailure(call, e)
            }
        }

        companion object {
            private val handler = Handler(Looper.getMainLooper())
            private val RE_THUMBNAIL_URL =
                """<thumbnail_url>(https?://.+)</thumbnail_url>""".toRegex()
            private val MAX_STALE_10DAYS =
                CacheControl.Builder().maxStale(10, TimeUnit.DAYS).build()
        }
    }

    class OnActionView(private val c: Context, private val u: Uri) : Runnable {
        override fun run() {
            c.startActivity(Intent(Intent.ACTION_VIEW, u).also {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    // 1: ?KBのサイズ制限ありで読み込む
    //(2: サイズ制限に引っかかった場合、クリックしてリトライ -> サイズ制限なしで読み込む)
    // 3: クリックしたらビューワで表示
    class DefaultPresenter(
        vm: ThumbnailViewModel,
        private val u: Uri,
        private val showImageViewer: (Bitmap) -> Unit
    ) : BasePresenter(vm) {
        private var showPopup: Runnable = DO_NOTHING
        private var retryLoading: Runnable = DO_NOTHING

        override fun load() {
            showPopup = Runnable {
                //Timber.d("showPopup: $u")
                ThumbnailLoader.loadInto(u, this)
            }
            retryLoading = Runnable {
                //Timber.d("retryLoading: $u")
                ThumbnailLoader.unlimitedUrl[u.toString()] = true
                ThumbnailLoader.loadInto(u, this)
            }
            ThumbnailLoader.loadInto(u, this)
        }

        override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
            if (e is ImageFileSizeException) {
                vm.image.set(a.getDrawable(R.drawable.ic_help_outline_gray_24dp))
                vm.description.set(
                    when {
                        e.length > 0 -> "%dKB".format(e.length / 1024)
                        else -> "?KB"
                    }
                )
                vm.clickListener.set(retryLoading)
            } else {
                super.onBitmapFailed(e, errorDrawable)
            }
        }

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            super.onBitmapLoaded(bitmap, from)
            if (vm.clickListener.get() == showPopup) {
                showImageViewer(bitmap)
            } else {
                vm.clickListener.set(showPopup)
            }
        }
    }

    companion object {
        private val DO_NOTHING: Runnable = Runnable { }
    }
}

private class ImageFileSizeException(val length: Int) : Exception()

private object ThumbnailLoader : KoinComponent {
    private val a by inject<Application>()
    private val square by inject<ISquareHolder>()
    private val picasso: Picasso = Picasso.Builder(a)
        .downloader(LimitedFileSizeDownloader)
        .indicatorsEnabled(BuildConfig.DEBUG)
        .loggingEnabled(BuildConfig.DEBUG)
        .build()

    //このURLはサイズ無制限で読み込む
    val unlimitedUrl = ConcurrentHashMap<String, Boolean>()

    @MainThread
    fun loadInto(u: Uri, target: Target) {
        picasso.load(u)
            .into(target)
    }

    @MainThread
    fun cancelRequest(target: Target) {
        picasso.cancelRequest(target)
    }

    private object LimitedFileSizeDownloader : Downloader {
        private val delegate = OkHttp3Downloader(square.okHttpClient)

        override fun load(request: Request): Response {
            //Timber.d("$request")
            if (!request.cacheControl.noCache) {
                val reqCache = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
                val resCache = delegate.load(reqCache)
                if (resCache.isSuccessful)
                    return resCache
            }

            if (unlimitedUrl[request.url.toString()] != true) {
                val reqHead = request.newBuilder().head().build()
                val resHead = delegate.load(reqHead)
                if (!resHead.isSuccessful)
                    return resHead

                val length = resHead.header("Content-Length")?.toIntOrNull() ?: -1
                Timber.d(" length=$length")
                if (length < 0 || length > MAX_IMAGE_SIZE) {
                    throw ImageFileSizeException(length)
                }
            }
            return delegate.load(request)
        }

        override fun shutdown() {
            delegate.shutdown()
        }
    }

    const val MAX_IMAGE_SIZE = 512 * 1024
}
