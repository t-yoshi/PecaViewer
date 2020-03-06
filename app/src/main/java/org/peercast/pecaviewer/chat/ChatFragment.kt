package org.peercast.pecaviewer.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_chat.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
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

    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()

    private val threadAdapter = ThreadAdapter()
    private val messageAdapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return FragmentChatBinding.inflate(inflater, container, false).also {
            it.viewModel = chatViewModel
            it.lifecycleOwner = viewLifecycleOwner
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vThreadList.layoutManager = LinearLayoutManager(view.context)
        vThreadList.adapter = threadAdapter

        vMessageList.layoutManager = LinearLayoutManager(view.context)
        vMessageList.adapter = messageAdapter

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
        vThreadListRefresh.setOnRefreshListener {
            launch {
                chatViewModel.presenter.reload()
            }
        }
        vMessageListRefresh.setOnRefreshListener {
            launch {
                chatViewModel.presenter.reloadThread()
            }
        }
        threadAdapter.onSelectThread = { info ->
            launch {
                chatViewModel.presenter.threadSelect(info)
            }
        }

        val owner = viewLifecycleOwner
        playerViewModel.channelContactUrl.observe(owner, Observer { u ->
            launch {
                chatViewModel.presenter.loadUrl(u)
            }
        })
        chatViewModel.threadLiveData.observe(owner, Observer {
            threadAdapter.items = it
        })
        chatViewModel.selectedThread.observe(owner, Observer {
            threadAdapter.selected = it
        })
        chatViewModel.messageLiveData.observe(viewLifecycleOwner, Observer {
            messageAdapter.setItems(it)
            scrollToBottom()
        })

        chatViewModel.snackbarMessage.observe(owner, SnackbarObserver(view, activity?.vPostDialogButton))

        savedInstanceState?.let(messageAdapter::restoreInstanceState)
    }

    private class SnackbarObserver(val view: View, val anchor: View?) : Observer<SnackbarMessage> {
        var bar: Snackbar? = null
        override fun onChanged(msg: SnackbarMessage?) {
            if (msg == null) {
                bar?.dismiss()
                bar = null
                return
            }

            val length = when {
                msg.cancelJob != null -> Snackbar.LENGTH_INDEFINITE
                else ->Snackbar.LENGTH_LONG
            }

            bar = Snackbar.make(view, msg.text, length).also { bar ->
                val c = bar.context
                msg.cancelJob?.let { j ->
                    bar.setAction(msg.cancelText ?: c.getText(android.R.string.cancel)) {
                        j.cancel()
                    }
                }
                if (msg.textColor != 0)
                    bar.setTextColor(ContextCompat.getColor(c, msg.textColor))
                //FABの上に出すのが正統
                anchor?.let(bar::setAnchorView)
                bar.show()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reload -> {
                launch {
                    when (chatViewModel.isThreadListVisible.value) {
                        true -> chatViewModel.presenter.reload()
                        else -> chatViewModel.presenter.reloadThread()
                    }
                }
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
        val n = messageAdapter.itemCount
        if (n > 0) {
            val manager = vMessageList.layoutManager as LinearLayoutManager
            manager.scrollToPositionWithOffset(n - 1, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        messageAdapter.saveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

}


