package org.peercast.pecaviewer.player

import android.app.Application
import androidx.lifecycle.*
import com.github.t_yoshi.vlcext.VLCLogMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.service2.*
import org.videolan.libvlc.MediaPlayer


class PlayerViewModel(a: Application) : AndroidViewModel(a), KoinComponent {
    val presenter = PlayerPresenter(this)

    //サービスからのイベントを元に表示する
    private val eventLiveData by inject<PlayerServiceEventLiveData>()

    /**再生中か*/
    val isPlaying: LiveData<Boolean> = MediatorLiveData<Boolean>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev) {
                is MediaPlayerEvent -> {
                    ld.value = ev.isPlaying
                }
            }
        }
    }

    /**コントロールボタンの表示。タッチして数秒後に消える*/
    val isControlsViewVisible = MutableLiveData<Boolean>(false).also { ld ->
        var j: Job? = null
        ld.observeForever {
            if (it) {
                j?.cancel()
                j = viewModelScope.launch {
                    delay(8_000)
                    ld.value = false
                }
            }
        }
    }

    val isFullScreenMode = MutableLiveData(false)

    //　ツールバーの表示用

    /**チャンネル名*/
    val channelTitle: MutableLiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev) {
                is PeerCastChannelEvent -> {
                    ld.value = ev.name
                }
            }
        }
    }

    /**チャンネル詳細、コメントなど*/
    val channelDescription: MutableLiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev) {
                is PeerCastChannelEvent -> {
                    ld.value = "${ev.desc} ${ev.comment}"
                }
            }
        }
    }

    /**ステータス。配信時間など*/
    val channelStatus: LiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev){
                is MediaPlayerEvent->{
                    when (ev.ev.type) {
                        MediaPlayer.Event.TimeChanged -> {
                            val t = ev.ev.timeChanged / 1000
                            ld.value =
                                "%d:%02d:%02d".format(t / 60 / 60, t / 60 % 60, t % 60)
                        }
                    }
                }
             }
        }
    }

    /**警告メッセージ。エラー、バッファ発生など*/
    val channelWarning: LiveData<CharSequence> = MediatorLiveData<CharSequence>().also { ld ->
        //赤文字の警告は数秒後に消え、緑のステータス表示に戻る
        var j: Job? = null
        ld.observeForever {
            if (it.isNotEmpty()) {
                j?.cancel()
                j = viewModelScope.launch {
                    delay(5_000)
                    ld.value = ""
                }
            }
        }

        ld.addSource(eventLiveData) { ev ->
            when (ev) {
                is VLCLogEvent -> ev.log.let { log ->
                    if (log.level == VLCLogMessage.ERROR &&
                        log.ctx.object_type !in listOf("window")
                    ) {
                        ld.value = log.msg
                        //Timber.w("-> $log")
                    }
                }
                is PeerCastNotifyMessageEvent ->{
                    ld.value = ev.message
                }
                is MediaPlayerEvent -> {
                    when (ev.ev.type) {
                        MediaPlayer.Event.Buffering -> {
                            ld.value = a.getString(R.string.buffering, ev.ev.buffering)
                        }
                    }
                }
            }
        }
    }

    /**コンタクトURL*/
    val channelContactUrl: MutableLiveData<String> = MediatorLiveData<String>().also { ld ->
        ld.addSource(eventLiveData) { ev ->
            when (ev) {
                is PeerCastChannelEvent -> {
                    ld.value = ev.url
                }
            }
        }
    }

    init {
        //フルスクリーン直後はボタン類を隠す
        isFullScreenMode.observeForever {
            if (it)
                isControlsViewVisible.value = false
        }

//        eventLiveData.observeForever {
//            Timber.d("$it")
//        }
    }
}