package org.peercast.pecaviewer.chat.adapter

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.bbs_message_item_simple.view.*
import org.peercast.pecaviewer.BR
import org.peercast.pecaviewer.chat.net2.IBrowsable
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.chat.net2.PostMessage
import org.peercast.pecaviewer.databinding.BbsMessageItemBasicBinding
import org.peercast.pecaviewer.databinding.BbsMessageItemSeparatorBinding
import org.peercast.pecaviewer.databinding.BbsMessageItemSimpleBinding
import timber.log.Timber
import kotlin.properties.Delegates


class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>(),
    PopupSpan.SupportAdapter {

    private var itemsOrigin = emptyList<IMessage>()
    private var itemsHolder = object : ItemsHolder<IMessage>() {
        override fun areContentsTheSame(oldItem: IMessage, newItem: IMessage): Boolean {
            //"n分前"の表示は更新したい
            if (defaultViewType == SIMPLE)
                return false
            return super.areContentsTheSame(oldItem, newItem)
        }

        override fun getChangePayload(oldItem: IMessage, newItem: IMessage): Any? {
            if (defaultViewType == SIMPLE)
                return 1
            return null
        }
    }

    //前回最後尾のurl
    private var prevLastItem: String? = null

    @MainThread
    suspend fun setItems(newItems: List<IMessage>) {
        val items = newItems.toMutableList()
        val threadChanged = newItems.firstOrNull()?.threadInfo !=
                itemsOrigin.firstOrNull()?.threadInfo
        itemsOrigin = newItems

        //前回の最後尾にスペーサーを入れる
        when (val i = items.indexOfLast { (it as? IBrowsable)?.url == prevLastItem }) {
            -1 -> items.add(ITEM_SPACER)
            else -> items.add(i + 1, ITEM_SPACER)
        }

        if (prevLastItem == null)
            markAlreadyAllRead()

        if (threadChanged)
            itemsHolder.clear(this)

        itemsHolder.asyncUpdate(items, this)
    }

    /**全て既読のフラグ*/
    fun markAlreadyAllRead(){
        prevLastItem = (itemsOrigin.lastOrNull() as? IBrowsable)?.url
    }

    /**簡易表示、または詳細表示。*/
    var defaultViewType by Delegates.observable(SIMPLE) { _, oldVal, newVal ->
        if (newVal !in arrayOf(SIMPLE, BASIC))
            throw IllegalArgumentException("not support viewType: $newVal")
        if (newVal != oldVal)
            notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(
            DATA_BINDING_INFLATES[viewType](inflater, parent, false)
        )
    }

    class ViewHolder(val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        val viewModel = MessageViewModel()

        init {
            if (!binding.setVariable(BR.viewModel, viewModel))
                throw RuntimeException("Nothing defined viewModel in layout.")
            itemView.vBody?.movementMethod = LinkMovementMethod.getInstance()
            itemView.vThumbnail?.adapter = ThumbnailAdapter()
        }
    }

    override fun createViewForPopupWindow(resNumber: Int, parent: ViewGroup): View? {
        val m = itemsOrigin.lastOrNull { it.number == resNumber }
        if (m == null) {
            Timber.w("#$resNumber is not found")
            return null
        }

        val inflater = LayoutInflater.from(parent.context)
        val vh = ViewHolder(
            DATA_BINDING_INFLATES[defaultViewType](inflater, parent, false)
        )
        vh.viewModel.setMessage(m)
        return vh.itemView
    }

    override fun getItemCount(): Int = itemsHolder.size

    override fun getItemViewType(position: Int): Int {
        val item = itemsHolder[position]
        if (item === ITEM_SPACER)
            return SEPARATOR
        return defaultViewType
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.viewModel.setMessage(itemsHolder[position], defaultViewType == SIMPLE)
        holder.binding.executePendingBindings()
    }

    fun saveInstanceState(outState: Bundle) {
        outState.putString(STATE_LAST_ITEM_URL, prevLastItem)
    }

    fun restoreInstanceState(inState: Bundle) {
        prevLastItem = inState.getString(STATE_LAST_ITEM_URL)
    }

    companion object {
        private val ITEM_SPACER: IMessage = PostMessage("", "", "")

        /**簡易表示*/
        const val SIMPLE = 0

        /**TODO 詳細表示*/
        const val BASIC = 1

        private const val SEPARATOR = 2

        private val DATA_BINDING_INFLATES = listOf<ViewDataBinding_inflate>(
            BbsMessageItemSimpleBinding::inflate,
            BbsMessageItemBasicBinding::inflate,
            BbsMessageItemSeparatorBinding::inflate
        )

        private const val STATE_LAST_ITEM_URL =
            "org.peercast.pecaviewer.chat.adapter.MessageAdapter#LAST_ITEM_URL"
    }
}

private typealias ViewDataBinding_inflate = (LayoutInflater, ViewGroup, Boolean) -> ViewDataBinding

