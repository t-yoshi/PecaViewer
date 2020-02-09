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
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net2.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net2.PostMessage
import org.peercast.pecaviewer.util.localizedSystemMessage
import java.io.IOException
import kotlin.coroutines.CoroutineContext

class PostMessageDialogFragment : BottomSheetDialogFragment(), DialogInterface.OnShowListener,
    CoroutineScope {
    private val job = Job()
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private lateinit var poster: IBoardThreadPoster

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        poster = chatViewModel.selectedThreadPoster.value ?: return dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also { d ->
            d.setContentView(R.layout.fragment_witedialog)
            d.setOnShowListener(this)
        }
    }

    override fun onShow(dialog: DialogInterface) {
        val u = poster.info.url
        with(dialog as BottomSheetDialog) {
            chatViewModel.messageDraft[u]?.let(vEdit::setText)
            vSend.setOnClickListener {
                launch {
                    postMessage("", null, vEdit.text.toString())
                    chatViewModel.messageDraft.remove(u)
                    dismiss()
                }
            }
            vEdit.hint = "${poster.info.title} (${poster.info.numMessages})"
            vEdit.requestFocus()

            vEdit.doOnTextChanged { text, start, count, after ->
                chatViewModel.messageDraft[u] = text?.toString() ?: ""
                vSend.isEnabled = text?.isNotEmpty() == true
            }
        }
    }

    private suspend fun postMessage(name: String, mail: String?, body: String) {
        chatViewModel.networkMessage.value = try {
            poster.postMessage(
                PostMessage(name, mail ?: "sage", body)
            )
        } catch (e: IOException) {
            e.localizedSystemMessage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

}