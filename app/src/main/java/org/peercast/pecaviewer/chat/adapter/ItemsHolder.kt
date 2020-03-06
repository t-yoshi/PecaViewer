package org.peercast.pecaviewer.chat.adapter

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

open class ItemsHolder<T> {
    private var oldItems = emptyList<T>()
    private var newItems = emptyList<T>()

    private val callback = object : DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldItems[oldItemPosition] == newItems[newItemPosition]
        }

        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return areContentsTheSame(oldItems[oldItemPosition], newItems[newItemPosition])
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            return getChangePayload(oldItems[oldItemPosition], newItems[newItemPosition])
        }
    }

    protected open fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem == newItem
    }

    protected open fun getChangePayload(oldItem: T, newItem: T): Any? = null


    fun update(items: List<T>, adapter: RecyclerView.Adapter<*>) {
        oldItems = newItems
        newItems = items
        DiffUtil.calculateDiff(callback).dispatchUpdatesTo(adapter)
    }

    operator fun get(index: Int) = newItems[index]

    val size: Int get() = newItems.size
}