package org.peercast.pecaviewer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import org.peercast.pecaviewer.R
import java.io.File


class NotificationHelper(
    private val service: PecaViewerService,
    private val sessionToken: MediaSessionCompat.Token
) {
    private val notificationManager: NotificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val tmpIconFile = File(service.cacheDir, "tmp_icon.png")

    init {
        if (tmpIconFile.isFile)
            tmpIconFile.delete()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    private val playAction = NotificationCompat.Action(
        R.drawable.ic_play_arrow_black_24dp,
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

    fun takeScreenShotForIcon(){
        if (tmpIconFile.isFile)
            tmpIconFile.delete()
        service.screenShot(tmpIconFile.absolutePath, 320, 180)
    }

    fun startForeground() {
        service.startForeground(ID, buildNotification())
    }

    private fun buildNotification(): Notification {

        val controller = MediaControllerCompat(service, sessionToken)
        val builder = NotificationCompat.Builder(service, NOW_PLAYING_CHANNEL)

        when (controller.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING -> builder.addAction(stopAction)
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED -> builder.addAction(playAction)
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setCancelButtonIntent(stopPendingIntent)
            .setMediaSession(sessionToken)
            .also {
                if (builder.mActions.isNotEmpty())
                    it.setShowActionsInCompactView(0)
            }
            .setShowCancelButton(true)
        val largeIcon =
            BitmapFactory.decodeFile(tmpIconFile.absolutePath) ?:
            BitmapFactory.decodeResource(service.resources, R.drawable.ic_launcher)

        return builder.setContentIntent(controller.sessionActivity)
            .setContentTitle("PecaViewer")
            .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
            .setLargeIcon(largeIcon)
            .also {
                controller.metadata?.description?.let { d ->
                    it.setContentTitle(d.title)
                    it.setContentText(d.subtitle)
                    it.setLargeIcon(d.iconBitmap ?: largeIcon)
                }
            }
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(stopPendingIntent)
            .setAutoCancel(true)
            .setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (notificationManager.getNotificationChannel(NOW_PLAYING_CHANNEL) != null)
            return

        val ch = NotificationChannel(
            NOW_PLAYING_CHANNEL,
            "PecaViewer",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(ch)
    }


    companion object {
        private const val NOW_PLAYING_CHANNEL = "PecaViewer"
        const val ID = 0x1
    }
}
