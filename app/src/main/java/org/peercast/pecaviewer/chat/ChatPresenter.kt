package org.peercast.pecaviewer.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.peercast.pecaviewer.AppPreference
import org.peercast.pecaviewer.chat.net.ChatConnection
import org.peercast.pecaviewer.chat.net.ChatThreadConnection
import org.peercast.pecaviewer.chat.net.MessageBody
import org.peercast.pecaviewer.chat.net.openChatConnection
import timber.log.Timber
import java.io.IOException

class ChatPresenter(private val chatViewModel: ChatViewModel) : KoinComponent {
    private val appPrefs by inject<AppPreference>()
    private var chatConn: ChatConnection? = null

    /**[ChatViewModel.threadInfoLiveData] の中からスレッドを選択したら呼び出す。*/
    var onThreadSelect: (index: Int) -> Unit = {}
        private set

    private var loadingJob: Job? = null

    //コンタクトURL。配信者が更新しないかぎり変わらない。
    private var contactUrl = ""

    /**スレッドのリストを含め、全体を再読込する。*/
    fun reload() {
        loadUrl(contactUrl, true)
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
            if (alternateUrl != url){
                Timber.i("alternate contact url: $alternateUrl")
            }
            loadingJob = internalLoadUrl(alternateUrl)
        }
    }

    private fun internalLoadUrl(url: String) = chatViewModel.viewModelScope.launch {
        Timber.d("internalLoadUrl: $url")

        try {
            chatViewModel.isThreadListRefreshing.value = true
            val baseConn = openChatConnection(url)

            val index = baseConn.threadConnections.indexOf(baseConn)

            chatViewModel.selectedThreadIndex.value = index
            chatViewModel.threadInfoLiveData.value = baseConn.threadConnections.map { it.threadInfo }
            chatViewModel.chatToolbarTitle.value = baseConn.baseInfo.title

            onThreadSelect = {
                val threadConn = baseConn.threadConnections.getOrNull(it)
                if (threadConn is ChatThreadConnection) {
                    //Timber.d("Thread selected: $thread")
                    chatViewModel.chatToolbarTitle.value = threadConn.threadInfo.title
                    chatViewModel.selectedThreadConnection.value = if (threadConn.isPostable) threadConn else null
                    //スレッドの選択を保存する
                    appPrefs.userSelectedContactUrlMap[contactUrl] = threadConn.threadInfo.browseableUrl

                    if (chatConn != threadConn)
                        chatViewModel.lastMessage = null

                    chatConn = threadConn
                } else {
                    chatConn = null
                    chatViewModel.selectedThreadConnection.value = null
                    Timber.w("Thread not selected: $url")
                }

                chatViewModel.messagePagedListLiveData.value?.dataSource?.invalidate()
            }

            if (index >= 0) {
                onThreadSelect(index)
            }

        } catch (e: IOException) {
            Timber.e(e)
        } finally {
            chatViewModel.isThreadListRefreshing.value = false
        }
    }

    /**@see [ChatViewModel.messagePagedListLiveData]*/
    fun createMessageLiveData(): LiveData<PagedList<MessageBody>> {
        return LivePagedListBuilder(
            BBSDataSourceFactory(chatViewModel.viewModelScope, chatViewModel.isMessageListRefreshing) {
                chatConn as? ChatThreadConnection
            }, PAGED_LIST_CONFIG
        ).setBoundaryCallback(object : PagedList.BoundaryCallback<MessageBody>(){
            override fun onItemAtEndLoaded(itemAtEnd: MessageBody) {
                chatViewModel.lastMessage = itemAtEnd
            }
        }) .build()
    }

    companion object {
        private val PAGED_LIST_CONFIG = PagedList.Config.Builder()
            .setInitialLoadSizeHint(5)
            .setPageSize(200)
            .setEnablePlaceholders(false)
            .build()
    }

}