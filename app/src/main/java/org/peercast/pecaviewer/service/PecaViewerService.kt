package org.peercast.pecaviewer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.session.MediaButtonReceiver
import com.github.t_yoshi.vlcext.VLCExt
import com.github.t_yoshi.vlcext.VLCLogger
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.peercast.core.lib.LibPeerCast
import org.peercast.core.lib.PeerCastController
import org.peercast.core.lib.PeerCastRpcClient
import org.peercast.core.lib.rpc.Channel
import org.peercast.core.lib.rpc.ConnectionStatus
import org.peercast.core.lib.rpc.JsonRpcException
import org.peercast.pecaviewer.AppPreference
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates


class PecaViewerService : Service(), IPecaViewerService, CoroutineScope {

    private val job = Job()
    private lateinit var libVLC: LibVLC
    /**
     *  オリジナルの[org.videolan.libvlc.MediaPlayer]は
     *  DecorViewのサイズを元にしているので全画面表示しかできない。
     *  全画面でない[VLCVideoLayout]内でも表示できるように[org.videolan.libvlc.VideoHelper]にパッチを当てている。
     * */
    private lateinit var player: MediaPlayer
    private lateinit var audioManager: AudioManager
    override lateinit var mediaSession: MediaSessionCompat
        private set
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationHelper: NotificationHelper
    private var peerCastController: PeerCastController? = null
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val appPreference by inject<AppPreference>()

    override fun onCreate() {
        libVLC = LibVLC(this)
        Timber.i("VLC: version=${libVLC.version()}")
        player = MediaPlayer(libVLC)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        player.setEventListener(mediaPlayerEventListener)
        notificationManager = NotificationManagerCompat.from(this)

        mediaSession = MediaSessionCompat(this, TAG).also {
            it.setCallback(sessionCallback)
        }
        notificationHelper = NotificationHelper(this, mediaSession.sessionToken)

        PeerCastController.from(this).also {
            if (it.isInstalled) {
                it.addEventListener(peerCastServiceEventHandler)
                peerCastController = it
                it.bindService()
            }
        }
        VLCLogger.register(libVLC)
    }

    class Binder(
        val service: IPecaViewerService
    ) : android.os.Binder()

    override fun onBind(intent: Intent): IBinder {
        //android.os.Binder.getCallingUid()
        //applicationInfo.uid
        return Binder(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand($intent)")
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        //FIX: Intentにnullが渡されてクラッシュする
        return START_REDELIVER_INTENT
    }

    override val vlcLogMessage = VLCLogger.liveData

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        private fun focusRequest(listener: (Int) -> Unit = {}) =
            AudioFocusRequestCompat.Builder(
                AudioManagerCompat.AUDIOFOCUS_GAIN
            ).setOnAudioFocusChangeListener(listener).build()

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
            Timber.d("command=$command")
        }

        override fun onPlay() {
            playingUrl.let {
                if (it != Uri.EMPTY)
                    onPlayFromUri(it, null)
            }
        }

        var playingChannelId = ""
            private set
        var playingUrl: Uri = Uri.EMPTY
            private set(value) {
                playingChannelId = """([\dA-F]{32})""".toRegex().find(value.path ?: "")
                    ?.groupValues?.getOrNull(1) ?: ""
                field = value
            }

        //PecaPlayからのインテントをもとにメタをセットする。
        private fun metaFromPecaPlayIntentExtra(extras: Bundle?) {
            fun MediaMetadataCompat.Builder.putStringFromExtras(
                metaKey: String, extrasKey: String
            ): MediaMetadataCompat.Builder {
                putString(metaKey, extras?.getString(extrasKey) ?: "")
                return this
            }

            val m = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, playingChannelId)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, playingUrl.toString())
                .putStringFromExtras(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                    LibPeerCast.EXTRA_NAME
                )
                .putStringFromExtras(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                    LibPeerCast.EXTRA_COMMENT
                )
                .putStringFromExtras(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                    LibPeerCast.EXTRA_DESCRIPTION
                )
                .putStringFromExtras(
                    METADATA_KEY_CONTACT_URL,
                    LibPeerCast.EXTRA_CONTACT_URL
                )
                .build()
            mediaSession.setMetadata(m)
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle?) {
            Timber.d("onPlayFromUri($uri, $extras)")
            if (player.isPlaying && playingUrl == uri)
                return
            playingUrl = uri
            metaFromPecaPlayIntentExtra(extras)

            val r = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest {
                //FIX: Serviceが死んでいるのにOnAudioFocusChangeListenerが残っていてクラッシュする
                if (!player.isReleased && player.isPlaying && it != AudioManager.AUDIOFOCUS_GAIN) {
                    player.pause()
                }
            })

            if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mediaSession.isActive = true
                player.play(uri)
            }
        }

        override fun onPause() {
            player.pause()
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest())
        }

        override fun onStop() {
            player.stop()
            mediaSession.isActive = false
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest())
        }
    }

    private val peerCastServiceEventHandler = object : PeerCastController.EventListener, Runnable {
        private val handler = Handler(Looper.getMainLooper())
        private var rpcClient: PeerCastRpcClient? = null
        override fun run() {
            launch {
                try {
                    rpcClient?.getChannels()?.firstOrNull { ch ->
                        ch.status.status in listOf(
                            ConnectionStatus.Receiving,
                            ConnectionStatus.RECEIVE
                        ) &&
                                ch.channelId == sessionCallback.playingChannelId
                    }?.let(::sendMeta)
                } catch (e: JsonRpcException) {
                    Timber.e(e)
                }
            }
            handler.postDelayed(this, 10_000)
        }

        private fun sendMeta(ch: Channel) {
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                        sessionCallback.playingChannelId
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                        sessionCallback.playingUrl.toString()
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, ch.info.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, ch.info.comment)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, ch.info.desc)
                    .putString(METADATA_KEY_CONTACT_URL, ch.info.url)
                    .build()
            )
        }

        override fun onConnectService(controller: PeerCastController) {
            rpcClient = PeerCastRpcClient(controller)
            handler.postDelayed(this, 5_000)
        }

        override fun onDisconnectService(controller: PeerCastController) {
            handler.removeCallbacks(this)
            rpcClient = null
        }
    }


    override var videoScale by Delegates.observable(MediaPlayer.ScaleType.SURFACE_BEST_FIT) { _, _, newValue ->
        player.videoScale = newValue
    }

    override fun updateVideoSurfaces() = player.updateVideoSurfaces()


    private val mediaPlayerEventListener = MediaPlayer.EventListener { ev ->
        val updateTime = SystemClock.elapsedRealtime()

        val state = when (ev.type) {
            MediaPlayer.Event.Playing,
            MediaPlayer.Event.TimeChanged -> {
                PlaybackStateCompat.STATE_PLAYING
            }
            MediaPlayer.Event.Stopped -> {
                stopForeground(true)
                PlaybackStateCompat.STATE_STOPPED
            }
            MediaPlayer.Event.Paused -> {
                PlaybackStateCompat.STATE_PAUSED
            }
            MediaPlayer.Event.Buffering -> {
                PlaybackStateCompat.STATE_BUFFERING
            }
            MediaPlayer.Event.Opening -> {
                PlaybackStateCompat.STATE_CONNECTING
            }
            else -> return@EventListener
        }

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder().setState(
                state, player.time, player.rate, updateTime
            )
                .setBufferedPosition((ev.buffering / 100 * updateTime).toLong())
                .setActions(IMPLEMENTED_PLAYBACK_ACTIONS).build()
        )

        //バックグラウンド再生中に他アプリの割り込みでPausedになった場合、通知バーから復帰できるように
        if (!isViewAttached && ev.type == MediaPlayer.Event.Paused) {
            notificationHelper.startForeground()
        }
    }


    private var isViewAttached = false


    override fun attachViews(view: VLCVideoLayout) {
        if (isViewAttached)
            return
        stopForeground(true)
        //Timber.d("attachViews($view)")
        player.attachViews(view, null, false, false)
        player.videoScale = videoScale
        isViewAttached = true
    }

    override fun detachViews() {
        if (!isViewAttached)
            return
        if (player.isPlaying && appPreference.isBackgroundPlaying) {
            notificationHelper.takeScreenShotForIcon()
            notificationHelper.startForeground()
        }
        //Timber.d("detachViews()")
        player.detachViews()
        isViewAttached = false
    }

    override fun screenShot(path: String, width: Int, height: Int): Boolean {
        if (!path.endsWith(".png"))
            throw IllegalArgumentException("suffix isn't png.")
        return player.hasMedia() && VLCExt.videoTakeSnapshot(player, path, width, height)
    }

    override fun onDestroy() {
        job.cancel()
        VLCLogger.unregister(libVLC)
        peerCastController?.let {
            peerCastServiceEventHandler.onDisconnectService(it)
            it.unbindService()
        }
        mediaSession.setCallback(null)
        mediaSession.release()
        detachViews()
        player.release()
        libVLC.release()
    }

    companion object {
        private const val TAG = "PecaViewerService"

        const val METADATA_KEY_CONTACT_URL = "$TAG.CONTACT_URL"

        private const val IMPLEMENTED_PLAYBACK_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_FROM_URI or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP

    }

}
