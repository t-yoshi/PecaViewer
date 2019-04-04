package org.peercast.pecaviewer.service

import android.support.v4.media.session.MediaSessionCompat
import androidx.lifecycle.LiveData
import com.github.t_yoshi.vlcext.VLCLogMessage
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * */
interface IPecaViewerService {

    val mediaSession: MediaSessionCompat

    var videoScale: MediaPlayer.ScaleType

    fun attachViews(view: VLCVideoLayout)

    fun detachViews()

    fun updateVideoSurfaces()

    fun screenShot(path: String, width: Int = 0, height: Int=0) : Boolean

    val vlcLogMessage : LiveData<VLCLogMessage>
}