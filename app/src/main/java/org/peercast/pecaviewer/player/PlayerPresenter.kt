package org.peercast.pecaviewer.player

import android.content.Context

class PlayerPresenter(private val viewModel: PlayerViewModel) {
    val c : Context = viewModel.getApplication()

//    private val browserCallback = object : MediaBrowserCompat.ConnectionCallback () {
//        override fun onConnected() {
//            Timber.d("onConnected()")
//            MediaControllerCompat(c, mediaBrowser.sessionToken).let {
//                //if (intent.data?.scheme in listOf("http", "mmsh"))
//                //    it.transportControls.playFromUri(intent.data, null)
//                it.registerCallback(object : MediaControllerCompat.Callback(){
//                    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
//                        Timber.d("onPlaybackStateChanged($state)")
//                    }
//                })
//                //it.unregisterCallback()
//            }
//        }
//
//        override fun onConnectionSuspended() {
//            super.onConnectionSuspended()
//            Timber.d("onConnectionSuspended()")
//        }
//
//        override fun onConnectionFailed() {
//            super.onConnectionFailed()
//            Timber.d("onConnectionFailed()")
//        }
//    }
//
//
//    private val mediaBrowser = MediaBrowserCompat(c,
//        ComponentName(c, PecaViewerService::class.java),
//        browserCallback, null)

}