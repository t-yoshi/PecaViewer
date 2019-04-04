package org.peercast.pecaviewer.chat

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.bbs_message_item_simple.view.*
import org.peercast.pecaviewer.chat.net.MessageBody
import org.peercast.pecaviewer.databinding.BbsMessageItemSimpleBinding

class PagedMessageAdapter(private val getMarker: (MessageBody) -> Boolean) :
    PagedListAdapter<MessageBody, PagedMessageAdapter.BaseViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = BbsMessageItemSimpleBinding.inflate(inflater, parent, false)
        binding.root.vBody.movementMethod = LinkMovementMethod.getInstance()

        binding.root.vBody.autoLinkMask
        return SimpleViewHolder(binding)
    }

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val viewModel = ListItemViewModel()
        abstract fun bind(position: Int, msg: MessageBody)
    }

    inner class SimpleViewHolder(binding: BbsMessageItemSimpleBinding) : BaseViewHolder(binding.root) {
        init {
            binding.viewModel = viewModel
        }

        override fun bind(position: Int, msg: MessageBody) {
            with(viewModel) {
                isLast = getMarker(msg)
                viewModel.setMessage(msg)
                notifyChange()
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).hashCode().toLong()
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        getItem(position)?.let {
            holder.bind(position, it)
        }
    }


    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MessageBody>() {
            override fun areItemsTheSame(oldItem: MessageBody, newItem: MessageBody): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: MessageBody, newItem: MessageBody): Boolean {
                return oldItem == newItem
            }
        }
    }
}
