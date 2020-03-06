package org.peercast.pecaviewer.player

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.io.File
import java.io.IOException

class PlayerPresenter(private val viewModel: PlayerViewModel) {
    private val a = viewModel.getApplication<Application>()

    /**スクリーンショットを撮る*/
    @RequiresApi(Build.VERSION_CODES.N)
    fun takeScreenShot(
        view: VLCVideoLayout, maxWidthDp: Int,
        @MainThread onSuccess: (Bitmap) -> Unit
    ) {
        val sf = view.findViewById<SurfaceView?>(org.videolan.R.id.surface_video)
        if (sf == null || sf.width <= 0 || sf.height <= 0) {
            Timber.e("org.videolan.R.id.surface_video not found.")
            return
        }
        val w: Int // DP
        val h: Int // DP
        val dm = sf.context.resources.displayMetrics
        if (maxWidthDp * dm.density < sf.width) {
            w = maxWidthDp
            h = (1f * sf.height / sf.width * maxWidthDp).toInt()
        } else {
            w = (sf.width / dm.density).toInt()
            h = (sf.height / dm.density).toInt()
        }
        Timber.d("Bitmap w=$w, h=$h")

        val b = Bitmap.createBitmap(dm, w, h, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())

        PixelCopy.request(sf.holder.surface, b, { copyResult ->
            Timber.d("PixelCopy: $copyResult")
            if (copyResult == PixelCopy.SUCCESS) {
                onSuccess(b)
            }
        }, handler)
    }


    /**スクリーンショットを撮り、関連アプリに送るためのインテントを作成する。*/
    @RequiresApi(Build.VERSION_CODES.N)
    fun takeScreenShotAndCreateIntent(
        view: VLCVideoLayout, maxWidthDp: Int,
        @MainThread onSuccess: (Intent) -> Unit
    ) {
        val title = viewModel.channelTitle.value ?: "Screenshot"
        val f = File(a.filesDir, "$title.png")
        takeScreenShot(view, maxWidthDp) { b ->
            try {
                f.outputStream().use { os ->
                    b.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            } catch (e: IOException) {
                Timber.e(e)
                return@takeScreenShot
            }
            val u = FileProvider.getUriForFile(a, "org.peercast.pecaviewer.fileprovider", f)
            Timber.i("Screenshot success: $u: ${f.length()}")

            val intent = Intent(Intent.ACTION_SEND).also {
                it.type = "image/png"
                it.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                it.putExtra(Intent.EXTRA_STREAM, u)
                it.putExtra(Intent.EXTRA_TEXT, "$title  #PeerCast")
            }
            onSuccess(intent)
        }
    }


}