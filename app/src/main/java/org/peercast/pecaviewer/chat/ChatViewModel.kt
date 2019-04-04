package org.peercast.pecaviewer.chat

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import org.peercast.pecaviewer.chat.net.ChatThreadConnection
import org.peercast.pecaviewer.chat.net.MessageBody


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

    val messagePagedListLiveData = presenter.createMessageLiveData()
    /**リスト最後のオブジェクトに色をつける*/
    var lastMessage : MessageBody? = null

    val threadInfoLiveData = MutableLiveData<List<ChatThreadConnection.Info>>(emptyList())
    val selectedThreadIndex = MutableLiveData(-1)

    /**現在選択されているスレッドが書き込み可能なら、[ChatThreadConnection]を返す。不可ならnull*/
    val selectedThreadConnection = MutableLiveData<ChatThreadConnection>()


    /**下書き (URL/内容)*/
    val postMessageDraft = HashMap<String, String>()


    init {
        isToolbarVisible.observeForever {
            if (it) {
                handler.removeCallbacks(invisibleToolbarRunnable)
                handler.postDelayed(invisibleToolbarRunnable, 8000)
            }
        }
    }
}


