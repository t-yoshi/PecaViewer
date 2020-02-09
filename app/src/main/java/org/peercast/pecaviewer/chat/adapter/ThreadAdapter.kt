package org.peercast.pecaviewer.chat.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.BaseObservable
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.chat.net2.IThreadInfo
import org.peercast.pecaviewer.databinding.BbsThreadItemBinding

class ThreadAdapter : RecyclerView.Adapter<ThreadAdapter.ViewHolder>() {
    var items = emptyList<IThreadInfo>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    var onSelectThread: ((IThreadInfo) -> Unit)? = null

    var selected: IThreadInfo? = null
        set(value) {
            if (field == value)
                return
            field = value
            notifyDataSetChanged()
        }

    class ViewHolder(binding: BbsThreadItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val viewModel =
            ViewModel()

        init {
            binding.viewModel = viewModel
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = BbsThreadItemBinding.inflate(inflater, parent, false)
        return ViewHolder(
            binding
        )
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = items[position]
        holder.itemView.setOnClickListener {
            selected = thread
            onSelectThread?.invoke(thread)
        }
        holder.viewModel.run {
            number = "% 2d".format(position + 1)
            title = thread.title
            count = thread.numMessages.toString()
            isSelected = thread == selected
            notifyChange()
        }
    }

    class ViewModel : BaseObservable() {
        var number: CharSequence = ""
        var title: CharSequence = ""
        var count: CharSequence = ""
        var isSelected = false
    }
}
