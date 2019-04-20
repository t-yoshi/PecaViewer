package org.peercast.pecaviewer.player

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.*
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.github.t_yoshi.vlcext.VLCLogMessage
import kotlinx.android.synthetic.main.fragment_player.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.sharedViewModel
import org.peercast.pecaviewer.AppPreference
import org.peercast.pecaviewer.AppViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.FragmentPlayerBinding
import org.peercast.pecaviewer.service.IPecaViewerService
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber
import java.io.File


class PlayerFragment : Fragment() {

    private val appViewModel by sharedViewModel<AppViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val appPreference by inject<AppPreference>()

    private var mediaController: MediaControllerCompat? = null
    private var service: IPecaViewerService? = null

    //画面回転時orスクリーンショット処理時には一時的にバックグラウンド再生を許可する
    private var isTemporaryBackgroundPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appViewModel.serviceLiveData.observe(this, Observer { srv ->
            if (srv != null) {
                service = srv
                mediaController = MediaControllerCompat(context, srv.mediaSession).apply {
                    registerCallback(playerViewModel.mediaControllerHandler)
                }
                if (isResumed)
                    srv.attachViews(vVLCVideoLayout)

                srv.vlcLogMessage.observe(this, Observer {
                    if (it.level == VLCLogMessage.ERROR &&
                        it.ctx.object_type !in listOf("window")
                    ) {
                        playerViewModel.channelWarning.value = it.msg
                        Timber.w("-> $it")
                    }
                })

            } else {
                mediaController?.unregisterCallback(playerViewModel.mediaControllerHandler)
                mediaController = null
                service?.detachViews()
                service = null
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentPlayerBinding.inflate(inflater, container, false).let {
            it.lifecycleOwner = viewLifecycleOwner
            it.appViewModel = appViewModel
            it.playerViewModel = playerViewModel
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vPlayerMenu.also {
            MenuInflater(it.context).inflate(R.menu.menu_player, it.menu)
            onPrepareOptionsMenu(it.menu)
            it.setOnMenuItemClickListener(::onOptionsItemSelected)
        }

        view.setOnClickListener {
            playerViewModel.isControlsViewVisible.value = true
        }

        vPlay.setOnClickListener {
            mediaController?.transportControls?.run {
                if (playerViewModel.isPlaying.value!!)
                    stop()
                else
                    play()
            }
        }

        vFullScreen.setOnClickListener {
            playerViewModel.isFullScreenMode.let {
                if (it.value != true)
                    isTemporaryBackgroundPlaying = true
                it.value = it.value != true
            }
        }

        vVLCVideoLayout.doOnLayout {
            it.post {
                service?.updateVideoSurfaces()
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val scale = appPreference.videoScale
        menu.findItem(R.id.menu_scale).subMenu.children.forEachIndexed { i, mi ->
           mi.isChecked = i == scale.ordinal
           mi.isCheckable = i == scale.ordinal
        }
        menu.findItem(R.id.menu_background).isChecked = appPreference.isBackgroundPlaying
        //menu.findItem(R.id.menu_screenshot).isEnabled = mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_surface_best_fit,
            R.id.menu_surface_fill,
            R.id.menu_surface_original,
            R.id.menu_surface_fit_screen,
            R.id.menu_surface_16_9,
            R.id.menu_surface_4_3 -> {
                MediaPlayer.ScaleType.values()[item.order].let { scale ->
                    appPreference.videoScale = scale
                    service?.videoScale = scale
                }
            }

            R.id.menu_background -> {
                item.isChecked = !item.isChecked
                appPreference.isBackgroundPlaying = item.isChecked
            }

            R.id.menu_screenshot -> {
                invokeScreenShot()
            }
        }

        onPrepareOptionsMenu(vPlayerMenu.menu)
        return true
    }


    private fun invokeScreenShot() {
        val c = context!!
        val title = mediaController?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) ?: "Screenshot"
        val f = File(c.filesDir, "$title.png")
        if (service?.screenShot(f.absolutePath) != true)
            return
        val u = FileProvider.getUriForFile(c, "org.peercast.pecaviewer.fileprovider", f)
        Timber.i("Screenshot success: $u: ${f.length()}")

        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            putExtra(Intent.EXTRA_STREAM, u)
            putExtra(Intent.EXTRA_TEXT, "$title  #PeerCast")
        }
        isTemporaryBackgroundPlaying = true
        startActivity(Intent.createChooser(i, "Choose an app"))
    }

    override fun onPause() {
        super.onPause()
        service?.detachViews()

        if (!appPreference.isBackgroundPlaying && !isTemporaryBackgroundPlaying) {
            mediaController?.transportControls?.stop()
        }
        isTemporaryBackgroundPlaying = false
    }

    override fun onResume() {
        super.onResume()
        service?.attachViews(vVLCVideoLayout)
    }


}
