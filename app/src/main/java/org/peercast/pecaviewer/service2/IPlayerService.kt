package org.peercast.pecaviewer.service2

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 *  VLCVideoLayoutをMediaPlayerにアタッチするために直接アクセスする
 * */
interface IPlayerService {
    fun attachViews(view: VLCVideoLayout)

    fun detachViews()

    fun updateVideoSurfaces()

    /**
     * (前回再生したURLを)再生する
     */
    fun play()

    /**
     * 再生を準備する
     * @param extras PecaPlayからのチャンネル情報を含むインテントのextras
     */
    fun prepareFromUri(uri: Uri, extras: Bundle)

    /**
     * 再生する
     * @param extras PecaPlayからのチャンネル情報を含むインテントのextras
     */
    fun playFromUri(uri: Uri, extras: Bundle)

    /**
     * 一時停止する
     * */
    fun pause()

    /**
     * 停止する
     * */
    fun stop()

    /**
     * 再生中動画のサムネ
     * */
    var thumbnail: Bitmap

    var videoScale: MediaPlayer.ScaleType

    interface Binder {
        val service: IPlayerService
    }

    companion object {
        /**サービスをバインドする
         * */
        fun bind(c: Context, connection: ServiceConnection) {
            val intent = Intent(c, PecaViewerService::class.java)
            c.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        fun unbind(c: Context, connection: ServiceConnection) {
            c.unbindService(connection)
        }
    }
}

