package org.peercast.pecaviewer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.koin.core.KoinComponent
import org.peercast.pecaviewer.service.IPecaViewerService
import org.peercast.pecaviewer.service.PecaViewerService


class AppViewModel(private val a: Application) : AndroidViewModel(a), KoinComponent {
    /**
        EXPANDED=0,
        COLLAPSED=1,
        ANCHORED=2
    */
    val slidingPanelState = MutableLiveData<Int>()

    val serviceLiveData = MutableLiveData<IPecaViewerService>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceLiveData.value = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            serviceLiveData.value = (service as PecaViewerService.Binder).service
        }
    }

    init {
        val i = Intent("Start", Uri.EMPTY, a, PecaViewerService::class.java)
        a.startService(i)
        a.bindService(i, serviceConnection, 0)
    }


    override fun onCleared() {
        a.unbindService(serviceConnection)
        serviceConnection.onServiceDisconnected(null)
    }
}
