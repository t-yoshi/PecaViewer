package org.peercast.pecaviewer

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.player_toolbar.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.viewModel
import org.peercast.core.lib.LibPeerCast
import org.peercast.pecaplay.PecaPlayIntent
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.ActivityMainBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val appViewModel by viewModel<AppViewModel>()
    private val playerViewModel by viewModel<PlayerViewModel>()
    private val chatViewModel by viewModel<ChatViewModel>()
    private val appPreference by inject<AppPreference>()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            delegate.localNightMode = AppCompatDelegate.getDefaultNightMode()
        }

        super.onCreate(savedInstanceState)

        requestedOrientation = when (appPreference.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.chatViewModel = chatViewModel
        binding.lifecycleOwner = this

        onViewCreated()

        val streamUrl = intent.data ?: Uri.EMPTY

        //PecaPlay以外からの呼び出し
        if (streamUrl.scheme in listOf<String?>("http", "mmsh")) {
            val ts = createTaskStackBuilder(streamUrl, intent.extras)
            ts.startActivities()
            return finish()
        }


        appViewModel.serviceLiveData.observe(this, Observer {
            if (streamUrl.scheme != "pecaplay")
                return@Observer
            //通知バーをタップして復帰できるように
            val pi = createTaskStackBuilder(streamUrl, intent.extras)
                .getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT)
            it.mediaSession.setSessionActivity(pi)

            if (savedInstanceState == null || savedInstanceState.getBoolean(STATE_PLAYING)) {
                val proto = when (".wmv" in streamUrl.path ?: "") {
                    true -> "mmsh"
                    else -> "http"
                }
                MediaControllerCompat(this, it.mediaSession).transportControls.playFromUri(
                    streamUrl.buildUpon().scheme(proto).build(),
                    intent.extras
                )
            }
        })

        intent.getStringExtra(LibPeerCast.EXTRA_CONTACT_URL)?.let {
            chatViewModel.presenter.loadUrl(it)
        }

        playerViewModel.isFullScreenMode.let {
            it.value = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            it.observe(this, Observer {
                appPreference.isFullScreenMode = it
                requestedOrientation = when (it) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            })
        }

        if (intent.hasExtra(PecaPlayIntent.EXTRA_NIGHT_MODE))
            appPreference.isNightMode = intent.getBooleanExtra(PecaPlayIntent.EXTRA_NIGHT_MODE, false)


        //再生中は自動消灯しない
        playerViewModel.isPlaying.observe(this, Observer {
            when (it) {
                true -> window::addFlags
                else -> window::clearFlags
            }(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        })
    }

    private fun onViewCreated() {
        vPostButton.setOnClickListener {
            val f = PostMessageDialogFragment()
            f.show(supportFragmentManager, "tag#PostMessageDialogFragment")
        }

        vNavigation.setOnClickListener {
            vSlidingUpPanel.run {
                panelState = when {
                    anchorPoint < 1f -> SlidingUpPanelLayout.PanelState.ANCHORED
                    panelState == SlidingUpPanelLayout.PanelState.EXPANDED -> {
                        SlidingUpPanelLayout.PanelState.COLLAPSED
                    }
                    else -> SlidingUpPanelLayout.PanelState.EXPANDED
                }
            }
        }

        vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        if (isLandscapeMode) {
            vSlidingUpPanel.anchorPoint = 1f
            initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)

            playerViewModel.isControlsViewVisible.observe(this, Observer {
                window.decorView.systemUiVisibility = if (!it &&
                    playerViewModel.isFullScreenMode.value == true &&
                    vSlidingUpPanel.panelState != SlidingUpPanelLayout.PanelState.COLLAPSED
                ) {
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                } else {
                    0
                }
            })
        } else {
            initPanelState(appPreference.initPanelState)
        }
    }

    private val isLandscapeMode: Boolean
        get() = resources.getBoolean(R.bool.isLandscapeMode)

    private fun initPanelState(state: SlidingUpPanelLayout.PanelState) = launch {
        //launch{} で"requestLayout() improperly called"を回避する
        vSlidingUpPanel.panelState = state
        vSlidingUpPanel.doOnLayout {
            panelSlideListener.onPanelStateChanged(
                vSlidingUpPanel,
                SlidingUpPanelLayout.PanelState.COLLAPSED,
                state
            )
        }
    }

    private val panelSlideListener = object : SlidingUpPanelLayout.SimplePanelSlideListener() {
        override fun onPanelStateChanged(
            panel: View,
            previousState: SlidingUpPanelLayout.PanelState,
            newState: SlidingUpPanelLayout.PanelState
        ) {
            when (newState) {
                //パネル位置・中間
                SlidingUpPanelLayout.PanelState.ANCHORED -> {
                    //Timber.d("vPlayerFragmentContainer=$vPlayerFragmentContainer")
                    //Timber.d("vChatFragmentContainer=$vChatFragmentContainer")
                    val b = vPlayerFragmentContainer.bottom
                    vPlayerFragmentContainer.updatePadding(top = panel.height - b)
                    vChatFragmentContainer.updatePadding(bottom = b - vToolbar.height)
                    chatViewModel.isToolbarVisible.value = true
                }
                SlidingUpPanelLayout.PanelState.EXPANDED,
                SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                    vPlayerFragmentContainer.updatePadding(top = 0)
                    vChatFragmentContainer.updatePadding(bottom = 0)
                }
                else -> {}
            }
            when (newState) {
                //プレーヤー前面
                SlidingUpPanelLayout.PanelState.EXPANDED -> {
                    vNavigation.setImageResource(R.drawable.ic_expand_less_white_24dp)
                }
                //チャット前面
                SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                    vNavigation.setImageResource(R.drawable.ic_expand_more_white_24dp)
                }
                else -> {
                    vNavigation.setImageDrawable(null)
                }
            }
            if (newState in listOf(
                    SlidingUpPanelLayout.PanelState.EXPANDED,
                    SlidingUpPanelLayout.PanelState.COLLAPSED,
                    SlidingUpPanelLayout.PanelState.ANCHORED
                )
            ) {
                appViewModel.slidingPanelState.value = newState.ordinal
                if (!isLandscapeMode)
                    appPreference.initPanelState = newState
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PLAYING, playerViewModel.isPlaying.value ?: true)
    }

    /**
     * http://の暗黙インテントをpecaplay://に変換し、
     * pecaplay -> プレーヤーのスタックを作る。
     * */
    private fun createTaskStackBuilder(steamUri: Uri, extras: Bundle?): TaskStackBuilder {
        val iPecaPlay = packageManager.getLaunchIntentForPackage("org.peercast.pecaplay")
        val iViewer = Intent(
            Intent.ACTION_VIEW,
            steamUri.buildUpon().scheme("pecaplay").build(),
            this, javaClass
        )
        iViewer.putExtras(extras ?: Bundle.EMPTY)

        return TaskStackBuilder.create(this).also {
            if (iPecaPlay != null)
                it.addNextIntent(iPecaPlay)
            it.addNextIntent(iViewer)
        }
    }

    companion object {
        private const val STATE_PLAYING = "STATE_PLAYING"


    }
}
