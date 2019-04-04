package org.peercast.pecaviewer.player

import android.app.Application
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.peercast.pecaviewer.service.PecaViewerService
import timber.log.Timber


class PlayerViewModel(a: Application) : AndroidViewModel(a) {
    private val handler = Handler(Looper.getMainLooper())

    /**再生中か*/
    val isPlaying = MutableLiveData<Boolean>(false)

    /**コントロールボタンの表示。タッチして数秒後に消える*/
    val isControlsViewVisible = MutableLiveData<Boolean>(false).apply {
        val r = Runnable { value = false }
        observeForever {
            if (it) {
                handler.removeCallbacks(r)
                handler.postDelayed(r, 8000)
            }
        }
    }

    val isFullScreenMode = MutableLiveData<Boolean>(false)

    //　ツールバーの表示用

    /**チャンネル名*/
    val channelTitle = MutableLiveData<CharSequence>("")

    /**チャンネル詳細、コメントなど*/
    val channelDescription = MutableLiveData<CharSequence>("")

    /**ステータス。配信時間など*/
    val channelStatus = MutableLiveData<CharSequence>("")

    /**警告メッセージ。エラー、バッファ発生など*/
    val channelWarning = MutableLiveData<CharSequence>("").apply {
        //赤文字の警告は数秒後に消え、緑のステータス表示に戻る
        val r = Runnable { value = "" }
        observeForever {
            if (it.isNotEmpty()) {
                handler.removeCallbacks(r)
                handler.postDelayed(r, 5000)
            }
        }
    }

    /**コンタクトURL*/
    val channelContactUrl = MutableLiveData<String>()

    val mediaControllerHandler = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            when (state.state) {
                PlaybackState.STATE_PLAYING -> {
                    val t = state.position / 1000
                    channelStatus.value = "%d:%02d:%02d".format(t / 60 / 60, t / 60 % 60, t % 60)
                    isPlaying.value = true
                }
                PlaybackState.STATE_BUFFERING -> {
                    val buffering = 100f * state.bufferedPosition / state.lastPositionUpdateTime
                    //Timber.d("Buffering.... $buffering")
                    channelWarning.value = "Buffering.. %.1f%%".format(buffering)
                }
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_PAUSED -> {
                    isPlaying.value = false
                }

            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            Timber.d("onMetadataChanged: $metadata")
            channelTitle.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            channelDescription.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE) +
                    metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)
            channelContactUrl.value = metadata.getString(PecaViewerService.METADATA_KEY_CONTACT_URL) ?: ""
        }
    }


}