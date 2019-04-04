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
import org.peercast.core.Channel
import org.peercast.core.PeerCastController
import org.peercast.pecaplay.PecaPlayIntent
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
        notificationHelper = NotificationHelper(this)

        mediaSession = MediaSessionCompat(this, TAG).also {
            it.setCallback(sessionCallback)
        }

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
                    PecaPlayIntent.EXTRA_NAME
                )
                .putStringFromExtras(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                    PecaPlayIntent.EXTRA_COMMENT
                )
                .putStringFromExtras(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                    PecaPlayIntent.EXTRA_DESCRIPTION
                )
                .putStringFromExtras(
                    METADATA_KEY_CONTACT_URL,
                    PecaPlayIntent.EXTRA_CONTACT_URL
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
                if (player.isPlaying && it != AudioManager.AUDIOFOCUS_GAIN) {
                    player.stop()
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
        override fun run() {
            launch {
                peerCastController?.let {
                    if (!it.isConnected)
                        return@let

                    it.getChannels().firstOrNull { ch ->
                        ch.status == Channel.S_RECEIVING && ch.id == sessionCallback.playingChannelId
                    }?.let(::sendMeta)
                }
            }
            handler.postDelayed(this, 10_000)
        }

        private fun sendMeta(ch: Channel) {
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, sessionCallback.playingChannelId)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, sessionCallback.playingUrl.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, ch.info.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, ch.info.comment)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, ch.info.desc)
                    .putString(METADATA_KEY_CONTACT_URL, ch.info.url)
                    .build()
            )
        }

        override fun onConnectService(controller: PeerCastController) {
            handler.postDelayed(this, 5_000)
        }

        override fun onDisconnectService(controller: PeerCastController) {
            handler.removeCallbacks(this)
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
    }


    private var isAttached = false


    override fun attachViews(view: VLCVideoLayout) {
        if (isAttached)
            return
        stopForeground(true)
        //Timber.d("attachViews($view)")
        player.attachViews(view, null, false, false)
        player.videoScale = videoScale
        isAttached = true
    }

    override fun detachViews() {
        if (!isAttached)
            return
        if (player.isPlaying && appPreference.isBackgroundPlaying) {
            notificationHelper.update(mediaSession.sessionToken)
        }
        //Timber.d("detachViews()")
        player.detachViews()
        isAttached = false
    }

    override fun screenShot(path: String, width: Int, height: Int): Boolean {
        if (!path.endsWith(".png"))
            throw IllegalArgumentException()
        return player.hasMedia() && VLCExt.videoTakeSnapshot(player, path, width, height)
    }

    override fun onDestroy() {
        job.cancel()
        VLCLogger.unregister(libVLC)
        peerCastController?.let {
            peerCastServiceEventHandler.onDisconnectService(it)
            it.unbindService()
        }
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
