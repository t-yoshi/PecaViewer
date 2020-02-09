package org.peercast.pecaviewer.chat.adapter

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaviewer.R
import org.peercast.pecaviewer.chat.net2.IMessage
import org.peercast.pecaviewer.chat.net2.PostMessage
import org.peercast.pecaviewer.databinding.BbsMessageItemSimple2Binding
import timber.log.Timber


class MessageAdapter : RecyclerView.Adapter<MessageAdapter.BaseViewHolder>(),
    PopupSpan.SupportAdapter {

    private val items = ArrayList<IMessage>()

    //前回の最後尾
    private var prevLastItem: IMessage? = null

    fun setItems(newItems: List<IMessage>) {
        if (items != newItems || prevLastItem == null)
            prevLastItem = items.lastOrNull { it !== ITEM_SPACER }
        items.clear()
        items.addAll(newItems)

        //前回の最後尾にスペーサーを入れる
        when (val i = items.indexOfLast { it == prevLastItem }) {
            -1 -> items.add(ITEM_SPACER)
            else -> items.add(i + 1, ITEM_SPACER)
        }

        notifyDataSetChanged()
    }

    /**簡易表示、または詳細表示。*/
    var defaultViewType = SIMPLE
        set(value) {
            if (value !in arrayOf(SIMPLE, BASIC))
                throw IllegalArgumentException("not support viewType: $value")
            val changed = field != value
            field = value
            if (changed)
                notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return VIEW_HOLDER_FACTORIES[viewType](inflater, parent)
    }


    private abstract class ViewHolderFactory {
        abstract operator fun invoke(inflater: LayoutInflater, parent: ViewGroup): BaseViewHolder
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        protected val viewModel = ListItemViewModel()

        init {
            itemView.findViewById<TextView?>(R.id.vBody)?.movementMethod =
                LinkMovementMethod.getInstance()
        }

        open fun bind(msg: IMessage) {
            viewModel.setMessage(msg)
            viewModel.notifyChange()
        }
    }

    private class SimpleViewHolder(binding: BbsMessageItemSimple2Binding) :
        BaseViewHolder(binding.root) {
        init {
            binding.viewModel = viewModel
        }
    }

    override fun createViewForPopupWindow(resNumber: Int, parent: ViewGroup): View? {
        val m = items.lastOrNull { it.number == resNumber }
        return if (m != null) {
            val inflater = LayoutInflater.from(parent.context)
            val vh = VIEW_HOLDER_FACTORIES[defaultViewType](inflater, parent)
            vh.bind(m)
            vh.itemView
        } else {
            Timber.w("#$resNumber is not found")
            null
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        if (item === ITEM_SPACER)
            return SEPARATOR
        return defaultViewType
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    companion object {
        private val ITEM_SPACER: IMessage = PostMessage("", "", "")

        /**簡易表示*/
        const val SIMPLE = 0

        /**TODO 詳細表示*/
        const val BASIC = 1

        private const val SEPARATOR = 2

        private val VIEW_HOLDER_FACTORIES = arrayOf(
            object : ViewHolderFactory() { //0: SIMPLE
                override fun invoke(inflater: LayoutInflater, parent: ViewGroup): BaseViewHolder {
                    val binding = BbsMessageItemSimple2Binding.inflate(inflater, parent, false)
                    return SimpleViewHolder(binding)
                }
            },
            object : ViewHolderFactory() { //1: BASIC
                override fun invoke(inflater: LayoutInflater, parent: ViewGroup): BaseViewHolder {
                    TODO("掲示板のような表示")
                }
            },
            object : ViewHolderFactory() { //2: SEPARATOR
                override fun invoke(inflater: LayoutInflater, parent: ViewGroup): BaseViewHolder {
                    val v = inflater.inflate(R.layout.bbs_message_item_separator, parent, false)
                    return BaseViewHolder(v)
                }
            }

        )
    }
}


