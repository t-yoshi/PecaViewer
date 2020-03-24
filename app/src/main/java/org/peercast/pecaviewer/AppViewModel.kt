package org.peercast.pecaviewer

import android.app.Application
import androidx.lifecycle.*
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
     * -> systemUiVisibilityを変え、FABも半透明にする。
     * */
    val isImmersiveMode: LiveData<Boolean> = MediatorLiveData<Boolean>().also { ld ->
        val o = Observer<Any> {
            ld.value = playerViewModel.isFullScreenMode.value == true &&
                    playerViewModel.isControlsViewVisible.value != true &&
                    slidingPanelState.value == 0
        }
        ld.addSource(playerViewModel.isFullScreenMode, o)
        ld.addSource(playerViewModel.isControlsViewVisible, o)
        ld.addSource(slidingPanelState, o)
    }
}
