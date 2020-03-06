package org.peercast.pecaviewer.chat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.widget.doOnTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_post_message_dialog.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net2.IBoardThreadPoster
import org.peercast.pecaviewer.chat.net2.PostMessage

class PostMessageDialogFragment : BottomSheetDialogFragment(),
    DialogInterface.OnShowListener {
    private val chatViewModel by sharedViewModel<ChatViewModel>()
    private lateinit var poster: IBoardThreadPoster

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        poster = chatViewModel.selectedThreadPoster.value ?: return dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also { d ->
            d.setContentView(R.layout.fragment_post_message_dialog)
            d.setOnShowListener(this)
        }
    }

    override fun onShow(dialog: DialogInterface) {
        val u = poster.info.url
        with(dialog as BottomSheetDialog) {
            chatViewModel.messageDraft[u]?.let(vEdit::setText)
            vSend.setOnClickListener {
                vEdit.isEnabled = false
                vSend.isEnabled = false
                chatViewModel.presenter.postMessage(
                    poster,
                    PostMessage("", "sage", vEdit.text.toString())
                )
                chatViewModel.messageDraft.remove(u)
                dismiss()
            }
            vEdit.hint = "${poster.info.title} (${poster.info.numMessages})"
            vEdit.requestFocus()

            vEdit.doOnTextChanged { text, start, count, after ->
                chatViewModel.messageDraft[u] = text?.toString() ?: ""
                vSend.isEnabled = text?.isNotEmpty() == true
            }
        }
    }

}