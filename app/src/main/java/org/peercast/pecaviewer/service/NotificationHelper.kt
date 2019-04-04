package org.peercast.pecaviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import org.peercast.pecaviewer.R


class NotificationHelper(private val service: Service) {
    private val notificationManager: NotificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    //    private val skipToPreviousAction = NotificationCompat.Action(
//        R.drawable.exo_controls_previous,
//        context.getString(R.string.notification_skip_to_previous),
//        MediaButtonReceiver.buildMediaButtonPendingIntent(context, ACTION_SKIP_TO_PREVIOUS))
    private val playAction = NotificationCompat.Action(
        android.R.drawable.ic_media_play,
        "play",
        MediaButtonReceiver.buildMediaButtonPendingIntent(service, PlaybackStateCompat.ACTION_PLAY)
    )
    private val pauseAction = NotificationCompat.Action(
        android.R.drawable.ic_media_pause,
        "pause",
        MediaButtonReceiver.buildMediaButtonPendingIntent(service, PlaybackStateCompat.ACTION_PAUSE)
    )
    private val stopAction = NotificationCompat.Action(
        R.drawable.ic_stop_black_24dp,
        "stop",
        MediaButtonReceiver.buildMediaButtonPendingIntent(service, PlaybackStateCompat.ACTION_STOP)
    )


    private val stopPendingIntent =
        MediaButtonReceiver.buildMediaButtonPendingIntent(service, PlaybackStateCompat.ACTION_STOP)


    fun update(sessionToken: MediaSessionCompat.Token){
        service.startForeground(ID, buildNotification(sessionToken))
    }

    private fun buildNotification(sessionToken: MediaSessionCompat.Token): Notification {
        if (shouldCreateNowPlayingChannel()) {
            createNowPlayingChannel()
        }

        val controller = MediaControllerCompat(service, sessionToken)

        val playbackState = controller.playbackState
        playbackState.state
        val builder = NotificationCompat.Builder(service, NOW_PLAYING_CHANNEL)

        // Only add actions for skip back, play/pause, skip forward, based on what's enabled.
//        var playPauseIndex = 0
//        if (playbackState.isSkipToPreviousEnabled) {
//            builder.addAction(skipToPreviousAction)
//            ++playPauseIndex
//        }
        when (playbackState.state) {
            PlaybackStateCompat.STATE_PLAYING -> builder.addAction(stopAction)
//            PlaybackStateCompat.STATE_PAUSED -> builder.addAction(playAction)
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setCancelButtonIntent(stopPendingIntent)
            .setMediaSession(sessionToken)
            .also {
                if (builder.mActions.isNotEmpty())
                    it.setShowActionsInCompactView(0)
            }
            .setShowCancelButton(true)

        return builder.setContentIntent(controller.sessionActivity)
            .setContentTitle("PecaViewer")
            .also {
                controller.metadata?.description?.let { d ->
                    it.setContentTitle(d.title)
                    it.setContentText(d.subtitle)
                    it.setLargeIcon(d.iconBitmap)
                }
            }
            .setDeleteIntent(stopPendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_live_tv_black_24dp)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun shouldCreateNowPlayingChannel() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !nowPlayingChannelExists()

    @RequiresApi(Build.VERSION_CODES.O)
    private fun nowPlayingChannelExists() =
        notificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNowPlayingChannel() {
        val notificationChannel = NotificationChannel(
            NOW_PLAYING_CHANNEL,
            "PecaViewer",
            NotificationManager.IMPORTANCE_LOW
        )
            .apply {
                //description = context.getString(R.string.notification_channel_description)
            }

        notificationManager.createNotificationChannel(notificationChannel)
    }

    companion object {
        private const val NOW_PLAYING_CHANNEL = "PecaViewer"
        const val ID = 0x1
    }
}
