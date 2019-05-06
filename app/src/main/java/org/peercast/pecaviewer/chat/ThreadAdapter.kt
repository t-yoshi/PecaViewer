package org.peercast.pecaviewer.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.BaseObservable
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.chat.net.ChatThreadConnection
import org.peercast.pecaviewer.databinding.BbsThreadItemBinding
import kotlin.properties.Delegates

class ThreadAdapter(private val onSelectThread: (ChatThreadConnection.Info, Int)->Unit) : RecyclerView.Adapter<ThreadAdapter.ViewHolder>() {
    var threads: List<ChatThreadConnection.Info> = emptyList()
    var selectedPosition by Delegates.observable(-1) { _, oldVal, newVal ->
        notifyItemChanged(oldVal)
        notifyItemChanged(newVal)
    }

    class ViewHolder(binding: BbsThreadItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val viewModel = ViewModel()

        init {
            binding.viewModel = viewModel
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = BbsThreadItemBinding.inflate(inflater, parent, false)


        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = threads.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val thread = threads[position]
        holder.itemView.setOnClickListener {
            selectedPosition = position
            onSelectThread(thread, position)
        }
        holder.viewModel.run {
            number = "% 2d".format(position + 1)
            title = thread.title
            count = thread.messageCount.toString()
            isSelected = position == selectedPosition
            notifyChange()
        }
    }

    class ViewModel : BaseObservable() {
        var number: CharSequence = ""
        var title: CharSequence = ""
        var count: CharSequence = ""
        var isSelected   = false
    }
}