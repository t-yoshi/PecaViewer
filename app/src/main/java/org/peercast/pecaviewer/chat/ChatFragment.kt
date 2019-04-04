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
import org.koin.androidx.viewmodel.ext.sharedViewModel
import org.peercast.pecaviewer.AppViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.databinding.FragmentChatBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


class ChatFragment : Fragment(), CoroutineScope, Toolbar.OnMenuItemClickListener {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val appViewModel by sharedViewModel<AppViewModel>()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()

    private val messageAdapter = PagedMessageAdapter {
        it == chatViewModel.lastMessage
    }
    private val threadAdapter = ThreadAdapter { thread, position ->
        Timber.d("selectThread: [$position] $thread")
        chatViewModel.presenter.onThreadSelect(position)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatViewModel.let { vm ->
            vm.messagePagedListLiveData.observe(this, Observer {
                messageAdapter.submitList(it)

            })
            vm.threadInfoLiveData.observe(this, Observer {
                threadAdapter.threads = it
                threadAdapter.notifyDataSetChanged()
            })
            vm.selectedThreadIndex.observe(this, Observer {
                threadAdapter.selectedPosition = it
            })
        }

        playerViewModel.channelContactUrl.observe(this, Observer { u ->
            chatViewModel.presenter.loadUrl(u)
        })

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return FragmentChatBinding.inflate(inflater, container, false).let {
            it.appViewModel = appViewModel
            it.chatViewModel = chatViewModel
            it.lifecycleOwner = this
            it.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vMessageList.layoutManager = LinearLayoutManager(view.context)
        vMessageList.adapter = messageAdapter
        vThreadList.layoutManager = LinearLayoutManager(view.context)
        vThreadList.adapter = threadAdapter

        vChatToolbar.inflateMenu(R.menu.menu_chat)
        vChatToolbar.setNavigationOnClickListener {
            chatViewModel.isThreadListVisible.run {
                value = value != true
            }
        }
        vChatToolbar.setOnMenuItemClickListener(this)

        vMessageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                chatViewModel.isToolbarVisible.value = true
            }
        })
        vMessageList.setOnClickListener {
            chatViewModel.isToolbarVisible.value = true
        }
        vMessageListRefresh.setOnRefreshListener {
            messageAdapter.currentList?.dataSource?.invalidate()
        }

        vThreadListRefresh.setOnRefreshListener {
            chatViewModel.presenter.reload()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                messageAdapter.currentList?.dataSource?.invalidate()
            }
            R.id.menu_align_top -> {
                vMessageList.scrollToPosition(0)
            }
            R.id.menu_align_bottom -> {
                val n = messageAdapter.itemCount
                if (n > 0)
                    vMessageList.scrollToPosition(n - 1)
            }
        }
        return true
    }


    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

}
