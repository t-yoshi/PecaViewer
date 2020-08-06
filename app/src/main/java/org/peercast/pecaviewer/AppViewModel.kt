package org.peercast.pecaviewer

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.peercast.pecaviewer.chat.ChatViewModel
import org.peercast.pecaviewer.player.PlayerViewModel


class AppViewModel(
    a: Application,
    private val playerViewModel: PlayerViewModel,
    private val chatViewModel: ChatViewModel
) : AndroidViewModel(a) {
    /**
     * スライディングパネルの状態
    EXPANDED=0,
    COLLAPSED=1,
    ANCHORED=2
     * @see com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
     */
    val slidingPanelState = MutableLiveData<Int>()

    /**
     * 没入モード。フルスクリーンかつコントロール類が表示されていない状態。
     * -> systemUiVisibilityを変える。
     * */
    val isImmersiveMode: LiveData<Boolean> = MediatorLiveData<Boolean>().also { ld ->
        val o = Observer<Any> {
            ld.value = playerViewModel.isFullScreenMode.value == true &&
                    playerViewModel.isControlsViewVisible.value != true &&
                    slidingPanelState.value == 0 // EXPANDED
        }
        ld.addSource(playerViewModel.isFullScreenMode, o)
        ld.addSource(playerViewModel.isControlsViewVisible, o)
        ld.addSource(slidingPanelState, o)
    }

    val isPostDialogButtonFullVisible: MutableLiveData<Boolean> =
        MediatorLiveData<Boolean>().also { ld ->
            val o = Observer<Any> {
                ld.value = true
            }
            ld.addSource(playerViewModel.isFullScreenMode, o)
            ld.addSource(playerViewModel.isControlsViewVisible, o)
            ld.addSource(slidingPanelState, o)

            //狭いスマホの画面ではスクロール時に数秒消す
            var j: Job? = null
            ld.observeForever {
                j?.cancel()
                if (!it) {
                    j = viewModelScope.launch {
                        delay(2 * 1000L)
                        ld.value = true
                    }
                }
            }

        }
}
