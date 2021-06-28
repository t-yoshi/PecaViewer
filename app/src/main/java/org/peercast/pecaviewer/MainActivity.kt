package org.peercast.pecaviewer

import android.content.*
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.peercast.pecaplay.PecaPlayIntent
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.chat.PostMessageDialogFragment
import org.peercast.pecaviewer.databinding.ActivityMainBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import org.peercast.pecaviewer.service2.ACTION_STOP
import org.peercast.pecaviewer.service2.IPlayerService
import org.peercast.pecaviewer.util.ThemeUtils
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(),
    ServiceConnection, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private lateinit var binding: ActivityMainBinding
    private val playerViewModel by viewModel<PlayerViewModel>()
    private val chatViewModel by viewModel<ChatViewModel>()
    private val appViewModel by viewModel<AppViewModel> {
        parametersOf(
            playerViewModel,
            chatViewModel
        )
    }
    private val appPreference by inject<AppPreference>()
    private var onServiceConnect: (IPlayerService) -> Unit = {}
    private var service: IPlayerService? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            delegate.localNightMode = AppCompatDelegate.getDefaultNightMode()
        }

        super.onCreate(savedInstanceState)

        if (intent.hasExtra(PecaPlayIntent.EXTRA_NIGHT_MODE)) {
            val nightMode = intent.getBooleanExtra(PecaPlayIntent.EXTRA_NIGHT_MODE, false)
            val changed = appPreference.isNightMode != nightMode
            appPreference.isNightMode = nightMode
            ThemeUtils.setNightMode(this, nightMode)
            if (changed && Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
                recreate()//5.1まで再生成必要
                return
            }
        }

        requestedOrientation = when (appPreference.isFullScreenMode) {
            true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        binding = ActivityMainBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.chatViewModel = chatViewModel
            binding.playerViewModel = playerViewModel
            binding.appViewModel = appViewModel
            binding.lifecycleOwner = this
        }

        onViewCreated()

        playerViewModel.isFullScreenMode.let { ld ->
            ld.value = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ld.observe(this, Observer {
                appPreference.isFullScreenMode = it
                requestedOrientation = when (it) {
                    true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            })
        }

        //再生中は自動消灯しない
        playerViewModel.isPlaying.observe(this, Observer {
            when (it) {
                true -> window::addFlags
                else -> window::clearFlags
            }(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        })

        onServiceConnect = {
            it.prepareFromUri(intent.data ?: Uri.EMPTY, intent.extras ?: Bundle.EMPTY)
            if (savedInstanceState?.getBoolean(STATE_PLAYING) != false)
                it.play()
        }

        registerReceiver(receiver, IntentFilter(ACTION_STOP))

        IPlayerService.bind(this, this)
    }

    private fun onViewCreated() {
        binding.vPostDialogButton.setOnClickListener {
            //フルスクリーン時には一時的にコントロールボタンを
            //表示させないとOSのナビゲーションバーが残る
            if (playerViewModel.isFullScreenMode.value == true)
                playerViewModel.isControlsViewVisible.value = true
            val f = PostMessageDialogFragment()
            f.show(supportFragmentManager, "tag#PostMessageDialogFragment")
        }

        binding.vSlidingUpPanel.addPanelSlideListener(panelSlideListener)

        if (isLandscapeMode) {
            binding.vSlidingUpPanel.anchorPoint = 1f
            initPanelState(SlidingUpPanelLayout.PanelState.EXPANDED)

            appViewModel.isImmersiveMode.observe(this, Observer {
                window.decorView.systemUiVisibility = if (it) {
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

        //昇降ボタン
        binding.toolbar.vNavigation.setOnClickListener {
            binding.vSlidingUpPanel.run {
                panelState = when {
                    anchorPoint < 1f -> SlidingUpPanelLayout.PanelState.ANCHORED
                    panelState == SlidingUpPanelLayout.PanelState.EXPANDED -> {
                        SlidingUpPanelLayout.PanelState.COLLAPSED
                    }
                    else -> SlidingUpPanelLayout.PanelState.EXPANDED
                }
            }
        }
    }

    //通知バーの停止ボタンが押されたとき
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP -> finish()
            }
        }
    }

    private val isLandscapeMode: Boolean
        get() = resources.getBoolean(R.bool.isLandscapeMode)

    private fun initPanelState(state: SlidingUpPanelLayout.PanelState) = launch {
        //launch{} で"requestLayout() improperly called"を回避する
        binding.vSlidingUpPanel.panelState = state
        binding.vSlidingUpPanel.doOnLayout {
            panelSlideListener.onPanelStateChanged(
                binding.vSlidingUpPanel,
                SlidingUpPanelLayout.PanelState.COLLAPSED,
                state
            )
        }
    }

    private val panelSlideListener = object : SlidingUpPanelLayout.PanelSlideListener {
        override fun onPanelSlide(panel: View, __slideOffset: Float) {
            val b = binding.vPlayerFragmentContainer.bottom
            binding.vPlayerFragmentContainer.updatePadding(top = panel.height - b)
            binding.vChatFragmentContainer.updatePadding(bottom = b - binding.toolbar.vPlayerToolbar.height)
        }

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
                    onPanelSlide(panel, 0f)
                    chatViewModel.isToolbarVisible.value = true
                }
                SlidingUpPanelLayout.PanelState.EXPANDED,
                SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                    binding.vPlayerFragmentContainer.updatePadding(top = 0)
                    binding.vChatFragmentContainer.updatePadding(bottom = 0)
                }
                else -> {
                }
            }
            when (newState) {
                //プレーヤー前面
                SlidingUpPanelLayout.PanelState.EXPANDED -> {
                    binding.toolbar.vNavigation.setImageResource(R.drawable.ic_expand_less_white_24dp)
                }
                //チャット前面
                SlidingUpPanelLayout.PanelState.COLLAPSED -> {
                    binding.toolbar.vNavigation.setImageResource(R.drawable.ic_expand_more_white_24dp)
                }
                else -> {
                    binding.toolbar.vNavigation.setImageDrawable(null)
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

    override fun onServiceConnected(name: ComponentName?, service_: IBinder) {
        service = (service_ as IPlayerService.Binder).service.also(onServiceConnect)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PLAYING, playerViewModel.isPlaying.value ?: true)
    }

    override fun onBackPressed() {
//        if (chatViewModel.isThreadListVisible.value == true){
//            chatViewModel.isThreadListVisible.value = false
//            return
//        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        unregisterReceiver(receiver)
        if (service != null)
            IPlayerService.unbind(this, this)
    }

    companion object {
        private const val STATE_PLAYING = "STATE_PLAYING"
    }
}
