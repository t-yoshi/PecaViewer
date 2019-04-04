package org.peercast.pecaviewer.chat.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.chat.ListItemViewModel
import org.peercast.pecaviewer.chat.net.MessageBody
import org.peercast.pecaviewer.databinding.BbsMessageItemSimpleBinding

class MessageBodyAdapter : RecyclerView.Adapter<MessageBodyAdapter.BaseViewHolder>() {
    init {
        setHasStableIds(true)
    }

    var messages: List<MessageBody> = emptyList()



    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val viewModel = ListItemViewModel()
        abstract fun bind(msg: MessageBody)
    }

    class SimpleViewHolder( binding: BbsMessageItemSimpleBinding) : BaseViewHolder(binding.root){
        init {
            binding.viewModel = viewModel
        }
        override fun bind(msg: MessageBody) {
            viewModel.setMessage(msg)
            viewModel.notifyChange()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder{
        val inflater = LayoutInflater.from(parent.context)
        val binding = BbsMessageItemSimpleBinding.inflate(inflater, parent, false)
        return SimpleViewHolder(binding)
    }

    override fun getItemCount(): Int  = messages.size

    override fun getItemId(position: Int): Long {
        return messages[position].run {
            number.hashCode() * 31L + body.hashCode()
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(messages[position])
    }
}