package org.peercast.pecaviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData


class AppViewModel(a: Application) : AndroidViewModel(a) {
    /**
    EXPANDED=0,
    COLLAPSED=1,
    ANCHORED=2
     * @see com.sothree.slidinguppanel.SlidingUpPanelLayout.PanelState
     */
    val slidingPanelState = MutableLiveData<Int>()
}
