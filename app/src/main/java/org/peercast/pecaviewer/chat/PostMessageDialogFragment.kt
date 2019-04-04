package org.peercast.pecaviewer.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_witedialog.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net.ChatThreadConnection
import org.peercast.pecaviewer.chat.net.PostMessage
import kotlin.coroutines.CoroutineContext

class PostMessageDialogFragment : BottomSheetDialogFragment(), DialogInterface.OnShowListener, CoroutineScope {
    private val job = Job()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private var threadConnection : ChatThreadConnection? = null
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatViewModel.selectedThreadConnection.value?.let {
            threadConnection = it
        } ?: dismiss()

        //Timber.d("$threadUrl: draft=${chatViewModel.postMessageDraft[threadUrl]}")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also { d ->
            d.setContentView(R.layout.fragment_witedialog)
            d.setOnShowListener(this)
        }
    }

    private val threadUrl: String
        get() = threadConnection?.threadInfo?.browseableUrl ?: ""


    override fun onShow(dialog: DialogInterface) {
        with(dialog as BottomSheetDialog) {
            chatViewModel.postMessageDraft[threadUrl]?.let(vEdit::setText)
            vSend.setOnClickListener {
                launch {
                    threadConnection?.postMessage(PostMessage("", "sage", vEdit.text.toString()))
                    chatViewModel.postMessageDraft.remove(threadUrl)
                    dismiss()
                }
            }
            vEdit.requestFocus()

            vEdit.doOnTextChanged { text, start, count, after ->
                chatViewModel.postMessageDraft[threadUrl] = text?.toString() ?: ""
                vSend.isEnabled = text?.isNotEmpty() == true
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        chatViewModel.postMessageDraft[threadUrl].let {
            if (it == null || it.isBlank())
                chatViewModel.postMessageDraft.remove(threadUrl)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

}