package org.peercast.pecaviewer.player

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.view.children
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_player.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.AppPreference
import org.peercast.pecaviewer.AppViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.FragmentPlayerBinding
import org.peercast.pecaviewer.service2.IPlayerService
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber


class PlayerFragment : Fragment(), ServiceConnection {

    private val appViewModel by sharedViewModel<AppViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val appPreference by inject<AppPreference>()

    private var service: IPlayerService? = null

    //画面回転時orスクリーンショット処理時には一時的にバックグラウンド再生を許可する
    private var isTemporaryBackgroundPlaying = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return FragmentPlayerBinding.inflate(inflater, container, false).also {
            it.lifecycleOwner = viewLifecycleOwner
            it.viewModel = playerViewModel
        }.root
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
            service?.run {
                if (playerViewModel.isPlaying.value == true)
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
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val scale = appPreference.videoScale
        menu.findItem(R.id.menu_scale).subMenu.children.forEachIndexed { i, mi ->
            mi.isChecked = i == scale.ordinal
            mi.isCheckable = i == scale.ordinal
        }
        menu.findItem(R.id.menu_background).isChecked = appPreference.isBackgroundPlaying
        menu.findItem(R.id.menu_screenshot).isEnabled =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    onScreenShot()
            }
        }

        onPrepareOptionsMenu(vPlayerMenu.menu)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun onScreenShot() {
        playerViewModel.presenter.takeScreenShotAndCreateIntent(
            vVLCVideoLayout, 1280
        ) {
            isTemporaryBackgroundPlaying = true
            try {
                startActivity(Intent.createChooser(it, "Choose an app"))
            } catch (e: ActivityNotFoundException) {
                Timber.e(e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        context?.let { IPlayerService.bind(it, this) }
    }

    override fun onPause() {
        super.onPause()

        if (!appPreference.isBackgroundPlaying && !isTemporaryBackgroundPlaying) {
            service?.stop()
        }
        isTemporaryBackgroundPlaying = false

        service?.let { s ->
            if (appPreference.isBackgroundPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                playerViewModel.presenter.takeScreenShot(vVLCVideoLayout, 256) {
                    s.thumbnail = it
                }
            }
            s.detachViews()
        }

        context?.let { c->
            IPlayerService.unbind(c, this)
        }
        service = null
    }

    override fun onServiceConnected(name: ComponentName, service_: IBinder) {
        service = (service_ as IPlayerService.Binder).service.also {s->
           s.attachViews(vVLCVideoLayout)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

}
