package org.peercast.pecaviewer.service

import androidx.lifecycle.MutableLiveData
@Deprecated("")
sealed class AppServiceEvent {
    class OnConnected(val service: IPecaViewerService) : AppServiceEvent()
    object OnDisconnected : AppServiceEvent()
}

@Deprecated("")
class AppServiceEventLiveData : MutableLiveData<AppServiceEvent>()

