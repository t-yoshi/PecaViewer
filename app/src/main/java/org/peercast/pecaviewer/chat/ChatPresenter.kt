package org.peercast.pecaviewer.chat

import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.AppPreference
import org.peercast.pecaviewer.chat.net2.IBoardConnection
import org.peercast.pecaviewer.chat.net2.IBoardThreadConnection
import org.peercast.pecaviewer.chat.net2.IThreadInfo
import org.peercast.pecaviewer.chat.net2.openBoardConnection
import org.peercast.pecaviewer.util.localizedSystemMessage
import timber.log.Timber
import java.io.IOException

class ChatPresenter(private val chatViewModel: ChatViewModel) : KoinComponent, CoroutineScope {
    private val appPrefs by inject<AppPreference>()
    private var boardConn: IBoardConnection? = null
        set(value) {
            field = value
            chatViewModel.chatToolbarTitle.value = value?.info?.title
        }

    override val coroutineContext = chatViewModel.viewModelScope.coroutineContext
    private var loadingJob: Job? = null

    //コンタクトURL。配信者が更新しないかぎり変わらない。
    private var contactUrl = ""

    /**スレッドのリストを含め、全体を再読込する。*/
    fun reload() {
        loadUrl(contactUrl, true)
    }

    fun reloadThread() {
        launch {
            chatViewModel.isMessageListRefreshing.value = true
            try {
                val conn = boardConn
                if (conn is IBoardThreadConnection) {
                    chatViewModel.selectedThreadPoster.value =
                        if (conn.info.isPostable) conn else null

                    val messages = conn.loadMessages()
                    if (messages != chatViewModel.messageLiveData.value) {
                        chatViewModel.messageLiveData.value = messages
                        postNetworkErrorMessage("")
                    } else {
                        //postNetworkErrorMessage("have not changed.")
                    }
                } else {
                    postNetworkErrorMessage("thread is not selected.")
                }
            } catch (e: IOException) {
                postNetworkErrorMessage(e.localizedSystemMessage())
            } finally {
                chatViewModel.isMessageListRefreshing.value = false
            }
        }
    }


    /**コンタクトURLを読込む。*/
    fun loadUrl(url: String, isForce: Boolean = false) {
        //接続中のリロードは無視
        if (url.isEmpty() || loadingJob?.isActive == true)
            return
        if (!url.matches("""^https?://.+""".toRegex())) {
            Timber.w("invalid url: $url")
            return
        }

        if (url != contactUrl || isForce) {
            contactUrl = url
            //以前にスレッドを選択していたのなら
            val alternateUrl = appPrefs.userSelectedContactUrlMap[url]
            if (alternateUrl != url) {
                Timber.i("alternate contact url: $alternateUrl")
            }
            loadingJob = launch {
                doLoadUrl(alternateUrl)
            }
        }
    }

    @MainThread
    private suspend fun doLoadUrl(url: String) {
        Timber.d("doLoadUrl: $url")

        try {
            chatViewModel.isThreadListRefreshing.value = true
            val conn = openBoardConnection(url)
            boardConn = conn

            chatViewModel.threadLiveData.postValue(conn.loadThreads())

            if (conn is IBoardThreadConnection) {
                threadSelect(conn.info)
            } else {
                threadSelect(null)
            }
            postNetworkErrorMessage("")
        } catch (e: IOException) {
            threadSelect(null)
            postNetworkErrorMessage(e.localizedSystemMessage())
        } finally {
            chatViewModel.isThreadListRefreshing.value = false
        }
    }

    fun threadSelect(info: IThreadInfo?) {
        chatViewModel.selectedThread.postValue(info)

        if (info == null) {
            //boardConn = null
            chatViewModel.selectedThreadPoster.value = null
            Timber.w("Thread not selected: $contactUrl")
            return
        }

        launch {
            try {
                val threadConn = boardConn?.openThreadConnection(info) ?: return@launch

                //スレッドの選択を保存する
                appPrefs.userSelectedContactUrlMap[contactUrl] = info.url

                boardConn = threadConn
                reloadThread()

            } catch (e: IOException) {
                postNetworkErrorMessage(e.localizedSystemMessage())
            }
        }
    }

    private fun postNetworkErrorMessage(s: String) {
        chatViewModel.networkMessage.postValue(s)
    }



}