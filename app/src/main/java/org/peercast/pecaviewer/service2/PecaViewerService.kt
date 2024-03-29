package org.peercast.pecaviewer.service2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.t_yoshi.vlcext.VLCLogger
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.peercast.core.lib.LibPeerCast
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.notify.NotifyChannelType
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.core.lib.rpc.ChannelInfo
import org.peercast.pecaviewer.AppPreference
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.util.*
import kotlin.properties.Delegates


class PecaViewerService : LifecycleService(), IPlayerService {

    private lateinit var libVLC: LibVLC

    /**
     *  オリジナルの[org.videolan.libvlc.MediaPlayer]は
     *  DecorViewのサイズを元にしているので全画面表示しかできない。
     *  全画面でない[VLCVideoLayout]内でも表示できるように[org.videolan.libvlc.VideoHelper]にパッチを当てている。
     * */
    private lateinit var player: MediaPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper
    private var peerCastController: PeerCastController? = null

    private var jobPlay: Job? = null

    private val appPreference by inject<AppPreference>()
    private val eventLiveData by inject<PlayerServiceEventLiveData>()
    var playingUrl: Uri = Uri.EMPTY
        private set
    override var thumbnail: Bitmap
        set(value) {
            notificationHelper.thumbnail = value
        }
        get() = notificationHelper.thumbnail


    private var isViewAttached = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_STOP -> stop()
                else -> Timber.e("$intent")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        libVLC = LibVLC(this, arrayListOf("--no-spu", "--http-reconnect"))
        Timber.i("VLC: version=${LibVLC.version()}")

        player = MediaPlayer(libVLC)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        player.setEventListener(mediaPlayerEventListener)
        notificationManager = NotificationManagerCompat.from(this)

        notificationHelper = NotificationHelper(this)
        eventLiveData.observeForever(notificationHelper)

        PeerCastController.from(this).also {
            if (it.isInstalled) {
                it.eventListener = pecaEventHandler
                peerCastController = it
            }
        }
        VLCLogger.register(libVLC) { log ->
            //Timber.d("$log")
            if (log.msg == "can't get Subtitles Surface")
                return@register

            lifecycleScope.launch {
                eventLiveData.value = VLCLogEvent(log)
            }
        }
        registerReceiver(receiver, IntentFilter().also {
            it.addAction(ACTION_PLAY)
            it.addAction(ACTION_STOP)
            it.addAction(ACTION_PAUSE)
        })
    }

    override fun onBind(intent: Intent): Binder {
        super.onBind(intent)

        return object : Binder(), IPlayerService.Binder {
            override val service = this@PecaViewerService
        }
    }

    private val audioFocusRequest = AudioFocusRequestCompat.Builder(
        AudioManagerCompat.AUDIOFOCUS_GAIN
    )
        .setAudioAttributes(AA_MEDIA_MOVIE)
        .setOnAudioFocusChangeListener { focusChange ->
            if (player.isPlaying && focusChange != AudioManager.AUDIOFOCUS_GAIN) {
                pause()
            }
        }
        .build()

    private fun bindPeerCastService(){
        lifecycleScope.launch {
            peerCastController?.let {
                if (!it.isConnected)
                    peerCastController?.tryBindService()
            }
        }
    }

    override fun prepareFromUri(uri: Uri, extras: Bundle) {
        if (uri.host in listOf("localhost", "127.0.0.1"))
            bindPeerCastService()

        /**PecaPlayからのインテントをもとにタイトル等を表示する。*/
        extras.let { b ->
            val name = b.getString(LibPeerCast.EXTRA_NAME, "")
            val comment = b.getString(LibPeerCast.EXTRA_COMMENT, "")
            val desc = b.getString(LibPeerCast.EXTRA_DESCRIPTION, "")
            val contact = b.getString(LibPeerCast.EXTRA_CONTACT_URL, "")

            Timber.i("Channel info from PacaPlay's intent. [name=$name, comment=$comment, desc=$desc, contact=$contact]")
            eventLiveData.value = PeerCastChannelEvent(name, contact, desc, comment)
            notificationHelper.launchIntentExtras.putAll(b)
        }

        if (playingUrl != uri) {
            stop()
            notificationHelper.setDefaultThumbnail()
            playingUrl = uri
        }
    }

    override fun playFromUri(uri: Uri, extras: Bundle) {
        Timber.d("onPlayFromUri($uri)")
        if (player.isPlaying && uri == playingUrl)
            return
        prepareFromUri(uri, extras)
        play()
    }

    override fun play() {
        if (playingUrl == Uri.EMPTY || player.isPlaying)
            return

        val r = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
        if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            jobPlay?.cancel()
            jobPlay = lifecycleScope.launch {
                player.play(playingUrl)

                var tried = 0
                while (tried++ < 6) {
                    if (!appPreference.isAutoReconnect)
                        break
                    delay(10_000)
                    if (player.isPlaying){
                        tried = 0
                    } else {
                        player.play(playingUrl)
                    }
                }
            }
        } else {
            Timber.w("audio focus not granted: $r")
        }
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
        jobPlay?.cancel()
    }

    private val pecaEventHandler = object : PeerCastController.EventListener {
        override fun onConnectService(controller: PeerCastController) {
        }

        override fun onDisconnectService() {
        }

        override fun onNotifyChannel(
            type: NotifyChannelType,
            channelId: String,
            channelInfo: ChannelInfo
        ) {
            Timber.d("onNotifyChannel: $type $channelId $channelInfo")
            if (playingUrl.path?.contains(channelId) == true) {
                val ev = PeerCastChannelEvent(channelInfo)
                //接続中に空白だけが来ても無視
                if ("${ev.name}${ev.desc}${ev.comment}".isNotBlank())
                    eventLiveData.value = ev
            }
        }

        override fun onNotifyMessage(types: EnumSet<NotifyMessageType>, message: String) {
            Timber.d("onNotifyMessage: $types $message")
            eventLiveData.value = PeerCastNotifyMessageEvent(types, message)
        }
    }

    override var videoScale by Delegates.observable(MediaPlayer.ScaleType.SURFACE_BEST_FIT) { _, _, newScale ->
        player.videoScale = newScale
    }

    override fun updateVideoSurfaces() = player.updateVideoSurfaces()


    private val mediaPlayerEventListener = MediaPlayer.EventListener { ev ->
        lifecycleScope.launch {
            eventLiveData.value = MediaPlayerEvent(ev, player.isPlaying)
        }
        when (ev.type) {
            MediaPlayer.Event.Paused,
            MediaPlayer.Event.Stopped ->
                AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        }
        Timber.d("ev=%03d", ev.type)

        //バックグラウンド再生中に他アプリの割り込みでPausedになった場合、通知バーから復帰できるように
        if (!isViewAttached && ev.type == MediaPlayer.Event.Paused) {
            notificationHelper.startForeground()
        } else if (ev.type == MediaPlayer.Event.Stopped) {
            notificationHelper.stopForeground()
        }
    }

    override fun attachViews(view: VLCVideoLayout) {
        if (isViewAttached)
            return
        notificationHelper.stopForeground()
        Timber.d("attachViews($view)")
        player.attachViews(view, null, false, false)
        player.videoScale = videoScale
        isViewAttached = true
    }

    override fun detachViews() {
        if (!isViewAttached)
            return

        if (player.isPlaying && appPreference.isBackgroundPlaying) {
            notificationHelper.startForeground()
        }
        Timber.d("detachViews()")
        player.detachViews()
        isViewAttached = false
    }


    override fun onDestroy() {
        super.onDestroy()

        VLCLogger.unregister(libVLC)
        peerCastController?.unbindService()

        eventLiveData.removeObserver(notificationHelper)
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)

        unregisterReceiver(receiver)

        detachViews()
        player.release()
        libVLC.release()
    }

    companion object {
        private val AA_MEDIA_MOVIE = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
            .build()
    }
}


