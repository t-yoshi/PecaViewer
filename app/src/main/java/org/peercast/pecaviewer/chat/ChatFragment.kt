package org.peercast.pecaviewer.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_chat.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.AppPreference
import org.peercast.pecaviewer.AppViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.adapter.MessageAdapter
import org.peercast.pecaviewer.chat.adapter.ThreadAdapter
import org.peercast.pecaviewer.databinding.FragmentChatBinding
import org.peercast.pecaviewer.player.PlayerViewModel
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

@Suppress("unused")
class ChatFragment : Fragment(), CoroutineScope, Toolbar.OnMenuItemClickListener {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private val playerViewModel by sharedViewModel<PlayerViewModel>()
    private val appViewModel by sharedViewModel<AppViewModel>()
    private val appPrefs by inject<AppPreference>()

    private val threadAdapter = ThreadAdapter()
    private val messageAdapter = MessageAdapter()
    private var isAlreadyRead = false //既読
    private val autoReload = AutoReload()
    private var loadingJob: Job? = null
    private val loadingLiveData = MutableLiveData<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoReload.isEnabled = appPrefs.isAutoReloadEnabled
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        job = Job()
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

        vChatToolbar.inflateMenu(R.menu.menu_chat_thread)
        vChatToolbar.setNavigationOnClickListener {
            chatViewModel.isThreadListVisible.run {
                value = value != true
            }
        }
        vChatToolbar.setOnMenuItemClickListener(this)
        vChatToolbar.overflowIcon = vChatToolbar.context.getDrawable(R.drawable.ic_more_vert_black_24dp)

        vMessageList.setOnClickListener {
            chatViewModel.isToolbarVisible.value = true
        }
        vMessageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    //最後までスクロールしたらすべて既読とみなす
                    Timber.d("AlreadyRead!")
                    isAlreadyRead = true
                    autoReload.isEnabled = appPrefs.isAutoReloadEnabled
                    autoReload.scheduleRun()
                }

                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    if (recyclerView.context.resources.getBoolean(R.bool.isNarrowScreen)) {
                        //狭い画面ではスクロール中にFABを消す。そして数秒後に再表示される。
                        appViewModel.isPostDialogButtonFullVisible.value = false
                    }
                    //過去のレスを見ているときは自動リロードを無効にする
                    if (recyclerView.canScrollVertically(1))
                        autoReload.isEnabled = false
                }
            }
        })

        vThreadListRefresh.setOnRefreshListener {
            launchLoading {
                chatViewModel.presenter.reload()
            }
        }
        vMessageListRefresh.setOnRefreshListener {
            isAlreadyRead = true
            launchLoading {
                chatViewModel.presenter.reloadThread()
            }
        }
        threadAdapter.onSelectThread = { info ->
            launchLoading {
                chatViewModel.presenter.threadSelect(info)
            }
        }

        playerViewModel.channelContactUrl.observe(viewLifecycleOwner, Observer { u ->
            loadingJob?.cancel("new url is coming $u")
            launchLoading {
                chatViewModel.presenter.loadUrl(u)
            }
        })
        chatViewModel.threadLiveData.observe(viewLifecycleOwner, Observer {
            threadAdapter.items = it
        })
        chatViewModel.selectedThread.observe(viewLifecycleOwner, Observer {
            threadAdapter.selected = it
            if (it == null)
                chatViewModel.isThreadListVisible.postValue(true)
        })
        chatViewModel.messageLiveData.observe(viewLifecycleOwner, Observer {
            val b = isAlreadyRead
            Timber.d("isAlreadyRead=$isAlreadyRead")
            if (b)
                messageAdapter.markAlreadyAllRead()
            isAlreadyRead = false
            launch {
                messageAdapter.setItems(it)
                if (true || b)
                    scrollToBottom()
            }
            autoReload.scheduleRun()
        })

        chatViewModel.snackbarMessage.observe(
            viewLifecycleOwner,
            SnackbarObserver(view, activity?.vPostDialogButton)
        )

        chatViewModel.isThreadListVisible.observe(viewLifecycleOwner, Observer {
            vChatToolbar.menu.clear()
            if (it) {
                vChatToolbar.inflateMenu(R.menu.menu_chat_board)
                vChatToolbar.menu.findItem(R.id.menu_auto_reload_enabled).isChecked =
                    appPrefs.isAutoReloadEnabled
            } else {
                vChatToolbar.inflateMenu(R.menu.menu_chat_thread)
            }
        })

        loadingLiveData.observe(viewLifecycleOwner, Observer {
            with(vChatToolbar.menu){
                findItem(R.id.menu_reload).isVisible = !it
                findItem(R.id.menu_abort).isVisible = it
            }
        })

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
                else -> Snackbar.LENGTH_LONG
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
                isAlreadyRead = true
                launchLoading {
                    when (chatViewModel.isThreadListVisible.value) {
                        true -> chatViewModel.presenter.reload()
                        else -> chatViewModel.presenter.reloadThread()
                    }
                }
            }
            R.id.menu_abort -> {
                loadingJob?.cancel("abort button clicked")
            }
            R.id.menu_align_top -> {
                vMessageList.scrollToPosition(0)
            }
            R.id.menu_align_bottom -> {
                scrollToBottom()
            }
            R.id.menu_auto_reload_enabled -> {
                val b = !item.isChecked
                item.isChecked = b
                appPrefs.isAutoReloadEnabled = b
                autoReload.isEnabled = b
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

    override fun onResume() {
        super.onResume()
        //復帰時に再描画
        messageAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadingJob = null
        job.cancel("view destroyed")
    }


    private fun launchLoading(block: suspend CoroutineScope.() -> Unit) {
        if (loadingJob?.run { isActive && !isCancelled } == true) {
            Timber.d("loadingJob [$loadingJob] is still active.")
            return
        }
        autoReload.cancelScheduleRun()
        loadingJob = launch {
            loadingLiveData.postValue(true)
            try {
                block()
            } finally {
                loadingLiveData.postValue(false)
            }
        }
    }

    /**
     * スレッドの自動読込。
     * */
    private inner class AutoReload {
        private var j: Job? = null
        private var f = {}

        fun scheduleRun() = f()

        fun cancelScheduleRun() {
            j?.cancel()
        }

        var isEnabled = false
            set(value) {
                if (field == value)
                    return
                field = value
                if (value) {
                    f = {
                        j?.cancel()
                        j = launch {
                            Timber.d("Set auto-reloading after ${AUTO_RELOAD_SEC}seconds.")
                            delay(AUTO_RELOAD_SEC * 1000L)
                            Timber.d("Start auto-reloading.")
                            j = null
                            launchLoading {
                                chatViewModel.presenter.reloadThread()
                            }
                        }
                    }
                } else {
                    j?.cancel()
                    f = {}
                }
            }
    }

    companion object {
        private const val AUTO_RELOAD_SEC = 60
    }
}


