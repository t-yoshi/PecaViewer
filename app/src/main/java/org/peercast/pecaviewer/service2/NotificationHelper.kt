package org.peercast.pecaviewer.service2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.peercast.pecaviewer.R
import kotlin.properties.Delegates

const val ACTION_PLAY = "org.peercast.pecaviewer.play"
const val ACTION_PAUSE = "org.peercast.pecaviewer.pause"
const val ACTION_STOP = "org.peercast.pecaviewer.stop"


class NotificationHelper(private val service: PecaViewerService) : Observer<PlayerServiceEvent> {
    private val notificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onChanged(ev: PlayerServiceEvent) {
        when (ev) {
            is PeerCastChannelEvent -> {
                contentTitle = ev.name
                contentText = "${ev.comment} ${ev.desc}"
            }
            is MediaPlayerEvent -> {
                isPlaying = ev.isPlaying
            }
        }
    }

    private fun <T> updateNotifyWhenChanged(initialValue: T) =
        Delegates.observable(initialValue) { _, oldValue, newValue ->
            if (isForeground && oldValue != newValue)
                notificationManager.notify(ID, buildNotification())
        }

    private var contentTitle by updateNotifyWhenChanged("")
    private var contentText by updateNotifyWhenChanged("")
    private var isPlaying by updateNotifyWhenChanged(true)

    private val launcherIcon = BitmapFactory.decodeResource(
        service.resources,
        R.drawable.ic_launcher
    )

    var thumbnail by updateNotifyWhenChanged<Bitmap>(launcherIcon)

    val launchIntentExtras = Bundle()

    fun setDefaultThumbnail() {
        thumbnail = launcherIcon
    }

    private var isForeground = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    private val playAction = NotificationCompat.Action(
        R.drawable.ic_play_arrow_black_24dp,
        "play",
        buildActionPendingIntent(ACTION_PLAY)
    )

    private val pauseAction = NotificationCompat.Action(
        R.drawable.ic_pause_black_24dp,
        "pause",
        buildActionPendingIntent(ACTION_PAUSE)
    )

    private val stopAction = NotificationCompat.Action(
        R.drawable.ic_stop_black_24dp,
        "stop",
        buildActionPendingIntent(ACTION_STOP)
    )

    private fun buildActionPendingIntent(act: String): PendingIntent {
        return PendingIntent.getBroadcast(
            service,
            0,
            Intent(act).also { it.setPackage(service.packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    //PecaPlay経由で復帰する
    private fun buildPecaPlayPendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, service.playingUrl)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtras(launchIntentExtras)
        intent.setPackage("org.peercast.pecaplay")
        return PendingIntent.getActivity(
            service,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun startForeground() {
        isForeground = true
        service.startForeground(ID, buildNotification())
        service.startService(Intent(service, service.javaClass))
    }

    fun stopForeground() {
        isForeground = false
        service.stopForeground(true)
        service.stopSelf()
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(
            service, NOW_PLAYING_CHANNEL
        )

        when (isPlaying) {
            true -> {
                builder.addAction(stopAction)
                builder.addAction(pauseAction)
            }
            else -> builder.addAction(playAction)
        }

        androidx.media.app.NotificationCompat.MediaStyle(builder)
            .setCancelButtonIntent(stopAction.actionIntent)
            //.setShowActionsInCompactView(0)
            .setShowCancelButton(true)

        return builder
            .setContentIntent(buildPecaPlayPendingIntent())
            .setContentTitle("PecaViewer")
            .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
            .setLargeIcon(service.thumbnail)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(stopAction.actionIntent)
            .setAutoCancel(true)
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