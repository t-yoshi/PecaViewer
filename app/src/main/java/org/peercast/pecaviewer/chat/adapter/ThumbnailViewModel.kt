package org.peercast.pecaviewer.chat.adapter

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import com.squareup.picasso.Downloader
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.BuildConfig
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.util.ISquareHolder
import timber.log.Timber

class ThumbnailViewModel {
    val isYouTube = ObservableBoolean(false)
    val image = ObservableField<Drawable>()
    val description = ObservableField("")
    var clickListener: Runnable = DO_NOTHING
        private set
    private var prevTarget: Target? = null


    abstract class BasePresenter(protected val vm: ThumbnailViewModel) : Target, KoinComponent {
        init {
            //以前の(遅い)読み込みをキャンセル
            vm.prevTarget?.let {
                ThumbnailLoader.cancelRequest(it)
            }
            vm.clickListener = DO_NOTHING
            vm.description.set("")
            vm.isYouTube.set(this is YouTubePresenter)
            vm.prevTarget = this
        }

        protected val a by inject<Application>()

        abstract fun load()

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
            vm.image.set(placeHolderDrawable)
        }

        override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
            vm.image.set(a.getDrawable(R.drawable.ic_warning_gray_24dp))
            if (e?.javaClass?.name == "com.squareup.picasso.NetworkRequestHandler\$ResponseException") {
                vm.description.set(e.message)
            }
        }

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            Timber.d("from-> $from")
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
            vm.clickListener = Runnable {
                a.startActivity(Intent(Intent.ACTION_VIEW, u).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            ThumbnailLoader.loadInto(thumbnail, this)
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
                Timber.d("showPopup: $u")
                ThumbnailLoader.loadInto(u, this)
            }
            retryLoading = Runnable {
                Timber.d("retryLoading: $u")
                ThumbnailLoader.unlimitedUrl[u.toString()] = true
                ThumbnailLoader.loadInto(u, this)
            }
            ThumbnailLoader.loadInto(u, this)
        }

        override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
            super.onBitmapFailed(e, errorDrawable)
            if (e is ImageFileSizeException) {
                vm.image.set(a.getDrawable(R.drawable.ic_help_outline_gray_24dp))
                vm.description.set(
                    when {
                        e.length > 0 -> "%dKB".format(e.length / 1024)
                        else -> "?KB"
                    }
                )
                vm.clickListener = retryLoading
            }
        }

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
            super.onBitmapLoaded(bitmap, from)
            if (vm.clickListener == showPopup) {
                showImageViewer(bitmap)
            } else {
                vm.clickListener = showPopup
            }
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
        val unlimitedUrl = HashMap<String, Boolean>()

        fun loadInto(u: Uri, target: Target) {
            picasso
                .load(u)
                .into(target)
        }

        fun cancelRequest(target: Target) {
            picasso.cancelRequest(target)
        }

        private object LimitedFileSizeDownloader : Downloader {
            private val delegate = OkHttp3Downloader(square.okHttpClient)

            override fun load(request: Request): Response {
                Timber.d("$request")
                if (unlimitedUrl[request.url.toString()] != true) {
                    val reqCache = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
                    val resCache = delegate.load(reqCache)
                    if (resCache.isSuccessful)
                        return resCache

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
    }

    companion object {
        private val DO_NOTHING = Runnable { }
        private const val MAX_IMAGE_SIZE = 512 * 1024
    }
}