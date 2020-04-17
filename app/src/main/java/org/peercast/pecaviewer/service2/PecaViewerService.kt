package org.peercast.pecaviewer.service2

import android.app.Service
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
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.t_yoshi.vlcext.VLCLogger
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.peercast.core.lib.JsonRpcException
import org.peercast.core.lib.LibPeerCast
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.PeerCastRpcClient
import org.peercast.core.lib.notify.NotifyChannelType
import org.peercast.core.lib.notify.NotifyMessageType
import org.peercast.core.lib.rpc.ChannelInfo
import org.peercast.core.lib.rpc.ConnectionStatus
import org.peercast.pecaviewer.AppPreference
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates


class PecaViewerService : Service(), IPlayerService, CoroutineScope {

    private val job = Job()
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

    override val coroutineContext: CoroutineContext
        get() = job

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

        libVLC = LibVLC(this, arrayListOf("--http-reconnect"))
        Timber.i("VLC: version=${libVLC.version()}")

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
            Timber.d("$log")
            launch(Dispatchers.Main) {
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
        peerCastController?.let {
            if (!it.isConnected)
                it.bindService()
        }

        return object : Binder(), IPlayerService.Binder {
            override val service = this@PecaViewerService
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand($intent)")
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> {
                stop()
            }
            else -> {
                return super.onStartCommand(intent, flags, startId)
            }
        }
        // ACTION_PLAY,... の場合、stopSelfする
        stopSelf(startId)
        return START_NOT_STICKY
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

    override fun prepareFromUri(uri: Uri, extras: Bundle?) {
        extras?.let { b ->
            /**PecaPlayからのインテントをもとにタイトル等を表示する。*/
            val name = b.getString(LibPeerCast.EXTRA_NAME)
            val comment = b.getString(LibPeerCast.EXTRA_COMMENT, "")
            val desc = b.getString(LibPeerCast.EXTRA_DESCRIPTION, "")
            val contact = b.getString(LibPeerCast.EXTRA_CONTACT_URL, "")

            Timber.d("name=$name, comment=$comment, desc=$desc, contact=$contact")
            if (name != null) {
                eventLiveData.value = PeerCastChannelEvent(name, contact, desc, comment)
                notificationHelper.launchIntentExtras.putAll(b)
            }
        }
        if (playingUrl != uri) {
            player.stop()
            notificationHelper.setDefaultThumbnail()
            playingUrl = uri
        }
    }

    override fun playFromUri(uri: Uri, extras: Bundle?) {
        Timber.d("onPlayFromUri($uri)")
        if (player.isPlaying && uri == playingUrl)
            return
        prepareFromUri(uri, extras)

        val r = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
        if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player.play(uri)
        } else {
            Timber.w("audio focus not granted: $r")
        }
    }

    override fun play() {
        playFromUri(playingUrl)
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    private val pecaEventHandler = object : PeerCastController.EventListener {
        private var j: Job? = null

        override fun onConnectService(controller: PeerCastController) {
            val rpcClient = PeerCastRpcClient(controller)
            j = launch {
                while (isActive) {
                    try {
                        rpcClient.getChannels().firstOrNull { ch ->
                            ch.status.status in listOf(
                                ConnectionStatus.Receiving,
                                ConnectionStatus.RECEIVE
                            ) && playingUrl.path?.contains(ch.channelId) == true
                        }?.let { ch ->
                            launch (Dispatchers.Main){
                                eventLiveData.value = PeerCastChannelEvent(ch.info)
                            }
                        }
                    } catch (e: JsonRpcException) {
                        Timber.e(e)
                        break
                    }
                    delay(30_000)
                }
            }
        }

        override fun onNotifyChannel(
            type: NotifyChannelType,
            channelId: String,
            channelInfo: ChannelInfo
        ) {
            //eventLiveData.value = PeerCastChannelEvent(channelInfo)
        }

        override fun onNotifyMessage(types: EnumSet<NotifyMessageType>, message: String) {
            eventLiveData.value = PeerCastNotifyMessageEvent(types, message)
        }

        override fun onDisconnectService() {
            j?.cancel()
        }
    }

    override var videoScale by Delegates.observable(MediaPlayer.ScaleType.SURFACE_BEST_FIT) { _, _, newScale ->
        player.videoScale = newScale
    }

    override fun updateVideoSurfaces() = player.updateVideoSurfaces()


    private val mediaPlayerEventListener = MediaPlayer.EventListener { ev ->
        launch (Dispatchers.Main){
            eventLiveData.value = MediaPlayerEvent(ev, player.isPlaying)
        }
        when (ev.type) {
            MediaPlayer.Event.Paused,
            MediaPlayer.Event.Stopped ->
                AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        }
        //Timber.d("ev=0x%03x".format(ev.type))

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
        job.cancel()
        VLCLogger.unregister(libVLC)
        peerCastController?.let {
            pecaEventHandler.onDisconnectService()
            it.unbindService()
        }

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


