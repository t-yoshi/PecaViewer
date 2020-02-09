package org.peercast.pecaviewer.chat

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.peercast.pecaviewer.chat.net2.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.chat.net2.IThreadInfo
import timber.log.Timber


class ChatViewModel(a: Application) : AndroidViewModel(a) {
    val presenter = ChatPresenter(this)

    /**n秒後に自動的にfalse*/
    val isToolbarVisible = MutableLiveData<Boolean>(true)
    val isThreadListVisible = MutableLiveData<Boolean>(false)

    private val handler = Handler(Looper.getMainLooper())

    private val invisibleToolbarRunnable = Runnable {
        isToolbarVisible.value = false
    }


    val chatToolbarTitle = MutableLiveData<CharSequence>("")
    val chatToolbarSubTitle = MutableLiveData<CharSequence>("")

    val isThreadListRefreshing = MutableLiveData<Boolean>(false)
    val isMessageListRefreshing = MutableLiveData<Boolean>(false)

    val messageLiveData = MutableLiveData<List<IMessage>>()
    val threadLiveData = MutableLiveData<List<IThreadInfo>>()

    /**現在選択されているスレッド。されていなければnull*/
    val selectedThread = MutableLiveData<IThreadInfo?>()

    /**現在選択されているスレッドへの書き込み。不可ならnull*/
    val selectedThreadPoster = MutableLiveData<IBoardThreadPoster?>()

    /**下書き (URL/内容)*/
    val messageDraft = HashMap<String, String>()

    /**接続エラー等*/
    val networkMessage = MutableLiveData<String>()

    init {
        isToolbarVisible.observeForever {
            if (it) {
                handler.removeCallbacks(invisibleToolbarRunnable)
                handler.postDelayed(invisibleToolbarRunnable, 8000)
            }
        }

        networkMessage.observeForever {
            if (it.isNotBlank()) {
                Timber.d(it)
                Toast.makeText(a, it, Toast.LENGTH_LONG).show()
            }
        }
    }
}


