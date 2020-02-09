package org.peercast.pecaviewer.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_chat.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.AppViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.adapter.MessageAdapter
import org.peercast.pecaviewer.chat.adapter.ThreadAdapter
import org.peercast.pecaviewer.databinding.FragmentChatBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import kotlin.coroutines.CoroutineContext


class ChatFragment : Fragment(), CoroutineScope, Toolbar.OnMenuItemClickListener {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val appViewModel by sharedViewModel<AppViewModel>()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()

    private val threadAdapter =
        ThreadAdapter()
    private val messageAdapter =
        MessageAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playerViewModel.channelContactUrl.observe(this, Observer { u ->
            chatViewModel.presenter.loadUrl(u)
        })
        chatViewModel.threadLiveData.observe(this, Observer {
            threadAdapter.items = it
        })
        chatViewModel.selectedThread.observe(this, Observer {
            threadAdapter.selected = it
        })
        chatViewModel.messageLiveData.observe(this, Observer {
            messageAdapter.setItems(it)
            scrollToBottom()
        })
        threadAdapter.onSelectThread = chatViewModel.presenter::threadSelect
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return FragmentChatBinding.inflate(inflater, container, false).let {
            it.appViewModel = appViewModel
            it.chatViewModel = chatViewModel
            it.lifecycleOwner = viewLifecycleOwner
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vMessageList.layoutManager = LinearLayoutManager(view.context)
        vThreadList.layoutManager = LinearLayoutManager(view.context)
        vThreadList.adapter = threadAdapter

        vChatToolbar.inflateMenu(R.menu.menu_chat)
        vChatToolbar.setNavigationOnClickListener {
            chatViewModel.isThreadListVisible.run {
                value = value != true
            }
        }
        vChatToolbar.setOnMenuItemClickListener(this)

        vMessageList.adapter = messageAdapter
        vMessageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                chatViewModel.isToolbarVisible.value = true
            }
        })
        vMessageList.setOnClickListener {
            chatViewModel.isToolbarVisible.value = true
        }
        vThreadListRefresh.setOnRefreshListener {
            chatViewModel.presenter.reload()
        }
        vMessageListRefresh.setOnRefreshListener {
            chatViewModel.presenter.reloadThread()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                chatViewModel.presenter.reloadThread()
            }
            R.id.menu_align_top -> {
                vMessageList.scrollToPosition(0)
            }
            R.id.menu_align_bottom -> {
                scrollToBottom()
            }
        }
        return true
    }

    private fun scrollToBottom() {
        val n = vMessageList?.adapter?.itemCount ?: 0
        if (n > 0) {
            val manager = vMessageList?.layoutManager as? LinearLayoutManager
            manager?.scrollToPositionWithOffset(n - 1, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

}


